package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.PMath
import io.github.omegadestroyer.pairfree.protocol.toHex2

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * What the Sound tab needs of a Sennheiser product's equalizer, whichever protocol it speaks: the bands, their range, Smart Control's presets for the product,
 * the gains the earbuds hold, and the messages that set a curve.
 */
public interface SennheiserEqSource {
    public val eqBandsHz: List<Int>
    public val eqMinDb: Double
    public val eqMaxDb: Double
    public val eqPresets: List<Pair<String, List<Double>>>
    public val eqGains: List<Double>?
    public val eqKnown: Boolean
    public fun eqCurve(gains: List<Double>): List<ByteArray>?
}

/**
 * A GAIA v2 frame, as Smart Control 4.9.4's `SPPGaiaFramer` writes it (docs/sennheiser/PROTOCOL.md section 4.9): `FF`, version 1, flags, the payload's length, the
 * vendor, the command and the payload, big-endian. A response is the command with bit 15 set, and its payload starts with a status byte (0 is success).
 */
public class Gaia2Packet(public val vendor: Int, public val command: Int, public val payload: ByteArray = ByteArray(0)) {
    public val isResponse: Boolean get() = command and 0x8000 != 0

    /** What identifies the command this is or answers. */
    public val key: Int get() = (vendor shl 16) or (command and 0x7FFF)

    public fun bytes(): ByteArray {
        require(payload.size <= 254) { "a GAIA frame holds at most 254 bytes of payload" }
        return byteArrayOf(START.toByte(), VERSION.toByte(), 0, payload.size.toByte(), (vendor shr 8).toByte(), vendor.toByte(), (command shr 8).toByte(), command.toByte()) + payload
    }

    public companion object {
        public const val START: Int = 0xFF
        public const val VERSION: Int = 1
        public fun keyOf(vendor: Int, command: Int): Int = (vendor shl 16) or (command and 0x7FFF)
    }
}

/** Cuts what the channel gives into [Gaia2Packet]s: fragments and joined frames, and resynchronising after bad data. */
public class Gaia2Parser {
    private var pending = ByteArray(0)

    public fun feed(bytes: ByteArray): List<Gaia2Packet> {
        val out = mutableListOf<Gaia2Packet>()
        pending += bytes
        while (pending.isNotEmpty()) {
            if ((pending[0].toInt() and 0xFF) != Gaia2Packet.START) {
                skip(1)
                continue
            }
            if (pending.size < 4) break
            val flags = pending[2].toInt() and 0xFF
            if ((pending[1].toInt() and 0xFF) != Gaia2Packet.VERSION || flags and 0xFE != 0) {
                skip(1)
                continue
            }
            val length = pending[3].toInt() and 0xFF
            val size = 8 + length + (flags and 1)
            if (pending.size < size) break
            val vendor = ((pending[4].toInt() and 0xFF) shl 8) or (pending[5].toInt() and 0xFF)
            val command = ((pending[6].toInt() and 0xFF) shl 8) or (pending[7].toInt() and 0xFF)
            out += Gaia2Packet(vendor, command, pending.copyOfRange(8, 8 + length))
            skip(size)
        }
        return out
    }

    private fun skip(n: Int) {
        pending = pending.copyOfRange(n, pending.size)
    }
}

public object Gaia2Models {
    public val ALL: List<Gaia2Model> get() = GAIA2_MODELS

    public fun fromName(name: String): Gaia2Model? = ALL.firstOrNull { m -> m.names.any { it.equals(name.trim(), ignoreCase = true) } }

    public fun model(name: String): Model? = fromName(name)?.let { Model(it.name, listOf(it.name), "Sennheiser", fitKeepsAnc = true, form = it.form) }
}

