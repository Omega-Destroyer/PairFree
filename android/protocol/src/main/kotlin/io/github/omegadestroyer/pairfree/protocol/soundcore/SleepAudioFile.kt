package io.github.omegadestroyer.pairfree.protocol.soundcore

/**
 * The file the Soundcore app 5.0.35 writes for the Sleep A30's local sound, and the messages of the BES transfer that carries it
 * (docs/soundcore/PROTOCOL.md section 14, "Sleep A30 upload"). Groundwork only: nothing in the app sends a file yet. The container follows the app's native
 * writer (`transfer_sbc_t_bin`, 48 executions compared byte for byte outside the repo); the messages are the app's BES builders. No A30 has accepted a file
 * built here, and the 92-byte transfer configuration (`86`) is not rebuilt, so the transfer cannot be completed from this code.
 */
public object SleepAudioFile {
    public const val MAGIC: Int = 0x36363131
    public const val HEADER_SIZE: Int = 256
    public const val CODEC_SBC: Int = 0
    public const val CODEC_AAC: Int = 1
    private const val NAME_SIZE = 64

    /** CRC-16 with polynomial 0x1021, initial value 0, not reflected (XMODEM): the container's two checksums. */
    public fun crc16(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0
        for (i in from until to) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }

    /**
     * The file: a [headerSize]-byte header, then [payload] (the encoded audio) unchanged. [name] is cut at 64 bytes of ASCII. Field 92 stays 128 whatever the
     * header's size, as the app's writer leaves it; the audio starts at [headerSize].
     */
    public fun build(
        payload: ByteArray,
        mediaId: Long,
        name: String,
        soundType: Int,
        sampleRate: Int,
        codec: Int = CODEC_SBC,
        headerSize: Int = HEADER_SIZE,
    ): ByteArray {
        require(headerSize >= 112) { "the header holds its checksums up to byte 111" }
        val out = ByteArray(headerSize + payload.size)
        putInt(out, 0, MAGIC)
        out[4] = codec.toByte()
        out[5] = 0x10
        out[6] = 0x01
        for (i in 0 until 8) out[8 + i] = (mediaId shr (8 * i)).toByte()
        out[16] = soundType.toByte()
        val text = name.encodeToByteArray()
        text.copyInto(out, 24, 0, minOf(text.size, NAME_SIZE))
        putInt(out, 88, payload.size)
        putInt(out, 92, 128)
        putInt(out, 96, sampleRate)
        putInt(out, 104, headerSize)
        val payloadCrc = crc16(payload)
        out[108] = payloadCrc.toByte()
        out[109] = (payloadCrc shr 8).toByte()
        val headerCrc = crc16(out, 0, 110)
        out[110] = headerCrc.toByte()
        out[111] = (headerCrc shr 8).toByte()
        payload.copyInto(out, headerSize)
        return out
    }

    private fun putInt(to: ByteArray, at: Int, v: Int) {
        for (i in 0 until 4) to[at + i] = (v shr (8 * i)).toByte()
    }

    /** CRC-32 (the zip polynomial), as the transfer's file announcement and segment checks use. */
    public fun crc32(data: ByteArray, from: Int = 0, to: Int = data.size): Long {
        var crc = 0xFFFFFFFFL
        for (i in from until to) {
            crc = crc xor (data[i].toLong() and 0xFF)
            repeat(8) { crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xEDB88320L else crc ushr 1 }
        }
        return crc xor 0xFFFFFFFFL
    }
}

/**
 * The BES transfer's messages as the app's builders emit them in user-flag-1 mode: an opcode byte, a four-byte little-endian length and the payload (BES's
 * own framing, not the Soundcore frame). Only the messages the app's builders produce from fixed or simple inputs are here; the resume token and the
 * 92-byte configuration are not.
 */
public object BesTransferMessages {
    private val BEST = byteArrayOf(0x42, 0x45, 0x53, 0x54)

    private fun message(opcode: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(5 + payload.size)
        out[0] = opcode.toByte()
        for (i in 0 until 4) out[1 + i] = (payload.size shr (8 * i)).toByte()
        payload.copyInto(out, 5)
        return out
    }

    private fun u32(v: Long) = ByteArray(4) { (v shr (8 * it)).toByte() }

    /** The version query. */
    public fun version(): ByteArray = message(0x8E, BEST)

    /** Selects the side (0 the default, 1 left, 2 right). */
    public fun side(side: Int): ByteArray = message(0x9D, byteArrayOf(if (side == 1) 1 else if (side == 2) 2 else 0))

    /** Selects the audio file index: the app writes 10 plus the index (0 or 1). */
    public fun user(indexInDevice: Int): ByteArray = message(0x97, byteArrayOf((10 + indexInDevice).toByte()))

    /** Begins the negotiation. */
    public fun begin(): ByteArray = message(0x90, byteArrayOf(0))

    /** The file announcement: `BEST`, the file's length and its CRC-32. */
    public fun announce(length: Long, crc32: Long): ByteArray = message(0x80, BEST + u32(length) + u32(crc32))

    /** The check of one segment: `BEST` and its CRC-32. */
    public fun segmentCrc(crc32: Long): ByteArray = message(0x82, BEST + u32(crc32))

    /** Ends the file; the earbuds' answer to it is not yet the success. */
    public fun end(): ByteArray = message(0x88)

    /** The final activation, sent after the earbuds accept the whole file. */
    public fun activate(): ByteArray = message(0x92, BEST)
}
