package io.github.omegadestroyer.soundpeter.capture

import io.github.omegadestroyer.soundpeter.protocol.EqCapture
import io.github.omegadestroyer.soundpeter.protocol.FirmwareVersion
import io.github.omegadestroyer.soundpeter.protocol.LoggedReads

/**
 * The recording screen: what PeatsAudio sends and reads, so a model the maintainer does not
 * own can get its controls and equalizer (docs/DESIGN.md, Record from PeatsAudio).
 */
sealed interface Capture {
    data object Closed : Capture

    /** The steps, and a button to pick a bug report. */
    data object Guide : Capture

    data object Reading : Capture

    /** What the file held; [names] has the user's name for each write, null to leave it out. */
    data class Done(val result: CaptureResult, val names: List<String?>) : Capture {
        val writes: List<EqCapture.Write> get() = (result as? CaptureResult.Found)?.writes.orEmpty()

        /** The writes the user named, in order, with their names. */
        val named: List<Pair<String, EqCapture.Write>> get() = writes.indices.mapNotNull { i -> names.getOrNull(i)?.let { it to writes[i] } }

        /** The settings read from each pair of earbuds in the log. */
        val reads: List<LoggedReads.Earbuds> get() = (result as? CaptureResult.Found)?.reads.orEmpty()

        /** The earbuds the report is about: those of the named curves, else the first in the log. */
        val firmware: FirmwareVersion?
            get() = named.firstNotNullOfOrNull { it.second.firmware } ?: writes.firstNotNullOfOrNull { it.firmware }
                ?: reads.firstNotNullOfOrNull { it.firmware }

        /** Something worth sending: a named curve, or settings read from the earbuds. */
        val ready: Boolean get() = named.isNotEmpty() || reads.isNotEmpty()
    }
}
