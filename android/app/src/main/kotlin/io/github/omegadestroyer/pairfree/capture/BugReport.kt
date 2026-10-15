package io.github.omegadestroyer.pairfree.capture

import android.content.ContentResolver
import android.net.Uri
import io.github.omegadestroyer.pairfree.protocol.SnoopScanner
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** What a shared file held, for Record from the official app. */
sealed interface CaptureResult {
    /**
     * Every SOUNDPEATS control frame ([frames]) and every Soundcore and Samsung packet ([packets])
     * in the log's files, except those [BugReport.read] was told to skip.
     */
    data class Found(val frames: List<SnoopScanner.Found>, val packets: List<SnoopScanner.Packet> = emptyList()) : CaptureResult {
        val isEmpty: Boolean get() = frames.isEmpty() && packets.isEmpty()
    }

    /** A bug report with no Bluetooth log in it: the snoop log was off. */
    data object NoBluetoothLog : CaptureResult

    data object NotABugReport : CaptureResult

    data object Unreadable : CaptureResult
}

/**
 * Reads an Android bug report (a zip, sometimes with a zip inside) or a bare btsnoop file,
 * on this phone only. Only files named like the Bluetooth snoop log are read, and of those
 * only the control messages of earbuds PairFree knows are kept ([SnoopScanner]); the rest is
 * never stored.
 */
object BugReport {
    /** Reads [uri]. Frames for which [skip] is true (by snoop log time) are left out. */
    fun read(resolver: ContentResolver, uri: Uri, skip: (Long) -> Boolean = { false }): CaptureResult = try {
        resolver.openInputStream(uri)?.use { read(it, skip) } ?: CaptureResult.Unreadable
    } catch (_: IOException) {
        CaptureResult.Unreadable
    } catch (_: SecurityException) {
        CaptureResult.Unreadable
    }

    fun read(input: InputStream, skip: (Long) -> Boolean = { false }): CaptureResult {
        val stream = BufferedInputStream(input, BUFFER)
        stream.mark(HEAD)
        val head = ByteArray(HEAD)
        var got = 0
        while (got < HEAD) {
            val n = stream.read(head, got, HEAD - got)
            if (n < 0) break
            got += n
        }
        stream.reset()
        val found = Found()
        val logs = when {
            got >= 4 && head.startsWith(ZIP) -> zip(ZipInputStream(stream), found, depth = 0)
            got >= 8 && head.startsWith(SNOOP) -> if (scan(stream, found)) 1 else 0
            else -> return CaptureResult.NotABugReport
        }
        if (logs == 0) return CaptureResult.NoBluetoothLog
        return CaptureResult.Found(found.frames.filterNot { skip(it.timeMicros) }, found.packets.filterNot { skip(it.timeMicros) })
    }

    private class Found {
        val frames = mutableListOf<SnoopScanner.Found>()
        val packets = mutableListOf<SnoopScanner.Packet>()
    }

    /** Reads every snoop log in [zip], and in a zip inside it. Returns how many were logs. */
    private fun zip(zip: ZipInputStream, frames: Found, depth: Int): Int {
        var logs = 0
        while (true) {
            val entry = zip.nextEntry ?: return logs
            if (entry.isDirectory) continue
            val name = entry.name.substringAfterLast('/').lowercase()
            when {
                // The inner stream must not close the outer one, so it is left open.
                name.endsWith(".zip") && depth == 0 -> logs += zip(ZipInputStream(Unclosable(zip)), frames, depth + 1)
                "btsnoop" in name && name.endsWith(".gz") -> if (scan(GZIPInputStream(Unclosable(zip)), frames)) logs++
                "btsnoop" in name -> if (scan(zip, frames)) logs++
            }
        }
    }

    /** Feeds one file to a fresh [SnoopScanner]. True if it was a snoop log. */
    private fun scan(input: InputStream, found: Found): Boolean {
        val scanner = SnoopScanner()
        val buffer = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            scanner.feed(buffer, 0, n)
            if (scanner.problem != null) break
        }
        found.frames += scanner.frames()
        found.packets += scanner.packets()
        return scanner.isSnoopLog
    }

    private class Unclosable(input: InputStream) : java.io.FilterInputStream(input) {
        override fun close() = Unit
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = prefix.indices.all { this[it] == prefix[it] }

    private const val HEAD = 8
    private const val BUFFER = 64 * 1024
    private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val SNOOP = "btsnoop".encodeToByteArray() + 0
}
