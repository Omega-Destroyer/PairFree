package io.github.omegadestroyer.pairfree.protocol.hearing

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * PairFree's own A/B sound finder (not a vendor feature): the user listens to their own music while the earbuds switch between two curves and says which
 * one they prefer; the best one so far stays and a variation of it is tried next. It is a plain pairwise-preference search (the idea behind the dueling
 * bandit tuners some vendors ship, Soundcore's `libhid_app` among them, but none of their tables or code), so it needs nothing from any vendor and works
 * on any earbuds that take a custom curve on any number of bands ([bandsHz]).
 *
 * The search moves along three broad shapes of the curve, in this order: bass (below about 1 kHz), treble (above about 2 kHz) and presence (around
 * 2.5 kHz, where voices sit). For each it tries a step up from the current best curve and, when that loses, a step down; then the whole round is repeated
 * with a half-size step. That is at most [MAX_TRIALS] comparisons. Steps that would not change any band (the range is reached) are skipped.
 */
public class EqFinder(private val bandsHz: List<Int>, private val rangeDb: Int, start: List<Int>) {
    private val origin: List<Int> = start.mapIndexed { i, g -> g.coerceIn(-rangeDb, rangeDb) }.let { if (it.size == bandsHz.size) it else List(bandsHz.size) { 0 } }
    private val position = DoubleArray(SHAPES.size)
    private var stage = 0
    private var axis = 0
    private var down = false
    private var trials = 0

    /** The curve that is best so far (A). */
    public var best: List<Int> = origin
        private set

    /** The curve to compare with it (B), or null once the search is over. */
    public var candidate: List<Int>? = null
        private set

    public val finished: Boolean get() = candidate == null

    /** How far the search is, from 0 to 1. */
    public val progress: Float get() = if (finished) 1f else (trials.toFloat() / MAX_TRIALS).coerceAtMost(0.99f)

    /** How many comparisons have been answered. */
    public val answered: Int get() = trials

    init {
        next()
    }

    /** The user's answer for the current pair: B is preferred ([preferCandidate] true) or A stays (also when they cannot tell). */
    public fun choose(preferCandidate: Boolean) {
        val b = candidate ?: return
        trials++
        settle(preferCandidate, b)
        next()
    }

    private fun settle(won: Boolean, b: List<Int>) {
        if (won) {
            best = b
            position[axis] += (if (down) -1 else 1) * stepDb()
        }
        // A won the step up: try the step down; any other outcome moves to the next shape.
        if (!won && !down) {
            down = true
        } else {
            down = false
            axis++
            if (axis == SHAPES.size) {
                axis = 0
                stage++
            }
        }
    }

    private fun next() {
        while (stage < STEPS.size) {
            val b = curveWith(axis, (if (down) -1 else 1) * stepDb())
            if (b != best) {
                candidate = b
                return
            }
            settle(false, b) // no audible change: treated as lost, without asking
        }
        candidate = null
    }

    private fun stepDb(): Double = (rangeDb * STEPS[stage]).coerceAtLeast(1.0)

    private fun curveWith(shape: Int, deltaDb: Double): List<Int> {
        val moved = position.copyOf().also { it[shape] += deltaDb }
        return bandsHz.indices.map { i ->
            val db = origin[i] + SHAPES.indices.sumOf { moved[it] * SHAPES[it](bandsHz[i]) }
            db.roundToInt().coerceIn(-rangeDb, rangeDb)
        }
    }

    public companion object {
        /** The size of the step in each round, as a share of the gain range. */
        private val STEPS = doubleArrayOf(0.4, 0.2)
        public const val MAX_TRIALS: Int = 6 * 2

        private fun lnHz(hz: Int) = ln(hz.coerceAtLeast(1).toDouble())

        private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)

        private val SHAPES: List<(Int) -> Double> = listOf(
            { hz -> clamp01((lnHz(1000) - lnHz(hz)) / (lnHz(1000) - lnHz(100))) },
            { hz -> clamp01((lnHz(hz) - lnHz(2000)) / (lnHz(8000) - lnHz(2000))) },
            { hz -> exp(-0.5 * ((lnHz(hz) - lnHz(2500)) / 0.5) * ((lnHz(hz) - lnHz(2500)) / 0.5)) },
        )
    }
}
