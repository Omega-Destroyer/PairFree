package io.github.omegadestroyer.pairfree.protocol.skullcandy

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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** One message of the BES channel: its payload (before the frame and the cipher), a name for logs and reports, and whether it belongs to the handshake (sent unencrypted). */
public class BesMessage(public val payload: ByteArray, public val label: String, public val handshake: Boolean = false) {
    /** The command the message reads or writes (the byte at offset 4), or -1 for a handshake message. */
    public val command: Int get() = if (handshake || payload.size < 5) -1 else payload[4].toInt() and 0xFF
}

/** What the earbuds sent, decrypted and unframed. */
public class BesReply(public val payload: ByteArray)

/** The BES TOTA commands the app's Dart classes build (`data/skullcandy/tota_commands.json`, docs/skullcandy/PROTOCOL.md section 12.1). */
public object BesCommand {
    public const val BATTERY: Int = 0x01
    public const val EQ_MODE: Int = 0x02
    public const val CUSTOM_EQ: Int = 0x03
    public const val BUTTONS: Int = 0x04
    public const val MULTIPOINT: Int = 0x05
    public const val LOW_LATENCY: Int = 0x06
    public const val SERIAL: Int = 0x07
    public const val FIRMWARE: Int = 0x08
    public const val HEARING: Int = 0x09
    public const val SIDETONE: Int = 0x10
    public const val TOUCH: Int = 0x11
    public const val TWS: Int = 0x12

    /** The commands PairFree may write: never the serial number's (private) or the firmware's. */
    public val WRITABLE: Set<Int> = setOf(EQ_MODE, CUSTOM_EQ, BUTTONS, MULTIPOINT, LOW_LATENCY, HEARING, SIDETONE, TOUCH)

    /** The commands PairFree may read: everything but the serial number. */
    public val READABLE: Set<Int> = setOf(BATTERY, EQ_MODE, CUSTOM_EQ, BUTTONS, MULTIPOINT, LOW_LATENCY, FIRMWARE, HEARING, SIDETONE, TOUCH, TWS)

    public fun read(command: Int): BesMessage = BesMessage(byteArrayOf(0x09, 0x63, 0x02, 0x00, command.toByte(), 0x00), "read ${command.toString(16)}")

    public fun write(command: Int, value: ByteArray): BesMessage =
        BesMessage(byteArrayOf(0x08, 0x63, (2 + value.size).toByte(), (((2 + value.size) shr 8) and 0xFF).toByte(), command.toByte(), 0x00) + value, "write ${command.toString(16)}")

    public fun write(command: Int, vararg value: Int): BesMessage = write(command, ByteArray(value.size) { value[it].toByte() })
}

/**
 * The button settings of the BES models that have custom button actions (docs/skullcandy/PROTOCOL.md section 12.4): four entries `02 <gesture> <action u16 LE>` that the earbuds report and the
 * app writes whole. The names are the app's own codes, read from its builder (`BesGesture.besGestureId`, `BesAction.besActionId`) and its reply decoder.
 */
public object BesButtons {
    /** The gestures: the wire's byte and the row's name (single, double and triple press, and the press and hold). */
    public val GESTURES: List<Pair<Int, String>> = listOf(0x08 to "single", 0x09 to "double", 0x0A to "triple", 0x06 to "hold")

    /** The actions the app's writer encodes: the wire's code and the option's name. */
    public val ACTIONS: List<Pair<Int, String>> = listOf(
        3 to "play_pause", 1 to "track_back", 2 to "track_forward", 5 to "volume_down", 4 to "volume_up", 7 to "take_photo", 6 to "voice_assistant",
    )

    public val OPTIONS: List<String> = ACTIONS.map { it.second }

    /** One entry of the reply or the write. */
    public class Entry(public val gesture: Int, public val action: Int)

    /** The entries of a reply's data (a whole number of four-byte entries, whose first byte the app does not use), or null when the data is not that. */
    public fun parse(data: ByteArray): List<Entry>? {
        if (data.isEmpty() || data.size % 4 != 0) return null
        return (0 until data.size / 4).map { i ->
            Entry(data[4 * i + 1].toInt() and 0xFF, (data[4 * i + 2].toInt() and 0xFF) or ((data[4 * i + 3].toInt() and 0xFF) shl 8))
        }
    }

