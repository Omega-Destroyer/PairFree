package io.github.omegadestroyer.pairfree.protocol.soundcore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class SleepLibraryTest {
    private fun entry(hash: String, id: Long, name: String = "Rain", added: Long = 1) = SleepLibrary.Entry(hash, id, name, 30, 128000, 2, added)

    @Test
    fun theHashIsSha256() {
        val random = java.util.Random(4)
        for (size in listOf(0, 1, 55, 56, 63, 64, 65, 119, 120, 1000, 4097)) {
            val data = ByteArray(size).also { random.nextBytes(it) }
            val expected = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
            assertEquals(expected, Sha256.hex(data), "$size bytes")
        }
    }

    @Test
    fun aSoundIsKnownByItsAudioNotItsMediaId() {
        val payload = ByteArray(2000) { (it * 5).toByte() }
        val a = SleepAudioFile.build(payload, 111, "One", 2, 16000)
        val b = SleepAudioFile.build(payload, 222, "Two", 2, 16000)
        val other = SleepAudioFile.build(payload.copyOf().also { it[10] = 99 }, 111, "One", 2, 16000)
        assertEquals(SleepLibrary.hashOf(a, soundType = 2), SleepLibrary.hashOf(b, soundType = 2))
        assertNotEquals(SleepLibrary.hashOf(a, soundType = 2), SleepLibrary.hashOf(other, soundType = 2))
        assertNotEquals(SleepLibrary.hashOf(a, soundType = 2), SleepLibrary.hashOf(a, soundType = 3), "the sound type is part of it")
    }

    @Test
    fun aSoundIsStoredOnceAndKeepsItsFirstMediaId() {
        val library = SleepLibrary()
        val first = library.add(entry("h1", 7))
        val again = library.add(entry("h1", 99, "Other name"))
        assertEquals(first, again)
        assertEquals(7, again.mediaId)
        assertEquals(1, library.entries.size)
    }

    @Test
    fun theLibraryKnowsWhatEachSlotWasGiven() {
        val library = SleepLibrary()
        library.add(entry("h1", 7))
        library.add(entry("h2", 8, "Waves", added = 2))
        library.sent("AA:BB", 0, "h1", 100)
        assertTrue(library.holds("AA:BB", 0, "h1"))
        assertFalse(library.holds("AA:BB", 1, "h1"))
        assertFalse(library.holds("CC:DD", 0, "h1"), "another pair of earbuds")
        library.sent("AA:BB", 0, "h2", 200)
        assertFalse(library.holds("AA:BB", 0, "h1"), "a slot holds one sound")
        assertEquals("Waves", library.heldBy("AA:BB", 0)?.name)
        library.sent("AA:BB", 1, "h2", 300)
        assertEquals(listOf(0, 1), library.slotsHolding("AA:BB", "h2"))
        assertEquals(listOf("Waves", "Rain"), library.entries.map { it.name }, "newest first")
    }

    @Test
    fun removingASoundForgetsItsSlotsOnly() {
        val library = SleepLibrary()
        library.add(entry("h1", 7))
        library.add(entry("h2", 8))
        library.sent("AA", 0, "h1", 1)
        library.sent("AA", 1, "h2", 1)
        library.remove("h1")
        assertNull(library.find("h1"))
        assertNull(library.heldBy("AA", 0))
        assertEquals("Rain", library.heldBy("AA", 1)?.name)
    }

    @Test
    fun itSurvivesBeingWrittenAndReadAndSkipsBrokenLines() {
        val library = SleepLibrary()
        library.add(entry("h1", Long.MAX_VALUE - 5, "Tab\tand\nnewline"))
        library.sent("AA:BB:CC", 1, "h1", 12345)
        val text = library.serialize() + "slot\tonly\tbroken\nentry\tx\tnot-a-number\t\t\t\t\t\n\n"
        val back = SleepLibrary.parse(text)
        assertEquals(1, back.entries.size)
        assertEquals(Long.MAX_VALUE - 5, back.entries[0].mediaId)
        assertEquals("Tab and newline", back.entries[0].name)
        assertTrue(back.holds("AA:BB:CC", 1, "h1"))
        assertEquals(text.lines().first(), back.serialize().lines().first())
    }
}
