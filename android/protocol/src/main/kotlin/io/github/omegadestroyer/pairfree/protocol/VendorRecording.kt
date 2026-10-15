package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungMessage
import io.github.omegadestroyer.pairfree.protocol.sony.SonyCommand
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCommand

/** A brand whose official app a recording can hold, besides SOUNDPEATS (whose frames are [SnoopScanner.Found]). */
public enum class RecordedBrand { SOUNDCORE, SAMSUNG, SONY }

/**
 * What another brand's official app (the Soundcore app, Galaxy Wearable, Sony's app) and its earbuds sent each
 * other, found in a Bluetooth log ([SnoopScanner.packets]), as a timeline for a report: when each
 * message went, which way, its number and name, and its bytes. A recording shows what the
 * official app sends for each setting, which is what confirms a model's protocol.
 *
 * The report goes into a public issue, so the bytes are shown only where they cannot carry
 * personal data:
 * - messages marked private (Soundcore's list of known devices and its serial number) and
 *   firmware update messages are left out, with their size;
 * - a message PairFree does not know is shown only up to [SHOWN_UNKNOWN] bytes, too few for an
 *   address, a name or a key;
 * - the earbuds' Bluetooth address is masked wherever it appears, as bytes or as text, and so is
 *   anything in a Soundcore message that looks like a serial number (12 or more hex digits as text).
 */
public object VendorRecording {
    /** The earbuds on one connection of the log, with every packet from the recording on. [address] is for matching and masking only. */
    public class Earbuds(
        public val brand: RecordedBrand,
        public val address: ByteArray?,
        public val packets: List<SnoopScanner.Packet>,
    ) {
        public val fromApp: Int get() = packets.count { !it.fromEarbuds }
        public val fromEarbuds: Int get() = packets.count { it.fromEarbuds }

        /** What the app sent, by message name, with how many times: what the user did in it. */
        public val sent: List<Pair<String, Int>>
            get() = packets.filter { !it.fromEarbuds }.groupingBy { name(brand, it.id) ?: idText(brand, it.id).trim() }.eachCount().toList()
    }

    /** Each pair of earbuds in [packets], in the order they first appear, with the packets from [since] on. */
    public fun of(packets: List<SnoopScanner.Packet>, since: Long? = null): List<Earbuds> =
        packets.sortedBy { it.timeMicros }.filter { since == null || it.timeMicros >= since }
            .groupBy { it.brand to (it.address?.let(::hex) ?: "connection ${it.connection}") }
            .map { (key, list) -> Earbuds(key.first, list.first().address, list) }

    /**
     * Where a recording of another brand's app starts when the user did not open the recording
     * screen first: when the earbuds last connected (the connection with the latest packet), or its
     * first packet when the log does not show the connection being made.
     */
    public fun start(packets: List<SnoopScanner.Packet>): Long? {
        val last = packets.maxByOrNull { it.timeMicros } ?: return null
        val same = packets.filter { it.connection == last.connection && it.address.contentEqualsOrBothNull(last.address) }
        return last.connectedAtMicros ?: same.minOf { it.timeMicros }
    }

    /**
     * The timeline: one line per message, seconds from the first, `app` or `buds` for who sent
     * it, its number and name, then its bytes. A message repeated unchanged shows once with a
     * count. [compact] leaves out the names and the padding, to fit a report in a link.
     */
    public fun lines(earbuds: Earbuds, compact: Boolean = false): List<String> {
        val first = earbuds.packets.firstOrNull()?.timeMicros ?: return emptyList()
        val out = mutableListOf<String>()
        if (!compact) out += "Seconds from the first message; app: sent by the official app, buds: sent by the earbuds."
        var previous: String? = null
        var repeats = 0
        fun flush() {
            if (repeats > 1) out[out.lastIndex] = out.last() + " (x$repeats)"
        }
        for (p in earbuds.packets) {
            val who = if (p.fromEarbuds) "buds" else "app"
            val id = idText(earbuds.brand, p.id)
            val bytes = shown(earbuds.brand, p.id, p.payload, earbuds.address)
            val what = if (compact) "$who $id $bytes" else "${who.padEnd(4)} $id ${(name(earbuds.brand, p.id) ?: "?").padEnd(NAME_WIDTH)} $bytes"
            if (what == previous) {
                repeats++
                continue
            }
            flush()
            previous = what
            repeats = 1
            val seconds = (p.timeMicros - first) / 1000 / 1000.0
            out += (if (compact) seconds.format3() else seconds.format3().padStart(9)) + " " + what.trimEnd()
        }
        flush()
        return out
    }

    /** The bytes of one message as a report shows them: hex, `XX` where masked, or left out. */
    public fun shown(brand: RecordedBrand, id: Int, payload: ByteArray, address: ByteArray?): String {
        if (payload.isEmpty()) return ""
        val known = name(brand, id) != null
        val withheld = when (brand) {
            RecordedBrand.SOUNDCORE -> id in SoundcoreCommand.PRIVATE || id in SoundcoreCommand.NEVER_SEND
            RecordedBrand.SAMSUNG -> id in SamsungMessage.NEVER_SEND
            RecordedBrand.SONY -> sonyNeverSend(id)
        }
        if (withheld || (!known && payload.size > SHOWN_UNKNOWN)) return "(${payload.size} bytes left out)"
        val hidden = masked(payload, address, serials = brand != RecordedBrand.SAMSUNG)
        return payload.indices.joinToString(" ") { i -> if (hidden[i]) "XX" else hex(payload[i]) }
    }

