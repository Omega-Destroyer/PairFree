package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.nothing.NothingParser
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungParser
import io.github.omegadestroyer.pairfree.protocol.sony.SonyCommand
import io.github.omegadestroyer.pairfree.protocol.sony.SonyParser
import io.github.omegadestroyer.pairfree.protocol.sony.SonyWire
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreParser
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreWire

/**
 * Reads an Android Bluetooth HCI snoop log (the btsnoop format, found inside a bug report)
 * and keeps only the control messages of earbuds PairFree knows: SOUNDPEATS frames
 * (docs/soundpeats/PROTOCOL.md section 2) on vendor `00 0A` or `00 1D` ([frames]), and
 * Soundcore, Samsung Galaxy Buds and Sony packets ([packets]). Everything else in the log is skipped
 * and never kept.
 *
 * Feed the file in chunks of any size with [feed]; a large log never has to fit in memory.
 * ACL packets are put back together into L2CAP frames per connection and direction. Each one
 * is searched for SOUNDPEATS frames, as the PC script that found the H3's and Air5 Pro+'s EQ
 * did. RFCOMM data is also read channel by channel and fed to the Soundcore, Samsung and Sony parsers,
 * so their packets are found even when split across RFCOMM frames. The connection events give
 * each connection's remote address, which goes with its packets so a recording can be matched
 * to a paired device and the address masked; it is never shown.
 */
public class SnoopScanner {
    /**
     * One control frame in the log. [connection] is the ACL handle. [timeMicros] counts from
     * 1970 on the phone's local clock: Android writes btsnoop times in local time, so read it
     * as UTC to get the wall-clock time the phone showed.
     */
    public class Found(
        public val timeMicros: Long,
        public val fromEarbuds: Boolean,
        public val connection: Int,
        public val frame: Frame,
    )

    /**
     * One Soundcore, Samsung or Sony packet in the log: its [id] (a Soundcore command, a Samsung
     * message or a Sony command, [VendorRecording.sonyId]) and payload, on RFCOMM [channel] (the DLCI) of ACL [connection]. [address] is the
     * remote device's Bluetooth address as the connection event gave it (least significant byte
     * first), and [connectedAtMicros] when that connection was made; both null when the log
     * started after the connection.
     */
    public class Packet(
        public val timeMicros: Long,
        public val fromEarbuds: Boolean,
        public val connection: Int,
        public val channel: Int,
        public val brand: RecordedBrand,
        public val id: Int,
        public val payload: ByteArray,
        public val address: ByteArray?,
        public val connectedAtMicros: Long?,
    )

    /** False until the first 16 bytes have been read; stays false if they are not a btsnoop header. */
    public var isSnoopLog: Boolean = false
        private set

    /** Set when the log uses a layout this reader does not know, or is damaged. Reading stops there. */
    public var problem: String? = null
        private set

    private val found = mutableListOf<Found>()
    private var datalink = 0
    private var buffer = ByteArray(RECORD_HEADER)
    private var filled = 0
    private var need = FILE_HEADER
    private var stage = Stage.FILE_HEADER
    private var recordFlags = 0
    private var recordTime = 0L
    private val l2cap = HashMap<Int, Assembly>()
    private val links = HashMap<Int, Link>()
    private val streams = HashMap<Int, Stream>()

    /** Each Sony channel's table set, once the earbuds' first reply showed it. */
    private val sonySets = HashMap<Int, Int>()
    private val packets = mutableListOf<Packet>()

    /** A connection the log saw being made: the remote address and when. */
    private class Link(val address: ByteArray, val atMicros: Long)

    /** One direction of one RFCOMM channel, with a parser per protocol it may carry. */
    private class Stream(fromEarbuds: Boolean) {
        val soundcore = SoundcoreParser(if (fromEarbuds) SoundcoreWire.FROM_EARBUDS_HEADER else SoundcoreWire.TO_EARBUDS_HEADER)
        val samsung = SamsungParser(legacy = false)
        val samsungLegacy = SamsungParser(legacy = true)
        val sony = SonyParser()
        val nothing = NothingParser()
    }

