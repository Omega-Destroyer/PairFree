package io.github.omegadestroyer.pairfree.protocol.skullcandy

/**
 * Skullcandy's AT dialect (docs/skullcandy/PROTOCOL.md sections 9.1 and 9.2): text lines over the serial service, `AT.<GROUP>=<args>` to set and
 * `AT.<GROUP>?` or `AT.<GROUP>=<name>` to read, each ended by CR LF. A reply is lines of `<prefix>:<value>` closed by `OK`; `ERROR` is a refusal and `NO CARRIER`
 * means the device is gone. Written from what the app's Bragi SDK sends and parses (Skullcandy 5.0.6).
 *
 * Only reads and the writes the app's own screens make are here. [NEVER_SEND] holds the groups that reset, clear, calibrate, set the clock or location or
 * run arbitrary key commands; [isAllowed] is checked on every line.
 */
public object SkullcandyAt {
    /** Groups of the app's AT set that PairFree never sends: factory reset, paired-device list reset, IMU calibration, time and location, device ids, arbitrary UI key commands. */
    public val NEVER_SEND: List<String> = listOf(
        "AT.FACTORY_RESET", "AT.BLUETOOTH=pdlreset", "AT.IMU=", "AT.TIME", "AT.LOCATION", "AT.IDAM", "AT.UI=inject", "AT.UI=invoke", "AT.UI=notify",
    )

    /** True when [line] is a read or one of the writes this class builds, and not among [NEVER_SEND]. */
    public fun isAllowed(line: String): Boolean = line.startsWith("AT.") && NEVER_SEND.none { line.startsWith(it) }

    /** The line as the channel takes it: the text and CR LF. */
    public fun line(command: String): ByteArray = (command + "\r\n").encodeToByteArray()

    public const val BATTERY: String = "AT.SYSINFO=battery"
    public const val ANC: String = "AT.UIAUDIO=anc"
    public const val ANC_FILTER: String = "AT.UIAUDIO=anc_filter"
    public const val TRANSPARENCY: String = "AT.UIAUDIO=transparency"
    public const val LATENCY: String = "AT.LATENCY?"
    public const val CRUSHER: String = "AT.CRUSHER?"
    public const val AUTO_MUTE: String = "AT.AUTOMUTE?"
    public const val LANGUAGE: String = "AT.UILANGUAGE?"
    public const val VOLUME_GESTURE: String = "AT.VOLUMEGESTURE?"

    public fun setAnc(on: Boolean): String = "AT.UIAUDIO=anc," + onOff(on)

    /** The ANC filter strength [level], or off. */
    public fun setAncFilter(level: Int?): String = "AT.UIAUDIO=anc_filter," + (level?.toString() ?: "off")

    public fun setTransparency(on: Boolean): String = "AT.UIAUDIO=transparency," + onOff(on)

    /** The transparency level as the percent the app writes (0 to 100). */
    public fun setTransparencyLevel(percent: Int): String = "AT.UIAUDIO=transparency,," + percent.coerceIn(0, 100)

    public fun volumeUp(steps: Int): String = "AT.VOLUME=up,$steps"

    public fun volumeDown(steps: Int): String = "AT.VOLUME=down,$steps"

    public fun setGamingMode(on: Boolean): String = "AT.LATENCY=gaming_mode," + onOff(on)

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    /** What the earbuds answered: [lines] without the closing status, and whether they said `OK`. */
    public class Reply(public val lines: List<String>, public val ok: Boolean, public val error: Boolean)

    /** Splits [text] at line ends (a trailing CR dropped) and reads the closing status. */
    public fun parse(text: String): Reply {
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
        val ok = lines.any { it.contains("OK") }
        val error = lines.any { it.contains("ERROR") || it.contains("NO CARRIER") }
        return Reply(lines.filterNot { it == "OK" || it.contains("ERROR") || it.contains("NO CARRIER") }, ok && !error, error)
    }

    /** The text after the first colon of [line], trimmed; the whole line when it has none. */
    public fun value(line: String): String = line.substringAfter(':', line).trim()

    /** `on` or `off` after the colon (the noise cancelling reply); null for anything else. */
    public fun onOffOf(line: String): Boolean? = when (value(line).lowercase()) {
        "on" -> true
        "off" -> false
        else -> null
    }

    /** The ANC filter reply: null when off or unreadable, else the level. A line holding `off` anywhere is off. */
    public fun ancFilterOf(line: String): Int? = if (line.contains("off", ignoreCase = true)) null else value(line).toIntOrNull()

    /** The transparency reply: whether it is on and its level in percent, when the earbuds gave one (`on,45`). */
    public class Transparency(public val on: Boolean, public val percent: Int?)

    public fun transparencyOf(line: String): Transparency {
        val parts = value(line).split(',').map { it.trim() }
        return Transparency(parts.firstOrNull()?.lowercase() == "on", parts.getOrNull(1)?.toIntOrNull())
    }

    /** The battery reply's fields, in percent: the `single` (else `local`) level, and the left and right earbuds when given; -1 for a field the line leaves out, as the app reads it. */
    public class Battery(public val left: Int, public val right: Int, public val single: Int)

    public fun batteryOf(line: String): Battery {
        val fields = value(line).split('\t').map { it.trim() }
        fun field(name: String): Int = fields.firstOrNull { it.startsWith("$name:") }?.substringAfter(':')?.trim()?.toIntOrNull() ?: -1
        val single = field("single").let { if (it == -1) field("local") else it }
        return Battery(field("left"), field("right"), single)
    }
}
