package io.github.omegadestroyer.pairfree.protocol.soundcore

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A virtual earbud for [BesUpload], written from the app's own answers (`BesOtaCMD.y`): it checks every message it takes (the announcement's length and
 * CRC-32, the configuration's CRC-32, each segment's CRC-32 over the bytes it holds) and rebuilds the file from the chunks. It runs the protocol, not an A30.
 */
internal class VirtualBesEarbud(
    private val file: ByteArray,
    private val resumeFrom: Int = 0,
    private val refuseSegments: Int = 0,
) {
    val received = java.io.ByteArrayOutputStream()
    var seen = mutableListOf<Int>()
    var announced = false
    var configured = false
    var refused = 0
    var ended = false
    var activated = false
    private var segmentStart = 0

    private fun frame(opcode: Int, vararg payload: Int): ByteArray {
        val body = ByteArray(payload.size) { payload[it].toByte() }
        return byteArrayOf(opcode.toByte(), body.size.toByte(), 0, 0, 0) + body
    }

    private fun u32(v: Int) = ByteArray(4) { (v shr (8 * it)).toByte() }

    /** The answer to one message of the app, or null when none is due. */
    fun answer(m: ByteArray): ByteArray? {
        val opcode = m[0].toInt() and 0xFF
        seen += opcode
        return when (opcode) {
            0x99 -> byteArrayOf(0x9A.toByte(), 0)
            0x97 -> frame(0x98, 1)
            0x90 -> frame(0x91, 1)
            0x8C -> {
                val seed = m.copyOfRange(9, 45)
                assertEquals(SleepAudioFile.crc32(seed), (0 until 4).sumOf { (m[45 + it].toLong() and 0xFF) shl (8 * it) }, "the resume message's CRC")
                if (resumeFrom > 0) {
                    received.write(file, 0, resumeFrom)
                    segmentStart = (resumeFrom / 32768) * 32768
                    byteArrayOf(0x8D.toByte(), 36, 0, 0, 0) + u32(resumeFrom) + ByteArray(32)
                } else {
                    byteArrayOf(0x8D.toByte(), 36, 0, 0, 0) + u32(0) + ByteArray(32) { (it + 1).toByte() }
                }
            }
            0x80 -> {
                announced = true
                assertEquals(file.size.toLong(), (0 until 4).sumOf { (m[9 + it].toLong() and 0xFF) shl (8 * it) }, "the announced length")
                assertEquals(SleepAudioFile.crc32(file), (0 until 4).sumOf { (m[13 + it].toLong() and 0xFF) shl (8 * it) }, "the announced CRC-32")
                // BEST, two versions, the packet size 512.
                byteArrayOf(0x81.toByte(), 10, 0, 0, 0, 0x42, 0x45, 0x53, 0x54, 1, 0, 1, 0, 0x00, 0x02)
            }
            0x86 -> {
                configured = true
                val body = m.copyOfRange(5, m.size)
                assertEquals(92, body.size)
                assertEquals(SleepAudioFile.crc32(body, 0, 88), (0 until 4).sumOf { (body[88 + it].toLong() and 0xFF) shl (8 * it) })
                frame(0x87, 1)
            }
            0x85 -> {
                received.write(m, 5, m.size - 5)
                null
            }
            0x82 -> {
                val have = received.toByteArray()
                val crc = SleepAudioFile.crc32(have, segmentStart, have.size)
                val sent = (0 until 4).sumOf { (m[9 + it].toLong() and 0xFF) shl (8 * it) }
                if (refused < refuseSegments) {
                    refused++
                    // A bad segment: the app goes back to the last verified offset, so the earbuds drop what they hold of it.
                    val keep = segmentStart
                    val all = received.toByteArray()
                    received.reset()
                    received.write(all, 0, keep)
                    frame(0x83, 0)
                } else {
                    assertEquals(crc, sent, "the segment's CRC-32 over what the earbuds hold")
                    segmentStart = have.size
                    frame(0x83, 1)
                }
            }
            0x88 -> {
                ended = true
                assertEquals(file.size, received.size())
                frame(0x84, 1)
            }
            0x92 -> {
                activated = true
                frame(0x93, 1)
            }
            else -> null
        }
    }
}