    /** The write of [entries]: `08 63 <length> 00 04 00` and each as `02 <gesture> <action lo> <action hi>`. */
    public fun write(entries: List<Entry>): BesMessage {
        val body = ByteArray(entries.size * 4)
        entries.forEachIndexed { i, e ->
            body[4 * i] = 0x02
            body[4 * i + 1] = e.gesture.toByte()
            body[4 * i + 2] = (e.action and 0xFF).toByte()
            body[4 * i + 3] = ((e.action shr 8) and 0xFF).toByte()
        }
        return BesCommand.write(BesCommand.BUTTONS, body)
    }
}

/** The custom equaliser's wire form on BES (docs/skullcandy/PROTOCOL.md section 12.3): a gain as a sign and magnitude over the app's range of minus 5 to plus 5 dB, and the band's frequency and Q. */
public object BesEq {
    /** The gain range the wire encodes, in dB each way. */
    public const val RANGE_DB: Int = 5

    /** The Q byte the app writes for every band: 0.91 times 46.41, rounded. */
    public const val Q_BYTE: Int = 0x2A

    /** The code of the custom curve in the EQ mode command. */
    public const val CUSTOM_MODE: Int = 101

    /** The two bytes (little endian) of [db]. */
    public fun gain(db: Double): ByteArray {
        val g = db.coerceIn(-RANGE_DB.toDouble(), RANGE_DB.toDouble())
        val d = ((g + RANGE_DB) * 6553.5).roundToInt() - 32768
        // below zero the sign bit is set on the magnitude (minus 5 dB is 00 80); above, the magnitude tops out at 7FFF
        val v = if (d < 0) (abs(d) or 0x8000) else minOf(d, 0x7FFF)
        return byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }

    /** The gain in dB of two wire bytes. */
    public fun db(lo: Int, hi: Int): Double {
        val v = (lo and 0xFF) or ((hi and 0xFF) shl 8)
        val mag = v and 0x7FFF
        // a set sign bit over a zero magnitude is the lower end, minus 5 dB (`00 80`)
        val r = if (v and 0x8000 != 0) (if (mag == 0) 0 else 32768 - mag) else 32768 + mag
        return r / 6553.5 - RANGE_DB
    }

    /** The write of a curve: `08 63 21 00 03 00`, the band count and for each band `01`, the gain, the frequency (little endian) and the Q byte. */
    public fun curve(hz: List<Int>, gains: List<Double>): BesMessage {
        val body = ArrayList<Byte>()
        body += hz.size.toByte()
        for (i in hz.indices) {
            body += 0x01
            gain(gains[i]).forEach { body += it }
            body += (hz[i] and 0xFF).toByte()
            body += ((hz[i] shr 8) and 0xFF).toByte()
            body += Q_BYTE.toByte()
        }
        return BesCommand.write(BesCommand.CUSTOM_EQ, body.toByteArray())
    }
}

/**
 * The conversation with a BES product over its TOTA channel (docs/skullcandy/PROTOCOL.md section 12): the handshake that agrees an AES key, then the commands the app uses for each model's features,
 * read once on connecting and written on a change. A reply is read by the command it names, whenever it comes. Nothing has met a real earbud: the layouts are read from the app and its own
 * tables, and the places where a reading is an inference are named in the document.
 */
public class SkullcandyBesTalk(public val product: SkullcandyProduct, private val random: Random = Random.Default) : SessionTalk<BesMessage>() {
    override val pacing: Pacing = Pacing(replyMs = 1500, gapMs = 120, startGapMs = 120, pollMs = Pacing.POLL_MS, resends = 1, closeWhenUnanswered = true)

    private var current = EarbudState()
    override val state: EarbudState get() = current

