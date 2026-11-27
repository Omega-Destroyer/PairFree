package io.github.omegadestroyer.pairfree.ui

/**
 * Plays the steady tones the hearing tests, the fit tone and the seal check use: a sine at a level below full scale, in both ears or one.
 * Each system plays them its own way (Windows through waveOut).
 */
interface TonePlayer {
    /**
     * Plays [hz] at [levelDb] below full scale for [seconds] on [side] ([BOTH], [LEFT] or [RIGHT]). With [loop] the tone repeats until [stop],
     * so a low tone is not missed in one burst. [onEnd] runs once a tone that does not loop has played out, on the window's thread.
     */
    fun play(hz: Float, levelDb: Float, seconds: Int, side: Int, loop: Boolean, onEnd: () -> Unit = {})

    /** Stops what is playing. */
    fun stop()

    /** A test is starting: the tones' levels mean something only at a known volume, so the system's goes to the top until [end]. */
    fun begin() = Unit

    /** A test is over: stops the tone and puts the volume back. */
    fun end() = stop()

    /** True while another program plays sound, which would sound at full volume during a test. */
    val musicActive: Boolean

    companion object {
        const val BOTH = 0
        const val LEFT = 1
        const val RIGHT = 2
    }
}
