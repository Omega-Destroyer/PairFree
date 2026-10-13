package io.github.omegadestroyer.soundpeter.protocol

/**
 * Reads an Android Bluetooth HCI snoop log (the btsnoop format, found inside a bug report)
 * and keeps only SoundPEATS control frames (docs/soundpeats/PROTOCOL.md section 2) on vendor `00 0A`
 * or `00 1D`. Everything else in the log is skipped and never kept.
 *
 * Feed the file in chunks of any size with [feed]; a large log never has to fit in memory.
 * ACL packets are put back together into L2CAP frames per connection and direction, and each
 * one is searched for frames, as the PC script that found the H3's and Air5 Pro+'s EQ did.
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

    /** One HCI packet. Only ACL data is of interest. */
    private fun record(data: ByteArray, size: Int) {
        val start = when (datalink) {
            DATALINK_H4 -> if (size > 0 && data[0].toInt() == H4_ACL) 1 else return
            else -> if (recordFlags and FLAG_COMMAND_OR_EVENT != 0) return else 0
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