/**
 * A Sennheiser product on GAIA v2 (docs/sennheiser/PROTOCOL.md section 4.9): the headphones Smart Control opens over the serial port (CX 350BT, HD 250BT, HD 350BT,
 * HD 450BT, HD 450SE, HD 458BT, IE 80S BT). One command at a time, each answered by its response with a status byte; every property is read once and again with the
 * battery; a setting shows once its read answered. The products that only speak over Bluetooth LE (CX 400BT, Momentum True Wireless and 2) are in the table but cannot
 * be opened. The equalizer is the app's three-band one: a parameter group and two knob values.
 */
public class Gaia2Session(public val model: Gaia2Model, private val ble: Boolean = false) : SessionTalk<ByteArray>(), SennheiserEqSource {
    private var current: EarbudState = EarbudState()
    override val state: EarbudState get() = current
    override val pacing: Pacing = Pacing(replyMs = 3_000L, gapMs = 50L)

    private val parser = Gaia2Parser()
    private val asked = mutableSetOf<Int>()
    private val values = mutableMapOf<String, ByteArray>()
    private val knobQueue = ArrayDeque<Int>()
    private val knobs = mutableMapOf<Int, Int>()
    private var readBacks = emptyList<ByteArray>()

    /** The last answer to each property, for the device report. */
    public val replies: Map<String, ByteArray> get() = values

    public var firmware: String? = null
        private set

    private fun prop(id: String): Gaia2Prop? = model.props[id]

    private fun frame(vendor: Int, command: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val p = Gaia2Packet(vendor, command, payload)
        asked += p.key
        return p.bytes()
    }

    private fun read(id: String, payload: ByteArray = ByteArray(0)): ByteArray? = prop(id)?.let { p -> p.get?.let { frame(p.getVendor!!, it, payload) } }

    private val byKey: Map<Int, Gaia2Prop> = model.props.values.filter { it.get != null }.associateBy { Gaia2Packet.keyOf(it.getVendor!!, it.get!!) }

    override fun start(): List<ByteArray> = buildList {
        for (id in READ_ON_OPEN) read(id)?.let { add(it) }
        if (prop(PROP_AUTO_OFF)?.get != null) read(PROP_AUTO_OFF, byteArrayOf(0))?.let { add(it) }
        if (model.eq && prop(PROP_EQ_KNOB)?.get != null) addAll(knobReads())
        if (eq5) {
            read(PROP_EQ_CONTROL)?.let { add(it) }
            for (n in 0..4) read(PROP_EQ5_PARAM, u16(eq5Id(n, 2)))?.let { add(it) }.also { bandQueue.addLast(n) }
        }
    }

    /** The IE 80S BT's equalizer: five bands, each filter set up by the app's own parameter writes (docs/sennheiser/PROTOCOL.md section 4.9). */
    private val eq5: Boolean get() = prop(PROP_EQ5_GROUP)?.set != null && prop(PROP_EQ5_PARAM)?.get != null
    private val bandQueue = ArrayDeque<Int>()
    private val bandDb = DoubleArray(5)
    private var bandsRead = 0
    private var eq5Ready = false

    private fun eq5Id(band: Int, kind: Int): Int = ("1" + (band + 1) + kind).toInt(16)

    private fun knobReads(): List<ByteArray> = (0..1).mapNotNull { index ->
        read(PROP_EQ_KNOB, u16(index))?.also { knobQueue.addLast(index) }
    }

    override fun poll(): List<ByteArray> = listOfNotNull(read(PROP_BATTERY), read(PROP_CHARGING))

    /** Only the commands of the product's own table go out. */
    override fun allowed(message: ByteArray): Boolean {
        val packet = Gaia2Parser().feed(message).singleOrNull() ?: return false
        return model.props.values.any { (it.get != null && it.getVendor == packet.vendor && it.get == packet.command) || (it.set != null && it.setVendor == packet.vendor && it.set == packet.command) }
    }

    /** Over Bluetooth LE a packet is the vendor, the command and the payload with no frame. */
    override fun bytes(message: ByteArray): ByteArray = if (ble) message.copyOfRange(4, message.size) else message

