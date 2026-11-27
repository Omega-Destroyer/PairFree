package io.github.omegadestroyer.pairfree.protocol.jabra

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia3Packet
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia3Parser

/**
 * The Jabra products Sound+ drives through Qualcomm's GAIA v3 (Elite 2, 3, 3 Active, 4, 4 Active, 5 and Connect 5t; docs/jabra/PROTOCOL.md, "GAIA devices"): Jabra's own
 * vendor plugins (vendor 0x0067: one feature each for the battery, sound modes, HearThrough, sidetone, in-ear detection, the button and leveled noise cancelling) and
 * Qualcomm's music processing plugin (vendor 0x001D) for the equalizer. The conversation opens with the features the earbuds list; each feature is then read once.
 * Read from Sound+ 5.22.0's plugins; not tried on any Jabra product, and the framing (version 3, no checksum) is the one the other GAIA v3 brands use, not confirmed for Jabra.
 */
public class JabraGaiaSession(public val model: JabraModel) : SessionTalk<Gaia3Packet>(), JabraEqSource {
    private var current: EarbudState = EarbudState()
    override val state: EarbudState get() = current
    override val pacing: Pacing = Pacing(replyMs = REPLY_MS, gapMs = GAP_MS)

    private val parser = Gaia3Parser(setOf(VENDOR, QUALCOMM))
    private var features: Map<Int, Int>? = null
    private var configured = false
    private var outbox = emptyList<Gaia3Packet>()
    private var readBacks = emptyList<Gaia3Packet>()

    /** Each answer by feature and command (a notification under the command it reports), the payload: the whole state, and the device report's. */
    private val values = LinkedHashMap<Int, ByteArray>()
    public val answered: Map<String, ByteArray> get() = values.mapKeys { hex2(it.key shr 8) + "/" + hex2(it.key and 0xFF) }
    public val featureIds: Set<Int> get() = features?.keys.orEmpty()

    private fun has(feature: Int): Boolean = features?.containsKey(feature) == true

    private fun frame(feature: Int, command: Int, vararg payload: Int): Gaia3Packet =
        Gaia3Packet(VENDOR, (feature shl 9) or command, ByteArray(payload.size) { payload[it].toByte() })

    private fun frameOf(feature: Int, command: Int, payload: ByteArray): Gaia3Packet = Gaia3Packet(VENDOR, (feature shl 9) or command, payload)

    /** A read; HearThrough's takes a dummy byte, as the plugin sends. */
    private fun read(feature: Int, command: Int): Gaia3Packet = if (feature == HEARTHROUGH) frame(feature, command, 0) else frame(feature, command)

    private fun music(command: Int, vararg payload: Int): Gaia3Packet =
        Gaia3Packet(QUALCOMM, (MUSIC_FEATURE shl 9) or command, ByteArray(payload.size) { payload[it].toByte() })

    override fun start(): List<Gaia3Packet> = listOf(frame(FEATURES, 1))

    override fun poll(): List<Gaia3Packet> = if (has(BATTERY)) listOf(frame(BATTERY, 1)) else emptyList()

    override fun takeOutbox(): List<Gaia3Packet> = outbox.also { outbox = emptyList() }

    override fun takeReadBacks(): List<Gaia3Packet> = readBacks.also { readBacks = emptyList() }

    override fun bytes(message: Gaia3Packet): ByteArray = message.bytes()

    override fun describe(message: Gaia3Packet): String = message.toString()

    /** Only the features and operations of Sound+'s plugins go out: their reads and sets, the notification registration, and the music plugin's band read and gain write. */
    override fun allowed(message: Gaia3Packet): Boolean {
        if (message.vendor == QUALCOMM) return message.feature == MUSIC_FEATURE && (message.command == MUSIC_READ_BANDS || message.command == MUSIC_WRITE_GAINS)
        if (message.vendor != VENDOR || message.type != Gaia3Packet.TYPE_COMMAND) return false
        return message.command in (ALLOWED[message.feature] ?: return false)
    }