    private enum class Stage { FILE_HEADER, RECORD_HEADER, RECORD, STOPPED }

    private class Assembly(var bytes: ByteArray, var size: Int)

    public fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        var at = offset
        val end = offset + length
        while (at < end && stage != Stage.STOPPED) {
            val take = minOf(need - filled, end - at)
            data.copyInto(buffer, filled, at, at + take)
            filled += take
            at += take
            if (filled == need) step()
        }
    }

    /** Every control frame found so far, in log order. */
    public fun frames(): List<Found> = found.toList()

    /**
     * Every Soundcore, Samsung, Sony and Nothing packet found so far, in log order. A check byte can match
     * by chance, so Samsung, Sony and Nothing packets count only on a channel that carried at least two.
     */
    public fun packets(): List<Packet> {
        val checked = setOf(RecordedBrand.SAMSUNG, RecordedBrand.SONY, RecordedBrand.NOTHING)
        val counts = packets.filter { it.brand in checked }.groupingBy { Triple(it.brand, it.connection, it.channel) }.eachCount()
        return packets.filter { it.brand !in checked || (counts[Triple(it.brand, it.connection, it.channel)] ?: 0) >= MIN_SAMSUNG_PACKETS }
    }

    private fun step() {
        when (stage) {
            Stage.FILE_HEADER -> {
                if (!MAGIC.indices.all { buffer[it] == MAGIC[it] }) {
                    stop("not a btsnoop log")
                    return
                }
                datalink = buffer.u32(12)
                if (datalink != DATALINK_H4 && datalink != DATALINK_HCI) {
                    stop("btsnoop datalink $datalink is not handled")
                    return
                }
                isSnoopLog = true
                expect(Stage.RECORD_HEADER, RECORD_HEADER)
            }
            Stage.RECORD_HEADER -> {
                val included = buffer.u32(4)
                recordFlags = buffer.u32(8)
                recordTime = buffer.u64(16) - EPOCH_DELTA_US
                if (included < 0 || included > MAX_RECORD) {
                    stop("damaged record of $included bytes")
                    return
                }
                if (included == 0) expect(Stage.RECORD_HEADER, RECORD_HEADER) else expect(Stage.RECORD, included)
            }
            Stage.RECORD -> {
                record(buffer, need)
                expect(Stage.RECORD_HEADER, RECORD_HEADER)
            }
            Stage.STOPPED -> Unit
        }
    }

    private fun expect(next: Stage, bytes: Int) {
        stage = next
        need = bytes
        filled = 0
        if (buffer.size < bytes) buffer = ByteArray(bytes)
    }

    private fun stop(reason: String) {
        problem = reason
        stage = Stage.STOPPED
    }

    /** One HCI packet: ACL data, and the events that say which device a connection is with. */
    private fun record(data: ByteArray, size: Int) {
        val start = when (datalink) {
            DATALINK_H4 -> when {
                size > 0 && data[0].toInt() == H4_ACL -> 1
                size > 0 && data[0].toInt() == H4_EVENT -> return event(data, 1, size)
                else -> return
            }
            else -> when {
                recordFlags and FLAG_COMMAND_OR_EVENT == 0 -> 0
                recordFlags and FLAG_RECEIVED != 0 -> return event(data, 0, size)
                else -> return
            }
        }
        if (size - start < 4) return
        val header = data.u16le(start)
        val handle = header and 0x0FFF
        val boundary = (header shr 12) and 0x3
        val length = minOf(data.u16le(start + 2), size - start - 4)
        val fromEarbuds = recordFlags and FLAG_RECEIVED != 0
        val key = (handle shl 1) or (if (fromEarbuds) 1 else 0)
        val bodyAt = start + 4
        val assembly = when (boundary) {
            0, 2 -> Assembly(ByteArray(maxOf(length, 64)), 0).also { l2cap[key] = it }
            1 -> l2cap[key] ?: return
            else -> return
        }
        if (assembly.size + length > MAX_L2CAP) {
            l2cap.remove(key)
            return
        }
        if (assembly.bytes.size < assembly.size + length) assembly.bytes = assembly.bytes.copyOf(maxOf(assembly.size + length, assembly.bytes.size * 2))
        data.copyInto(assembly.bytes, assembly.size, bodyAt, bodyAt + length)
        assembly.size += length
        if (assembly.size < 4) return
        val l2len = assembly.bytes.u16le(0)
        if (assembly.size < 4 + l2len) return
        l2cap.remove(key)
        scan(assembly.bytes, 4, 4 + l2len, fromEarbuds, handle)
        if (assembly.bytes.u16le(2) >= FIRST_DYNAMIC_CID) rfcomm(assembly.bytes, 4, 4 + l2len, fromEarbuds, handle)
    }

    /** A connection made or ended: the remote address goes with the connection's packets. */
    private fun event(data: ByteArray, at: Int, size: Int) {
        if (size - at < 2) return
        val code = data[at].toInt() and 0xFF
        val params = at + 2
        val length = minOf(data[at + 1].toInt() and 0xFF, size - params)
        when {
            code == EVENT_CONNECTION_COMPLETE && length >= 10 && data[params].toInt() == 0 && data[params + 9].toInt() == LINK_ACL -> {
                val handle = data.u16le(params + 1) and 0x0FFF
                links[handle] = Link(data.copyOfRange(params + 3, params + 9), recordTime)
                dropStreams(handle)
            }
            code == EVENT_DISCONNECTION_COMPLETE && length >= 3 && data[params].toInt() == 0 -> {
                val handle = data.u16le(params + 1) and 0x0FFF
                links.remove(handle)
                dropStreams(handle)
            }
        }
    }

    private fun dropStreams(handle: Int) {
        streams.keys.removeAll { it shr STREAM_HANDLE_SHIFT == handle }
        sonySets.keys.removeAll { it shr STREAM_HANDLE_SHIFT == handle }
    }

    /**
     * Reads one L2CAP payload as an RFCOMM UIH frame (address, control, length, an optional
     * credit byte, the data, a check byte) and feeds the data of a data channel to its stream's
     * parsers. Anything that does not add up to exactly that is not RFCOMM and is skipped.
     */
    private fun rfcomm(bytes: ByteArray, from: Int, to: Int, fromEarbuds: Boolean, handle: Int) {
        if (to - from < 4) return
        val address = bytes[from].toInt() and 0xFF
        val dlci = address shr 2
        val control = bytes[from + 1].toInt() and 0xFF
        if (address and 1 == 0 || dlci == 0 || (control != UIH && control != UIH_CREDIT)) return
        var at = from + 2
        val first = bytes[at].toInt() and 0xFF
        val length = if (first and 1 == 1) {
            at += 1
            first shr 1
        } else {
            if (at + 1 >= to) return
            at += 2
            (first shr 1) or ((bytes[at - 1].toInt() and 0xFF) shl 7)
        }
        if (control == UIH_CREDIT) at += 1
        if (length == 0 || at + length + 1 != to) return
        val data = bytes.copyOfRange(at, at + length)
        val key = (handle shl STREAM_HANDLE_SHIFT) or (dlci shl 1) or (if (fromEarbuds) 1 else 0)
        val stream = streams.getOrPut(key) { Stream(fromEarbuds) }
        val link = links[handle]
        fun add(brand: RecordedBrand, id: Int, payload: ByteArray) {
            packets += Packet(recordTime, fromEarbuds, handle, dlci, brand, id, payload, link?.address?.copyOf(), link?.atMicros)
        }
        stream.soundcore.feed(data).forEach { add(RecordedBrand.SOUNDCORE, it.command, it.body) }
        stream.samsung.feed(data).forEach { add(RecordedBrand.SAMSUNG, it.id, it.payload) }
        stream.samsungLegacy.feed(data).forEach { add(RecordedBrand.SAMSUNG, it.id, it.payload) }
        stream.nothing.feed(data).forEach { add(RecordedBrand.NOTHING, it.command, it.payload) }
        // Sony: acknowledgements carry nothing; the earbuds' first reply says the table set (4 bytes set 1, 8 set 2).
        val channel = key and 1.inv()
        for (m in stream.sony.feed(data)) {
            if (m.dataType == SonyWire.ACK || m.payload.isEmpty()) continue
            val table = if (m.dataType == SonyWire.TABLE2) 2 else 1
            if (fromEarbuds && table == 1 && m.command == SonyCommand.Set1.CONNECT_RET_PROTOCOL_INFO) {
                when (m.payload.size) {
                    SONY_SET1_REPLY -> sonySets[channel] = 1
                    SONY_SET2_REPLY -> sonySets[channel] = 2
                }
            }
            add(RecordedBrand.SONY, VendorRecording.sonyId(sonySets[channel] ?: 0, table, m.command), m.payload.copyOfRange(1, m.payload.size))
        }
    }

    /** Finds `FF 03|04 00 LEN VENDOR COMMAND PAYLOAD` frames in one L2CAP payload. */
    private fun scan(bytes: ByteArray, from: Int, to: Int, fromEarbuds: Boolean, handle: Int) {
        var i = from
        while (i + Frame.HEADER_SIZE <= to) {
            val b = bytes[i].toInt() and 0xFF
            val version = bytes[i + 1].toInt() and 0xFF
            if (b != Wire.SOF || (version != 0x03 && version != 0x04) || bytes[i + 2].toInt() != 0) {
                i++
                continue
            }
            val n = bytes[i + 3].toInt() and 0xFF
            val vendor = bytes.u16(i + 4)
            if ((vendor != Wire.VENDOR_MAIN && vendor != Wire.VENDOR_EQ) || i + Frame.HEADER_SIZE + n > to) {
                i++
                continue
            }
            val payload = bytes.copyOfRange(i + Frame.HEADER_SIZE, i + Frame.HEADER_SIZE + n)
            found += Found(recordTime, fromEarbuds, handle, Frame(vendor, bytes.u16(i + 6), payload))
            i += Frame.HEADER_SIZE + n
        }
    }

    private companion object {
        val MAGIC = "btsnoop".encodeToByteArray() + 0
        const val FILE_HEADER = 16
        const val RECORD_HEADER = 24
        const val DATALINK_HCI = 1001
        const val DATALINK_H4 = 1002
        const val H4_ACL = 0x02
        const val H4_EVENT = 0x04
        const val EVENT_CONNECTION_COMPLETE = 0x03
        const val EVENT_DISCONNECTION_COMPLETE = 0x05
        const val LINK_ACL = 0x01
        const val FIRST_DYNAMIC_CID = 0x0040
        const val UIH = 0xEF
        const val UIH_CREDIT = 0xFF
        const val STREAM_HANDLE_SHIFT = 7
        const val MIN_SAMSUNG_PACKETS = 2
        const val SONY_SET1_REPLY = 4
        const val SONY_SET2_REPLY = 8
        const val FLAG_RECEIVED = 0x01
        const val FLAG_COMMAND_OR_EVENT = 0x02
        const val MAX_RECORD = 1 shl 20
        const val MAX_L2CAP = 0x10000 + 4

        /** Microseconds from year 0 to 1970, as btsnoop counts. */
        const val EPOCH_DELTA_US = 0x00DCDDB30F2F8000L
    }
}

private fun ByteArray.u16le(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

private fun ByteArray.u64(at: Int): Long = (u32(at).toLong() shl 32) or (u32(at + 4).toLong() and 0xFFFFFFFFL)
