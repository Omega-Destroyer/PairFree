package io.github.omegadestroyer.pairfree.protocol.oneplus

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to OnePlus or OPPO earbuds over HeyMelody's protocol (docs/oneplus/PROTOCOL.md). Source analysis of HeyMelody 117.6.6
 * and a virtual run of that app only: no earbuds have answered PairFree. A setting shows once its read has been answered, and a read is
 * sent only for a feature the model's catalog entry lists. Reads are `0100` capabilities, `0106` battery, `010C` current noise mode,
 * `010F` equalizer preset and `010D` the game mode switch; writes are `0404` (noise mode as a bitmap of the mode's protocol index),
 * `0406` (preset index) and `0403` (a switch's feature id and 0 or 1).
 */
public class OneplusSession(public val model: OneplusModel?) {
    public var state: EarbudState = EarbudState()
        private set

    private val parser = OneplusParser()
    private var sequence = -1
    private var noiseIndex: Int? = null
    private var noiseType = BITMAP_TYPE
    private var presetIndex: Int? = null
    private var gameMode: Boolean? = null

    /** The product id the earbuds reported, as hex, for the device report. */
    public var productId: String? = null
        private set

    /** The capability bits the earbuds listed, for the device report. */
    public var capabilities: Set<Int>? = null
        private set

    private fun next(command: Int, vararg payload: Int): ByteArray {
        sequence = (sequence + 1) % SEQUENCE_WRAP
        return OneplusPacket(command, sequence, ByteArray(payload.size) { payload[it].toByte() }).bytes()
    }

    /** The reads that open the conversation, in order. The vendor id is FFFF as HeyMelody sends it on every profile. */
    public fun start(): List<ByteArray> = buildList {
        add(next(OneplusCommand.CAPABILITIES))
        add(next(OneplusCommand.VENDOR, 0xFF, 0xFF))
        add(next(OneplusCommand.BATTERY))
        add(next(OneplusCommand.PRODUCT))
        add(next(OneplusCommand.MTU, 0x00, 0x02))
        if (model?.noise?.isNotEmpty() == true) add(next(OneplusCommand.TYPED_NOISE, 1, 1))
        if (model?.presets?.isNotEmpty() == true) add(next(OneplusCommand.EQ_PRESET))
        if (model?.gameMode == true) add(next(OneplusCommand.SWITCHES, 1, GAME_MODE_ID))
    }

    /** Applies bytes from the earbuds (any number of frames or part of one). True when [state] or [groups] changed. */
    public fun receive(bytes: ByteArray): Boolean {
        val before = state to groups
        for (packet in parser.feed(bytes)) apply(packet)
        refresh()
        return (state to groups) != before
    }

    private fun apply(p: OneplusPacket) {
        if (!p.response) return
        val d = p.payload
        if (d.firstOrNull()?.toInt() != 0) return
        when (p.id) {
            OneplusCommand.CAPABILITIES -> capabilities = OneplusReads.capabilityBits(p)
            OneplusCommand.BATTERY -> OneplusBattery.of(p)?.let(::battery)
            OneplusCommand.PRODUCT -> if (d.size >= PRODUCT_SIZE) productId = "%02X%02X%02X".format(d[3].toInt() and 0xFF, d[2].toInt() and 0xFF, d[1].toInt() and 0xFF)
            OneplusCommand.TYPED_NOISE -> noise(d)
            OneplusCommand.EQ_PRESET -> if (d.size >= 2) presetIndex = d[1].toInt() and 0xFF
            OneplusCommand.SWITCHES -> switches(d)
        }
    }

    /** Device types 1, 2 and 3 are left, right and the case, as HeyMelody's screen showed them in the virtual run. */
    private fun battery(list: List<OneplusBattery>) {
        val values = mutableMapOf<Command, Value>()
        for (b in list) when (b.type) {
            1 -> values[Command.BATTERY_LEFT] = Value.Number(b.percent)
            2 -> values[Command.BATTERY_RIGHT] = Value.Number(b.percent)
            3 -> values[Command.BATTERY_CASE] = Value.Number(b.percent)
        }
        state = state.copy(values = state.values + values)
    }

    /** `status, 01, type, bitmap...`: the first set bit is the current mode's protocol index (HeyMelody's CurrentNoiseModeInfo). */
    private fun noise(d: ByteArray) {
        if (d.size < NOISE_MIN || d[1].toInt() != 1) return
        val type = d[2].toInt() and 0xFF
        if (type != BITMAP_TYPE && type != WIDE_BITMAP_TYPE) return
        noiseType = type
        val bits = d.copyOfRange(3, d.size)
        val index = (0 until bits.size * 8).firstOrNull { bits[it / 8].toInt() and (1 shl (it % 8)) != 0 }
        noiseIndex = index
    }

    /** `status, count, (feature id, state)...`. */
    private fun switches(d: ByteArray) {
        if (d.size < 2) return
        val count = d[1].toInt() and 0xFF
        if (d.size < 2 + count * 2) return
        for (i in 0 until count) if ((d[2 + i * 2].toInt() and 0xFF) == GAME_MODE_ID) gameMode = d[3 + i * 2].toInt() != 0
    }

    private fun refresh() {
        val index = noiseIndex ?: return
        val type = model?.noise?.firstOrNull { it.index == index }?.type ?: model?.noise?.flatMap { it.children + it }?.firstOrNull { it.index == index }?.type
        val mode = when (type) {
            "off" -> "off"
            "noise_cancelling", "light", "strong", "moderate", "smart" -> "anc"
            "transparency", "voice_focus" -> "transparency"
            else -> null
        } ?: return
        state = state.copy(values = state.values + (Command.ANC_MODE to Value.Number(ancNumber(mode))))
    }

    /** The preset and the game mode as brand rows. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            presetIndex?.let { i ->
                val presets = model?.presets.orEmpty()
                SettingGroup("oneplus_sound", SettingTab.SOUND, listOf(Setting.Choice("op_eq_preset", presets.firstOrNull { it.index == i }?.name, presets.map { it.name })))
            },
            gameMode?.let { SettingGroup("oneplus_other", SettingTab.DEVICE, listOf(Setting.Toggle("game_mode", it))) },
        )

    /** The commands PairFree sends: reads above and these writes. Nothing else, in particular none of the `0Fxx` debug commands. */
    public fun allowed(frame: ByteArray): Boolean {
        val packet = OneplusParser().feed(frame).singleOrNull() ?: return false
        return packet.command in ALLOWED
    }

    /** The noise mode from the shared screens: off, noise cancelling or transparency, as the bit of the model's mode of that kind. */
    public fun set(command: Command, value: Int): List<ByteArray>? {
        if (command != Command.ANC_MODE || noiseIndex == null) return null
        val kind = when (value) {
            ancNumber("off") -> "off"
            ancNumber("anc") -> "noise_cancelling"
            ancNumber("transparency") -> "transparency"
            else -> return null
        }
        val mode = model?.noise?.firstOrNull { it.type == kind } ?: return null
        noiseIndex = mode.index
        refresh()
        val width = mode.index / 8 + 1
        val bitmap = IntArray(width).also { it[mode.index / 8] = 1 shl (mode.index % 8) }
        return listOf(next(OneplusCommand.SET_NOISE, 1, noiseType, *bitmap))
    }

    public fun change(change: SettingChange): List<ByteArray>? {
        when {
            change is SettingChange.Choice && change.key == "op_eq_preset" && presetIndex != null -> {
                val preset = model?.presets?.firstOrNull { it.name == change.option } ?: return null
                presetIndex = preset.index
                return listOf(next(OneplusCommand.SET_EQ_PRESET, preset.index))
            }
            change is SettingChange.Toggle && change.key == "game_mode" && gameMode != null -> {
                gameMode = change.on
                return listOf(next(OneplusCommand.SET_SWITCH, GAME_MODE_ID, if (change.on) 1 else 0))
            }
        }
        return null
    }

    private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    public companion object {
        /** Feature id of the game mode switch, as the virtual run wrote it (`0403 06 01`). */
        public const val GAME_MODE_ID: Int = 6
        private const val SEQUENCE_WRAP = 256
        private const val BITMAP_TYPE = 1
        private const val WIDE_BITMAP_TYPE = 5
        private const val NOISE_MIN = 4
        private const val PRODUCT_SIZE = 4

        public val ALLOWED: Set<Int> = setOf(
            OneplusCommand.CAPABILITIES, OneplusCommand.VENDOR, OneplusCommand.BATTERY, OneplusCommand.PRODUCT, OneplusCommand.MTU,
            OneplusCommand.TYPED_NOISE, OneplusCommand.EQ_PRESET, OneplusCommand.SWITCHES,
            OneplusCommand.SET_NOISE, OneplusCommand.SET_EQ_PRESET, OneplusCommand.SET_SWITCH,
        )
    }
}
