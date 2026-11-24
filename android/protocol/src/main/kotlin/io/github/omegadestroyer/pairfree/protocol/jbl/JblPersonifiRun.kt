package io.github.omegadestroyer.pairfree.protocol.jbl

import kotlin.math.abs

/**
 * One run of PersoniFi's hearing test, as the wizard of JBL Headphones 5.37.10 goes through it (`sk/q.java`, docs/jbl/PROTOCOL.md section 4.14): nine tones for each ear,
 * the left ear first, each held to as long as the user hears it ([JblPersonifi.levelDb] turns the seconds into a level). After the 500 Hz, 2 kHz and 4 kHz tones of an ear the
 * wizard puts a 750 Hz, 1.5 kHz or 3 kHz tone after it when the result is more than 20 dB from the 1 kHz, 1 kHz and 2 kHz result (`y1`). [openFit] drops the 250 Hz tone from
 * what goes to the library, as the app does on its open-fit models.
 */
public class JblPersonifiRun(private val openFit: Boolean = false) {
    /** One tone: the ear, its frequency in Hz and, once heard, the level in dB. */
    public class Tone(public val left: Boolean, public val hz: Int) {
        public var level: Float? = null
            internal set
    }

    private val ears = listOf(true, false).associateWith { left -> JblPersonifi.TEST_FREQS.map { Tone(left, it) }.toMutableList() }

    /** Every tone of the run in the order it is played, with the extra ones added so far. */
    public val tones: List<Tone> get() = ears.getValue(true) + ears.getValue(false)

    private var at = 0

    /** The tone to play now, or null when the test is over. */
    public val current: Tone? get() = tones.getOrNull(at)

    public val finished: Boolean get() = at >= tones.size

    /** How far the run is, from 0 to 1. */
    public val progress: Float get() = if (tones.isEmpty()) 1f else at.toFloat() / tones.size

    /** The user let go of the tone [seconds] after it started (`m2`): its level is the scale's for that second, and the extra tone is added when it is due. */
    public fun heard(seconds: Long) {
        val tone = current ?: return
        val level = JblPersonifi.levelDb(seconds).toFloat()
        tone.level = level
        extra(tone, level)
        at++
    }

    /** Goes back to the tone before, to be played again (the wizard's back button). */
    public fun back() {
        if (at == 0) return
        at--
        // An extra tone the earlier one added goes with the answer being redone.
        val t = tones[at]
        t.level = null
        val ear = ears.getValue(t.left)
        val extraHz = EXTRA[t.hz]
        if (extraHz != null) ear.removeAll { it.hz == extraHz }
    }

    private fun extra(tone: Tone, level: Float) {
        val ear = ears.getValue(tone.left)
        val (extraHz, against) = when (tone.hz) {
            500 -> 750 to 1000
            2000 -> 1500 to 1000
            4000 -> 3000 to 2000
            else -> return
        }
        val reference = ear.firstOrNull { it.hz == against }?.level ?: return
        if (abs(reference - level) > EXTRA_DB) ear.add(ear.indexOfFirst { it.hz == tone.hz } + 1, Tone(tone.left, extraHz))
    }

    /** The curve of the heard levels, left and right by frequency. Null while the test is not over. */
    public fun curve(): JblPersonifi.Curve? {
        if (!finished) return null
        fun levels(left: Boolean) = ears.getValue(left).filter { it.level != null }.associate { it.hz to it.level!! }
        return JblPersonifi.curve(levels(true), levels(false), openFit)
    }

    private companion object {
        const val EXTRA_DB = 20f
        val EXTRA = mapOf(500 to 750, 2000 to 1500, 4000 to 3000)
    }
}
