package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.airoha.AirohaPeq
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAirohaEq
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqBand
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceChip
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser
import kotlin.math.roundToInt

/**
 * The conversation over Skullcandy's second serial channel, the Airoha RACE one (docs/skullcandy/PROTOCOL.md sections 11 and 13): Skullcandy's own custom commands (multipoint, wear
 * detection, the Crusher PLYR 720's bass boost) and the custom equaliser, written as Airoha's SDK writes a PEQ curve ([JblAirohaEq] with the library variant Skullcandy's own library is:
 * the Q as each band's third value and a strict comparator). A message is a RACE frame; the chip family is read first, as JBL's session reads it, since the PEQ's NV keys depend on it.
 */
public class SkullcandyRaceTalk(public val product: SkullcandyProduct) : SessionTalk<RacePacket>() {
    override val pacing: Pacing = Pacing(replyMs = 1500, gapMs = 100, startGapMs = 100, resends = 1)
    private var current = EarbudState()
    override val state: EarbudState get() = current

    /** True for the Rail and Rail ANC, whose SDK configurations put everything over this RACE channel: it is then the session's main one, with the battery and the hearing mode (section 16). */
    public val primary: Boolean = product.usesRail
    private val hasAncMode = primary && ("adjustableNoiseCancellation" in product.features || "activeNoiseCancellation" in product.features)
    private val hasTransparencyMode = primary && ("adjustableStayAwareMode" in product.features || "stayAwareMode" in product.features)
    private val own = HashSet<RacePacket>()
    private var railMode: String? = null

    /** The key map the earbuds hold, by side (1 left, 2 right): the entries of the power button. */
    private val buttons = LinkedHashMap<Int, List<RailButtons.Entry>>()
    private var voiceLanguage: String? = null
    private val hasButtons = primary && "customButtonActions" in product.features
    private var agentRight = false

    private val parser = RaceParser()
    private var pending: RacePacket? = null
    private val kinds = HashMap<RacePacket, String>()
    private var readBacks: List<RacePacket> = emptyList()
    private val outbox = ArrayList<RacePacket>()

    /** The chip family the earbuds named, null until they have. */
    private var chip: RaceChip? = null
    private var chipRead: RacePacket? = null

    /** The curve write in progress, whose commands [allowed] lets through. */
    private var write: JblAirohaEq? = null

    private var multipoint: Boolean? = null
    private var wearDetect: Boolean? = null
    private var bassBoost: Boolean? = null
    private var gainSlider: Double? = null
    private var gainFor: String? = null
    private var askedFor: String? = null

    /** The hearing mode in force (`anc`, `transparency` or `off`), as the AT conversation holds it; set by the driver. */
    public var mode: () -> String? = { null }

    private val airoha = product.chip == "airoha"

    /** True for the Crusher 1080 ANC, whose wear detection and equaliser are Bragi RPC's ([SkullcandyRpcTalk]); the other RPC models' equaliser is this channel's. */
    private val equaliserOverRpc = product.sdk == "CHP300"
    public val hasMultipoint: Boolean = airoha && ("multipointPairingToggle" in product.features || "multipointPairing" in product.features)
    public val hasWearDetect: Boolean = airoha && !product.usesRpc && "wearDetect" in product.features
    public val hasBassBoost: Boolean = airoha && "crusherBassBoost" in product.features

    /** True when the model has a custom equaliser the app sets through Airoha's PEQ. */
    public val hasCustomEq: Boolean = airoha && !equaliserOverRpc && "customEqualizer" in product.features

    /** True when the strength of the hearing mode in force is an Airoha RACE gain: the Crusher ANC 2, whose SDK configuration lists both gains over RACE, and the Rail models. */
    public val hasGain: Boolean = product.sdk == "CHP200" || (primary && (hasAncMode || hasTransparencyMode))

    /** True when the model has hearing modes this channel carries (the Rail models). */
    public val hasNoise: Boolean = hasAncMode || hasTransparencyMode

