package io.github.omegadestroyer.pairfree.protocol.hearing

/**
 * An ear tip seal check with tones the phone plays, for any earbuds or headphones. JBL's app has this check (its "check seal" screens, read from JBL Headphones
 * 5.37.10: a 75 Hz tone in the left ear at the normal level, again 20 dB softer, then the same in the right ear, the user saying whether they hear it; "you need a
 * better seal" when they do not). A low tone is heard well only when the ear tip seals the ear canal, so the answers say how well each earbud seals.
 *
 * Above the vendors' apps (owner, 2026-11-10): PairFree offers it on every brand. JBL's levels come from the phone's volume setting, so PairFree's are its own
 * ([NORMAL_DB], [SOFTER_DB] below full scale at the top media volume); the verdict is also PairFree's reading of the answers.
 */
public class SealCheck {
    /** One tone: in the [left] or right earbud, at the normal level or [softer]. */
    public data class Step(val left: Boolean, val softer: Boolean) {
        public val levelDb: Float get() = if (softer) SOFTER_DB else NORMAL_DB
    }

    /** How well one earbud seals, from what the user heard in it. */
    public enum class Seal { GOOD, FAIR, POOR, UNKNOWN }

    private val steps = listOf(Step(true, false), Step(true, true), Step(false, false), Step(false, true))
    private val heard = BooleanArray(steps.size)
    private var index = 0

    /** The tone to play now, or null once the check is over. */
    public val current: Step? get() = steps.getOrNull(index)

    public val finished: Boolean get() = index >= steps.size

    /** How far the check is, from 0 to 1. */
    public val progress: Float get() = index.toFloat() / steps.size

    /** The user's answer to [current]: they heard the tone, or not. */
    public fun answer(heardIt: Boolean) {
        if (finished) return
        heard[index++] = heardIt
    }

    /** The seal of the left earbud once both its tones were played, else [Seal.UNKNOWN]. */
    public val left: Seal get() = if (index >= 2) seal(heard[0], heard[1]) else Seal.UNKNOWN

    public val right: Seal get() = if (index >= 4) seal(heard[2], heard[3]) else Seal.UNKNOWN

    private fun seal(normal: Boolean, softer: Boolean): Seal = when {
        normal && softer -> Seal.GOOD
        normal -> Seal.FAIR
        else -> Seal.POOR
    }

    public companion object {
        /** JBL's tone, a low one that only a sealed ear tip lets through. */
        public const val HZ: Float = 75f
        public const val SECONDS: Int = 6
        public const val NORMAL_DB: Float = -24f
        public const val SOFTER_DB: Float = -44f
    }
}