    override fun answers(sent: ByteArray, got: Any): Boolean = got is Int && Gaia2Parser().feed(sent).singleOrNull()?.key == got

    override fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    override fun unanswered(sent: ByteArray): Boolean = false

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val keys = mutableListOf<Any>()
        val framed = if (ble && bytes.size >= 4) byteArrayOf(0xFF.toByte(), 1, 0, (bytes.size - 4).toByte()) + bytes else bytes
        for (packet in parser.feed(framed)) guarded {
            log?.invoke("recv ${packet.vendor.toString(16)}:${packet.command.toString(16)} ${packet.payload.joinToString("") { it.toHex2() }}")
            if (!packet.isResponse) return@guarded
            if (packet.key !in asked) return@guarded
            keys += packet.key
            // The first byte of a response is its status: 0 is success.
            if (packet.payload.isEmpty() || packet.payload[0].toInt() != 0) return@guarded
            val p = byKey[packet.key] ?: return@guarded
            if (decode(p, packet.payload.copyOfRange(1, packet.payload.size))) changed = true
        }
        return Fed(changed, keys)
    }

    private fun decode(p: Gaia2Prop, data: ByteArray): Boolean {
        when (p.id) {
            PROP_EQ_KNOB -> {
                val index = knobQueue.removeFirstOrNull() ?: return false
                if (data.size < 2) return false
                knobs[index] = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                return decodeKnobs()
            }
            PROP_EQ5_PARAM -> {
                // The answer to the read of one band's gain: the parameter id and its value, a difference to the band below (`getEqBandValues`).
                val n = bandQueue.removeFirstOrNull() ?: return false
                if (data.size < 4) return false
                var r = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                if (r > 32767) r -= 65535
                bandDb[n] = if (n > 0) r / 60.0 + bandDb[n - 1] else r / 60.0
                bandsRead++
                if (bandsRead < 5) return false
                gains = bandDb.toList()
                return true
            }
            PROP_AUTO_OFF -> {
                // The timer's number, then its value in seconds.
                if (data.size < 3 || data[0].toInt() != 0) return false
                val seconds = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                val before = autoOffSeconds
                autoOffSeconds = seconds
                return before != seconds
            }
        }
        val before = values[p.id]
        if (before != null && before.contentEquals(data)) return false
        values[p.id] = data.copyOf()
        refresh()
        return true
    }

    private var autoOffSeconds: Int? = null

    private fun u8(id: String): Int? = values[id]?.firstOrNull()?.toInt()?.and(0xFF)

    private fun u16At(id: String): Int? = values[id]?.takeIf { it.size >= 2 }?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) } ?: u8(id)

    private fun refresh() {
        val next = current.values.toMutableMap()
        fun put(c: Command, v: Value?) {
            if (v == null) next.remove(c) else next[c] = v
        }
        values[PROP_BATTERY]?.let { b ->
            val levels = b.map { it.toInt() and 0xFF }.map { it.takeIf { v -> v in 0..100 } }
            put(Command.BATTERY_LEFT, levels.getOrNull(0)?.let { Value.Number(it) })
            put(Command.BATTERY_RIGHT, levels.getOrNull(1)?.let { Value.Number(it) })
        }
        values[PROP_VERSION]?.let { b ->
            if (b.size >= 6) {
                firmware = (0 until 3).map { ((b[it * 2].toInt() and 0xFF) shl 8) or (b[it * 2 + 1].toInt() and 0xFF) }.joinToString(".")
                put(Command.FIRMWARE_VERSION, Value.Text("${model.name}_$firmware"))
            }
        }
        current = current.copy(values = next)
    }

    // The rows ---------------------------------------------------------------------------------------------------------------------------------

    override val groups: List<SettingGroup>
        get() = buildList {
            val sound = buildList<Setting> {
                if (eqKnown) add(Setting.Toggle(SennheiserSession.KEY_BASS_BOOST, eqBass))
                u16At(PROP_AUDIO_MODE)?.let { add(Setting.Toggle(SennheiserSession.KEY_PODCAST, it == AUDIO_SPEECH)) }
            }
            if (sound.isNotEmpty()) add(SettingGroup("sound", SettingTab.SOUND, sound))
            val calls = buildList<Setting> {
                u8(PROP_AUTO_CALL)?.let { add(Setting.Toggle(SennheiserSession.KEY_AUTO_CALL, it != 0)) }
                val max = if (prop(PROP_SIDETONE)?.values.isNullOrEmpty()) 2 else prop(PROP_SIDETONE)!!.values.values.max()
                u8(PROP_SIDETONE)?.let { add(Setting.Level(SennheiserSession.KEY_SIDETONE, it.coerceAtMost(max), 0, max)) }
            }
            if (calls.isNotEmpty()) add(SettingGroup("calls", SettingTab.DEVICE, calls))
            val other = buildList<Setting> {
                u8(PROP_SMART_PAUSE)?.let { add(Setting.Toggle(SennheiserSession.KEY_SMART_PAUSE, it != 0)) }
                u8(PROP_PROMPT_MODE)?.let { add(Setting.Choice(SennheiserSession.KEY_PROMPTS, PROMPTS.entries.firstOrNull { e -> e.value == it }?.key, PROMPTS.keys.toList())) }
                autoOffSeconds?.let { sec -> add(Setting.Choice(SennheiserSession.KEY_AUTO_OFF, (sec / 60).toString(), SennheiserTouchTables.AUTO_OFF_SECONDS.map { (it / 60).toString() })) }
                u8(PROP_MULTI)?.let { v -> prop(PROP_MULTI)?.values?.entries?.firstOrNull { it.value == v }?.let { add(Setting.Choice(KEY_MULTI_FUNCTION, it.key, prop(PROP_MULTI)!!.values.keys.toList())) } }
                u8(PROP_LANGUAGE)?.let { v ->
                    val names = prop(PROP_LANGUAGE)?.values.orEmpty()
                    val name = names.entries.firstOrNull { it.value == v }?.key?.lowercase()?.replaceFirstChar { c -> c.uppercase() }
                    if (prop(PROP_LANGUAGE)?.set != null) add(Setting.Choice(SennheiserSession.KEY_PROMPT_LANGUAGE, name, names.keys.map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }))
                    else if (name != null) add(Setting.Information(SennheiserSession.KEY_PROMPT_LANGUAGE, name))
                }
                u8(PROP_CODEC)?.let { code -> prop(PROP_CODEC)?.values?.entries?.firstOrNull { it.value == code }?.key?.takeIf { it.isNotEmpty() }?.let { add(Setting.Information(SennheiserSession.KEY_CODEC, it)) } }
            }
            if (other.isNotEmpty()) add(SettingGroup("other", SettingTab.DEVICE, other))
        }

    private fun write(id: String, vararg payload: Int, back: ByteArray? = null): List<ByteArray>? {
        val p = prop(id) ?: return null
        val set = p.set ?: return null
        back?.let { readBacks += it } ?: read(id)?.let { readBacks += it }
        return listOf(frame(p.setVendor!!, set, ByteArray(payload.size) { payload[it].toByte() }))
    }

    override fun change(change: SettingChange): List<ByteArray>? {
        when {
            change is SettingChange.Toggle && change.key == SennheiserSession.KEY_BASS_BOOST -> return eqBassBoost(change.on)
            change is SettingChange.Toggle && change.key == SennheiserSession.KEY_PODCAST -> {
                if (u16At(PROP_AUDIO_MODE) == null) return null
                return audioModeFrames(if (change.on) AUDIO_SPEECH else AUDIO_USER_EQ)
            }
            change is SettingChange.Toggle -> {
                val id = TOGGLES[change.key] ?: return null
                if (values[id] == null) return null
                val v = if (change.on) 1 else 0
                values[id] = byteArrayOf(v.toByte())
                return write(id, v)?.also { refresh() }
            }
            change is SettingChange.Level && change.key == SennheiserSession.KEY_SIDETONE -> {
                val max = if (prop(PROP_SIDETONE)?.values.isNullOrEmpty()) 2 else prop(PROP_SIDETONE)!!.values.values.max()
                if (values[PROP_SIDETONE] == null) return null
                val v = change.value.coerceIn(0, max)
                values[PROP_SIDETONE] = byteArrayOf(v.toByte())
                return write(PROP_SIDETONE, v)
            }
            change is SettingChange.Choice && change.key == SennheiserSession.KEY_PROMPTS -> {
                val v = PROMPTS[change.option] ?: return null
                if (values[PROP_PROMPT_MODE] == null) return null
                values[PROP_PROMPT_MODE] = byteArrayOf(v.toByte())
                return write(PROP_PROMPT_MODE, v)
            }
            change is SettingChange.Choice && change.key == KEY_MULTI_FUNCTION -> {
                val v = prop(PROP_MULTI)?.values?.get(change.option) ?: return null
                if (values[PROP_MULTI] == null) return null
                values[PROP_MULTI] = byteArrayOf(v.toByte())
                return write(PROP_MULTI, v)
            }
            change is SettingChange.Choice && change.key == SennheiserSession.KEY_PROMPT_LANGUAGE -> {
                val v = prop(PROP_LANGUAGE)?.values?.entries?.firstOrNull { it.key.equals(change.option, ignoreCase = true) }?.value ?: return null
                if (values[PROP_LANGUAGE] == null) return null
                values[PROP_LANGUAGE] = byteArrayOf(v.toByte())
                return write(PROP_LANGUAGE, v)
            }
            change is SettingChange.Choice && change.key == SennheiserSession.KEY_AUTO_OFF -> {
                val seconds = (change.option.toIntOrNull() ?: return null) * 60
                if (seconds !in SennheiserTouchTables.AUTO_OFF_SECONDS) return null
                val p = prop(PROP_AUTO_OFF) ?: return null
                autoOffSeconds = seconds
                read(PROP_AUTO_OFF, byteArrayOf(0))?.let { readBacks += it }
                return listOf(frame(p.setVendor!!, p.set ?: return null, byteArrayOf(0, (seconds shr 8).toByte(), seconds.toByte())))
            }
        }
        return null
    }

    // The equalizer (the app's three bands: a parameter group and two knobs) ---------------------------------------------------------------------

    private var eqBassFlag = false
    private var gains: List<Double>? = null

    public val eqBass: Boolean get() = eqBassFlag

    override val eqGains: List<Double>? get() = gains
    override val eqKnown: Boolean get() = (model.eq || eq5) && gains != null
    override val eqBandsHz: List<Int> get() = if (eq5) EQ5_HZ else if (model.eq) SennheiserEqTables.THREE_BAND_HZ else emptyList()
    override val eqMaxDb: Double get() = SennheiserEqTables.THREE_BAND_RANGE_DB.toDouble()
    override val eqMinDb: Double get() = -eqMaxDb
    override val eqPresets: List<Pair<String, List<Double>>> get() = if (model.eq && !eq5) SennheiserEqTables.PRESETS["default3"].orEmpty() else emptyList()

    override val eq: Any? get() = if (eqKnown) gains to eqBassFlag else null

    private fun decodeKnobs(): Boolean {
        val (g, bass) = SennheiserEq.threeFromKnobs(knobs[0] ?: return false, knobs[1] ?: return false) ?: return false
        gains = g
        eqBassFlag = bass
        return true
    }

    private fun audioModeFrames(mode: Int): List<ByteArray>? {
        val p = prop(PROP_AUDIO_MODE) ?: return null
        val set = p.set ?: return null
        values[PROP_AUDIO_MODE] = u16(mode)
        read(PROP_AUDIO_MODE)?.let { readBacks += it }
        return listOf(frame(p.setVendor!!, set, u16(mode)))
    }

    /** The messages that set the band [gains] (dB), or null while the earbuds have not said what they hold or the product has no equalizer. */
    private fun eq5Curve(gains: List<Double>): List<ByteArray>? {
        val param = prop(PROP_EQ5_PARAM) ?: return null
        val group = prop(PROP_EQ5_GROUP) ?: return null
        if (gains.size != 5) return null
        val clipped = gains.map { it.coerceIn(eqMinDb, eqMaxDb) }
        val out = mutableListOf<ByteArray>()
        if (!eq5Ready) {
            // The app sets each band's filter type, Q and frequency when it opens the equalizer.
            for (n in 0..4) {
                for ((id, value) in listOf(eq5Id(n, 0) to (if (n < 2) 5 else 11), eq5Id(n, 3) to 2896, eq5Id(n, 1) to 3 * EQ5_FILTER_HZ[n])) {
                    out += frame(param.setVendor ?: return null, param.set ?: return null, u16(id) + u16(value) + byteArrayOf(0))
                }
            }
            eq5Ready = true
        }
        // The equalizer must be on (`Sound_EQControl` 1).
        if (u8(PROP_EQ_CONTROL) != 1) prop(PROP_EQ_CONTROL)?.let { c ->
            values[PROP_EQ_CONTROL] = byteArrayOf(1)
            out += frame(c.setVendor ?: return null, c.set ?: return null, byteArrayOf(1))
        }
        val values5 = eq5Params(clipped)
        out += frame(group.setVendor ?: return null, group.set ?: return null, u16(values5.size / 2) + shortList(values5) + u16(0))
        prop(PROP_EQ5_STORE)?.let { st -> out += frame(st.setVendor ?: return null, st.set ?: return null) }
        this.gains = clipped
        return out
    }

    /** `Eq5BandIE80.calculateEqParams`: the pre-gain (loudness weighted, kept under the clipping threshold) and each band's step above the one below, in sixtieths of a dB. */
    private fun eq5Params(g: List<Double>): List<Int> {
        val weights = listOf(-28.3, -10.8, -2.9, -4.1, -18.1)
        val t = -20 * PMath.log10(PMath.sqrt((0..4).sumOf { PMath.pow(10.0, (weights[it] + g[it]) / 10) }))
        val n = maxOf(g[0], g[1], g[2])
        val i = EQ5_PRE_GAIN + t
        val r = i + n
        val o = if (r > 1) i + 1 - r else i
        fun toInteger(x: Double): Int = (if (x < 0) PMath.ceil(x) else PMath.floor(x)).toInt()
        val actual = listOf(g[0], g[1] - g[0], g[2] - g[1], g[3] - g[2], g[4] - g[3])
        return listOf(257, toInteger(60 * o)) + (0..4).flatMap { listOf(eq5Id(it, 2), toInteger(60 * actual[it]) and 0xFFFF) }
    }

    override fun eqCurve(gains: List<Double>): List<ByteArray>? {
        if (eq5) return eq5Curve(gains)
        if (!model.eq || gains.size != 3) return null
        val params = prop(PROP_EQ_PARAMS) ?: return null
        val knob = prop(PROP_EQ_KNOB) ?: return null
        val clipped = gains.map { it.coerceIn(eqMinDb, eqMaxDb) }
        val out = mutableListOf<ByteArray>()
        // The equalizer only sounds in the user equalizer mode; the app turns it on with the equalizer.
        if (u16At(PROP_AUDIO_MODE) != null && u16At(PROP_AUDIO_MODE) != AUDIO_USER_EQ) audioModeFrames(AUDIO_USER_EQ)?.let { out += it }
        val t = clipped.toMutableList()
        if (eqBassFlag) t[0] += SennheiserEqTables.THREE_BAND_BASS_BOOST
        val smooth = SennheiserEq.threeSmooth(t, THREE_BAND_LIMIT)
        out += frame(params.setVendor ?: return null, params.set ?: return null, shortList(SennheiserEq.threeParams(smooth, eqBassFlag)))
        val (x, y) = SennheiserEq.threeKnobs(clipped, eqBassFlag)
        out += frame(knob.setVendor ?: return null, knob.set ?: return null, u16(0) + u16(x))
        out += frame(knob.setVendor!!, knob.set!!, u16(1) + u16(y))
        this.gains = clipped
        return out
    }

    private fun eqBassBoost(on: Boolean): List<ByteArray>? {
        val g = gains ?: return null
        eqBassFlag = on
        return eqCurve(g)
    }

    private fun u16(v: Int): ByteArray = byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun shortList(list: List<Int>): ByteArray = list.flatMap { listOf((it shr 8).toByte(), it.toByte()) }.toByteArray()

    public companion object {
        public const val KEY_MULTI_FUNCTION: String = "sh_multi_function"
        private const val PROP_BATTERY = "battery_percent"
        private const val PROP_CHARGING = "battery_charging"
        private const val PROP_VERSION = "firmware_version"
        private const val PROP_AUDIO_MODE = "audio_mode"
        private const val PROP_SIDETONE = "sidetone"
        private const val PROP_PROMPT_MODE = "prompt_mode"
        private const val PROP_AUTO_CALL = "auto_call"
        private const val PROP_SMART_PAUSE = "smart_pause"
        private const val PROP_CODEC = "codec"
        private const val PROP_AUTO_OFF = "auto_off"
        private const val PROP_MULTI = "multi_function"
        private const val PROP_LANGUAGE = "prompt_language"
        private const val PROP_EQ_KNOB = "eq_knob"
        private const val PROP_EQ_PARAMS = "eq_params"
        private const val PROP_EQ5_PARAM = "eq5_param"
        private const val PROP_EQ5_GROUP = "eq5_group"
        private const val PROP_EQ5_STORE = "eq5_store"
        private const val PROP_EQ_CONTROL = "eq_control"

        /** The IE 80S BT's bands as Smart Control labels them (62.5, 250, 1000, 4000, 16000 Hz) and the filter frequencies it sets (32, 125, 500, 2000, 8000). */
        private val EQ5_HZ = listOf(63, 250, 1000, 4000, 16000)
        private val EQ5_FILTER_HZ = listOf(32, 125, 500, 2000, 8000)

        /** The pre-gain of the firmware from 0.9.0 (`preGainNeutral`); the older firmware uses -2, which the product files do not let PairFree tell apart. */
        private const val EQ5_PRE_GAIN = -6.0
        private const val AUDIO_USER_EQ = 1
        private const val AUDIO_SPEECH = 2

        /** The gain limit of the app's three-band equalizer for these products (`get3BandParametersForDevice`'s default). */
        private const val THREE_BAND_LIMIT = 4

        private val READ_ON_OPEN = listOf(PROP_BATTERY, PROP_CHARGING, PROP_VERSION, PROP_AUDIO_MODE, PROP_SIDETONE, PROP_PROMPT_MODE, PROP_AUTO_CALL, PROP_SMART_PAUSE, PROP_CODEC, PROP_MULTI, PROP_LANGUAGE)
        private val PROMPTS = linkedMapOf("tone_voice_off" to 0, "tone_on" to 1, "tone_voice_on" to 2)
        private val TOGGLES = mapOf(SennheiserSession.KEY_AUTO_CALL to PROP_AUTO_CALL, SennheiserSession.KEY_SMART_PAUSE to PROP_SMART_PAUSE)
    }
}