    /** The Auracast receiver of the Crusher 1080 ANC, whose SDK configuration lists `AURACAST_RECEIVER` on Airoha's RACE (section 18). */
    private val auracast: SkullcandyAuracast? = if (product.sdk == "CHP300") SkullcandyAuracast() else null
    public val hasAuracast: Boolean = auracast != null

    /** True when the model has anything this channel carries. */
    public val hasRaceFeatures: Boolean get() = hasMultipoint || hasWearDetect || hasBassBoost || hasCustomEq || hasGain || primary || hasAuracast

    init {
        if (primary) mode = { railMode }
    }

    override val eq: Any? get() = Triple(chip, eqPresetName, eqGains)

    /** The equaliser's bands in Hz and the gain range, for the Sound tab. */
    public val eqBandsHz: List<Int> = SkullcandyEq.bandsHz(product.enumName)
    public val eqRangeDb: Int = SkullcandyEq.rangeDb(product.enumName)

    /** The presets the earbuds hold, by the app's names (Music, Podcast, Movie, Bass Boost and the gaming ones). */
    public val eqPresets: List<Pair<String, Int>> = if (hasCustomEq) SkullcandyEq.presets(product.enumName) else emptyList()

    /** True once the earbuds have named their chip, which a curve's write needs. */
    public val chipKnown: Boolean get() = chip != null

    /** The gains last written to the earbuds in this connection, by band; null before a write. */
    public var eqGains: List<Double>? = null
        private set

    /** True once a curve has gone out and every step has been answered. */
    public val eqWritten: Boolean get() = write?.done == true

