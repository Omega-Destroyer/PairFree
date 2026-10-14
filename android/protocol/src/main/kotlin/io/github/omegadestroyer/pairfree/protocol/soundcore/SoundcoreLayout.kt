package io.github.omegadestroyer.pairfree.protocol.soundcore

/** One field of a state reply: its name in data/soundcore/devices.json, its size, and whether only some firmware sends it. */
public data class SoundcoreField(val name: String, val size: Int, val optional: Boolean = false)

/** A battery level's scale: percent = (value - offset) * 100 / max. */
public data class BatteryScale(val max: Int, val offset: Int = 0) {
    public fun percent(value: Int): Int? = (value - offset).takeIf { it in 0..max }?.let { it * 100 / max }
}

/**
 * How one model lays out its reply to [SoundcoreCommand.STATE], and its quirks, from
 * data/soundcore/devices.json. The layouts are read from OpenSCQ30's parsers
 * (github.com/Oppzippy/OpenSCQ30, GPL-3.0-or-later) and checked against its test replies.
 */
public class SoundcoreLayout(
    public val model: String,
    public val fields: List<SoundcoreField>,
    public val battery: BatteryScale?,
    public val case: BatteryScale?,
    /** False for models whose packets carry no checksum. */
    public val checksum: Boolean = true,
    /** The RFCOMM service to open, when the model needs a fixed one. */
    public val serviceUuid: String? = null,
    public val gamingModeCommand: Int = SoundcoreCommand.GAMING_MODE,
    /** Fields the earbuds send but only use from a firmware version on, such as `01.60`; both earbuds must have it. */
    public val minFirmware: Map<String, String> = emptyMap(),
    /** What OpenSCQ30 offers on the model (its module names, such as `touch_tone`), and what PairFree confirmed on it. */
    public val features: Set<String> = emptySet(),
) {
    /** The bytes every reply has; optional fields come after them. */
    public val minSize: Int = fields.filterNot { it.optional }.sumOf { it.size }

    private val offsets: Map<String, Pair<Int, SoundcoreField>> = buildMap {
        var at = 0
        for (f in fields) {
            if (f.name != SKIP && f.name !in this) put(f.name, at to f)
            at += f.size
        }
    }

    /** Where a field starts and its size, or null when the model does not send it. */
    public fun locate(name: String): Pair<Int, Int>? = offsets[name]?.let { (at, f) -> at to f.size }

    public fun has(name: String): Boolean = name in offsets

    public companion object {
        public const val SKIP: String = "skip"

        public val ALL: List<SoundcoreLayout> get() = SOUNDCORE_LAYOUTS

        public fun of(model: String?): SoundcoreLayout? = SOUNDCORE_LAYOUTS.firstOrNull { it.model == model }
    }
}

/** A state reply read with its model's [SoundcoreLayout]. Fields the model or its firmware does not send read as null. */
public class SoundcoreState private constructor(public val layout: SoundcoreLayout, private val body: ByteArray) {
    /** The reply's length in bytes. */
    public val size: Int get() = body.size

    /** A field's bytes as the layout places them, or null when the model or this reply does not send it. */
    public fun field(name: String): ByteArray? = bytes(name)

    /** This reply with field [name] replaced by [value], for showing a change before the earbuds report it. */
    public fun with(name: String, value: ByteArray): SoundcoreState {
        val (at, size) = layout.locate(name) ?: return this
        if (value.size != size || at + size > body.size) return this
        return SoundcoreState(layout, body.copyOf().also { value.copyInto(it, at) })
    }

    /** A field's first byte, or null when the model or this reply does not send it. */
    public fun byte(name: String): Int? = u8(name)

    private fun bytes(name: String): ByteArray? =
        layout.locate(name)?.let { (at, size) -> if (at + size <= body.size) body.copyOfRange(at, at + size) else null }

    private fun u8(name: String, index: Int = 0): Int? = bytes(name)?.getOrNull(index)?.let { it.toInt() and 0xFF }

    /** A one-byte switch, such as gaming_mode or ldac; null when the model, or its firmware, does not have it. */
    public fun flag(name: String): Boolean? {
        layout.minFirmware[name]?.let { min -> if (firmwares.isEmpty() || firmwares.any { it < min }) return null }
        return u8(name)?.let { it != 0 }
    }

    public val batteryLeft: Int? get() = earbud(0)
    public val batteryRight: Int? get() = earbud(1)

    private fun earbud(side: Int): Int? {
        val scale = layout.battery ?: return null
        val raw = when {
            layout.has("dual_battery") -> u8("dual_battery", side)
            layout.has("dual_battery_level") -> u8("dual_battery_level", side)
            // One battery (headphones): shown as the left one.
            side == 1 -> null
            layout.has("single_battery") -> u8("single_battery", 0)
            layout.has("single_battery_charging_first") -> u8("single_battery_charging_first", 1)
            layout.has("battery_level") -> u8("battery_level", 0)
            else -> null
        }
        return raw?.let(scale::percent)
    }

    public val batteryCase: Int? get() = layout.case?.let { scale -> u8("case_battery")?.let(scale::percent) }

    /** The firmware version, such as `06.43`: the left earbud's when the model reports both. */
    public val firmware: String? get() = firmwares.firstOrNull()

    /** Each firmware version the reply holds, left earbud first; one that is not `NN.NN` (an earbud away) is left out. */
    public val firmwares: List<String>
        get() {
            bytes("firmware")?.decodeToString()?.takeIf(::isVersion)?.let { return listOf(it) }
            val dual = bytes("dual_firmware") ?: return emptyList()
            return listOf(dual.copyOfRange(0, 5), dual.copyOfRange(5, 10)).map { it.decodeToString() }.filter(::isVersion)
        }

    /** The noise mode block, as [SoundcoreCommand.SET_SOUND_MODES] takes it; its first byte is the [AmbientMode]. */
    public val soundModes: ByteArray? get() = bytes("sound_modes")

    public val ambientMode: AmbientMode? get() = u8("sound_modes")?.let(AmbientMode::of)

    /** The reply as hex, with the serial numbers' bytes shown as XX: they can carry the earbuds' Bluetooth address. */
    public fun reportHex(): String {
        val hidden = listOf("serial", "case_serial").mapNotNull(layout::locate).map { (at, size) -> at until at + size }
        return body.indices.joinToString(" ") { i ->
            if (hidden.any { i in it }) "XX" else (body[i].toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    public companion object {
        public fun parse(layout: SoundcoreLayout, body: ByteArray): SoundcoreState? =
            if (body.size >= layout.minSize) SoundcoreState(layout, body.copyOf()) else null

        private fun isVersion(s: String): Boolean = s.length == 5 && s[2] == '.' && s.removeRange(2, 3).all { it in '0'..'9' }
    }
}
