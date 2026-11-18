package io.github.omegadestroyer.pairfree.protocol.jabra

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * Jabra's GNP (docs/jabra/PROTOCOL.md): the Sound+ service over RFCOMM, for the earbuds and headphones the app drives through its own SDK. One request waits for its
 * reply before the next goes. The conversation opens with the product id and the firmware version, which pick the firmware snapshot (data/jabra/devices.json) whose form
 * lists the model's settings; each of them is then read once, and a row shows when its read answered. Noise control is the ambience command's (`13/BE`) where the
 * firmware has it. Not tried on any Jabra product.
 */
public class JabraSession(public val model: JabraModel) : SessionTalk<JabraPacket>(), JabraEqSource {
    private var current: EarbudState = EarbudState()
    override val state: EarbudState get() = current
    override val pacing: Pacing = Pacing(replyMs = REPLY_MS, gapMs = GAP_MS)

    private val parser = JabraParser()
    private var sequence = 0
    private val sentKeys = HashMap<Int, String>()

    /** The product id the earbuds reported, their firmware, and what they were matched to. */
    public var pid: Int? = null
        private set
    public var firmware: String? = null
        private set
    public var variant: JabraVariant? = model.variants.singleOrNull()
        private set
    public var profile: JabraProfile? = null
        private set
    private var configured = false

    /** Each answer by its read key (command and selector bytes), the bytes after the subcommand: the session's whole state, and the device report's. */
    private val replies = LinkedHashMap<String, ByteArray>()
    public val answered: Map<String, ByteArray> get() = replies

    private var outbox = emptyList<JabraPacket>()
    private var readBacks = emptyList<JabraPacket>()

    /** How many leading payload bytes of a command's reply are selectors, by main and subcommand: the settings of the profile tell, and the ambience command has one. */
    private val selectorCounts = HashMap<Int, Int>()

    private fun hasAmbience(): Boolean = profile?.caps?.contains("ambiencemodes") == true

    /** The older noise form (`13/87`) and the HearThrough switch (`13/76`) of the firmware that has no ambience command. */
    private fun legacyAnc(): JabraSetting? = settings().firstOrNull { it.command == "ANC" && it.main == CONFIG_MAIN && it.virtual == null }

    private fun legacyHearThrough(): JabraSetting? = settings().firstOrNull { it.command == "HEAR_THROUGH" && it.widget == JabraWidget.BOOL && switchCodes(it) != null }

    private fun caps(): Set<String> = profile?.caps.orEmpty()

    /** Spotify's one tap button, on the firmware whose snapshot lists it and the products whose definition offers it on Android. */
    private fun spotifyOffered(): Boolean = "spotifytap" in caps() && variant?.spotify == true

    /**
     * Spatial sound's mode (`13/E4` with one byte, the VSS mode: 0 off, 1 in calls, 2 in music, 3 in both, 4 with head tracking), by what the snapshot says the
     * firmware has. The three-byte reply of other schema revisions (call, media and Dolby bytes) has no code table in the app's schema and is not shown.
     */
    private fun spatialOptions(): List<String> {
        val c = caps()
        val media = "spatialsoundformediasupport" in c
        val call = "spatialsoundforcommunicationsupport" in c
        val head = "spatialsoundheadtrackingformediasupport" in c || "spatialsoundheadtrackingforcommunicationsupport" in c
        return buildList {
            if (!media && !call && !head) return@buildList
            add("spatial_off")
            if (call) add("spatial_voice")
            if (media) add("spatial_music")
            if (call && media) add("spatial_both")
            if (head) add("spatial_head_tracking")
        }
    }

    /** The voice prompt language setting the firmware's form lists, if any: the one among the settings whose command is the current language. */
    private fun language(): JabraSetting? = settings().firstOrNull { it.command == "CURRENT_LANGUAGE" && it.main != null && it.options.isNotEmpty() }

    /** The language ids the earbuds hold (`02/08`: a count, then each as two bytes, low first), or null before they answered. */
    private fun availableLanguages(): Set<Int>? {
        val b = replies["02/08"] ?: return null
        val n = b.firstOrNull()?.toInt()?.and(0xFF) ?: return null
        if (b.size < 1 + 2 * n) return null
        return (0 until n).map { (b[1 + 2 * it].toInt() and 0xFF) or ((b[2 + 2 * it].toInt() and 0xFF) shl 8) }.toSet()
    }