class BesUploadTest {
    private fun run(file: ByteArray, earbud: VirtualBesEarbud, session: BesUpload): BesUpload {
        var pending = session.start()
        var guard = 0
        while (pending.isNotEmpty() && guard++ < 10_000) {
            val next = mutableListOf<ByteArray>()
            for (m in pending) earbud.answer(m)?.let { next += session.feed(it) }
            pending = next
        }
        return session
    }

    private fun sampleFile(size: Int) = SleepAudioFile.build(ByteArray(size) { (it * 31 + 7).toByte() }, 0x1122334455667788L, "Rain", 3, 32000)

    @Test
    fun aSmallFileGoesThroughEveryStep() {
        val file = sampleFile(3000)
        val earbud = VirtualBesEarbud(file)
        val session = run(file, earbud, BesUpload(file, indexInDevice = 0))
        assertEquals(BesUpload.Phase.DONE, session.phase, session.failure)
        assertArrayEquals(file, earbud.received.toByteArray())
        assertTrue(earbud.announced && earbud.configured && earbud.ended && earbud.activated)
        assertEquals(0x99, earbud.seen.first(), "the protocol query comes first")
        assertEquals(listOf(0x99, 0x97, 0x90, 0x8C, 0x80, 0x86), earbud.seen.take(6))
        assertEquals(0x92, earbud.seen.last(), "the activation ends the conversation")
        assertEquals(file.size, session.verified)
        assertEquals(1, session.codeToKeep!![0].toInt(), "the fresh answer's 32-byte code is kept")
    }

    @Test
    fun aLargeFileGoesInSegmentsOfThirtyTwoKilobytes() {
        val file = sampleFile(100_000)
        val earbud = VirtualBesEarbud(file)
        val session = run(file, earbud, BesUpload(file, indexInDevice = 1))
        assertEquals(BesUpload.Phase.DONE, session.phase, session.failure)
        assertArrayEquals(file, earbud.received.toByteArray())
        assertEquals(listOf(0x82, 0x82, 0x82, 0x82), earbud.seen.filter { it == 0x82 }, "a check after each 32,768-byte segment and one for the rest")
        // 507-byte chunks (a 512-byte packet size less its overhead); a segment ends at its boundary.
        val chunks = earbud.seen.count { it == 0x85 }
        assertTrue(chunks in 195..205, "$chunks chunks")
    }

    @Test
    fun aSegmentTheEarbudsRefuseIsSentAgainFromTheLastVerifiedOffset() {
        val file = sampleFile(70_000)
        val earbud = VirtualBesEarbud(file, refuseSegments = 2)
        val session = run(file, earbud, BesUpload(file, indexInDevice = 0))
        assertEquals(BesUpload.Phase.DONE, session.phase, session.failure)
        assertArrayEquals(file, earbud.received.toByteArray())
        assertEquals(2, earbud.refused)
    }

    @Test
    fun threeRefusalsStopTheTransfer() {
        val file = sampleFile(70_000)
        val earbud = VirtualBesEarbud(file, refuseSegments = 3)
        val session = run(file, earbud, BesUpload(file, indexInDevice = 0))
        assertEquals(BesUpload.Phase.FAILED, session.phase)
        assertTrue(session.failure!!.contains("three times"))
    }

    @Test
    fun anEarlierTransferResumesWhereTheEarbudsHoldIt() {
        val file = sampleFile(100_000)
        val earbud = VirtualBesEarbud(file, resumeFrom = 40_000)
        val session = run(file, earbud, BesUpload(file, indexInDevice = 0))
        assertEquals(BesUpload.Phase.DONE, session.phase, session.failure)
        assertArrayEquals(file, earbud.received.toByteArray())
        assertTrue(!earbud.announced, "a resumed transfer goes straight to the configuration")
        assertNull(session.codeToKeep)
    }
}
