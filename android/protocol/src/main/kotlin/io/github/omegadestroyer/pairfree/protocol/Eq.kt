package io.github.omegadestroyer.pairfree.protocol

import kotlin.math.abs

/**
 * EQ band payloads (docs/soundpeats/PROTOCOL.md section 4). Send one frame per band with vendor
 * [Wire.VENDOR_EQ] and command [Wire.EQ_COMMAND_QUALCOMM] or [Wire.EQ_COMMAND_OTHER].
 *
 * Each value is scaled and truncated toward zero, as tools/probe.py does;
 * data/soundpeats/test_vectors.json pins the result.
 */
public object Eq {
    /** Payload for command `0E 01`: one byte with the band count in the high nibble and the index (from 1) in the low. */
    public fun band0E01(count: Int, index: Int, freqHz: Double, gainDb: Double, q: Double, masterGainDb: Double = 0.0): ByteArray {
        require(count in 1..15) { "0E01 holds 1 to 15 bands, got $count" }
        require(index in 1..count) { "band index $index outside 1..$count" }
        return byteArrayOf(((count shl 4) or index).toByte()) + values(freqHz, gainDb, q, masterGainDb)
    }

    /** Payload for command `0E 02`: band count and index (from 1) as separate bytes. */
    public fun band0E02(count: Int, index: Int, freqHz: Double, gainDb: Double, q: Double, masterGainDb: Double = 0.0): ByteArray {
        require(count in 1..255) { "0E02 holds 1 to 255 bands, got $count" }
        require(index in 1..count) { "band index $index outside 1..$count" }
        return byteArrayOf(count.toByte(), index.toByte()) + values(freqHz, gainDb, q, masterGainDb)
    }

    private fun values(freqHz: Double, gainDb: Double, q: Double, masterGainDb: Double): ByteArray {
        val out = ByteArray(8)
        out.putU16(0, signed(masterGainDb * 60, "master gain"))
        out.putU16(2, unsigned(freqHz * 3, "frequency"))
        out.putU16(4, signed(gainDb * 60, "gain"))
        out.putU16(6, unsigned(q * 4096, "Q"))
        return out
    }

    private fun signed(scaled: Double, name: String): Int {
        require(abs(scaled) <= 0x7FFF) { "$name out of range" }
        return scaled.toInt() and 0xFFFF
    }

    private fun unsigned(scaled: Double, name: String): Int {
        require(scaled > 0 && scaled <= 0xFFFF) { "$name out of range" }
        return scaled.toInt()
    }
}