    private fun hasEqualizer(): Boolean = profile?.caps?.contains("music_equalizer") == true
    private fun settings(): List<JabraSetting> = profile?.settings?.mapNotNull { JabraSettings.BY_ID[it] }.orEmpty()

    // ---- messages -------------------------------------------------------------------------------------------------------------------------------------

    private fun next(): Int {
        sequence = (sequence % 250) + 1
        return sequence
    }

    private fun packet(destination: Int, attribute: Int, command: JabraCommand, vararg payload: Int): JabraPacket =
        packet(destination, attribute, command.main, command.sub, ByteArray(payload.size) { payload[it].toByte() })

    private fun packet(destination: Int, attribute: Int, main: Int, sub: Int, body: ByteArray): JabraPacket {
        val p = JabraPacket(destination, JabraWire.SOURCE, next(), attribute, main, byteArrayOf(sub.toByte()) + body)
        sentKeys[p.sequence] = keyOf(main, sub, body, selectorCounts[(main shl 8) or sub] ?: 0)
        return p
    }

    private fun keyOf(main: Int, sub: Int, body: ByteArray, selectors: Int): String =
        hex2(main) + "/" + hex2(sub) + (0 until selectors).joinToString("") { "/" + hex2(body.getOrElse(it) { 0 }.toInt()) }

    private fun read(setting: JabraSetting): JabraPacket = packet(setting.destination, JabraPacket.READ, setting.main!!, setting.sub!!, setting.selectorBytes)