    override fun answers(sent: Gaia3Packet, got: Any): Boolean = got is Int && got == sent.key

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val keys = mutableListOf<Any>()
        for (packet in parser.feed(bytes)) guarded {
            log?.invoke("recv $packet")
            if (packet.isReply || packet.isError) keys += packet.key
            if (packet.isError) return@guarded
            if (packet.vendor == QUALCOMM) {
                if (packet.feature == MUSIC_FEATURE && (packet.isReply || packet.isNotification)) changed = handleMusic(packet) || changed
                return@guarded
            }
            if (packet.feature == FEATURES && packet.isReply) {
                parseFeatures(packet.payload)
                changed = true
                return@guarded
            }
            if (packet.isReply || packet.isNotification) {
                val command = if (packet.isNotification) NOTIFICATION_AS_COMMAND[(packet.feature shl 8) or packet.command] ?: packet.command else packet.command
                values[(packet.feature shl 8) or command] = packet.payload
                changed = true
                // The equalizer's bands are read over the range the maximum gains name.
                if (packet.feature == MAX_GAINS && command == 2 && packet.isReply && packet.payload.size >= 2) {
                    outbox = outbox + music(MUSIC_READ_BANDS, packet.payload[0].toInt() and 0xFF, packet.payload[1].toInt() and 0xFF)
                }
            }
        }
        if (changed) refresh()
        return Fed(changed, keys)
    }

    /** `01/01`: a byte that says whether more follow, then a feature id and its version for each. */
    private fun parseFeatures(p: ByteArray) {
        val found = LinkedHashMap<Int, Int>(features.orEmpty())
        var i = 1
        while (i + 1 < p.size) {
            found[p[i].toInt() and 0xFF] = p[i + 1].toInt() and 0xFF
            i += 2
        }
        features = found
        if (!configured && p.firstOrNull()?.toInt() != 1) configure()
    }

    private fun configure() {
        configured = true
        val out = mutableListOf<Gaia3Packet>()
        for (f in listOf(BATTERY, SOUND_MODES, HEARTHROUGH, SIDE_TONE, IN_EAR)) if (has(f)) out += frame(REGISTRY, 1, f)
        if (has(BATTERY)) out += frame(BATTERY, 1)
        if (has(SOUND_MODES)) {
            out += frame(SOUND_MODES, 4)
            out += frame(SOUND_MODES, 6)
        }
        if (has(HEARTHROUGH)) for (c in intArrayOf(1, 3, 5, 7)) out += frame(HEARTHROUGH, c, 0)
        if (has(SIDE_TONE)) for (c in intArrayOf(1, 4, 5)) out += frame(SIDE_TONE, c)
        if (has(IN_EAR)) for (c in intArrayOf(1, 3, 5, 7)) out += frame(IN_EAR, c)
        if (has(BUTTON_CONTROL)) out += frame(BUTTON_CONTROL, 1)
        if (has(LEVELED_ANC)) {
            out += frame(LEVELED_ANC, 1)
            out += frame(LEVELED_ANC, 2)
        }
        if (has(MAX_GAINS)) out += frame(MAX_GAINS, 2)
        outbox = out
    }

    // ---- state -------------------------------------------------------------------------------------------------------------------------------------------

    private fun u8(feature: Int, command: Int, at: Int = 0): Int? = values[(feature shl 8) or command]?.getOrNull(at)?.toInt()?.and(0xFF)

    private fun refresh() {
        val next = current.values.toMutableMap()
        fun put(c: Command, v: Value?) {
            if (v == null) next.remove(c) else next[c] = v
        }
        put(Command.FIRMWARE_VERSION, Value.Text(model.name))
        values[(BATTERY shl 8) or 1]?.let { b ->
            // The left and the right level, and the case's when it is 0 to 100 (the plugin's own rule).
            if (b.size == 2 || b.size == 3) {
                put(Command.BATTERY_LEFT, Value.Number(b[0].toInt() and 0xFF))
                put(Command.BATTERY_RIGHT, Value.Number(b[1].toInt() and 0xFF))
                put(Command.BATTERY_CASE, if (b.size == 3) (b[2].toInt() and 0xFF).takeIf { it in 0..100 }?.let { Value.Number(it) } else null)
            }
        }
        val mode: String? = when {
            has(SOUND_MODES) -> when (u8(SOUND_MODES, 4)) {
                0 -> "off"
                1 -> "anc"
                2 -> "transparency"
                else -> null
            }
            has(HEARTHROUGH) -> when (u8(HEARTHROUGH, 1)) {
                1 -> "transparency"
                2 -> "off"
                else -> null
            }
            else -> null
        }
        put(Command.ANC_MODE, mode?.let { n -> Value.Number(Command.ANC_MODE.labels.entries.first { it.value == n }.key) })
        current = current.copy(values = next)
    }

    override val eq: Any? get() = gains

    // ---- rows ---------------------------------------------------------------------------------------------------------------------------------------------

    override val groups: List<SettingGroup>
        get() {
            val out = mutableListOf<SettingGroup>()
            val noise = buildList {
                val max = u8(HEARTHROUGH, 7)
                val level = u8(HEARTHROUGH, 3)
                // The wire's 0 is the strongest HearThrough, as on the GNP models; the screen counts from the weakest.
                if (max != null && max > 0 && level != null) add(Setting.Level("jb_ht_level", max - level.coerceIn(0, max) + 1, 1, max + 1))
                u8(HEARTHROUGH, 5)?.takeIf { it != 0 }?.let { add(Setting.Toggle("jb_ht_mix", it == 1)) }
                val ancMax = u8(LEVELED_ANC, 1)
                val anc = u8(LEVELED_ANC, 2)
                if (ancMax != null && ancMax > 0 && anc != null) add(Setting.Level("jb_anc_level", anc.coerceIn(0, ancMax), 0, ancMax))
                loop()?.let { add(Setting.Choice("jb_loop", it.joinToString("_"), LOOP_CHOICES.map { c -> c.joinToString("_") })) }
            }
            if (noise.isNotEmpty()) out += SettingGroup("jb_noise", SettingTab.HOME, noise)
            val device = buildList {
                switch(IN_EAR, 1, "jb_gaia_in_ear")?.let { add(it) }
                switch(IN_EAR, 3, "jb_gaia_auto_answer")?.let { add(it) }
                switch(IN_EAR, 5, "jb_gaia_auto_mute")?.let { add(it) }
                switch(IN_EAR, 7, "jb_gaia_auto_pause")?.let { add(it) }
                switch(SIDE_TONE, 1, "jb_gaia_sidetone")?.let { add(it) }
                val max = u8(SIDE_TONE, 5)
                val level = u8(SIDE_TONE, 4)
                if (max != null && max > 0 && level != null) add(Setting.Level("jb_gaia_sidetone_level", level.coerceIn(0, max), 0, max))
                u8(BUTTON_CONTROL, 1)?.let { v -> BUTTON_NAMES.getOrNull(v)?.let { add(Setting.Choice("jb_gaia_button", it, BUTTON_NAMES)) } }
            }
            if (device.isNotEmpty()) out += SettingGroup("jb_headset", SettingTab.DEVICE, device)
            return out
        }

    /** A switch of the plugins' common form: 0 not supported, 1 on, 2 off. */
    private fun switch(feature: Int, command: Int, key: String): Setting? = u8(feature, command)?.takeIf { it != 0 }?.let { Setting.Toggle(key, it == 1) }

    private fun loop(): List<String>? {
        val b = values[(SOUND_MODES shl 8) or 6] ?: return null
        if (b.isEmpty() || b.size > 3) return null
        return b.map { LOOP_NAMES.getOrNull(it.toInt() and 0xFF) ?: return null }.takeIf { it.toSet().size == it.size }
    }

    // ---- changes -------------------------------------------------------------------------------------------------------------------------------------------

    override fun set(command: Command, value: Int): List<Gaia3Packet>? {
        if (command != Command.ANC_MODE) return null
        val mode = Command.ANC_MODE.labels[value] ?: return null
        if (has(SOUND_MODES)) {
            val code = LOOP_NAMES.indexOf(mapOf("off" to "Off", "anc" to "ANC", "transparency" to "HT")[mode] ?: return null)
            values[(SOUND_MODES shl 8) or 4] = byteArrayOf(code.toByte())
            refresh()
            readBacks = listOf(frame(SOUND_MODES, 4))
            return listOf(frame(SOUND_MODES, 5, code))
        }
        if (has(HEARTHROUGH)) {
            val code = if (mode == "transparency") 1 else if (mode == "off") 2 else return null
            values[(HEARTHROUGH shl 8) or 1] = byteArrayOf(code.toByte())
            refresh()
            readBacks = listOf(frame(HEARTHROUGH, 1, 0))
            return listOf(frame(HEARTHROUGH, 2, code))
        }
        return null
    }

    override fun change(change: SettingChange): List<Gaia3Packet>? {
        fun toggle(feature: Int, getCmd: Int, write: Int): List<Gaia3Packet>? {
            val on = (change as? SettingChange.Toggle)?.on ?: return null
            if (values[(feature shl 8) or getCmd] == null) return null
            values[(feature shl 8) or getCmd] = byteArrayOf(if (on) 1 else 2)
            readBacks = listOf(read(feature, getCmd))
            return listOf(frame(feature, write, if (on) 1 else 2))
        }
        return when (change.key) {
            "jb_gaia_in_ear" -> toggle(IN_EAR, 1, 2)
            "jb_gaia_auto_answer" -> toggle(IN_EAR, 3, 4)
            // The vendor's own setter for the mute reminder calls the auto-mute operation (6), so the reminder is not written; auto-mute's own is operation 6.
            "jb_gaia_auto_mute" -> toggle(IN_EAR, 5, 6)
            "jb_gaia_auto_pause" -> toggle(IN_EAR, 7, 8)
            "jb_gaia_sidetone" -> toggle(SIDE_TONE, 1, 2)
            "jb_ht_mix" -> toggle(HEARTHROUGH, 5, 6)
            "jb_ht_level" -> {
                val value = (change as? SettingChange.Level)?.value ?: return null
                val max = u8(HEARTHROUGH, 7) ?: return null
                val wire = (max + 1 - value).coerceIn(0, max)
                values[(HEARTHROUGH shl 8) or 3] = byteArrayOf(wire.toByte())
                readBacks = listOf(frame(HEARTHROUGH, 3, 0))
                listOf(frame(HEARTHROUGH, 4, wire))
            }
            "jb_anc_level" -> {
                val value = (change as? SettingChange.Level)?.value ?: return null
                val max = u8(LEVELED_ANC, 1) ?: return null
                val level = value.coerceIn(0, max)
                values[(LEVELED_ANC shl 8) or 2] = byteArrayOf(level.toByte())
                readBacks = listOf(frame(LEVELED_ANC, 2))
                listOf(frame(LEVELED_ANC, 3, level))
            }
            "jb_gaia_sidetone_level" -> {
                val value = (change as? SettingChange.Level)?.value ?: return null
                val max = u8(SIDE_TONE, 5) ?: return null
                val level = value.coerceIn(0, max)
                values[(SIDE_TONE shl 8) or 4] = byteArrayOf(level.toByte())
                readBacks = listOf(frame(SIDE_TONE, 4))
                listOf(frame(SIDE_TONE, 3, level))
            }
            "jb_gaia_button" -> {
                val option = (change as? SettingChange.Choice)?.option ?: return null
                val code = BUTTON_NAMES.indexOf(option).takeIf { it >= 0 } ?: return null
                values[(BUTTON_CONTROL shl 8) or 1] = byteArrayOf(code.toByte())
                readBacks = listOf(frame(BUTTON_CONTROL, 1))
                listOf(frame(BUTTON_CONTROL, 2, code))
            }
            "jb_loop" -> {
                val option = (change as? SettingChange.Choice)?.option ?: return null
                val modes = LOOP_CHOICES.firstOrNull { it.joinToString("_") == option } ?: return null
                val body = ByteArray(modes.size) { LOOP_NAMES.indexOf(modes[it]).toByte() }
                values[(SOUND_MODES shl 8) or 6] = body
                readBacks = listOf(frame(SOUND_MODES, 6))
                listOf(frameOf(SOUND_MODES, 7, body))
            }
            else -> null
        }.also { if (it != null) refresh() }
    }

    // ---- the equalizer -------------------------------------------------------------------------------------------------------------------------------------

    private class Band(val hz: Int, val maxDb: Int, val gainDb: Double, val q: Double)

    private var bands: List<Band>? = null
    private var gains: List<Double>? = null

    /** Max gains (`07/02`: first band, last band, then one gain in dB for each band) and the bands (`001D` feature 5, command 5: the same range, then seven bytes each). */
    private fun handleMusic(packet: Gaia3Packet): Boolean {
        val p = packet.payload
        if (packet.command != MUSIC_READ_BANDS || p.size < 2) return false
        val start = p[0].toInt() and 0xFF
        val end = p[1].toInt() and 0xFF
        val n = end - start + 1
        if (n <= 0 || p.size < 2 + 7 * n) return false
        val maxes = maxGains()
        fun u16(i: Int) = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
        val parsed = List(n) { i ->
            val at = 2 + 7 * i
            Band(u16(at), maxes?.getOrNull(i) ?: 0, u16(at + 5).toShort().toInt() / 60.0, u16(at + 2) / 4096.0)
        }
        bands = parsed
        gains = parsed.map { it.gainDb }
        return true
    }

    private fun maxGains(): List<Int>? {
        val p = values[(MAX_GAINS shl 8) or 2] ?: return null
        if (p.size < 2 || p.size != (p[1].toInt() and 0xFF) + 3) return null
        return p.drop(2).map { it.toInt() and 0xFF }
    }

    override val eqKnown: Boolean get() = bands?.let { b -> b.isNotEmpty() && b.all { it.maxDb > 0 } } == true
    override val eqBandsHz: List<Int> get() = bands?.map { it.hz }.orEmpty()
    override val eqGains: List<Double>? get() = gains
    override val eqMaxDb: Double get() = bands?.minOfOrNull { it.maxDb.toDouble() } ?: 0.0
    override val eqPresets: List<Pair<String, List<Double>>>
        get() {
            val b = bands ?: return emptyList()
            return JabraPresets.ALL.filter { it.percent.size == b.size }.map { p -> p.name to b.indices.map { b[it].maxDb * p.percent[it] / 100.0 } }
        }

    /** `001D` feature 5, command 6: first band, last band, then each gain as a signed 16-bit value in 1/60 dB (a float times 60, truncated toward zero). A gain beyond a band's maximum sends nothing. */
    override fun eqCurve(gains: List<Double>): List<ByteArray>? {
        val b = bands ?: return null
        if (gains.size != b.size || gains.indices.any { kotlin.math.abs(gains[it]) > b[it].maxDb + 1e-9 }) return null
        val payload = ByteArray(2 + 2 * b.size)
        payload[0] = 0
        payload[1] = (b.size - 1).toByte()
        for (i in b.indices) {
            val g = (gains[i].toFloat() * 60f).toInt()
            payload[2 + 2 * i] = (g shr 8).toByte()
            payload[3 + 2 * i] = g.toByte()
        }
        this.gains = gains
        readBacks = listOf(music(MUSIC_READ_BANDS, 0, b.size - 1))
        return listOf(Gaia3Packet(QUALCOMM, (MUSIC_FEATURE shl 9) or MUSIC_WRITE_GAINS, payload).bytes())
    }

    public companion object {
        public const val VENDOR: Int = 0x0067
        public const val QUALCOMM: Int = 0x001D
        private const val REPLY_MS = 1_500L
        private const val GAP_MS = 20L
        private const val FEATURES = 1
        private const val REGISTRY = 2
        private const val SIDE_TONE = 3
        private const val HEARTHROUGH = 4
        private const val BATTERY = 5
        private const val MAX_GAINS = 7
        private const val BUTTON_CONTROL = 9
        private const val SOUND_MODES = 10
        private const val LEVELED_ANC = 12
        private const val IN_EAR = 16
        private const val MUSIC_FEATURE = 5
        private const val MUSIC_READ_BANDS = 5
        private const val MUSIC_WRITE_GAINS = 6
        private val BUTTON_NAMES = listOf("assistant", "disabled", "spotify")
        private val LOOP_NAMES = listOf("Off", "ANC", "HT")
        private val LOOP_CHOICES = listOf(listOf("HT", "ANC"), listOf("HT", "ANC", "Off"), listOf("HT", "Off"))

        /** A notification's id where it differs from the command that reads the same thing: HearThrough's level and mix, the sound mode and its loop. */
        private val NOTIFICATION_AS_COMMAND = mapOf(
            ((HEARTHROUGH shl 8) or 2) to 3, ((HEARTHROUGH shl 8) or 3) to 5, ((SOUND_MODES shl 8) or 1) to 4, ((SOUND_MODES shl 8) or 4) to 6,
        )

        /** Each feature's operations that may go out (gets and the sets PairFree builds). */
        private val ALLOWED: Map<Int, Set<Int>> = mapOf(
            FEATURES to setOf(1), REGISTRY to setOf(1), BATTERY to setOf(1), SIDE_TONE to setOf(1, 2, 3, 4, 5), HEARTHROUGH to setOf(1, 2, 3, 4, 5, 6, 7),
            MAX_GAINS to setOf(2), BUTTON_CONTROL to setOf(1, 2), SOUND_MODES to setOf(4, 5, 6, 7), LEVELED_ANC to setOf(1, 2, 3), IN_EAR to setOf(1, 2, 3, 4, 5, 6, 7, 8),
        )
    }
}
