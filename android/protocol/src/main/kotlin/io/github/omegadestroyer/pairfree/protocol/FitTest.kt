package io.github.omegadestroyer.pairfree.protocol

/**
 * A session whose earbuds have an ear tip fit test (docs/DESIGN.md, Device): the earbuds run the test themselves, and the result lands in
 * [EarbudState.fit]. The shared fit test screen and the connection need nothing else, so a brand offers the test by implementing this and setting
 * [EarbudState.fitTest] for the models that have it. SOUNDPEATS' Qualcomm models are the exception: their test is a GAIA feature and noise control
 * goes off around it, which the connection does itself.
 */
public interface FitTest {
    /** What the screens show now; the connection publishes it after the start frames go out. */
    public val state: EarbudState

    /** The frames that start the test, or null when these earbuds do not have one. Clears the last result. */
    public fun startFitTest(): List<ByteArray>?
}

/**
 * The result of an ear tip fit test (docs/soundpeats/PROTOCOL.md section 11): one byte per earbud in a
 * notification on the GAIA fit feature. The H3 sends `01 01` with both earbuds in the ears
 * (PeatsAudio: a good fit), `02 02` with both on a desk ("Not fit"), and `01 02` with only
 * the left one in.
 */
public data class FitResult(val left: Fit, val right: Fit) {
    public enum class Fit { GOOD, NOT_FIT, UNKNOWN }

    public companion object {
        /** The result in [frame], or null if it is not a fit result. */
        public fun of(frame: Frame): FitResult? {
            if (frame.vendor != Gaia.VENDOR || frame.command != Gaia.FIT_RESULT || frame.payload.size < 2) return null
            return FitResult(fit(frame.payload[0]), fit(frame.payload[1]))
        }

        private fun fit(b: Byte): Fit = when (Gaia.FIT_RESULTS[b.toInt() and 0xFF]) {
            "good" -> Fit.GOOD
            "not_fit" -> Fit.NOT_FIT
            else -> Fit.UNKNOWN
        }
    }
}

/**
 * The features in a reply to [Requests.gaiaFeatures]: a "more to come" byte, then a feature
 * id and version per pair. Null if [frame] is not that reply.
 */
internal fun gaiaFeaturesOf(frame: Frame): Set<Int>? {
    if (frame.vendor != Gaia.VENDOR || frame.command != Gaia.GET_FEATURES + Gaia.RESPONSE || frame.payload.isEmpty()) return null
    val p = frame.payload
    return (1 until p.size - 1 step 2).map { p[it].toInt() and 0xFF }.toSet()
}