    override fun start(): List<JabraPacket> = listOf(
        packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.PID),
        packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.VERSION),
        packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.BATTERY),
    )

    override fun poll(): List<JabraPacket> = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.BATTERY))

    /** Reads out in this order once the model is known: noise control, then each setting of the snapshot's form, once per command and selector. */
    private fun configure() {
        if (configured) return
        configured = true
        val p = pickProfile()
        profile = p
        selectorCounts[(JabraCommands.AMBIENCE.main shl 8) or JabraCommands.AMBIENCE.sub] = 1
        val out = mutableListOf<JabraPacket>()
        val seen = HashSet<String>()
        for (s in settings()) {
            if (s.main == null || s.sub == null) continue
            val k = (s.main shl 8) or s.sub
            selectorCounts[k] = maxOf(selectorCounts[k] ?: 0, s.selectors.size)
        }
        if (hasEqualizer()) {
            out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.EQUALIZER, 0)
            out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.EQUALIZER_ENABLE)
        }
        if (spotifyOffered()) out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.SPOTIFY.main, JabraCommands.SPOTIFY.sub, ByteArray(0))
        if (spatialOptions().size > 1) out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.SPATIAL.main, JabraCommands.SPATIAL.sub, ByteArray(0))
        if (language() != null) {
            out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.AVAILABLE_LANGUAGES.main, JabraCommands.AVAILABLE_LANGUAGES.sub, ByteArray(0))
            out += read(language()!!)
        }
        if (hasAmbience()) {
            for (selector in AMBIENCE_READS) out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.AMBIENCE, selector)
            if (variant?.has("ambience_mode_loop") == true) out += packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.SOUND_MODE_LOOP.main, JabraCommands.SOUND_MODE_LOOP.sub, ByteArray(0))
        }
        for (s in settings()) {
            if (s.virtual != null || s.special || s.main == null || s.sub == null || s.widget == JabraWidget.STATIC || s.widget == JabraWidget.TEXT) continue
            if (seen.add("${s.destination}/${keyOf(s.main, s.sub, s.selectorBytes, s.selectors.size)}")) out += read(s)
        }
        outbox = outbox + out
    }

    /** The snapshot for the product id: the firmware's own, else the nearest older one, else the oldest. */
    private fun pickProfile(): JabraProfile? {
        val id = pid ?: variant?.pid ?: return null
        val all = JabraProfiles.ALL.filter { it.pid == id }
        if (all.isEmpty()) return null
        val fw = firmware?.let(::versionOf)
        if (fw == null) return all.maxWithOrNull { x, y -> compare(versionOf(x.firmware), versionOf(y.firmware)) }
        all.firstOrNull { versionOf(it.firmware) == fw }?.let { return it }
        val older = all.filter { compare(versionOf(it.firmware), fw) <= 0 }
        return older.maxWithOrNull { a, b -> compare(versionOf(a.firmware), versionOf(b.firmware)) } ?: all.minWithOrNull { a, b -> compare(versionOf(a.firmware), versionOf(b.firmware)) }
    }

    override fun takeOutbox(): List<JabraPacket> = outbox.also { outbox = emptyList() }

    override fun takeReadBacks(): List<JabraPacket> = readBacks.also { readBacks = emptyList() }

    /** Only the settings and ambience commands of the tables go out, never a group or command of the never-send lists (hard rules). */
    override fun allowed(message: JabraPacket): Boolean {
        if (message.main in JabraCommands.NEVER_SEND_GROUPS) return false
        val sub = message.sub ?: return false
        if (JabraCommands.NEVER_SEND.any { it.first == message.main && it.second == sub }) return false
        if (message.isWrite) return message.main == CONFIG_MAIN || (message.main == JabraCommands.CONFIG_MODE_END.main && sub == JabraCommands.CONFIG_MODE_END.sub)
        return message.isRead
    }

    override fun bytes(message: JabraPacket): ByteArray = message.bytes()

    override fun describe(message: JabraPacket): String = message.toString()

    override fun answers(sent: JabraPacket, got: Any): Boolean = got is String && (got == sentKeys[sent.sequence] || got == "seq:${sent.sequence}")

    // ---- reading ----------------------------------------------------------------------------------------------------------------------------------------

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val answers = mutableListOf<Any>()
        for (packet in parser.feed(bytes)) guarded {
            log?.invoke("recv $packet")
            when {
                packet.main == NACK -> answers += "seq:${packet.sequence}"
                packet.isReply || packet.isEvent -> {
                    val sub = packet.sub ?: return@guarded
                    val body = packet.body
                    val key = keyOf(packet.main, sub, body, selectorCounts[(packet.main shl 8) or sub] ?: 0)
                    if (packet.isReply) answers += key
                    changed = handle(packet.main, sub, body, key, packet.isReply) || changed
                }
            }
        }
        if (changed) refresh()
        return Fed(changed, answers)
    }

    /** True when the answer changed what the screens show. */
    private fun handle(main: Int, sub: Int, body: ByteArray, key: String, reply: Boolean): Boolean {
        when {
            main == JabraCommands.PID.main && sub == JabraCommands.PID.sub -> {
                if (body.size >= 2) {
                    val id = (body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)
                    pid = id
                    variant = model.variant(id) ?: variant
                    if (firmware != null) configure()
                }
                return true
            }
            main == JabraCommands.VERSION.main && sub == JabraCommands.VERSION.sub -> {
                firmware = text(body)
                if (pid != null) configure()
                return true
            }
        }
        replies[key] = body
        if (reply && main == JabraCommands.EQUALIZER.main && sub == JabraCommands.EQUALIZER.sub) parseEqualizer(body)
        if (main == JabraCommands.EQUALIZER_ENABLE.main && sub == JabraCommands.EQUALIZER_ENABLE.sub) eqOn = body.firstOrNull()?.toInt()?.let { it != 0 }
        return true
    }

    // ---- the music equalizer ---------------------------------------------------------------------------------------------------------------------------

    /** A band as the earbuds describe it: its centre in Hz, the most gain they take either way in dB, the gain now, and Q as the wire holds it (times 4096). */
    private class EqBand(val hz: Int, val maxDb: Double, val gainDb: Double, val q: Int, val freqRaw: Int)

    private var bands: List<EqBand>? = null
    private var masterRaw = 0
    private var eqOn: Boolean? = null

    /**
     * `13/7D` read of all bands: the band count, the master gain (a signed 16-bit value in 1/60 dB), then per band a maximum gain byte in quarter dB, the
     * frequency times three, the gain in 1/60 dB and Q times 4096, each 16 bits high byte first (docs/jabra/PROTOCOL.md, Music EQ and presets).
     */
    private fun parseEqualizer(body: ByteArray) {
        val count = body.firstOrNull()?.toInt()?.and(0xFF) ?: return
        if (count == 0 || body.size < 3 + 7 * count) return
        fun u16(i: Int) = ((body[i].toInt() and 0xFF) shl 8) or (body[i + 1].toInt() and 0xFF)
        masterRaw = u16(1).toShort().toInt()
        bands = List(count) { b ->
            val at = 3 + 7 * b
            val freq = u16(at + 1)
            EqBand(freq / 3, (body[at].toInt() and 0xFF) / 4.0, u16(at + 3).toShort().toInt() / 60.0, u16(at + 5), freq)
        }
    }

    override val eqKnown: Boolean get() = bands != null
    override val eqBandsHz: List<Int> get() = bands?.map { it.hz }.orEmpty()
    override val eqGains: List<Double>? get() = bands?.map { it.gainDb }

    /** What every band takes either way: the smallest of the bands' maxima, so one range fits the whole graph. */
    override val eqMaxDb: Double get() = bands?.minOfOrNull { it.maxDb } ?: 0.0

    /** The app's six presets: each band's own maximum times the preset's percentage (the app converts them the same way). */
    override val eqPresets: List<Pair<String, List<Double>>>
        get() {
            val b = bands ?: return emptyList()
            return JabraPresets.ALL.filter { it.percent.size == b.size }.map { p -> p.name to b.indices.map { b[it].maxDb * p.percent[it] / 100.0 } }
        }

    /**
     * The frames that set the gains of every band: the equalizer on (off when the curve is flat, as the app's neutral check does; an inference from the app's
     * handler, which names the check but whose body did not decompile), then `13/7D` with the count, parameter 0 for all bands, the master gain kept, and each
     * band's frequency, gain (float times 60, truncated toward zero, as the native setter does) and Q kept. A gain beyond a band's maximum sends nothing.
     */
    override fun eqCurve(gains: List<Double>): List<ByteArray>? {
        val b = bands ?: return null
        if (gains.size != b.size || gains.indices.any { kotlin.math.abs(gains[it]) > b[it].maxDb + 1e-9 }) return null
        val body = ByteArray(4 + 6 * b.size)
        body[0] = b.size.toByte()
        body[1] = 0
        body[2] = (masterRaw shr 8).toByte()
        body[3] = masterRaw.toByte()
        for ((i, band) in b.withIndex()) {
            val at = 4 + 6 * i
            val g = (gains[i].toFloat() * 60f).toInt()
            body[at] = (band.freqRaw shr 8).toByte()
            body[at + 1] = band.freqRaw.toByte()
            body[at + 2] = (g shr 8).toByte()
            body[at + 3] = g.toByte()
            body[at + 4] = (band.q shr 8).toByte()
            body[at + 5] = band.q.toByte()
        }
        val out = mutableListOf<ByteArray>()
        val flat = gains.all { it == 0.0 }
        if (eqOn != !flat) {
            eqOn = !flat
            out += packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.EQUALIZER_ENABLE, if (flat) 0 else 1).bytes()
        }
        out += packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.EQUALIZER.main, JabraCommands.EQUALIZER.sub, body).bytes()
        bands = b.mapIndexed { i, band -> EqBand(band.hz, band.maxDb, (gains[i].toFloat() * 60f).toInt() / 60.0, band.q, band.freqRaw) }
        return out
    }

    override fun unanswered(sent: JabraPacket): Boolean {
        val k = sentKeys[sent.sequence]
        // Earbuds that never answered the product id or the version are read by what the model's table says.
        if (!configured && (k == keyOf(JabraCommands.PID.main, JabraCommands.PID.sub, ByteArray(0), 0) || k == keyOf(JabraCommands.VERSION.main, JabraCommands.VERSION.sub, ByteArray(0), 0))) {
            configure()
            return true
        }
        return false
    }

    /** The ASCII string after a length byte (the version reply), else the whole payload as text. */
    private fun text(body: ByteArray): String {
        if (body.isEmpty()) return ""
        val n = body[0].toInt() and 0xFF
        val chars = if (n in 1 until body.size) body.copyOfRange(1, 1 + minOf(n, body.size - 1)) else body.copyOfRange(1, body.size)
        return chars.takeWhile { it.toInt() != 0 }.toByteArray().decodeToString().trim()
    }

    // ---- state ----------------------------------------------------------------------------------------------------------------------------------------

    private fun u8(key: String, at: Int): Int? = replies[key]?.getOrNull(at)?.toInt()?.and(0xFF)

    private fun ambience(selector: Int): Int? = u8("13/be/" + hex2(selector), 1)

    private fun refresh() {
        val next = current.values.toMutableMap()
        fun put(c: Command, v: Value?) {
            if (v == null) next.remove(c) else next[c] = v
        }
        firmware?.let { put(Command.FIRMWARE_VERSION, Value.Text("${model.name}_$it")) }
        val battery = replies["12/02"]
        if (battery != null && battery.size >= 2) {
            val levels = batteryLevels(battery)
            put(Command.BATTERY_LEFT, levels.left?.let { Value.Number(it) })
            put(Command.BATTERY_RIGHT, levels.right?.let { Value.Number(it) })
            put(Command.BATTERY_CASE, levels.cradle?.let { Value.Number(it) })
        }
        val mode: String? = when {
            hasAmbience() -> when (ambience(1)) {
                AMBIENCE_ANC -> "anc"
                AMBIENCE_HEARTHROUGH -> "transparency"
                AMBIENCE_OFF -> "off"
                else -> null
            }
            // The older forms: one command for noise cancelling and HearThrough (`13/87`: off 0, ANC 1, HearThrough 2, with music 3) or only HearThrough's on and off.
            legacyAnc() != null -> when (valueOf(legacyAnc()!!)) {
                0 -> "off"
                1 -> "anc"
                2, 3 -> "transparency"
                else -> null
            }
            legacyHearThrough() != null -> valueOf(legacyHearThrough()!!)?.let { if (it == switchCodes(legacyHearThrough()!!)?.first) "transparency" else "off" }
            else -> null
        }
        put(Command.ANC_MODE, mode?.let { n -> Value.Number(Command.ANC_MODE.labels.entries.first { it.value == n }.key) })
        current = current.copy(values = next)
    }

    override val eq: Any? get() = bands?.map { it.gainDb }

    private class Levels(val left: Int?, val right: Int?, val cradle: Int?)

    /**
     * STATUS `12/02`: flags, the local level, two ADC bytes, the number of extra units, then each as address, a reserved byte and a level.
     * Jabra's SDK places the case by its address (0x15) and the other earbud by the extra unit's address, 4 for the right and 5 for the left,
     * the local level belonging to the other side; with no extra unit it is one combined level (shown as the left). The side of the local
     * level is inferred from the extra unit, not read from a link event (docs/jabra/PROTOCOL.md).
     */
    private fun batteryLevels(b: ByteArray): Levels {
        fun at(i: Int) = b.getOrNull(i)?.toInt()?.and(0xFF)
        val local = at(1)?.takeIf { it in 0..100 }
        val extras = at(4) ?: 0
        var cradle: Int? = null
        var other: Pair<Int, Int>? = null
        for (i in 0 until extras) {
            val base = 5 + 3 * i
            val address = at(base) ?: break
            val level = at(base + 2)?.takeIf { it in 0..100 } ?: continue
            if (address == ADDRESS_CRADLE) cradle = level else other = address to level
        }
        val o = other ?: return Levels(local, null, cradle)
        return if (o.first == ADDRESS_RIGHT) Levels(local, o.second, cradle) else Levels(o.second, local, cradle)
    }

    // ---- rows -------------------------------------------------------------------------------------------------------------------------------------------

    override val groups: List<SettingGroup>
        get() {
            val out = mutableListOf<SettingGroup>()
            val noise = noiseRows()
            if (noise.isNotEmpty()) out += SettingGroup("jb_noise", SettingTab.HOME, noise)
            val byGroup = LinkedHashMap<Pair<String, SettingTab>, MutableList<Setting>>()
            for (s in settings()) {
                val row = row(s) ?: continue
                val tab = if (s.command == "USER_BUTTONS") SettingTab.CONTROLS else SettingTab.DEVICE
                val group = if (tab == SettingTab.CONTROLS) BUTTON_GROUPS[s.selectors.firstOrNull()?.second] ?: "buttons" else s.group
                byGroup.getOrPut(group to tab) { mutableListOf() }.let { if (it.none { r -> r.key == row.key }) it += row }
            }
            val extra = extraRows()
            if (extra.isNotEmpty()) out += SettingGroup("jb_extra", SettingTab.DEVICE, extra)
            for ((k, rows) in byGroup) out += SettingGroup("jb_${k.first.lowercase()}", k.second, rows)
            return out
        }

    private fun noiseRows(): List<Setting> = buildList {
        if (!hasAmbience()) return@buildList
        val htMax = u8("13/be/00", 2)
        val ancMax = u8("13/be/00", 3)
        val ht = ambience(2)
        val anc = ambience(3)
        if (htMax != null && htMax > 0 && ht != null) add(Setting.Level("jb_ht_level", htMax - ht.coerceIn(0, htMax) + 1, 1, htMax + 1))
        if (ancMax != null && ancMax > 0 && anc != null && variant?.noiseLoop?.contains("anc") != false) add(Setting.Level("jb_anc_level", ancMax - anc.coerceIn(0, ancMax) + 1, 1, ancMax + 1))
        // The music mix: bit 0 of the HearThrough extended settings, offered where the supported-data reply's bit 0 of that byte says the earbuds have it.
        val ext = ambience(4)
        if (ext != null && (u8("13/be/00", 4) ?: 0) and 1 != 0) add(Setting.Toggle("jb_ht_mix", ext and 1 != 0))
        loop()?.let { names -> add(Setting.Choice("jb_loop", names.joinToString("_"), LOOP_CHOICES.map { it.joinToString("_") })) }
    }

    /** The modes the button cycles through, in order ("Off", "HT", "ANC"), from the loop read; null before it has answered or where the model has no loop. */
    private fun loop(): List<String>? {
        val b = replies["13/bf"] ?: return null
        val n = b.firstOrNull()?.toInt()?.and(0xFF) ?: return null
        if (n == 0 || b.size < 1 + n) return null
        return (1..n).map { LOOP_NAMES.getOrNull(b[it].toInt() and 0xFF) ?: return null }
    }

    /** The value of a setting from the last answer to its read, or null before it came. */
    private fun valueOf(s: JabraSetting): Int? {
        val body = replies[keyOf(s.main ?: return null, s.sub ?: return null, s.selectorBytes, s.selectors.size)] ?: return null
        if (s.pos < 1 || body.size < s.pos - 1 + s.width) return null
        var v = 0
        for (i in 0 until s.width) v = (v shl 8) or (body[s.pos - 1 + i].toInt() and 0xFF)
        return v
    }

    /**
     * What a choice calls an option on the screens: the vendor's name, except where the code is itself the value, the idle timer in units of five minutes and the
     * sidetone level in dB (a signed byte), which the screens write as numbers.
     */
    private fun optionKey(s: JabraSetting, o: JabraOption): String = when {
        s.command == "INACTIVITY_INTERVAL" -> if (o.code == 0) "off" else "min_${o.code * 5}"
        s.command == "DSP_SIDETONE" && s.pos == 2 -> "db_${o.code.toByte()}"
        else -> o.name
    }

    /** The two codes of a switch: the option called on and the one called off; null when the form gives both the same code. */
    private fun switchCodes(s: JabraSetting): Pair<Int, Int>? {
        val on = s.options.firstOrNull { it.name.lowercase() in ON_NAMES }?.code ?: return null
        val off = s.options.firstOrNull { it.name.lowercase() in OFF_NAMES }?.code ?: return null
        return if (on == off) null else on to off
    }

    /** The rows that are not a form entry of the snapshot: Spotify, spatial sound and the voice language. */
    private fun extraRows(): List<Setting> = buildList {
        u8("13/e5", 0)?.takeIf { spotifyOffered() }?.let { add(Setting.Toggle("jb_spotify", it != 0)) }
        val options = spatialOptions()
        val vss = replies["13/e4"]
        if (options.size > 1 && vss != null && vss.size == 1) {
            val current = SPATIAL_CODES.getOrNull(vss[0].toInt() and 0xFF)
            add(Setting.Choice("jb_spatial", current, options))
        }
        val lang = language()
        val available = availableLanguages()
        val body = lang?.let { replies[keyOf(it.main!!, it.sub!!, ByteArray(0), 0)] }
        if (lang != null && available != null && body != null && body.size >= 2) {
            val offered = lang.options.filter { it.code in available }
            val current = (body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)
            if (offered.size > 1) add(Setting.Choice("jb_${lang.id}", lang.option(current)?.name, offered.map { it.name }))
        }
    }

    private fun row(s: JabraSetting): Setting? {
        if (s.virtual != null || s.special || s.main == null) return null
        val v = valueOf(s) ?: return null
        val key = "jb_${s.id}"
        return when (s.widget) {
            JabraWidget.BOOL -> switchCodes(s)?.let { (on, _) -> Setting.Toggle(key, v == on) }
            JabraWidget.LIST -> if (s.options.isEmpty()) null else Setting.Choice(key, s.option(v)?.let { optionKey(s, it) }, s.options.map { optionKey(s, it) })
            else -> null
        }
    }

    // ---- changes -----------------------------------------------------------------------------------------------------------------------------------------

    override fun set(command: Command, value: Int): List<JabraPacket>? {
        if (command != Command.ANC_MODE) return null
        if (!hasAmbience()) return setLegacy(Command.ANC_MODE.labels[value] ?: return null)
        val code = when (Command.ANC_MODE.labels[value]) {
            "off" -> AMBIENCE_OFF
            "anc" -> AMBIENCE_ANC
            "transparency" -> AMBIENCE_HEARTHROUGH
            else -> return null
        }
        replies["13/be/01"] = byteArrayOf(1, code.toByte())
        refresh()
        readBacks = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.AMBIENCE, 1))
        return listOf(packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.AMBIENCE, 1, code))
    }

    /** Noise control on the older forms: the one noise command's code, or HearThrough's switch. HearThrough keeps the music mix the earbuds last had. */
    private fun setLegacy(mode: String): List<JabraPacket>? {
        legacyAnc()?.let { s ->
            val held = valueOf(s)
            val code = when (mode) {
                "off" -> 0
                "anc" -> 1
                else -> if (held == 3) 3 else 2
            }
            if (s.option(code) == null) return null
            return write(s, code)
        }
        val s = legacyHearThrough() ?: return null
        val (on, off) = switchCodes(s) ?: return null
        return write(s, if (mode == "transparency") on else off)
    }

    override fun change(change: SettingChange): List<JabraPacket>? {
        when (change.key) {
            "jb_spotify" -> {
                val on = (change as? SettingChange.Toggle)?.on ?: return null
                replies["13/e5"] = byteArrayOf(if (on) 1 else 0)
                readBacks = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.SPOTIFY.main, JabraCommands.SPOTIFY.sub, ByteArray(0)))
                return listOf(packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.SPOTIFY.main, JabraCommands.SPOTIFY.sub, byteArrayOf(if (on) 1 else 0)))
            }
            "jb_spatial" -> {
                val option = (change as? SettingChange.Choice)?.option ?: return null
                val code = SPATIAL_CODES.indexOf(option).takeIf { it >= 0 && option in spatialOptions() } ?: return null
                replies["13/e4"] = byteArrayOf(code.toByte())
                readBacks = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.SPATIAL.main, JabraCommands.SPATIAL.sub, ByteArray(0)))
                return listOf(packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.SPATIAL.main, JabraCommands.SPATIAL.sub, byteArrayOf(code.toByte())))
            }
            "jb_${language()?.id}" -> {
                // Languages already on the earbuds: the configuration mode is entered (`0D/11`), the language written (two bytes, low first) and the mode ended (`0D/12`).
                val lang = language() ?: return null
                val option = (change as? SettingChange.Choice)?.option ?: return null
                val code = lang.options.firstOrNull { it.name == option }?.code?.takeIf { it in (availableLanguages() ?: emptySet()) } ?: return null
                val body = byteArrayOf(code.toByte(), (code shr 8).toByte())
                replies[keyOf(lang.main!!, lang.sub!!, ByteArray(0), 0)] = body
                readBacks = listOf(read(lang))
                return listOf(
                    packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.CONFIG_MODE_ENTER.main, JabraCommands.CONFIG_MODE_ENTER.sub, ByteArray(0)),
                    packet(lang.destination, JabraPacket.WRITE, lang.main!!, lang.sub!!, body),
                    packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.CONFIG_MODE_END.main, JabraCommands.CONFIG_MODE_END.sub, ByteArray(0)),
                )
            }
            "jb_loop" -> {
                val option = (change as? SettingChange.Choice)?.option ?: return null
                val modes = LOOP_CHOICES.firstOrNull { it.joinToString("_") == option } ?: return null
                val body = byteArrayOf(modes.size.toByte()) + ByteArray(modes.size) { LOOP_NAMES.indexOf(modes[it]).toByte() }
                replies["13/bf"] = body
                readBacks = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.SOUND_MODE_LOOP.main, JabraCommands.SOUND_MODE_LOOP.sub, ByteArray(0)))
                return listOf(packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.SOUND_MODE_LOOP.main, JabraCommands.SOUND_MODE_LOOP.sub, body))
            }
            "jb_ht_mix" -> {
                val on = (change as? SettingChange.Toggle)?.on ?: return null
                val held = ambience(4) ?: return null
                val value = if (on) held or 1 else held and 1.inv()
                replies["13/be/04"] = byteArrayOf(4, value.toByte())
                readBacks = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.AMBIENCE, 4))
                return listOf(packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.AMBIENCE, 4, value))
            }
            "jb_ht_level", "jb_anc_level" -> {
                val value = (change as? SettingChange.Level)?.value ?: return null
                val ht = change.key == "jb_ht_level"
                val max = u8("13/be/00", if (ht) 2 else 3) ?: return null
                val raw = (max + 1 - value).coerceIn(0, max)
                val selector = if (ht) 2 else 3
                replies["13/be/" + hex2(selector)] = byteArrayOf(selector.toByte(), raw.toByte())
                readBacks = listOf(packet(JabraWire.DESTINATION, JabraPacket.READ, JabraCommands.AMBIENCE, selector))
                return listOf(packet(JabraWire.DESTINATION, JabraPacket.WRITE, JabraCommands.AMBIENCE, selector, raw))
            }
        }
        val s = settings().firstOrNull { "jb_${it.id}" == change.key } ?: return null
        val code = when (change) {
            is SettingChange.Toggle -> switchCodes(s)?.let { if (change.on) it.first else it.second }
            is SettingChange.Choice -> s.options.firstOrNull { optionKey(s, it) == change.option }?.code
            else -> null
        } ?: return null
        return write(s, code)
    }

    /** The write of one setting: the selectors and the value, or for a shared payload the last read payload with only this value changed. */
    private fun write(s: JabraSetting, code: Int): List<JabraPacket>? {
        val main = s.main ?: return null
        val sub = s.sub ?: return null
        if (main != CONFIG_MAIN || "write" in s.configMode) return null
        val key = keyOf(main, sub, s.selectorBytes, s.selectors.size)
        val base = replies[key]
        val body = if (s.shared) base?.copyOf() ?: return null else ByteArray(maxOf(s.pos - 1 + s.width, s.selectors.maxOfOrNull { it.first } ?: 0))
        if (body.size < s.pos - 1 + s.width) return null
        for ((position, byte) in s.selectors) body[position - 1] = byte.toByte()
        for (i in 0 until s.width) body[s.pos - 1 + i] = (code shr (8 * (s.width - 1 - i))).toByte()
        replies[key] = body
        refresh()
        readBacks = listOf(read(s))
        return listOf(packet(s.destination, JabraPacket.WRITE, main, sub, body))
    }

    public companion object {
        private const val REPLY_MS = 1_000L
        private const val GAP_MS = 20L
        private const val NACK = 0xFE
        private const val CONFIG_MAIN = 0x13
        private const val ADDRESS_RIGHT = 4
        private const val ADDRESS_CRADLE = 0x15
        /** The SDK's ordinals for the loop (off 0, HearThrough 1, ANC 2), which differ from the selected-mode bits. */
        private val LOOP_NAMES = listOf("Off", "HT", "ANC")

        /** The loops the Sound+ app offers: HearThrough and ANC, with or without off, and HearThrough and off. */
        private val LOOP_CHOICES = listOf(listOf("HT", "ANC"), listOf("HT", "ANC", "Off"), listOf("HT", "Off"))
        private val SPATIAL_CODES = listOf("spatial_off", "spatial_voice", "spatial_music", "spatial_both", "spatial_head_tracking")
        private val BUTTON_GROUPS = mapOf(0 to "buttons_music", 1 to "buttons_call_incoming", 2 to "buttons_call_ongoing")
        private const val AMBIENCE_OFF = 0x01
        private const val AMBIENCE_HEARTHROUGH = 0x02
        private const val AMBIENCE_ANC = 0x04
        private val AMBIENCE_READS = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        private val ON_NAMES = setOf("on", "enable", "enabled")
        private val OFF_NAMES = setOf("off", "disable", "disabled")

        /** A dotted version as numbers; non-numeric parts are dropped. */
        public fun versionOf(text: String): List<Int> = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()

        public fun compare(a: List<Int>, b: List<Int>): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