    override val groups: List<SettingGroup> get() = buildList {
        if (primary && buttons.isNotEmpty()) {
            val rows = ArrayList<Setting>()
            for ((side, entries) in buttons.entries.sortedBy { it.key }.map { it.key to it.value }) {
                for (g in RailButtons.GESTURES) {
                    val e = entries.firstOrNull { it.type == g.first } ?: continue
                    rows += Setting.Choice("sk_rbutton_${if (side == 1) "left" else "right"}_${g.second}", RailButtons.nameOf(e.event), RailButtons.OPTIONS)
                }
            }
            if (rows.isNotEmpty()) add(SettingGroup("sk_buttons", SettingTab.CONTROLS, rows))
        }
        if (primary && "voiceControl" in product.features) {
            val rows = ArrayList<Setting>()
            voiceLanguage?.let { rows += Setting.Information(VOICE_LANGUAGE, it) }
            rows += Setting.Action(VOICE_ON)
            rows += Setting.Action(VOICE_OFF)
            add(SettingGroup("sk_voice", SettingTab.DEVICE, rows))
        }
        auracast?.group()?.let { add(it) }
        if (primary) SkullcandyTile.group(product)?.let { add(it) }
        multipoint?.let { add(SettingGroup("connection", SettingTab.DEVICE, listOf(Setting.Toggle(MULTIPOINT, it)))) }
        wearDetect?.let { add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle(WEAR_DETECT, it)))) }
        bassBoost?.let { add(SettingGroup("sound", SettingTab.SOUND, listOf(Setting.Toggle(BASS_BOOST, it)))) }
        val m = mode()
        val s = gainSlider
        if (hasGain && s != null && gainFor == m && (m == "anc" || m == "transparency")) {
            add(SettingGroup("noise", SettingTab.HOME, listOf(Setting.Level(if (m == "anc") ANC_LEVEL else TRANSPARENCY_LEVEL, (s * 10).roundToInt(), 0, 10))))
        }
    }

    private fun packet(hex: String, kind: String): RacePacket {
        val p = RaceParser().feed(SkullcandyRace.bytes(hex)).single()
        kinds[p] = kind
        return p
    }

    private fun own(p: RacePacket): RacePacket = p.also { own += it }

    override fun start(): List<RacePacket> = buildList {
        if (primary) {
            // the batteries as the Airoha SDK reads them, and which earbud the phone talks to (the same reads JBL's Airoha models start with)
            add(own(RacePacket.of(JblRace.PUSH_STATUS, 1)))
            add(own(RacePacket.of(JblRace.AGENT_CHANNEL)))
            add(own(RacePacket.of(JblRace.BATTERY, 0)))
            add(own(RacePacket.of(JblRace.CASE_BATTERY)))
            if (hasNoise) add(own(RacePacket.mmiGet(JblRace.MMI_ANC)))
            if (hasButtons) for (side in 1..2) add(own(packet(RailButtons.readHex(side), "buttons$side")))
            if ("voiceControl" in product.features) add(own(packet(RailButtons.VOICE_LANGUAGE_GET, "voice_language")))
        }
        auracast?.let { add(own(it.readState())) }
        if (hasCustomEq) add(RacePacket.nvRead(JblRace.NV_CHIP_NAME, CHIP_NV_LENGTH).also { chipRead = it })
        if (hasMultipoint) add(packet(SkullcandyRace.multipointGet(product.enumName), "multipoint"))
        if (hasWearDetect) add(packet(SkullcandyRace.WEAR_DETECT_GET, "wear"))
        if (hasBassBoost) add(packet(SkullcandyRace.BASS_BOOST_GET, "bass"))
        if (hasGain) add(gainRead())
    }

    /** The read of the gain, for the mode in force now: the answer belongs to that mode. */
    public fun gainRead(): RacePacket {
        askedFor = mode()
        return packet(SkullcandyRace.GAIN_GET, "gain")
    }

    override fun poll(): List<RacePacket> = if (primary) listOf(own(RacePacket.of(JblRace.BATTERY, 0)), own(RacePacket.of(JblRace.CASE_BATTERY))) else emptyList()

    override fun allowed(message: RacePacket): Boolean =
        message == chipRead || message in own || write?.sent?.contains(message) == true || (primary && RailButtons.isAllowed(message)) || SkullcandyRace.isAllowed(message.bytes().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() })

    override fun bytes(message: RacePacket): ByteArray {
        pending = message
        return message.bytes()
    }

    override fun describe(message: RacePacket): String = message.toString()

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val answers = mutableListOf<Any>()
        for (p in parser.feed(bytes)) {
            guarded {
                val eq = write
                if (eq != null && !eq.done && !eq.failed && eq.receive(p) { outbox += it }) {
                    if (eq.done || eq.failed) changed = true
                    if (eq === reader && eq.done) changed = takeHeld(eq) || changed
                } else {
                    changed = (primary && railApply(p)) || changed
                    changed = apply(p) || changed
                }
            }
            answers += p
        }
        return Fed(changed, answers)
    }

    override fun answers(sent: RacePacket, got: Any): Boolean = got is RacePacket

    private fun u8(d: ByteArray, at: Int): Int = d[at].toInt() and 0xFF

    /**
     * What the Rail models' own reads and indications say (docs/skullcandy/PROTOCOL.md section 16): which earbud the phone talks to, the batteries, and the hearing mode. The mode byte of the
     * module read is the SDK's: 1 to 4 is noise cancelling on, 9 to 11 transparency on, anything else off.
     */
    private fun railApply(p: RacePacket): Boolean {
        if (p.type != 0x5B && p.type != 0x5D) return false
        val d = p.data
        when (p.id) {
            JblRace.AGENT_CHANNEL -> if (p.status == 0 && d.isNotEmpty()) { agentRight = u8(d, 0) == 1; return false }
            JblRace.BATTERY -> if (p.status == 0 && d.size >= 2) return battery(u8(d, 0), u8(d, 1))
            JblRace.CASE_BATTERY -> if (p.status == 0 && d.isNotEmpty()) return caseBattery(u8(d, 0))
            JblRace.MMI_GET -> if (p.status == 0 && d.isNotEmpty()) {
                return when (p.module) {
                    JblRace.MMI_ANC -> hearing(u8(d, 0))
                    JblRace.NOTIFY_AGENT_BATTERY -> { if (d.size >= 2) agentRight = u8(d, 1) == 1; battery(0, u8(d, 0)) }
                    JblRace.NOTIFY_PARTNER_BATTERY -> battery(1, u8(d, 0))
                    JblRace.NOTIFY_CASE_BATTERY -> caseBattery(u8(d, 0))
                    else -> false
                }
            }
        }
        return false
    }

    private fun battery(role: Int, level: Int): Boolean {
        if (level !in 0..100) return false
        val c = if ((role == 0) == agentRight) Command.BATTERY_RIGHT else Command.BATTERY_LEFT
        return replace(current.values + (c to Value.Number(level)))
    }

    private fun caseBattery(level: Int): Boolean = replace(current.values + (Command.BATTERY_CASE to Value.Number(level.takeIf { it in 1..100 })))

    private fun hearing(byte: Int): Boolean {
        if (!hasNoise) return false
        val label = when {
            hasAncMode && byte in 1..4 -> "anc"
            hasTransparencyMode && byte in 9..11 -> "transparency"
            else -> "off"
        }
        val before = railMode
        railMode = label
        // the strength belongs to the mode in force: read it again when the mode changed
        if (hasGain && label != before && (label == "anc" || label == "transparency")) outbox += gainRead()
        replace(current.values + (Command.ANC_MODE to Value.Number(Command.ANC_MODE.labels.entries.first { it.value == label }.key)))
        return true
    }

    private fun replace(next: Map<Command, Value>): Boolean {
        if (next == current.values) return false
        current = current.copy(values = next)
        return true
    }

    override fun set(command: Command, value: Int): List<RacePacket>? {
        if (!primary || command != Command.ANC_MODE || !hasNoise) return null
        val label = Command.ANC_MODE.labels[value] ?: return null
        val packet = when (label) {
            // the SDK's AB1562 forms: on is 00 0A <filter> <type> 01, off 00 0B 01 (filter 3 for noise cancelling, 4 for transparency)
            "off" -> RacePacket.of(JblRace.ANC, 0, JblRace.ANC_OFF, 1)
            "anc" -> if (hasAncMode) RacePacket.of(JblRace.ANC, 0, JblRace.ANC_ON, ANC_FILTER, JblRace.ANC_MODE_ANC, 1) else return null
            "transparency" -> if (hasTransparencyMode) RacePacket.of(JblRace.ANC, 0, JblRace.ANC_ON, TRANSPARENCY_FILTER, JblRace.ANC_MODE_PASSTHROUGH, 1) else return null
            else -> return null
        }
        railMode = label
        replace(current.values + (Command.ANC_MODE to Value.Number(value)))
        readBacks = listOf(own(RacePacket.mmiGet(JblRace.MMI_ANC)))
        return listOf(own(packet))
    }

    /** What an answer means: the chip name, or the value of the read that was asked. */
    private fun apply(p: RacePacket): Boolean {
        // the Auracast receiver's answers and events (ids 2200 to 2209)
        if (auracast != null && p.id in SkullcandyAuracast.ID_SCAN..SkullcandyAuracast.ID_CODE && (p.type == JblRace.TYPE_RESPONSE || p.type == JblRace.TYPE_INDICATION)) {
            return auracast.receive(p) { outbox += own(it) }
        }
        val asked = pending ?: return false
        if (asked == chipRead && p.id == JblRace.NV_READ) {
            val text = p.data.decodeToString().trimEnd('\u0000')
            if (text.length >= 4 && text.all { it in ' '..'~' }) {
                chip = RaceChip.of(text)
                startReadBack()
                return false
            }
            return false
        }
        val kind = kinds[asked] ?: return false
        if (p.type != 0x5B && p.type != 0x5D || p.payload.isEmpty()) return false
        if (kind.startsWith("buttons")) {
            val side = kind.removePrefix("buttons").toInt()
            val entries = RailButtons.parse(p.payload) ?: return false
            buttons[side] = entries
            return true
        }
        if (kind == "voice_language") {
            // the text of the answer, less its last byte (the app's decoder drops it)
            val text = p.payload.copyOfRange(0, (p.payload.size - 1).coerceAtLeast(0)).decodeToString().trim { it <= ' ' }
            if (text.isEmpty() || text.any { it < ' ' || it > '~' }) return false
            voiceLanguage = text
            return true
        }
        if (kind == "gain") {
            // the status, the sub-command 0D, then the gain
            if (p.payload.size < 4 || p.payload[0].toInt() != 0 || p.payload[1].toInt() != 0x0D) return false
            val range = if (askedFor == "anc") SkullcandyGain.ANC else SkullcandyGain.TRANSPARENCY
            val s = SkullcandyGain.sliderOf(p.payload.copyOfRange(2, 4), range) ?: return false
            gainSlider = s
            gainFor = askedFor
            return true
        }
        val on = p.payload.last().toInt() == 1
        val before = Triple(multipoint, wearDetect, bassBoost)
        when (kind) {
            "multipoint" -> multipoint = on
            "wear" -> wearDetect = on
            "bass" -> bassBoost = on
        }
        return before != Triple(multipoint, wearDetect, bassBoost)
    }

    override fun takeOutbox(): List<RacePacket> = outbox.toList().also { outbox.clear() }

    override fun takeReadBacks(): List<RacePacket> = readBacks.also { readBacks = emptyList() }

    private fun railButtonChange(change: SettingChange.Choice): List<RacePacket>? {
        if (!hasButtons || !change.key.startsWith("sk_rbutton_")) return null
        val parts = change.key.removePrefix("sk_rbutton_").split('_')
        val side = when (parts.firstOrNull()) { "left" -> 1; "right" -> 2; else -> return null }
        val gesture = RailButtons.GESTURES.firstOrNull { it.second == parts.getOrNull(1) }?.first ?: return null
        val event = RailButtons.EVENTS.firstOrNull { it.second == change.option }?.first ?: return null
        val now = buttons[side] ?: return null
        if (now.none { it.type == gesture }) return null
        val next = now.map { if (it.type == gesture) RailButtons.Entry(it.type, it.key, event) else it }
        buttons[side] = next
        readBacks = listOf(own(packet(RailButtons.readHex(side), "buttons$side")))
        return listOf(own(RailButtons.write(side, next)))
    }

    override fun change(change: SettingChange): List<RacePacket>? = when {
        change is SettingChange.Choice && change.key.startsWith("sk_rbutton_") -> railButtonChange(change)
        change is SettingChange.Press && primary && "voiceControl" in product.features && (change.key == VOICE_ON || change.key == VOICE_OFF) ->
            listOf(own(packet(RailButtons.voiceSet(change.key == VOICE_ON), "voice_set")))
        change is SettingChange.Toggle && change.key == MULTIPOINT && hasMultipoint -> {
            multipoint = change.on
            readBacks = listOf(packet(SkullcandyRace.multipointGet(product.enumName), "multipoint"))
            listOf(packet(SkullcandyRace.multipointSet(product.enumName, change.on), "multipoint_set"))
        }
        change is SettingChange.Toggle && change.key == WEAR_DETECT && hasWearDetect -> {
            wearDetect = change.on
            readBacks = listOf(packet(SkullcandyRace.WEAR_DETECT_GET, "wear"))
            listOf(packet(SkullcandyRace.wearDetectSet(change.on), "wear_set"))
        }
        change is SettingChange.Toggle && change.key == BASS_BOOST && hasBassBoost -> {
            bassBoost = change.on
            readBacks = listOf(packet(SkullcandyRace.BASS_BOOST_GET, "bass"))
            listOf(packet(SkullcandyRace.bassBoostSet(change.on), "bass_set"))
        }
        auracast != null && change is SettingChange.Press && change.key == SkullcandyAuracast.KEY_SCAN -> listOf(own(auracast.scan(!auracast.scanning)))
        auracast != null && change is SettingChange.Choice && change.key == SkullcandyAuracast.KEY_ADD -> auracast.select(change.option)?.let { listOf(own(it)) }
        auracast != null && change is SettingChange.Choice && change.key == SkullcandyAuracast.KEY_CODE -> auracast.code(change.option)?.let { listOf(own(it)) }
        auracast != null && change is SettingChange.Choice && change.key == SkullcandyAuracast.KEY_REMOVE -> listOf(own(auracast.stop()))
        change is SettingChange.Level && (change.key == ANC_LEVEL || change.key == TRANSPARENCY_LEVEL) && hasGain -> {
            val m = mode()
            val range = if (m == "anc") SkullcandyGain.ANC else SkullcandyGain.TRANSPARENCY
            val slider = (change.value / 10.0).coerceIn(0.0, 1.0)
            gainSlider = slider
            gainFor = m
            readBacks = listOf(gainRead())
            listOf(packet(SkullcandyRace.gainSet(SkullcandyGain.wire(slider, range)), "gain_set"))
        }
        else -> null
    }

    /**
     * The first frames of the write of the custom curve [gains] (one per band, in dB, within the model's range) with the master gain [masterDb]; the later steps follow as the earbuds
     * answer. Null when the chip is not known yet, the model has no custom curve, the gains do not fit the bands or the library refuses the curve.
     */
    public fun eqCurve(gains: List<Double>, masterDb: Double = 0.0): List<ByteArray>? {
        if (!hasCustomEq || gains.size != eqBandsHz.size) return null
        val c = chip ?: return null
        val range = eqRangeDb.toDouble()
        val bands = eqBandsHz.mapIndexed { i, hz -> JblEqBand(2, hz.toFloat(), gains[i].coerceIn(-range, range).toFloat(), SkullcandyEq.Q) }
        val earbuds = product.form != Form.HEADPHONES
        val w = JblAirohaEq.of(c, earbuds, null, SkullcandyEq.CATEGORY, bands, masterDb.toFloat(), AirohaPeq.Library.SERIALIZE) ?: return null
        write = w
        eqGains = gains
        eqPresetName = null
        return w.start().map { it.bytes() }
    }

    /** Picks the preset [name] the earbuds hold: the group of its number, as the app's `setEQSetting(category, null)` does. Null for a name the model does not have, or before the chip is known. */
    public fun eqPreset(name: String): List<ByteArray>? {
        val number = eqPresets.firstOrNull { it.first == name }?.second ?: return null
        val c = chip ?: return null
        // the writer's category 0 to 99 moves up one, so the number is given one less
        val w = JblAirohaEq.of(c, product.form != Form.HEADPHONES, null, number - 1, emptyList(), 0f, AirohaPeq.Library.SERIALIZE, selectOnly = true) ?: return null
        write = w
        eqPresetName = name
        return w.start().map { it.bytes() }
    }

    private var reader: JblAirohaEq? = null

    /** Reads what the earbuds hold once the chip is known (docs/skullcandy/PROTOCOL.md section 13.4), unless a write of PairFree's is already going. */
    private fun startReadBack() {
        val c = chip ?: return
        if (!hasCustomEq || write != null) return
        val r = JblAirohaEq.readBack(c, product.form != Form.HEADPHONES, null)
        reader = r
        write = r
        outbox += r.start()
    }

    /** The preset or the custom curve the read found, shown unless PairFree has written one since. */
    private fun takeHeld(r: JblAirohaEq): Boolean {
        val h = r.held ?: return false
        if (write !== r) return false
        write = null
        if (!h.custom) {
            val name = eqPresets.firstOrNull { it.second == h.category }?.first ?: return false
            eqPresetName = name
            eqGains = null
            return true
        }
        if (h.category != SkullcandyEq.CATEGORY) return false
        eqGains = eqBandsHz.map { hz -> h.bands.firstOrNull { kotlin.math.abs(it.hz - hz) < 1f }?.gainDb?.toDouble() ?: 0.0 }
        eqPresetName = null
        return true
    }

    /** The preset last picked in this connection, null after a custom curve or before any. */
    public var eqPresetName: String? = null
        private set

    public companion object {
        public const val MULTIPOINT: String = "multipoint"
        public const val WEAR_DETECT: String = "sk_wear_detect"
        public const val BASS_BOOST: String = "bass_boost"
        public const val VOICE_ON: String = "sk_voice_on"
        public const val VOICE_OFF: String = "sk_voice_off"
        public const val VOICE_LANGUAGE: String = "sk_voice_language"
        public const val ANC_LEVEL: String = "sk_anc_level"
        public const val TRANSPARENCY_LEVEL: String = "sk_transparency_level"
        private const val CHIP_NV_LENGTH = 1000
        private const val ANC_FILTER = 3
        private const val TRANSPARENCY_FILTER = 4
    }
}
