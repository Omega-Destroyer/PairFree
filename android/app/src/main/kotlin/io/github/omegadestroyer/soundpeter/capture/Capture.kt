package io.github.omegadestroyer.soundpeter.capture

import io.github.omegadestroyer.soundpeter.protocol.EqCapture

/**
 * The EQ capture screen: recording what PeatsAudio sends, so a model the maintainer does not
 * own can get its equalizer (docs/DESIGN.md, EQ capture).
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
    }
}
