package io.github.omegadestroyer.pairfree.protocol.soundcore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.random.Random

/**
 * A hostile line between the app and the virtual earbud of [BesUploadTest]: it cuts each answer into pieces, joins answers that came close together, makes the
 * earbuds miss a turn now and then, and can fail or fall silent. Virtual time, so the 20 ms between chunks and the waits cost nothing.
 */
private class HostileChannel(
    private val earbud: VirtualBesEarbud,
    seed: Int,
    private val fragment: Boolean = true,
    private val missTurns: Int = 0,
    private val silentAfter: Int = Int.MAX_VALUE,
    private val failAfterWrites: Int = Int.MAX_VALUE,
) : BesChannel {
    private val random = Random(seed)
    private val inbox = ArrayDeque<ByteArray>()
    private var writes = 0
    private var missed = 0
    var timeouts = 0
    var firstAnswer = true

    override suspend fun write(bytes: ByteArray) {
        if (++writes > failAfterWrites) throw IOException("link lost")
        val answer = earbud.answer(bytes) ?: return
        if (writes > silentAfter) return
        // The protocol answer comes whole (the app reads it before it knows the length field); later ones may be cut anywhere.
        if (firstAnswer || !fragment) {
            firstAnswer = false
            inbox += answer
            return
        }
        var at = 0
        while (at < answer.size) {
            val n = minOf(answer.size - at, 1 + random.nextInt(7))
            inbox += answer.copyOfRange(at, at + n)
            at += n
        }
    }

    override suspend fun read(timeoutMs: Long): ByteArray? {
        if (missed < missTurns && random.nextInt(3) == 0) {
            missed++
            timeouts++
            return null
        }
        val first = inbox.removeFirstOrNull()
        if (first == null) {
            timeouts++
            return null
        }
        // Sometimes several pieces arrive in one read.
        var joined = first
        while (inbox.isNotEmpty() && random.nextInt(3) == 0) joined += inbox.removeFirst()
        return joined
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BesUploadRunnerTest {
    private fun file(size: Int) = SleepAudioFile.build(ByteArray(size) { (it * 17 + 3).toByte() }, 0x0102030405060708L, "Rain", 2, 16000)

    @Test
    fun aFileGoesThroughALineThatCutsAndJoinsTheAnswers() = runTest {
        for (seed in 1..20) {
            val f = file(90_000 + seed * 137)
            val earbud = VirtualBesEarbud(f)
            val upload = BesUpload(f, 0)
            val seen = mutableListOf<Int>()
            val result = BesUploadRunner(upload, HostileChannel(earbud, seed), f.size, { done, _ -> seen += done }, { }).run()
            assertNull(result, "seed $seed: $result")
            assertArrayEquals(f, earbud.received.toByteArray(), "seed $seed")
            assertTrue(earbud.activated, "seed $seed")
            assertEquals(seen.sorted(), seen, "progress only goes forward")
            assertEquals(f.size, seen.last())
        }
    }

    @Test
    fun anEarbudThatMissesATurnIsWaitedForAgain() = runTest {
        for (seed in 1..10) {
            val f = file(40_000)
            val earbud = VirtualBesEarbud(f)
            val channel = HostileChannel(earbud, seed, missTurns = 1)
            val result = BesUploadRunner(BesUpload(f, 1), channel, f.size, pause = { }).run()
            assertNull(result, "seed $seed: $result")
            assertArrayEquals(f, earbud.received.toByteArray())
        }
    }

    @Test
    fun refusedSegmentsOverAHostileLineStillEndRight() = runTest {
        val f = file(80_000)
        val earbud = VirtualBesEarbud(f, refuseSegments = 2)
        val result = BesUploadRunner(BesUpload(f, 0), HostileChannel(earbud, 7), f.size, pause = { }).run()
        assertNull(result, result)
        assertArrayEquals(f, earbud.received.toByteArray())
        assertEquals(2, earbud.refused)
    }

    @Test
    fun anEarbudThatFallsSilentStopsTheTransferWithAReason() = runTest {
        val f = file(60_000)
        val earbud = VirtualBesEarbud(f)
        val result = BesUploadRunner(BesUpload(f, 0), HostileChannel(earbud, 3, silentAfter = 12), f.size, pause = { }).run()
        assertNotNull(result)
        assertTrue(result!!.contains("stopped answering"), result)
    }

    @Test
    fun aLostLinkStopsItWithAReason() = runTest {
        val f = file(60_000)
        val earbud = VirtualBesEarbud(f)
        val result = BesUploadRunner(BesUpload(f, 0), HostileChannel(earbud, 3, failAfterWrites = 20), f.size, pause = { }).run()
        assertNotNull(result)
        assertTrue(result!!.contains("link lost"), result)
    }

    @Test
    fun theEarbudsRefusingTheSlotEndsItAtOnce() = runTest {
        val f = file(5_000)
        val refusing = object : BesChannel {
            var reply: ByteArray? = null
            override suspend fun write(bytes: ByteArray) {
                reply = when (bytes[0].toInt() and 0xFF) {
                    0x99 -> byteArrayOf(0x9A.toByte(), 0)
                    0x97 -> byteArrayOf(0x98.toByte(), 1, 0, 0, 0, 0)
                    else -> null
                }
            }
            override suspend fun read(timeoutMs: Long) = reply.also { reply = null }
        }
        val result = BesUploadRunner(BesUpload(f, 0), refusing, f.size, pause = { }).run()
        assertEquals("the earbuds refused the sound slot", result)
    }

    @Test
    fun cancellingStopsTheTransferAndTellsTheStateMachine() = runTest {
        val f = file(60_000)
        val upload = BesUpload(f, 0)
        val earbud = VirtualBesEarbud(f)
        val job = launch { BesUploadRunner(upload, HostileChannel(earbud, 5), f.size).run() }
        advanceTimeBy(30)
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(BesUpload.Phase.FAILED, upload.phase)
        assertEquals("cancelled", upload.failure)
        assertTrue(!earbud.activated)
    }

    @Test
    fun theChunksAreSpacedByTwentyMilliseconds() = runTest {
        val f = file(20_000)
        val earbud = VirtualBesEarbud(f)
        val pauses = mutableListOf<Long>()
        val result = BesUploadRunner(BesUpload(f, 0), HostileChannel(earbud, 2, fragment = false), f.size, pause = { pauses += it }).run()
        assertNull(result, result)
        assertTrue(pauses.isNotEmpty() && pauses.all { it == 20L })
        assertEquals(earbud.seen.count { it == 0x85 }, pauses.size)
    }
}
