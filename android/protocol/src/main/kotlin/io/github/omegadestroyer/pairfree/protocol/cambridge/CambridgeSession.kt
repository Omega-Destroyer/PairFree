package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * Melomania 1+/Touch's ordinary settings. No upgrade, authentication, account, reset or power commands.
 * The composite reply contains addresses: [replies] redacts them and no raw composite is retained.
 * EQ frequencies are not established; five numbered gain rows avoid inventing a frequency response.
 */
public class CambridgeSession(public val model: CambridgeModel) {
    public var state: EarbudState = EarbudState()
        private set
    public var firmware: String? = null
        private set
    public var replies: Map<String, ByteArray> = emptyMap()
        private set
    private val parser = CambridgeParser()
    private val requested = mutableSetOf<Int>()
    private var readBacks = emptyList<ByteArray>()
    private var ambient: Boolean? = null
    private var ambientLevel: Int? = null
    private var language: Int? = null
    private var codec: Int? = null
    private var activeCodec: Int? = null
    private var controls: Int? = null
    private var preset: Int? = null
    private var curve: List<Int>? = null

    public fun start(): List<ByteArray> = listOf(CambridgeCommand.STATE, CambridgeCommand.PRESET, CambridgeCommand.CONTROLS).map(::read)
    public fun batteryRequests(): List<ByteArray> = listOf(read(CambridgeCommand.STATE))
    public fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    private fun read(id: Int): ByteArray {
        requested += id
        return CambridgePacket(id).bytes()
    }

    /** ACK ids, including failures, so a failed request releases the transport queue. */
    public fun feed(bytes: ByteArray): List<Int> {
        val answered = mutableListOf<Int>()
        for (packet in parser.feed(bytes)) {
            if (!packet.ack || packet.payload.isEmpty()) continue
            val id = packet.id
            answered += id
            val p = packet.payload
            if (p[0].u() != 0) {
                if (id == CambridgeCommand.SET_PRESET || id == CambridgeCommand.SET_BAND) curve = null
                continue
            }
            if (id !in requested) continue
            when (id) {
                CambridgeCommand.STATE -> composite(p)
                CambridgeCommand.PRESET -> if (p.size >= 2) {
                    preset = p[1].u()
                    curve = CambridgeValues.PRESETS.getOrNull(preset!!)?.second
                        ?: curve.takeIf { preset == CambridgeValue.CUSTOM_PRESET }
                }
                CambridgeCommand.CONTROLS -> if (p.size >= 3) controls = p[1].u() shl 8 or p[2].u()
                else -> continue
            }
            // Keep only these three explicitly requested reads; discard the addresses in the composite.
            val safe = p.copyOf()
            if (id == CambridgeCommand.STATE) {
                for (i in CambridgeOffset.ADDRESS_START until minOf(safe.size, CambridgeOffset.ADDRESS_END)) safe[i] = 0
                // Future extensions can contain identifiers too. Retain only the established schema.
                replies = replies + (id.toString(16) to safe.copyOf(minOf(safe.size, CambridgeOffset.CODEC + 1)))
            } else replies = replies + (id.toString(16) to safe.copyOf(minOf(safe.size, if (id == CambridgeCommand.PRESET) 2 else 3)))
        }
        return answered
    }

