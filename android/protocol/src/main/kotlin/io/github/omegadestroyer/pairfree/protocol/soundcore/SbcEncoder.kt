package io.github.omegadestroyer.pairfree.protocol.soundcore

/**
 * A mono SBC encoder (low-complexity subband codec, A2DP appendix B) for the Sleep A30's local sounds, written for exactly what the Soundcore app 5.0.35
 * asks of FFmpeg's `sbc` encoder through FFmpegKit: one channel, 8 subbands, 16 blocks, loudness allocation, 16 or 32 kHz, and a bitpool from the bit rate
 * (docs/soundcore/PROTOCOL.md section 14). It is a port of FFmpeg's `libavcodec/sbcenc.c`, `sbcdsp.c`, `sbc.c` and `sbcdsp_data.h` (fixed-point analysis
 * filter, scale factors, bit allocation, packing), which come from BlueZ's SBC codec (Marcel Holtmann, Brad Midgley, Frederic Dalleau and others) and were
 * written for FFmpeg by Aurelien Jacobs and Andreas Mueller; both are LGPL-2.1-or-later, which PairFree's GPL-3.0-or-later may include. Its output is
 * checked byte for byte against FFmpeg's in the tests. The analysis tables are the specification's prototype filter times its scale constants, as
 * FFmpeg's header computes them.
 */
