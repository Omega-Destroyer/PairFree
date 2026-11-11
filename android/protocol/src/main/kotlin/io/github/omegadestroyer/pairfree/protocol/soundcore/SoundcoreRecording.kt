package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.SnoopScanner

/** Recording-only inspection. Never builds or sends an upload command. */
public object SoundcoreRecording {
    public class Analysis(public val containers: List<String>, public val opaquePackets: Set<SnoopScanner.Packet>)
    /** A named command is not permission to publish an unexpected larger reply. */
    public fun expectedSize(id: Int, size: Int): Boolean = size <= (SoundcoreCommand.CAPTURE_MAX_BYTES[id] ?: Int.MAX_VALUE)

    public fun withheld(id: Int): Boolean = id in SoundcoreCommand.PRIVATE || id in SoundcoreCommand.NEVER_SEND

    /**
     * Recognizes the CRC-checked header executed from Soundcore 6.4.0's transfer_sbc_t_bin.
     * Its use on the A30 is still unverified. Export sizes/format checks, never the filename,
     * 64-bit identifier or audio bytes. Carry only a header tail across packets of the same
     * connection/channel/command/direction, never across a private message or reconnect.
     */
    public fun containers(packets: List<SnoopScanner.Packet>): List<String> = analyse(packets).containers

    public fun analyse(packets: List<SnoopScanner.Packet>): Analysis {
        val tails = mutableMapOf<List<Long>, ByteArray>()
        val tailPackets = mutableMapOf<List<Long>, List<SnoopScanner.Packet>>()
        val audioKeys = mutableSetOf<List<Long>>()
        val opaque = mutableSetOf<SnoopScanner.Packet>()
        val out = mutableListOf<String>()
        for (p in packets) {
            if (p.brand != io.github.omegadestroyer.pairfree.protocol.RecordedBrand.SOUNDCORE) continue
            val key = listOf(p.connection.toLong(), p.channel.toLong(), p.id.toLong(), if (p.fromEarbuds) 1L else 0L, p.connectedAtMicros ?: 0L)
            if (withheld(p.id)) {
                tails.clear()
                tailPackets.clear()
                continue
            }
            if (key in audioKeys) opaque += p
            if (p.payload.isEmpty()) continue
            val bytes = (tails[key] ?: byteArrayOf()) + p.payload
            for (at in 0..bytes.size - HEADER_SIZE) {
                if (!MAGIC.indices.all { bytes[at + it] == MAGIC[it] }) continue
                if (bytes.u32(at + 92) != HEADER_SIZE.toLong() || bytes.u32(at + 104) != HEADER_SIZE.toLong()) continue
                if (crc(bytes, at, 110) != bytes.u16(at + 110)) continue
                // Also hide earlier pieces of a split header and later chunks if an upload
                // happens to reuse a command whose name was already in the ordinary table.
                opaque += tailPackets[key].orEmpty()
                opaque += p
                audioKeys += key
                val length = bytes.u32(at + 88)
                val available = bytes.size - at - HEADER_SIZE
                val payloadCheck = if (length <= available) {
                    if (crc(bytes, at + HEADER_SIZE, length.toInt()) == bytes.u16(at + 108)) "valid" else "invalid"
                } else "not reconstructed"
                val command = if (p.transportOnly) "RFCOMM" else p.id.toString(16).uppercase().padStart(4, '0')
                out += "${if (p.fromEarbuds) "buds" else "app"} $command: " +
                    "6611-family SBC container; payload_bytes=$length; header_bytes=$HEADER_SIZE; " +
                    "type=${bytes[at + 16].toInt() and 255}; header_crc=valid; payload_crc=$payloadCheck; " +
                    "filename, identifier and audio omitted; A30 format unverified"
            }
            tails[key] = bytes.takeLast(HEADER_SIZE - 1).toByteArray()
            tailPackets[key] = if (p.payload.size >= HEADER_SIZE - 1) listOf(p)
                else (tailPackets[key].orEmpty() + p).takeLast(HEADER_SIZE - 1)
        }
        return Analysis(out, opaque)
    }

    private fun crc(bytes: ByteArray, start: Int, length: Int): Int {
        var crc = 0
        for (i in start until start + length) {
            crc = crc xor ((bytes[i].toInt() and 255) shl 8)
            repeat(8) { crc = ((crc shl 1) xor if (crc and 0x8000 != 0) 0x1021 else 0) and 0xFFFF }
        }
        return crc
    }

    private fun ByteArray.u16(at: Int): Int = (this[at].toInt() and 255) or ((this[at + 1].toInt() and 255) shl 8)
    private fun ByteArray.u32(at: Int): Long = (0..3).sumOf { (this[at + it].toLong() and 255) shl (it * 8) }
    private const val HEADER_SIZE = 128
    private val MAGIC = byteArrayOf(0x31, 0x31, 0x36, 0x36, 0x00, 0x10, 0x01, 0x00)
}