    private fun composite(p: ByteArray) {
        fun at(offset: Int): Int? = p.getOrNull(offset)?.u()
        fun percent(offset: Int): Value.Number? = at(offset)?.takeIf { it in 0..100 }?.let(Value::Number)
        val values = state.values.toMutableMap()
        for ((command, offset) in listOf(Command.BATTERY_LEFT to CambridgeOffset.LEFT, Command.BATTERY_RIGHT to CambridgeOffset.RIGHT)) {
            val value = percent(offset)
            if (value == null) values.remove(command) else values[command] = value
        }
        state = state.copy(values = values)
        ambient = at(CambridgeOffset.AMBIENT)?.takeIf { it in 0..CambridgeValue.AMBIENT_OFF }?.let { it == CambridgeValue.AMBIENT_ON }
        ambientLevel = at(CambridgeOffset.AMBIENT_LEVEL)?.takeIf { it in CambridgeValue.AMBIENT_MIN..CambridgeValue.AMBIENT_MAX }
        language = at(CambridgeOffset.LANGUAGE)?.takeIf { it in CambridgeValues.LANGUAGES.indices }
        codec = at(CambridgeOffset.CODEC)?.takeIf { it in CambridgeValues.CODECS.indices }
        activeCodec = at(CambridgeOffset.ACTIVE_CODEC)
        fun version(offset: Int): String? = if (p.size >= offset + 3) p.slice(offset until offset + 3).joinToString(".") { it.u().toString() } else null
        firmware = listOfNotNull(version(CambridgeOffset.LEFT_FIRMWARE)?.let { "L $it" }, version(CambridgeOffset.RIGHT_FIRMWARE)?.let { "R $it" }).joinToString(" / ").ifEmpty { null }
        state = state.copy(values = state.values - Command.FIRMWARE_VERSION +
            (firmware?.let { mapOf(Command.FIRMWARE_VERSION to Value.Text("${model.name}_$it")) } ?: emptyMap()))
    }

    public val groups: List<SettingGroup> get() = buildList {
        if (model.ambient) {
            val rows = buildList {
                ambient?.let { add(Setting.Toggle("ca_ambient", it)) }
                ambientLevel?.takeIf { ambient == true }?.let { add(Setting.Level("ca_ambient_level", it, CambridgeValue.AMBIENT_MIN, CambridgeValue.AMBIENT_MAX)) }
            }
            if (rows.isNotEmpty()) add(SettingGroup("noise", SettingTab.HOME, rows))
        }
        preset?.let { current ->
            val rows = mutableListOf<Setting>(Setting.Choice("ca_preset", CambridgeValues.PRESETS.getOrNull(current)?.first,
                CambridgeValues.PRESETS.map { it.first }))
            curve?.forEachIndexed { index, gain -> rows += Setting.Level("ca_band_${index + CambridgeValue.FIRST_BAND}", gain, CambridgeValue.GAIN_MIN, CambridgeValue.GAIN_MAX) }
            add(SettingGroup("ca_eq", SettingTab.SOUND, rows))
        }
        controls?.let { mask ->
            add(SettingGroup("touch_buttons", SettingTab.CONTROLS, CambridgeValues.CONTROLS.take(model.controlCount).mapIndexed { index, key ->
                Setting.Toggle("ca_control_$key", mask and (1 shl index) != 0)
            }))
        }
        val both = (state.batteryLeft ?: 0) > 0 && (state.batteryRight ?: 0) > 0
        val device = buildList {
            if (both) language?.let { add(Setting.Choice("ca_language", CambridgeValues.LANGUAGES[it], CambridgeValues.LANGUAGES)) }
            if (both && activeCodec != null && activeCodec != CambridgeValue.CODEC_LOCKED) codec?.let {
                add(Setting.Choice("ca_codec", CambridgeValues.CODECS[it], CambridgeValues.CODECS))
            }
        }
        if (device.isNotEmpty()) add(SettingGroup("ca_connection", SettingTab.DEVICE, device))
    }