    private val f = product.features
    private val hasHearing = "adjustableNoiseCancellation" in f || "activeNoiseCancellation" in f || "stayAwareMode" in f || "adjustableStayAwareMode" in f
    private val hasAnc = "adjustableNoiseCancellation" in f || "activeNoiseCancellation" in f
    private val hasLevel = "advancedHearingModes" in f
    private val hasEqModes = "definedEqualizer" in f || "definedEqualizerToggle" in f
    private val hasCustomEq = "customEqualizer" in f
    private val hasMultipoint = "multipointPairingToggle" in f || "multipointPairing" in f
    private val hasLatency = "lowLatencyMode" in f || "gamingMode" in f
    private val hasSidetone = "adjustableSidetoneMode" in f
    private val hasTouch = "capTouchControl" in f
    private val hasButtons = "customButtonActions" in f

    private val hostBuffer = BesTota.newBuffer(random)
    private val reader = BesTota.Reader()
    private var key: ByteArray? = null
    private var hash: ByteArray? = null

    /** True once the earbuds have accepted the key; commands go out only then. */
    public var secured: Boolean = false
        private set

    /** True when the earbuds refused the confirmation or never answered it. */
    public var refused: Boolean = false
        private set

    private val outbox = ArrayList<BesMessage>()
    private var readBacks: List<BesMessage> = emptyList()

    /** The last decrypted reply to each command, by its name, for the device report. */
    public val replies: MutableMap<String, ByteArray> = LinkedHashMap()

    private var multipoint: Boolean? = null
    private var latency: Boolean? = null
    private var sidetone: Boolean? = null
    private var touch: Boolean? = null
    private var hearing: Int? = null
    private var level: Int? = null
    private var eqMode: Int? = null
    private var curve: List<Pair<Int, Double>>? = null
    private var buttons: List<BesButtons.Entry>? = null

    /** The curve's bands in Hz: what the earbuds reported, else the app's own default. */
    public val eqBandsHz: List<Int> get() = curve?.map { it.first } ?: SkullcandyEq.bandsHz(product.enumName)

    /** The gain range the wire encodes. */
    public val eqRangeDb: Int = BesEq.RANGE_DB

    /** The presets the earbuds hold, by the app's names and the number each is picked by. */
    public val eqPresets: List<Pair<String, Int>> = if (hasEqModes) SkullcandyEq.presets(product.enumName) else emptyList()

    /** True when the model has an equaliser of either kind. */
    public val hasEq: Boolean = hasCustomEq || eqPresets.isNotEmpty()

    /** True when the custom curve has been read, which a write needs. */
    public val curveKnown: Boolean get() = curve != null

    /** The preset the earbuds report, or null while the custom curve is in force or nothing is known. */
    public val eqPresetName: String? get() = eqMode?.let { m -> eqPresets.firstOrNull { it.second == m }?.first }

    /** The curve the earbuds report, in whole dB, when the custom mode is in force. */
    public val eqGains: List<Double>? get() = if (eqMode == BesEq.CUSTOM_MODE) curve?.map { it.second } else null

    override val eq: Any? get() = if (eqMode != null || curve != null) Triple(eqMode, curve, hasEq) else null

