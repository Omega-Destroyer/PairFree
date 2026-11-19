package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.EarbudState
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
    override val state: EarbudState = EarbudState()

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
    public val hasMultipoint: Boolean = airoha && ("multipointPairingToggle" in product.features || "multipointPairing" in product.features)
    public val hasWearDetect: Boolean = airoha && "wearDetect" in product.features
    public val hasBassBoost: Boolean = airoha && "crusherBassBoost" in product.features

    /** True when the model has a custom equaliser the app sets through Airoha's PEQ. */
    public val hasCustomEq: Boolean = airoha && "customEqualizer" in product.features

    /** True when the strength of the hearing mode in force is an Airoha RACE gain: the Crusher ANC 2, whose SDK configuration lists both gains over RACE. */
    public val hasGain: Boolean = product.sdk == "CHP200"

    /** True when the model has anything this channel carries. */
    public val hasRaceFeatures: Boolean get() = hasMultipoint || hasWearDetect || hasBassBoost || hasCustomEq || hasGain

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

    override fun start(): List<RacePacket> = buildList {
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

    override fun poll(): List<RacePacket> = emptyList()

    override fun allowed(message: RacePacket): Boolean =
        message == chipRead || write?.sent?.contains(message) == true || SkullcandyRace.isAllowed(message.bytes().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() })

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
                } else {
                    changed = apply(p) || changed
                }
            }
            answers += p
        }
        return Fed(changed, answers)
    }

    override fun answers(sent: RacePacket, got: Any): Boolean = got is RacePacket

    /** What an answer means: the chip name, or the value of the read that was asked. */
    private fun apply(p: RacePacket): Boolean {
        val asked = pending ?: return false
        if (asked == chipRead && p.id == JblRace.NV_READ) {
            val text = p.data.decodeToString().trimEnd('\u0000')
            if (text.length >= 4 && text.all { it in ' '..'~' }) {
                chip = RaceChip.of(text)
                return false
            }
            return false
        }
        val kind = kinds[asked] ?: return false
        if (p.type != 0x5B && p.type != 0x5D || p.payload.isEmpty()) return false
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

    override fun change(change: SettingChange): List<RacePacket>? = when {
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

    /** The preset last picked in this connection, null after a custom curve or before any. */
    public var eqPresetName: String? = null
        private set

    public companion object {
        public const val MULTIPOINT: String = "multipoint"
        public const val WEAR_DETECT: String = "sk_wear_detect"
        public const val BASS_BOOST: String = "bass_boost"
        public const val ANC_LEVEL: String = "sk_anc_level"
        public const val TRANSPARENCY_LEVEL: String = "sk_transparency_level"
        private const val CHIP_NV_LENGTH = 1000
    }
}
