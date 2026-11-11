package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.nothing.NothingCommand
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungMessage
import io.github.omegadestroyer.pairfree.protocol.sony.SonyCommand
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCommand
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreRecording

/** A brand whose official app a recording can hold, besides SOUNDPEATS (whose frames are [SnoopScanner.Found]). */
public enum class RecordedBrand { SOUNDCORE, SAMSUNG, SONY, NOTHING }

/**
 * What another brand's official app (the Soundcore app, Galaxy Wearable, Sony's app, Nothing X) and its earbuds sent each
 * other, found in a Bluetooth log ([SnoopScanner.packets]), as a timeline for a report: when each
 * message went, which way, its number and name, and its bytes. A recording shows what the
 * official app sends for each setting, which is what confirms a model's protocol.
 *
 * The report goes into a public issue, so the bytes are shown only where they cannot carry
 * personal data:
 * - messages marked private (Soundcore's list of known devices and its serial number, Nothing's serial
 *   number and paired devices) and firmware update messages are left out, with their size;
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
            get() = packets.filter { !it.fromEarbuds }.groupingBy {
                if (it.transportOnly) "unframed RFCOMM data" else name(brand, it.id) ?: idText(brand, it.id).trim()
            }.eachCount().toList()
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
        val audio = if (earbuds.brand == RecordedBrand.SOUNDCORE) SoundcoreRecording.analyse(earbuds.packets) else null
        var previous: String? = null
        var previousPacket: SnoopScanner.Packet? = null
        var repeats = 0
        fun bulk(p: SnoopScanner.Packet): Boolean = earbuds.brand == RecordedBrand.SOUNDCORE &&
            !SoundcoreRecording.withheld(p.id) && p.payload.size >= 128 &&
            (p.transportOnly || p in audio?.opaquePackets.orEmpty() || name(earbuds.brand, p.id) == null || !SoundcoreRecording.expectedSize(p.id, p.payload.size))
        fun group(p: SnoopScanner.Packet) = Triple(p.id, p.fromEarbuds, p.channel)
        val bulkCounts = earbuds.packets.filter(::bulk).groupingBy(::group).eachCount()
        val bulkSeen = mutableMapOf<Triple<Int, Boolean, Int>, Int>()
        fun flush() {
            if (repeats > 1) out[out.lastIndex] = out.last() + " (x$repeats)"
        }
        for (p in earbuds.packets) {
            if (bulk(p)) {
                val key = group(p)
                val index = (bulkSeen[key] ?: 0) + 1
                bulkSeen[key] = index
                val count = bulkCounts.getValue(key)
                if (count > 16 && index in 9..count - 8) {
                    if (index == 9) {
                        flush()
                        previous = null
                        previousPacket = null
                        repeats = 0
                        out += "${idText(earbuds.brand, p.id)} channel=${p.channel}: ${count - 16} bulk packets summarized below (first/last 8 shown)"
                    }
                    continue
                }
            }
            val who = if (p.fromEarbuds) "buds" else "app"
            val id = idText(earbuds.brand, p.id)
            val bytes = if (p.transportOnly) "(${p.payload.size} unframed RFCOMM bytes; contents omitted)"
                else if (p in audio?.opaquePackets.orEmpty()) "(${p.payload.size} audio-transfer bytes; contents omitted)"
                else shown(earbuds.brand, p.id, p.payload, earbuds.address)
            val what = if (compact) "$who $id $bytes" else "${who.padEnd(4)} $id ${(name(earbuds.brand, p.id) ?: "?").padEnd(NAME_WIDTH)} $bytes"
            // Identical redactions/prefixes must not make different upload chunks look repeated.
            val samePayload = previousPacket?.let { it.channel == p.channel && it.payload.contentEquals(p.payload) } == true
            if (what == previous && samePayload) {
                repeats++
                continue
            }
            flush()
            previous = what
            previousPacket = p
            repeats = 1
            val seconds = (p.timeMicros - first) / 1000 / 1000.0
            out += (if (compact) seconds.format3() else seconds.format3().padStart(9)) + " " + what.trimEnd()
        }
        flush()
        if (earbuds.brand == RecordedBrand.SOUNDCORE) {
            val unknown = earbuds.packets.filter {
                !SoundcoreRecording.withheld(it.id) &&
                    (it in audio?.opaquePackets.orEmpty() || name(earbuds.brand, it.id) == null || !SoundcoreRecording.expectedSize(it.id, it.payload.size))
            }
            if (unknown.isNotEmpty()) {
                out += "Soundcore transfer/research diagnostics (unknown IDs are not identified as uploads):"
                for ((key, packets) in unknown.groupBy { Triple(it.id, it.fromEarbuds, it.channel) }) {
                    val sizes = packets.groupingBy { it.payload.size }.eachCount().toSortedMap()
                    out += "${if (key.second) "buds" else "app"} ${idText(earbuds.brand, key.first)} channel=${key.third} " +
                        "packets=${packets.size} payload_bytes=${packets.sumOf { it.payload.size.toLong() }} " +
                        "sizes=${sizes.entries.joinToString(",") { "${it.key}x${it.value}" }} " +
                        "first=${((packets.first().timeMicros - first) / 1_000_000.0).format3()} " +
                        "last=${((packets.last().timeMicros - first) / 1_000_000.0).format3()}"
                }
            }
            out += audio?.containers.orEmpty()
        }
        return out
    }

    /** The bytes of one message as a report shows them: hex, `XX` where masked, or left out. */
    public fun shown(brand: RecordedBrand, id: Int, payload: ByteArray, address: ByteArray?): String {
        if (payload.isEmpty()) return ""
        if (brand == RecordedBrand.SOUNDCORE && id < 0) return "(${payload.size} unframed RFCOMM bytes; contents omitted)"
        val known = name(brand, id) != null
        val withheld = when (brand) {
            RecordedBrand.SOUNDCORE -> id in SoundcoreCommand.PRIVATE || id in SoundcoreCommand.NEVER_SEND
            RecordedBrand.SAMSUNG -> id in SamsungMessage.NEVER_SEND
            RecordedBrand.SONY -> sonyNeverSend(id) || sonyPaired(id)
            RecordedBrand.NOTHING -> nothingWithheld(id)
        }
        if (withheld) return "(${payload.size} bytes left out)"
        val expected = brand != RecordedBrand.SOUNDCORE || SoundcoreRecording.expectedSize(id, payload.size)
        if ((!known || !expected) && payload.size > SHOWN_UNKNOWN) {
            if (brand != RecordedBrand.SOUNDCORE) return "(${payload.size} bytes left out)"
            val prefix = payload.copyOfRange(0, SHOWN_UNKNOWN)
            val hidden = masked(payload, address, serials = true)
            val shown = prefix.indices.joinToString(" ") { if (hidden[it]) "XX" else hex(prefix[it]) }
            return "(${payload.size} bytes; prefix=$shown; remaining bytes left out)"
        }
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
        RecordedBrand.NOTHING -> nothingName(id)
    }

    /**
     * A Soundcore command as four hex digits, as its docs write it; a Samsung message in decimal, as
     * GalaxyBudsClient numbers them; a Sony command as its table, a dot and its number in hex; a Nothing
     * command as four hex digits.
     */
    public fun idText(brand: RecordedBrand, id: Int): String = when (brand) {
        RecordedBrand.SOUNDCORE, RecordedBrand.NOTHING -> if (id < 0) "RFCOMM" else id.toString(16).uppercase().padStart(4, '0')
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

    /**
     * The peripheral parameter messages of table 2 (commands 0x34 to 0x3D, either set) carry the earbuds' list of paired and connected devices: their
     * Bluetooth addresses and names as text (a user's WF-1000XM6 recording, issue #33, showed a phone's and a computer's), so a report leaves them out.
     */
    private fun sonyPaired(id: Int): Boolean = ((id shr SONY_TABLE_SHIFT) and 0xF) == 2 && (id and 0xFF) in 0x34..0x3D

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

    /**
     * A Nothing command's name, lowercase; a reply (bit 15 cleared) by the command it answers. Ids are the
     * frame's command, so the app's reads and writes and the earbuds' events keep bit 15.
     */
    private fun nothingName(id: Int): String? =
        NothingCommand.NAMES[id]?.lowercase() ?: NothingCommand.NAMES[id or NOTHING_REQUEST]?.let { "${it.lowercase()} reply" }

    /**
     * Left out of a report: what PairFree never sends (firmware update, factory reset, debug), the replies
     * to the reads that hold the serial number or paired devices' addresses, and the events about other
     * connected devices.
     */
    private fun nothingWithheld(id: Int): Boolean {
        val request = id or NOTHING_REQUEST
        return request in NothingCommand.NEVER_SEND || request in NothingCommand.PRIVATE || id in NOTHING_PRIVATE_EVENTS
    }

    private const val NOTHING_REQUEST = 0x8000
    private val NOTHING_PRIVATE_EVENTS = setOf(NothingCommand.EVENT_DUAL_DEVICE_CONNECT_STATE, NothingCommand.EVENT_DUAL_DEVICE_SWITCH_STATE)

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
