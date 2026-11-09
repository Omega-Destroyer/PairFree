package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreCommand
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreRecording
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SoundcoreRecordingTest {
    // Output of the original 6.4.0 native wrapper, with synthetic bytes i % 251 (256 bytes).
    // Verified independently in E:/Builds/research/soundcore-a30-2026-11-09/check_wrapper.py.
    private val header = ("3131363600100100080706050403020101000000000000005265736561726368" +
        "0000000000000000000000000000000000000000000000000000000000000000" +
        "0000000000000000000000000000000000000000000000000001000080000000" +
        "7b0000000000000080000000bdffdcfa00000000000000000000000000000000").hexBytes()
    private val container get() = header + ByteArray(256) { (it % 251).toByte() }
    private fun packet(bytes: ByteArray, id: Int = 0x7E01, at: Long = 1, connection: Long = 1) =
        SnoopScanner.Packet(at, false, 11, 10, RecordedBrand.SOUNDCORE, id, bytes, null, connection)

    @Test fun nativeContainerMetadataExcludesNameIdentifierAndAudio() {
        assertEquals(128, header.size)
        val text = SoundcoreRecording.containers(listOf(packet(container))).single()
        assertTrue(text.contains("payload_bytes=256"), text)
        assertTrue(text.contains("header_crc=valid; payload_crc=valid"), text)
        assertTrue(text.contains("A30 format unverified"), text)
        assertFalse(text.contains("Research"))
        assertFalse(text.contains("08 07 06 05"))
        assertFalse(text.contains("00 01 02 03"))
    }

    @Test fun headerCanSpanPacketsButNotConnectionsOrPrivateMessages() {
        val a = container.copyOfRange(0, 60)
        val b = container.copyOfRange(60, container.size)
        assertEquals(1, SoundcoreRecording.containers(listOf(packet(a), packet(b, at = 2))).size)
        assertTrue(SoundcoreRecording.containers(listOf(packet(a), packet(b, connection = 2))).isEmpty())
        assertTrue(SoundcoreRecording.containers(listOf(packet(a), packet(byteArrayOf(1), SoundcoreCommand.SLEEP_CLOCK), packet(b))).isEmpty())
    }

    @Test fun corruptHeadersAreNotRecognizedAndIncompletePayloadsAreExplicit() {
        val bad = container.copyOf().apply { this[30] = (this[30].toInt() xor 1).toByte() }
        assertTrue(SoundcoreRecording.containers(listOf(packet(bad))).isEmpty())
        assertTrue(SoundcoreRecording.containers(listOf(packet(container, SoundcoreCommand.SLEEP_ALARMS))).isEmpty())
        val text = SoundcoreRecording.containers(listOf(packet(header))).single()
        assertTrue(text.contains("payload_crc=not reconstructed"))
    }

    @Test fun splitAudioHeaderDoesNotExposeANameEvenOnAnAlreadyKnownCommand() {
        val packets = listOf(
            packet(container.copyOfRange(0, 100), SoundcoreCommand.STATE),
            packet(container.copyOfRange(100, container.size), SoundcoreCommand.STATE, at = 2),
            packet("Later audio data".encodeToByteArray(), SoundcoreCommand.STATE, at = 3),
        )
        val text = VendorRecording.lines(VendorRecording.of(packets).single()).joinToString("\n")
        assertTrue(text.contains("payload_bytes=256"), text)
        assertTrue(text.contains("audio-transfer bytes; contents omitted"), text)
        assertFalse(text.contains("52 65 73 65 61 72 63 68"), "split filename omitted retroactively")
        assertFalse(text.contains("4c 61 74 65 72"), "subsequent chunks of that command stay opaque")
    }

    @Test fun unknownPreviewsStayShortAndDifferentChunksDoNotCollapse() {
        val a = byteArrayOf(1, 2, 3, 4, 5, 6)
        val b = byteArrayOf(1, 2, 3, 4, 8, 9)
        val text = VendorRecording.lines(VendorRecording.of(listOf(packet(a), packet(b, at = 2))).single()).joinToString("\n")
        assertTrue(text.contains("packets=2 payload_bytes=12 sizes=6x2"), text)
        assertFalse(text.contains("(x2)"), "same preview does not mean the same payload")
        assertFalse(text.contains("01 02 03 04 05"))
        val repeated = VendorRecording.lines(VendorRecording.of(listOf(packet(a), packet(a, at = 2))).single()).joinToString("\n")
        assertTrue(repeated.contains("(x2)"))
    }

    @Test fun prefixesStillMaskPartialAddressesAndSerialText() {
        val address = byteArrayOf(10, 11, 12, 13, 14, 15)
        val shown = VendorRecording.shown(RecordedBrand.SOUNDCORE, 0x7E01, byteArrayOf(1, 2) + address, address)
        assertTrue(shown.contains("prefix=01 02 XX XX"), shown)
        val serial = VendorRecording.shown(RecordedBrand.SOUNDCORE, 0x7E01, "ABCDEF0123456789".encodeToByteArray(), null)
        assertTrue(serial.contains("prefix=XX XX XX XX"), serial)
        val report = RecordingReport.sections(emptyList(), null, emptyList(), vendor = listOf("Sleep A30" to VendorRecording.of(listOf(packet(container))).single())).joinToString("\n")
        assertTrue(report.contains("payload_bytes=256"), report)
        assertFalse(report.contains("52 65 73 65 61 72 63 68"), "container filename must never reach the public report")
        assertFalse(report.contains("08 07 06 05 04 03 02 01"), "container identifier must never reach the public report")
    }

    @Test fun knownSleepControlsAreBoundedAndSleepHistoryIsWithheld() {
        assertEquals("01", VendorRecording.shown(RecordedBrand.SOUNDCORE, SoundcoreCommand.LISTENING_MODE, byteArrayOf(1), null))
        val big = ByteArray(20) { 7 }
        assertTrue(VendorRecording.shown(RecordedBrand.SOUNDCORE, SoundcoreCommand.LISTENING_MODE, big, null).contains("remaining bytes left out"))
        for (id in listOf(SoundcoreCommand.SLEEP_CLOCK, SoundcoreCommand.SLEEP_ALARMS, SoundcoreCommand.SET_SLEEP_ALARM, SoundcoreCommand.SLEEP_LOG_PART)) {
            assertEquals("(20 bytes left out)", VendorRecording.shown(RecordedBrand.SOUNDCORE, id, big, null))
        }
    }

    @Test fun longUploadsStaySmallEnoughForAReportAndKeepAllCounts() {
        val packets = (1..200).map { i -> packet(ByteArray(512) { if (it == 10) i.toByte() else 42 }, at = i * 1_000_000L) }
        val text = VendorRecording.lines(VendorRecording.of(packets).single()).joinToString("\n")
        assertTrue(text.contains("184 bulk packets summarized below"), text)
        assertTrue(text.contains("packets=200 payload_bytes=102400 sizes=512x200"), text)
        assertTrue(text.contains("first=0.000 last=199.000"), text)
        assertTrue(text.length < 8000, "summarize bulk data instead of making a huge issue")
    }
}
