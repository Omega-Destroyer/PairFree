package io.github.omegadestroyer.pairfree.capture

import io.github.omegadestroyer.pairfree.protocol.SnoopScanner

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
