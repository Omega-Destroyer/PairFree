package io.github.omegadestroyer.pairfree.protocol.oneplus

/**
 * HeyMelody's hearing enhancement test ("Golden Sound", docs/oneplus/PROTOCOL.md section 12), a port of what the app does (HeyMelody
 * `HearingEnhancementDetectingFragmentV2` and `DetectCompleteFragment`, read 2026-11-11). The earbuds play the tones: 12 points, the left earbud's six
 * frequency types then the right one's, each a slider the user moves to the quietest level they still hear. The slider's 12 positions are the dB values in
 * [LEVELS_DB], starting at position 5 (-19 dB). Every move sends that point's tone; Next goes to the following point; at the end the points go to the
 * earbuds as a saved enhancement and the enhancement switch (feature 11) is turned on. The earbuds work out the enhancement themselves.
 *
 * Not done (the app does them, PairFree does not): the optional ear scan that comes first on models with `earScan`, the audition (mode 1 and 2), and the
 * restore data (`0411`) that only records which of 20 wordings the app showed.
 */
public class OneplusHearingTest(private val frame: (command: Int, payload: IntArray) -> ByteArray, private val uid: Int, private val name: String) {
    /** One point of the test: the [left] or right earbud and a frequency type from 1 (low) to 6 (high). */
    public data class Point(val left: Boolean, val type: Int)

    public val points: List<Point> = (1..TYPES).map { Point(true, it) } + (1..TYPES).map { Point(false, it) }

    private val levels = IntArray(points.size) { START_LEVEL }
    private var index = 0

    /** The point being tested, or null once all are done. */
    public val current: Point? get() = points.getOrNull(index)

    public val finished: Boolean get() = index >= points.size

    /** How far the test is, from 0 to 1. */
    public val progress: Float get() = index.toFloat() / points.size

    /** The slider's position (0 to 11) for the point being tested. */
    public val level: Int get() = levels.getOrElse(index) { START_LEVEL }

    /** The dB values the user has chosen so far, one per point (the slider's start for the points not reached). */
    public val dbs: List<Int> get() = levels.map { LEVELS_DB[it] }

    /** Moves the slider to [position] (0 to 11) and returns the tone for it. */
    public fun setLevel(position: Int): List<ByteArray> {
        if (finished) return emptyList()
        levels[index] = position.coerceIn(0, LEVELS_DB.size - 1)
        return tone()
    }

    /** The tone for the current point at the slider's level: command `040E` with 3, 1 and the point's three bytes. */
    public fun tone(): List<ByteArray> {
        val p = current ?: return emptyList()
        return listOf(frame(DETECT, intArrayOf(MODE_POINT, 1, device(p), p.type, LEVELS_DB[levels[index]] and 0xFF)))
    }

    /** Opens the test: `040D` with 2 and 1 (detection on). */
    public fun start(): List<ByteArray> = listOf(frame(STATUS, intArrayOf(STATUS_DETECTION, 1)))

    /** Goes to the next point, pausing the tone (`040E` with 4) and returning the first tone of the next point; empty frames when the test is over. */
    public fun next(): List<ByteArray> {
        if (finished) return emptyList()
        val pause = frame(DETECT, intArrayOf(MODE_PAUSE))
        index++
        return listOf(pause) + tone()
    }

    /** Leaves the test without saving: the tone is paused and detection turned off. */
    public fun cancel(): List<ByteArray> = listOf(frame(DETECT, intArrayOf(MODE_PAUSE)), frame(STATUS, intArrayOf(STATUS_DETECTION, 0)))

    /**
     * The end: the tone paused, the 12 points saved (`040E` with 3, the count, each point's three bytes, the id as a big-endian int and the name), detection
     * turned off and the enhancement switched on (`0403` with feature 11).
     */
    public fun save(): List<ByteArray> {
        val data = mutableListOf(MODE_POINT, points.size)
        points.forEachIndexed { i, p ->
            data += device(p)
            data += p.type
            data += LEVELS_DB[levels[i]] and 0xFF
        }
        for (shift in intArrayOf(24, 16, 8, 0)) data += (uid ushr shift) and 0xFF
        data += name.toByteArray().map { it.toInt() and 0xFF }
        return listOf(
            frame(DETECT, intArrayOf(MODE_PAUSE)),
            frame(DETECT, data.toIntArray()),
            frame(STATUS, intArrayOf(STATUS_DETECTION, 0)),
            frame(OneplusCommand.SET_SWITCH, intArrayOf(ENHANCEMENT_FEATURE, 1)),
        )
    }

    private fun device(p: Point): Int = if (p.left) 1 else 2

    public companion object {
        /** The slider's 12 positions in dB (HeyMelody's `C0990e.f30021a`). */
        public val LEVELS_DB: List<Int> = listOf(-55, -49, -41, -35, -25, -19, -11, -5, 3, 7, 13, 17)
        public const val START_LEVEL: Int = 5
        public const val TYPES: Int = 6
        public const val ENHANCEMENT_FEATURE: Int = 11

        private const val DETECT = OneplusCommand.SET_HEARING_DETECT
        private const val STATUS = OneplusCommand.SET_HEARING_STATUS
        private const val MODE_POINT = 3
        private const val MODE_PAUSE = 4
        private const val STATUS_DETECTION = 2
    }
}