    override val groups: List<SettingGroup> get() = buildList {
        val m = hearing
        val l = level
        if (hasLevel && l != null && (m == 1 || m == 2)) {
            add(SettingGroup("noise", SettingTab.HOME, listOf(Setting.Level(if (m == 2) ANC_LEVEL else TRANSPARENCY_LEVEL, (l + 5) / 10, 0, 10))))
        }
        buttons?.let { entries ->
            val rows = entries.mapNotNull { e ->
                val name = BesButtons.GESTURES.firstOrNull { it.first == e.gesture }?.second ?: return@mapNotNull null
                Setting.Choice("sk_button_$name", BesButtons.ACTIONS.firstOrNull { it.first == e.action }?.second, BesButtons.OPTIONS)
            }
            if (rows.isNotEmpty()) add(SettingGroup("sk_buttons", SettingTab.CONTROLS, rows))
        }
        multipoint?.let { add(SettingGroup("connection", SettingTab.DEVICE, listOf(Setting.Toggle(MULTIPOINT, it)))) }
        latency?.let { add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle(GAMING_MODE, it)))) }
        sidetone?.let { add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle(SIDETONE, it)))) }
        touch?.let { add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle(TOUCH_CONTROL, it)))) }
    }

    override fun start(): List<BesMessage> = listOf(BesMessage(BesTota.hello(hostBuffer), "handshake", handshake = true))

    /** The reads once the key is agreed: the model's features only. */
    private fun opening(): List<BesMessage> = buildList {
        add(BesCommand.read(BesCommand.BATTERY))
        if (hasHearing) add(BesCommand.read(BesCommand.HEARING))
        if (hasEqModes || hasCustomEq) add(BesCommand.read(BesCommand.EQ_MODE))
        if (hasCustomEq) add(BesCommand.read(BesCommand.CUSTOM_EQ))
        if (hasMultipoint) add(BesCommand.read(BesCommand.MULTIPOINT))
        if (hasLatency) add(BesCommand.read(BesCommand.LOW_LATENCY))
        if (hasSidetone) add(BesCommand.read(BesCommand.SIDETONE))
        if (hasTouch) add(BesCommand.read(BesCommand.TOUCH))
        if (hasButtons) add(BesCommand.read(BesCommand.BUTTONS))
        add(BesCommand.read(BesCommand.FIRMWARE))
        add(BesCommand.read(BesCommand.TWS))
    }

    override fun poll(): List<BesMessage> = if (secured) listOf(BesCommand.read(BesCommand.BATTERY)) else emptyList()

    override fun allowed(message: BesMessage): Boolean {
        val p = message.payload
        if (message.handshake) return p.size >= 5 && p[1].toInt() == 0x10 && (p[0].toInt() == 0x01 || p[0].toInt() == 0x03)
        if (!secured || p.size < 6 || p[1].toInt() != 0x63) return false
        val c = p[4].toInt() and 0xFF
        return when (p[0].toInt()) {
            0x09 -> c in BesCommand.READABLE && p.size == 6
            0x08 -> c in BesCommand.WRITABLE && (c != BesCommand.BUTTONS || p.size == 6 + 16)
            else -> false
        }
    }

    override fun bytes(message: BesMessage): ByteArray =
        if (message.handshake) BesTota.plainWire(message.payload) else BesTota.secureWire(key ?: error("no key"), message.payload)

    override fun describe(message: BesMessage): String = message.label

    override fun replyMs(message: BesMessage): Long = if (message.handshake) HANDSHAKE_MS else pacing.replyMs

    override fun answers(sent: BesMessage, got: Any): Boolean {
        val r = got as? BesReply ?: return false
        if (sent.handshake) return BesTota.isHandshake(r.payload)
        return r.payload.size >= 5 && r.payload[4].toInt() and 0xFF == sent.command
    }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val answers = mutableListOf<Any>()
        for (payload in reader.feed(bytes)) {
            guarded {
                if (!secured) {
                    if (handshake(payload)) answers += BesReply(payload)
                } else {
                    val k = key ?: return@guarded
                    if (payload.isEmpty() || payload.size % 16 != 0) return@guarded
                    val plain = Aes128.cbcDecrypt(k, BesTota.IV, payload)
                    answers += BesReply(plain)
                    changed = apply(plain) || changed
                }
            }
        }
        return Fed(changed, answers)
    }

    /** One handshake message: true when it is one the session answers. */
    private fun handshake(payload: ByteArray): Boolean {
        BesTota.theirBuffer(payload)?.let { theirs ->
            val h = BesTota.hashOf(hostBuffer, theirs)
            hash = h
            key = BesTota.keyOf(h)
            outbox += BesMessage(BesTota.confirm(h), "confirm", handshake = true)
            return true
        }
        if (BesTota.isHandshake(payload) && payload[0].toInt() == 0x00) {
            if (BesTota.accepted(payload)) {
                secured = true
                outbox += opening()
            } else {
                refused = true
            }
            return true
        }
        return false
    }

    /** The data of a reply: the bytes from offset 7 to the length its header gives (zero padding left out), or null when it is not a TOTA reply. */
    private fun dataOf(p: ByteArray): ByteArray? {
        if (p.size < 8 || p[1].toInt() != 0x63 || (p[0].toInt() != 0x0B && p[0].toInt() != 0x0A)) return null
        val length = (p[2].toInt() and 0xFF) or ((p[3].toInt() and 0xFF) shl 8)
        val end = if (length >= 3 && 4 + length <= p.size) 4 + length else p.size
        return p.copyOfRange(7, maxOf(7, end))
    }

    /** Applies a decrypted reply to the state; true when something the screens show changed. */
    private fun apply(p: ByteArray): Boolean {
        val command = if (p.size > 4) p[4].toInt() and 0xFF else return false
        replies[command.toString(16)] = p
        if (p[0].toInt() != 0x0B) return false
        val d = dataOf(p) ?: return false
        if (d.isEmpty()) return false
        val on = d.last().toInt() == 1
        return when (command) {
            BesCommand.BATTERY -> {
                if (d.size < 2) return false
                val next = current.values.toMutableMap()
                val left = d[0].toInt() and 0xFF
                val right = d[1].toInt() and 0xFF
                if (left in 0..100) next[Command.BATTERY_LEFT] = Value.Number(left)
                if (right in 0..100) next[Command.BATTERY_RIGHT] = Value.Number(right)
                replace(next)
            }
            BesCommand.HEARING -> {
                // the second to last byte is the mode, the last the level (the app's decoder reads them so)
                val mode = (if (d.size >= 2) d[d.size - 2] else d[0]).toInt() and 0xFF
                hearing = mode
                if (d.size >= 2) level = (d.last().toInt() and 0xFF).coerceIn(0, 100)
                val label = when (mode) { 0 -> "off"; 1 -> "transparency"; 2 -> if (hasAnc) "anc" else null; else -> null }
                val next = current.values.toMutableMap()
                if (label != null) next[Command.ANC_MODE] = Value.Number(Command.ANC_MODE.labels.entries.first { it.value == label }.key)
                replace(next)
                true
            }
            BesCommand.EQ_MODE -> { eqMode = d.last().toInt() and 0xFF; true }
            BesCommand.CUSTOM_EQ -> {
                val count = d[0].toInt() and 0xFF
                if (count < 1 || d.size < 1 + count * 6) return false
                curve = (0 until count).map { i ->
                    val b = 1 + i * 6
                    val hz = (d[b + 3].toInt() and 0xFF) or ((d[b + 4].toInt() and 0xFF) shl 8)
                    hz to BesEq.db(d[b + 1].toInt(), d[b + 2].toInt())
                }
                true
            }
            BesCommand.MULTIPOINT -> { multipoint = on; true }
            BesCommand.LOW_LATENCY -> { latency = on; true }
            BesCommand.SIDETONE -> { sidetone = on; true }
            BesCommand.TOUCH -> { touch = on; true }
            BesCommand.BUTTONS -> BesButtons.parse(d)?.let { buttons = it; true } ?: false
            else -> false
        }
    }

    private fun replace(next: Map<Command, Value>): Boolean {
        if (next == current.values) return false
        current = current.copy(values = next)
        return true
    }

    override fun takeOutbox(): List<BesMessage> = outbox.toList().also { outbox.clear() }

    override fun takeReadBacks(): List<BesMessage> = readBacks.also { readBacks = emptyList() }

    /** The hearing mode write for [mode] (0 off, 1 transparency, 2 noise cancelling): with the level on the models that have one, as the app's slider sends it. */
    private fun hearingWrite(mode: Int, lvl: Int): BesMessage =
        if (hasLevel && mode != 0) BesCommand.write(BesCommand.HEARING, mode, lvl) else BesCommand.write(BesCommand.HEARING, mode)

    override fun set(command: Command, value: Int): List<BesMessage>? {
        if (command != Command.ANC_MODE || !hasHearing) return null
        val mode = when (Command.ANC_MODE.labels[value]) {
            "off" -> 0
            "transparency" -> 1
            "anc" -> if (hasAnc) 2 else return null
            else -> return null
        }
        hearing = mode
        readBacks = listOf(BesCommand.read(BesCommand.HEARING))
        return listOf(hearingWrite(mode, level ?: DEFAULT_LEVEL))
    }

    override fun change(change: SettingChange): List<BesMessage>? {
        if (change is SettingChange.Toggle) {
            val (cmd, ok) = when (change.key) {
                MULTIPOINT -> BesCommand.MULTIPOINT to hasMultipoint
                GAMING_MODE -> BesCommand.LOW_LATENCY to hasLatency
                SIDETONE -> BesCommand.SIDETONE to hasSidetone
                TOUCH_CONTROL -> BesCommand.TOUCH to hasTouch
                else -> return null
            }
            if (!ok) return null
            when (change.key) {
                MULTIPOINT -> multipoint = change.on
                GAMING_MODE -> latency = change.on
                SIDETONE -> sidetone = change.on
                else -> touch = change.on
            }
            readBacks = listOf(BesCommand.read(cmd))
            return listOf(BesCommand.write(cmd, if (change.on) 1 else 0))
        }
        if (change is SettingChange.Choice && change.key.startsWith("sk_button_") && hasButtons) {
            val gesture = BesButtons.GESTURES.firstOrNull { "sk_button_${it.second}" == change.key }?.first ?: return null
            val action = BesButtons.ACTIONS.firstOrNull { it.second == change.option }?.first ?: return null
            val now = buttons ?: return null
            if (now.none { it.gesture == gesture }) return null
            // the app writes all four entries each time, the changed one with its new action
            buttons = now.map { if (it.gesture == gesture) BesButtons.Entry(gesture, action) else it }
            readBacks = listOf(BesCommand.read(BesCommand.BUTTONS))
            return listOf(BesButtons.write(buttons!!))
        }
        if (change is SettingChange.Level && (change.key == ANC_LEVEL || change.key == TRANSPARENCY_LEVEL) && hasLevel) {
            val mode = hearing?.takeIf { it == 1 || it == 2 } ?: return null
            level = (change.value * 10).coerceIn(0, 100)
            readBacks = listOf(BesCommand.read(BesCommand.HEARING))
            return listOf(hearingWrite(mode, level!!))
        }
        return null
    }

    /** The frames (payloads) that pick the preset [name], or null for a name the model does not have. */
    public fun eqPreset(name: String): List<ByteArray>? {
        val n = eqPresets.firstOrNull { it.first == name }?.second ?: return null
        eqMode = n
        return listOf(BesCommand.write(BesCommand.EQ_MODE, n).payload)
    }

    /** The frames (payloads) that put a custom curve of [gains] (one per band) on the earbuds, once the earbuds have reported their bands. */
    public fun eqCurve(gains: List<Double>): List<ByteArray>? {
        val c = curve ?: return null
        if (!hasCustomEq || gains.size != c.size) return null
        eqMode = BesEq.CUSTOM_MODE
        curve = c.mapIndexed { i, (hz, _) -> hz to gains[i].coerceIn(-BesEq.RANGE_DB.toDouble(), BesEq.RANGE_DB.toDouble()) }
        return listOf(BesCommand.write(BesCommand.EQ_MODE, BesEq.CUSTOM_MODE).payload, BesEq.curve(c.map { it.first }, gains).payload)
    }

    /** A message for a payload an equaliser frame holds. */
    public fun messageOf(payload: ByteArray): BesMessage = BesMessage(payload, "write ${(payload[4].toInt() and 0xFF).toString(16)}")

    public companion object {
        public const val MULTIPOINT: String = "multipoint"
        public const val GAMING_MODE: String = "sk_gaming_mode"
        public const val SIDETONE: String = "sk_sidetone"
        public const val TOUCH_CONTROL: String = "sk_touch_control"
        public const val ANC_LEVEL: String = "sk_anc_level"
        public const val TRANSPARENCY_LEVEL: String = "sk_transparency_level"
        private const val HANDSHAKE_MS: Long = 10_000L
        private const val DEFAULT_LEVEL: Int = 50
    }
}
