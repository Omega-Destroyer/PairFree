package io.github.omegadestroyer.pairfree.protocol.soundcore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/**
 * The SBC encoder against FFmpeg's `sbc` encoder (PyAV 17, FFmpeg's own): the same 3,250 samples of an integer-only signal went through it at the four
 * settings the Soundcore app writes for the Sleep A30, and the output's SHA-256 and first 16 bytes are kept here.
 */
class SbcEncoderTest {
    private fun signal(n: Int): ShortArray {
        var state = 12345L
        return ShortArray(n) { i ->
            state = (state * 1103515245L + 12345L) and 0x7FFFFFFFL
            val noise = ((state shr 16) % 601).toInt() - 300
            val saw = ((i * 37) % 4001 - 2000) * 3
            val tri = Math.abs((i * 13) % 2000 - 1000) * 5 - 2500
            (saw + tri + noise).coerceIn(-32768, 32767).toShort()
        }
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun hex(b: ByteArray, n: Int) = b.take(n).joinToString("") { "%02x".format(it) }

    private fun check(rate: Int, bitRate: Int, bitpool: Int, frame: Int, size: Int, first: String, digest: String) {
        val encoder = SbcEncoder(rate, bitRate)
        assertEquals(bitpool, encoder.bitpool, "bitpool at $rate Hz, $bitRate b/s")
        assertEquals(frame, encoder.frameBytes)
        val out = encoder.encode(signal(3250))
        assertEquals(size, out.size)
        assertEquals(first, hex(out, 16), "first frame at $rate Hz, $bitRate b/s")
        assertEquals(digest, sha(out), "FFmpeg's output at $rate Hz, $bitRate b/s")
    }

    @Test
    fun matchesFfmpegAt16kHz128k() = check(16000, 128000, 60, 128, 3328, "9c313c95b97877777f5fa0403f7cfdf7", "4ccd545b32f2cc2bac7d34b99509c2ecf68fb0053bc9a801d3355da34e4af76f")

    @Test
    fun matchesFfmpegAt16kHz72k() = check(16000, 72000, 32, 72, 1872, "9c3120b9b97877777ef776db7cf776db", "a84f0a69192d088e53ce6560dbd24cd42149de0618eb6f4e3eb22467520ebd0a")

    @Test
    fun matchesFfmpegAt32kHz128k() = check(32000, 128000, 28, 64, 1664, "9c711c04b97877777e76edb7e76edb75", "59ace94e3e7578bad36f124fcd263714df7785c14162e57749e93d7eb89cb069")

    @Test
    fun matchesFfmpegAt32kHz72k() = check(32000, 72000, 14, 36, 936, "9c710e3eb97877777dadf6b71adb831c", "2262cde88edca48cab301b240e5cf53832559d4498babe62b9c1aac3ae5c1de5")
}
