package io.github.omegadestroyer.pairfree.protocol

/** A noise-cancelling scene or transparency level the earbuds offer, and whether it is the current one. */
public data class Level(val code: Int, val active: Boolean)

/** A touch gesture and the action it triggers. See [CustomKeys]. */
public data class KeyBinding(val gesture: Int, val action: Int)

/** Firmware version text such as `S5_20250605_V0.4.9`: firmware code, build date, version. */
public data class FirmwareVersion(val text: String) {
    /** The first field. It identifies the model; see data/soundpeats/devices.json. */
    public val code: String get() = text.substringBefore('_')

    /** The last field, without a leading `V`. */
    public val version: String get() = text.substringAfterLast('_').removePrefix("V")
}

/**
 * A battery reading: [level] of [levels], or a null level while it is unknown (an earbud away).
 * Most earbuds report a percentage ([PERCENT] levels); some report a few steps, such as fifths,
 * and the screens show those as bars, one per step, as the brand's own app does.
 */
public data class BatteryLevel(val level: Int?, val levels: Int = PERCENT) {
    public val isPercent: Boolean get() = levels == PERCENT

    /** The level as a percentage, for anything that needs one. */
    public val percent: Int? get() = level?.let { it * 100 / levels }

    public companion object {
        public const val PERCENT: Int = 100
    }
}

/** What a reply says, decoded according to its command's [Kind]. */
public sealed interface Value {
    /** Percent, enum or minutes. Null when the earbuds report `FF` (unknown). */
    public data class Number(val value: Int?) : Value

    public data class Switch(val on: Boolean) : Value

    public data class Levels(val levels: List<Level>) : Value

    public data class Keys(val keys: List<KeyBinding>) : Value

    public data class Text(val text: String) : Value

    /** A level in whole steps, such as a battery in fifths: [value] of [steps]. Null while unknown. */
    public data class Steps(val value: Int?, val steps: Int) : Value

    public class Raw(public val bytes: ByteArray) : Value {
        override fun equals(other: Any?): Boolean = other is Raw && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = "Raw(${bytes.hex()})"
    }
}

/** Decodes reply payloads (docs/soundpeats/PROTOCOL.md sections 2 and 3). */
public object Replies {
    private const val UNKNOWN = 0xFF

    private val BY_GET: Map<Int, Command> = Command.entries.mapNotNull { c -> c.get?.let { it to c } }.toMap()

    /** The command whose GET [frame] answers, or null when it is not such a reply. */
    public fun commandFor(frame: Frame): Command? =
        if (frame.vendor == Wire.VENDOR_MAIN && frame.isReply) BY_GET[frame.answers] else null

    /**
     * Decodes [payload] as the reply to [command]'s GET. Returns null when the reply
     * carries an error status or is too short; the official app ignores those too.
     */
    public fun decode(command: Command, payload: ByteArray): Value? {
        val u = IntArray(payload.size) { payload[it].toInt() and 0xFF }
        return when (command.kind) {
            Kind.TEXT -> if (u.isNotEmpty() && u[0] == 0) Value.Text(payload.copyOfRange(1, payload.size).decodeToString()) else null
            Kind.PERCENT -> single(u)?.let { Value.Number(if (it == UNKNOWN || it > 100) null else it) }
            Kind.ENUM, Kind.MINUTES -> single(u)?.let { Value.Number(if (it == UNKNOWN) null else it) }
            Kind.SWITCH -> single(u)?.let { Value.Switch(it == 1) }
            Kind.LEVEL_LIST -> pairs(u)?.let { p -> Value.Levels(p.map { Level(it.first, it.second == 1) }) }
            Kind.KEY_MAP -> pairs(u)?.let { p -> Value.Keys(p.map { KeyBinding(it.first, it.second) }) }
            Kind.RAW -> Value.Raw(payload.copyOf())
            Kind.ACTION -> null
        }
    }

    /** `[status, value]` with status 0. */
    private fun single(u: IntArray): Int? = if (u.size >= 2 && u[0] == 0) u[1] else null

    /**
     * `(a, b)` pairs. An odd-length payload starts with a status byte, which must be 0
     * and is dropped; an even-length payload is pairs only.
     */
    private fun pairs(u: IntArray): List<Pair<Int, Int>>? {
        val body = if (u.size % 2 == 1) {
            if (u[0] != 0) return null
            u.copyOfRange(1, u.size)
        } else {
            u
        }
        return (body.indices step 2).map { body[it] to body[it + 1] }
    }
}