public class SbcEncoder(
    /** 16000 or 32000 (the app writes no other rate for the A30). */
    public val sampleRate: Int,
    /** The bit rate the app asks for, 128000 or 72000; sets the bitpool as FFmpeg's `sbc` encoder does. */
    public val bitRate: Int,
) {
    public val bitpool: Int
    private val frequency: Int

    init {
        frequency = when (sampleRate) {
            16000 -> 0
            32000 -> 1
            else -> throw IllegalArgumentException("SBC here is 16 or 32 kHz: $sampleRate")
        }
        // FFmpeg: blocks = clip(((13000 us * rate + 2) / (1e6 * subbands)) - 10, 4, 16) and ~3; the bitpool follows from the rate.
        val blocks = (((13000L * sampleRate + 2) / (1_000_000L * SUBBANDS)) - 10).toInt().coerceIn(4, 16) and 3.inv()
        require(blocks == BLOCKS) { "this encoder runs 16 blocks; $sampleRate Hz gives $blocks" }
        val d = BLOCKS
        bitpool = ((bitRate.toLong() * SUBBANDS * BLOCKS / sampleRate).toInt() - 4 * SUBBANDS - 32 + d / 2) / d
    }

    /** The samples one frame takes, and the bytes it gives. */
    public val frameSamples: Int = SUBBANDS * BLOCKS
    public val frameBytes: Int = 4 + SUBBANDS / 2 + (BLOCKS * bitpool + 7) / 8

    private val x = ShortArray(X_SIZE)
    private var position = (X_SIZE - SUBBANDS * 9) and 7.inv()

    /** Encodes [pcm] (16-bit mono) to frames; the last frame is padded with zeros. */
    public fun encode(pcm: ShortArray): ByteArray {
        val frames = (pcm.size + frameSamples - 1) / frameSamples
        val out = ByteArray(frames * frameBytes)
        for (f in 0 until frames) {
            val block = ShortArray(frameSamples)
            val from = f * frameSamples
            pcm.copyInto(block, 0, from, minOf(pcm.size, from + frameSamples))
            frame(block, out, f * frameBytes)
        }
        return out
    }

    private fun frame(pcm: ShortArray, out: ByteArray, at: Int) {
        // sbc_enc_process_input_8s for one channel and 128 samples (the position stays a multiple of 16, so the 8-sample steps do not occur).
        if (position < frameSamples) {
            x.copyInto(x, X_SIZE - 72, position, position + 72)
            position = X_SIZE - 72
        }
        var p = 0
        var n = frameSamples
        while (n >= 16) {
            position -= 16
            for (k in 0 until 16) x[position + k] = pcm[p + PERMUTE[k]]
            n -= 16
            p += 16
        }
        // sbc_analyze_audio: four blocks per call, the odd and even tables alternating.
        val sample = Array(BLOCKS) { IntArray(SUBBANDS) }
        var xi = position - 4 * SUBBANDS + BLOCKS * SUBBANDS
        var blk = 0
        while (blk < BLOCKS) {
            analyze(xi + 24, sample[blk], ODD)
            analyze(xi + 16, sample[blk + 1], EVEN)
            analyze(xi + 8, sample[blk + 2], ODD)
            analyze(xi + 0, sample[blk + 3], EVEN)
            xi -= 4 * SUBBANDS
            blk += 4
        }
        val scale = IntArray(SUBBANDS)
        for (sb in 0 until SUBBANDS) {
            var m = 1 shl SCALE_OUT_BITS
            for (b in 0 until BLOCKS) {
                val t = if (sample[b][sb] < 0) -sample[b][sb] else sample[b][sb]
                if (t != 0) m = m or (t - 1)
            }
            scale[sb] = (31 - SCALE_OUT_BITS) - m.countLeadingZeroBits()
        }
        pack(out, at, scale, sample)
    }

    /** `sbc_analyze_simd` for 8 subbands: the polyphase filter, then the cosine transform. */
    private fun analyze(at: Int, out: IntArray, c: IntArray) {
        val t1 = IntArray(SUBBANDS) { 1 shl (PROTO_SCALE - 1) }
        var hop = 0
        while (hop < 10 * SUBBANDS) {
            for (i in 0 until 2 * SUBBANDS) t1[i shr 1] += x[at + hop + i].toInt() * c[hop + i]
            hop += 2 * SUBBANDS
        }
        val t2 = IntArray(SUBBANDS) { (t1[it] shr PROTO_SCALE).toShort().toInt() }
        t1.fill(0)
        for (i in 0 until SUBBANDS / 2) for (j in 0 until 2 * SUBBANDS) t1[j shr 1] += t2[i * 2 + (j and 1)] * c[10 * SUBBANDS + i * 2 * SUBBANDS + j]
        for (i in 0 until SUBBANDS) out[i] = t1[i] shr (COS_SCALE - SCALE_OUT_BITS)
    }

    /** `ff_sbc_calculate_bits` for one channel, loudness allocation. */
    private fun bits(scale: IntArray): IntArray {
        val bitneed = IntArray(SUBBANDS)
        var max = 0
        for (sb in 0 until SUBBANDS) {
            if (scale[sb] == 0) {
                bitneed[sb] = -5
            } else {
                val loudness = scale[sb] - OFFSET8[frequency][sb]
                bitneed[sb] = if (loudness > 0) loudness / 2 else loudness
            }
            if (bitneed[sb] > max) max = bitneed[sb]
        }
        var bitcount = 0
        var slicecount = 0
        var bitslice = max + 1
        do {
            bitslice--
            bitcount += slicecount
            slicecount = 0
            for (sb in 0 until SUBBANDS) {
                if (bitneed[sb] > bitslice + 1 && bitneed[sb] < bitslice + 16) slicecount++
                else if (bitneed[sb] == bitslice + 1) slicecount += 2
            }
        } while (bitcount + slicecount < bitpool)
        if (bitcount + slicecount == bitpool) {
            bitcount += slicecount
            bitslice--
        }
        val bits = IntArray(SUBBANDS)
        for (sb in 0 until SUBBANDS) {
            if (bitneed[sb] < bitslice + 2) {
                bits[sb] = 0
            } else {
                bits[sb] = bitneed[sb] - bitslice
                if (bits[sb] > 16) bits[sb] = 16
            }
        }
        var sb = 0
        while (bitcount < bitpool && sb < SUBBANDS) {
            if (bits[sb] >= 2 && bits[sb] < 16) {
                bits[sb]++
                bitcount++
            } else if (bitneed[sb] == bitslice + 1 && bitpool > bitcount + 1) {
                bits[sb] = 2
                bitcount += 2
            }
            sb++
        }
        sb = 0
        while (bitcount < bitpool && sb < SUBBANDS) {
            if (bits[sb] < 16) {
                bits[sb]++
                bitcount++
            }
            sb++
        }
        return bits
    }

    private fun pack(out: ByteArray, at: Int, scale: IntArray, sample: Array<IntArray>) {
        val h = ((frequency and 3) shl 6) or ((((BLOCKS shr 2) - 1) and 3) shl 4) or 1 // mono, loudness, 8 subbands
        out[at] = 0x9C.toByte()
        out[at + 1] = h.toByte()
        out[at + 2] = bitpool.toByte()
        val header = ByteArray(2 + SUBBANDS / 2)
        header[0] = h.toByte()
        header[1] = bitpool.toByte()
        for (sb in 0 until SUBBANDS) {
            val i = 2 + sb / 2
            header[i] = (if (sb % 2 == 0) (scale[sb] and 15) shl 4 else header[i].toInt() or (scale[sb] and 15)).toByte()
        }
        out[at + 3] = crc8(header).toByte()
        var bitPos = 32
        fun put(count: Int, value: Long) {
            for (k in count - 1 downTo 0) {
                if ((value shr k) and 1L != 0L) out[at + bitPos / 8] = (out[at + bitPos / 8].toInt() or (0x80 shr (bitPos % 8))).toByte()
                bitPos++
            }
        }
        for (sb in 0 until SUBBANDS) put(4, (scale[sb] and 15).toLong())
        val bits = bits(scale)
        val levels = LongArray(SUBBANDS) { (((1L shl bits[it]) - 1) shl (32 - (scale[it] + SCALE_OUT_BITS + 2))) and 0xFFFFFFFFL }
        val delta = LongArray(SUBBANDS) { (1L shl (scale[it] + SCALE_OUT_BITS + 1)) and 0xFFFFFFFFL }
        for (blk in 0 until BLOCKS) for (sb in 0 until SUBBANDS) {
            if (bits[sb] == 0) continue
            val v = (delta[sb] + sample[blk][sb]) and 0xFFFFFFFFL
            put(bits[sb], ((levels[sb].toULong() * v.toULong()) shr 32).toLong())
        }
    }

    /** CRC-8, polynomial 0x1D, initial value 0x0F, over whole bytes (the header holds 48 bits). */
    private fun crc8(data: ByteArray): Int {
        var crc = 0x0F
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x1D) and 0xFF else (crc shl 1) and 0xFF }
        }
        return crc
    }

    private companion object {
        const val SUBBANDS = 8
        const val BLOCKS = 16
        const val X_SIZE = 328
        const val SCALE_OUT_BITS = 15
        const val PROTO_SCALE = 16
        const val COS_SCALE = 15

        /** `sbc_enc_process_input_8s`: which of the 16 input samples each slot takes (the C code counts bytes). */
        val PERMUTE = intArrayOf(15, 7, 14, 8, 13, 9, 12, 10, 11, 3, 6, 0, 5, 1, 4, 2)

        /** A2DP appendix B, page 69, for 8 subbands. */
        val OFFSET8 = arrayOf(
            intArrayOf(-2, 0, 0, 0, 0, 0, 0, 1),
            intArrayOf(-3, 0, 0, 0, 0, 0, 1, 2),
            intArrayOf(-4, 0, 0, 0, 0, 0, 1, 2),
            intArrayOf(-4, 0, 0, 0, 0, 0, 1, 2),
        )

        /** `sbcdsp_analysis_consts_fixed8_simd_even`: 80 filter and 64 cosine constants. */
        val EVEN = intArrayOf(
        0, 368, 25, 284, 63, 271, 115, 237, -137, 0, 335, 556,
        366, 303, 336, 37, 1035, 2366, 1277, 2436, 1920, 2978, 2649, 3305,
        -2437, 0, 1409, 8460, 537, 7174, -1022, 5426, 12436, 26876, 13199, 23125,
        17915, 25843, 23109, 27695, -20501, 0, 23125, -13199, 25843, -17915, 27695, -23109,
        -12436, 2366, -8460, 1409, -7174, 537, -5426, -1022, 2435, 0, 2436, -1277,
        2978, -1920, 3305, -2649, -1035, 368, -556, 335, -303, 366, -37, 336,
        -150, 0, 284, -25, 271, -63, 237, -115, 8303, 11226, -8303, -2634,
        -8303, -13242, 8303, -7501, 8303, 7501, -8303, 13242, -8303, 2634, 8303, -11226,
        10806, 10135, 4476, 8592, -4476, 5741, -10806, 2016, -10806, -2016, -4476, -5741,
        4476, -8592, 10806, -10135, -12912, 7501, -12912, -13242, -12912, 2634, -12912, 11226,
        -12912, -11226, -12912, -2634, -12912, 13242, -12912, -7501, 4476, 2016, -10806, -5741,
        10806, 8592, -4476, -10135, -4476, 10135, 10806, -8592, -10806, 5741, 4476, -2016,
        )

        /** `sbcdsp_analysis_consts_fixed8_simd_odd`. */
        val ODD = intArrayOf(
        0, -137, 25, 284, 63, 271, 115, 237, 368, 1035, 335, 556,
        366, 303, 336, 37, 0, -2437, 1277, 2436, 1920, 2978, 2649, 3305,
        2366, 12436, 1409, 8460, 537, 7174, -1022, 5426, 0, -20501, 13199, 23125,
        17915, 25843, 23109, 27695, 26876, -12436, 23125, -13199, 25843, -17915, 27695, -23109,
        0, 2435, -8460, 1409, -7174, 537, -5426, -1022, 2366, -1035, 2436, -1277,
        2978, -1920, 3305, -2649, 0, -150, -556, 335, -303, 366, -37, 336,
        368, 0, 284, -25, 271, -63, 237, -115, -12912, 11226, -12912, -2634,
        -12912, -13242, -12912, -7501, -12912, 7501, -12912, 13242, -12912, 2634, -12912, -11226,
        10806, 10135, 4476, 8592, -4476, 5741, -10806, 2016, -10806, -2016, -4476, -5741,
        4476, -8592, 10806, -10135, 8303, 7501, -8303, -13242, -8303, 2634, 8303, 11226,
        8303, -11226, -8303, -2634, -8303, 13242, 8303, -7501, 4476, 2016, -10806, -5741,
        10806, 8592, -4476, -10135, -4476, 10135, 10806, -8592, -10806, 5741, 4476, -2016,
        )
    }
}