    /** Only a row currently offered by this model and its last reply may be changed. Confirm by reading back. */
    public fun change(change: SettingChange): List<ByteArray>? {
        val row = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        val out: List<ByteArray>
        val reads: List<Int>
        when {
            row is Setting.Toggle && change is SettingChange.Toggle -> {
                if (change.key == "ca_ambient") {
                    out = listOf(write(CambridgeCommand.SET_AMBIENT, if (change.on) CambridgeValue.AMBIENT_ON else CambridgeValue.AMBIENT_OFF))
                    reads = listOf(CambridgeCommand.STATE)
                } else {
                    val index = CambridgeValues.CONTROLS.indexOf(change.key.removePrefix("ca_control_"))
                    if (index !in 0 until model.controlCount) return null
                    val bit = 1 shl index
                    val mask = controls ?: return null
                    val next = if (change.on) mask or bit else mask and bit.inv()
                    controls = next // preserve rapid changes to other bits until the read-back
                    out = listOf(write(CambridgeCommand.SET_CONTROLS, next shr 8, next and 0xff))
                    reads = listOf(CambridgeCommand.CONTROLS)
                }
            }
            row is Setting.Choice && change is SettingChange.Choice && change.option in row.options -> {
                val index = row.options.indexOf(change.option)
                val command = when (change.key) {
                    "ca_preset" -> CambridgeCommand.SET_PRESET
                    "ca_language" -> CambridgeCommand.SET_LANGUAGE
                    "ca_codec" -> CambridgeCommand.SET_CODEC
                    else -> return null
                }
                out = listOf(write(command, index))
                reads = listOf(if (change.key == "ca_preset") CambridgeCommand.PRESET else CambridgeCommand.STATE)
            }
            row is Setting.Level && change is SettingChange.Level && change.value in row.min..row.max -> {
                if (change.key == "ca_ambient_level") {
                    out = listOf(write(CambridgeCommand.SET_AMBIENT_LEVEL, change.value))
                    reads = listOf(CambridgeCommand.STATE)
                } else {
                    val band = change.key.removePrefix("ca_band_").toIntOrNull() ?: return null
                    val gains = curve?.toMutableList() ?: return null
                    gains[band - CambridgeValue.FIRST_BAND] = change.value
                    curve = gains
                    // u3/b selects Custom1 before t3/b writes bands. All five are explicit: no unknown custom bank assumed.
                    out = listOf(write(CambridgeCommand.SET_PRESET, CambridgeValue.CUSTOM_PRESET)) + gains.mapIndexed { i, gain ->
                        val raw = gain * CambridgeValue.GAIN_SCALE
                        write(CambridgeCommand.SET_BAND, CambridgeValue.BAND_HEADER,
                            ((i + CambridgeValue.FIRST_BAND) shl 4) or CambridgeValue.BAND_PARAMETER,
                            (raw shr 8) and 0xff, raw and 0xff, CambridgeValue.BAND_APPLY)
                    }
                    reads = listOf(CambridgeCommand.PRESET)
                }
            }
            else -> return null
        }
        readBacks = reads.map(::read)
        return out
    }

    private fun write(id: Int, vararg payload: Int): ByteArray = CambridgePacket(id, payload.map(Int::toByte).toByteArray()).bytes()

    /** Strict outbound allowlist and payload validation; firmware and GAIA's generic command space never pass. */
    public fun allowed(bytes: ByteArray): Boolean {
        val packet = CambridgeParser().feed(bytes).singleOrNull() ?: return false
        if (!packet.bytes().contentEquals(bytes) || packet.ack) return false
        val p = packet.payload.map { it.u() }
        return when (packet.id) {
            CambridgeCommand.STATE, CambridgeCommand.PRESET, CambridgeCommand.CONTROLS -> p.isEmpty()
            CambridgeCommand.SET_PRESET -> p.size == 1 && (p[0] in CambridgeValues.PRESETS.indices || p[0] == CambridgeValue.CUSTOM_PRESET)
            CambridgeCommand.SET_CONTROLS -> p.size == 2
            CambridgeCommand.SET_AMBIENT -> model.ambient && p.size == 1 && p[0] in listOf(CambridgeValue.AMBIENT_ON, CambridgeValue.AMBIENT_OFF)
            CambridgeCommand.SET_AMBIENT_LEVEL -> model.ambient && p.size == 1 && p[0] in CambridgeValue.AMBIENT_MIN..CambridgeValue.AMBIENT_MAX
            CambridgeCommand.SET_LANGUAGE -> p.size == 1 && p[0] in CambridgeValues.LANGUAGES.indices
            CambridgeCommand.SET_CODEC -> p.size == 1 && p[0] in CambridgeValues.CODECS.indices
            CambridgeCommand.SET_BAND -> p.size == 5 && p[0] == CambridgeValue.BAND_HEADER && p[1] and 15 == CambridgeValue.BAND_PARAMETER &&
                p[1] shr 4 in CambridgeValue.FIRST_BAND..CambridgeValue.LAST_BAND && p[4] == CambridgeValue.BAND_APPLY &&
                ((p[2] shl 8 or p[3]).toShort().toInt()) in CambridgeValue.GAIN_MIN * CambridgeValue.GAIN_SCALE..CambridgeValue.GAIN_MAX * CambridgeValue.GAIN_SCALE
            else -> false
        }
    }
}