    /** Which bytes of [payload] to hide: the [address] as bytes or text, and with [serials] text that looks like a serial number. */
    internal fun masked(payload: ByteArray, address: ByteArray?, serials: Boolean): BooleanArray {
        val hidden = BooleanArray(payload.size)
        fun hide(pattern: ByteArray) {
            if (pattern.isEmpty()) return
            var i = 0
            while (i + pattern.size <= payload.size) {
                if (pattern.indices.all { payload[i + it] == pattern[it] }) {
                    for (j in i until i + pattern.size) hidden[j] = true
                    i += pattern.size
                } else {
                    i++
                }
            }
        }
        if (address != null && address.size == ADDRESS_SIZE) {
            val reversed = address.reversedArray()
            for (order in listOf(address, reversed)) {
                hide(order)
                for (upper in listOf(true, false)) {
                    for (separator in listOf("", ":")) {
                        val text = order.joinToString(separator) { hex(it).let { s -> if (upper) s.uppercase() else s } }
                        hide(text.encodeToByteArray())
                    }
                }
            }
        }
        if (serials) {
            var run = 0
            for (i in 0..payload.size) {
                val c = if (i < payload.size) payload[i].toInt() and 0xFF else -1
                if (c in '0'.code..'9'.code || c in 'A'.code..'F'.code) {
                    run++
                } else {
                    if (run >= SERIAL_RUN) for (j in i - run until i) hidden[j] = true
                    run = 0
                }
            }
        }
        return hidden
    }

    /** The message's name in PairFree's tables, or null when PairFree does not know it. */
    public fun name(brand: RecordedBrand, id: Int): String? = when (brand) {
        RecordedBrand.SOUNDCORE -> SoundcoreCommand.NAMES[id]
        RecordedBrand.SAMSUNG -> SamsungMessage.NAMES[id]
        RecordedBrand.SONY -> sonyName(id)
    }

    /**
     * A Soundcore command as four hex digits, as its docs write it; a Samsung message in decimal, as
     * GalaxyBudsClient numbers them; a Sony command as its table, a dot and its number in hex.
     */
    public fun idText(brand: RecordedBrand, id: Int): String = when (brand) {
        RecordedBrand.SOUNDCORE -> id.toString(16).uppercase().padStart(4, '0')
        RecordedBrand.SAMSUNG -> id.toString().padStart(3, ' ')
        RecordedBrand.SONY -> "${(id shr SONY_TABLE_SHIFT) and 0xF}.${(id and 0xFF).toString(16).uppercase().padStart(2, '0')}"
    }

    /**
     * A Sony message as a packet id: its table set (0 while the recording has not shown it), its table
     * and its command. The two sets number some commands differently (docs/sony/PROTOCOL.md section 1).
     */
    public fun sonyId(tableSet: Int, table: Int, command: Int): Int = (tableSet shl SONY_SET_SHIFT) or (table shl SONY_TABLE_SHIFT) or command

    /** The Sony command's name in its set's table; with the set unknown, only a name both sets share. */
    private fun sonyName(id: Int): String? {
        val command = id and 0xFF
        val table = (id shr SONY_TABLE_SHIFT) and 0xF
        val one = (if (table == 2) SonyCommand.Set1Table2.NAMES else SonyCommand.Set1.NAMES)[command]
        val two = (if (table == 2) SonyCommand.Set2Table2.NAMES else SonyCommand.Set2.NAMES)[command]
        return when (id shr SONY_SET_SHIFT) {
            1 -> one
            2 -> two
            else -> one?.takeIf { it == two }
        }?.lowercase()
    }

    /** Firmware update, test and log messages, in either set, are left out of a report. */
    private fun sonyNeverSend(id: Int): Boolean {
        val command = id and 0xFF
        val table = (id shr SONY_TABLE_SHIFT) and 0xF
        val one = command in (if (table == 2) SonyCommand.Set1Table2.NEVER_SEND else SonyCommand.Set1.NEVER_SEND)
        val two = command in (if (table == 2) SonyCommand.Set2Table2.NEVER_SEND else SonyCommand.Set2.NEVER_SEND)
        return when (id shr SONY_SET_SHIFT) {
            1 -> one
            2 -> two
            else -> one || two
        }
    }

    private const val SONY_TABLE_SHIFT = 8
    private const val SONY_SET_SHIFT = 12

    /** At most this many bytes of a message PairFree does not know go into a report. */
    public const val SHOWN_UNKNOWN: Int = 4
    private const val SERIAL_RUN = 12
    private const val ADDRESS_SIZE = 6
    private const val NAME_WIDTH = 26

    private fun hex(b: Byte): String = (b.toInt() and 0xFF).toString(16).padStart(2, '0')

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { hex(it) }

    private fun Double.format3(): String {
        val millis = kotlin.math.round(this * 1000).toLong()
        return "${millis / 1000}.${(millis % 1000).toString().padStart(3, '0')}"
    }

    private fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean =
        if (this == null || other == null) this == null && other == null else contentEquals(other)
}
