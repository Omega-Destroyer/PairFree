package io.github.omegadestroyer.pairfree.protocol.soundcore

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The step the Soundcore app offers after HearID 3 (docs/soundcore/PROTOCOL.md, HearID), from the Soundcore app 5.0.35
 * (`A3953HearIdPreferenceM`): six rounds in which the listener compares two variants of the test's result while music
 * plays, A or B or no difference. The candidates start from twelve fixed offsets and move towards what was preferred;
 * the last choice and the average of all of them make the final offset. The app plays a clip of its own; PairFree leaves
 * the music to the listener.
 */
public class SoundcoreHearingPreference(private val test: SoundcoreHearingResult, private val random: Random = Random.Default) {
    private val candidates = Array(START.size) { START[it].copyOf() }
    private val speeds = Array(START.size) { DoubleArray(BANDS) }
    private val chosen = Array(ROUNDS) { DoubleArray(BANDS) }
    private var noDifference = 0
    private var randomLeft = 0
    private var a = candidates[2].copyOf()
    private var b = candidates[1].copyOf()
    private var offset: DoubleArray? = null

    /** The round, 1 to [ROUNDS]. */
    public var round: Int = 1
        private set
    public val finished: Boolean get() = offset != null

    /** The two variants of this round. */
    public fun a(): SoundcoreHearingResult = with(a)
    public fun b(): SoundcoreHearingResult = with(b)

    /** The listener's answer: true for A, false for B, null for no difference. */
    public fun choose(preferA: Boolean?) {
        if (finished) return
        val picked = when (preferA) {
            true -> a
            false -> b
            null -> DoubleArray(BANDS).also { noDifference++ }
        }
        picked.copyInto(chosen[round - 1])
        if (round >= ROUNDS) {
            offset = last(chosen[round - 1])
            return
        }
        if (preferA != null) move()
        randomLeft = random.nextInt(START.size)
        round++
        a = candidates[if (round == 2) 8 else randomLeft].copyOf()
        b = candidates[if (round == 2) 9 else unlike(if (round == 2) 8 else randomLeft)].copyOf()
    }

    /** The test's result with what the six rounds settled on, or null before they are done. */
    public fun result(): SoundcoreHearingResult? = offset?.let(::with)

    /** The test's result with [change] (in dB) added, kept within 6 dB of flat (`addTwoEqData`). */
    private fun with(change: DoubleArray): SoundcoreHearingResult {
        fun add(ear: IntArray) = IntArray(BANDS) { band ->
            floor(ear.getOrElse(band) { 0 } + change[band] * 10 + 0.5).toInt().coerceIn(-LIMIT, LIMIT)
        }
        return SoundcoreHearingResult(add(test.left), add(test.right))
    }

    /** The mean of the choices so far, counting only those that preferred one. */
    private fun mean(rounds: Int): DoubleArray {
        val counted = rounds - noDifference
        return DoubleArray(BANDS) { band -> if (counted == 0) 0.0 else (0 until rounds).sumOf { chosen[it][band] } / counted }
    }

    /** Moves some of the candidates towards this round's choice and the mean (`updateDataSource`). */
    private fun move() {
        val best = mean(round)
        val count = minOf(round + 5, START.size - 1)
        val rows = if (round == 1) (0 until count).toList() else START.indices.shuffled(random).take(count)
        for (row in rows) {
            val r1 = random.nextDouble()
            val r2 = random.nextDouble()
            for (band in 0 until BANDS) {
                val x = candidates[row][band]
                val v = 0.3 * speeds[row][band] + 0.3 * r1 * (chosen[round - 1][band] - x) + 0.3 * r2 * (best[band] - x)
                speeds[row][band] = v
                candidates[row][band] = bounded(x + v)
            }
        }
    }

    private fun bounded(x: Double): Double = when {
        x > 6.0 -> 3.0 + random.nextDouble(3.0)
        x < -6.0 -> -6.0 + random.nextDouble(3.0)
        else -> x
    }

    /** The first candidate unlike enough to [left]'s, else the least alike (`getRightData`). */
    private fun unlike(left: Int): Int {
        var least = 1.0
        var at = 0
        for (i in candidates.indices) {
            val similarity = similarity(candidates[left], candidates[i])
            if (similarity <= THRESHOLDS[round - 1]) return i
            if (similarity < least) {
                least = similarity
                at = i
            }
        }
        return at
    }

    private fun similarity(x: DoubleArray, y: DoubleArray): Double {
        val xx = sqrt(x.sumOf { it * it })
        val yy = sqrt(y.sumOf { it * it })
        return if (xx == 0.0 || yy == 0.0) 1.0 else x.indices.sumOf { x[it] * y[it] } / (xx * yy)
    }

    /** 0.4 of the last choice and 0.8 of the mean, scaled up to 2.5 dB where smaller, then 0.5 dB up (`calculateResult`). */
    private fun last(lastChoice: DoubleArray): DoubleArray {
        val best = mean(ROUNDS)
        val out = DoubleArray(BANDS) { lastChoice[it] * 0.4 + best[it] * 0.8 }
        val largest = out.maxOf { abs(it) }
        return DoubleArray(BANDS) { (if (largest < 2.5 && largest != 0.0) out[it] * 2.5 / largest else out[it]) + 0.5 }
    }

    public companion object {
        public const val ROUNDS: Int = 6
        private const val BANDS = 8
        private const val LIMIT = 60
        private val THRESHOLDS = doubleArrayOf(0.354, 0.4, 0.45, 0.5, 0.55, 0.6)
        private val START = arrayOf(
            doubleArrayOf(4.0, 3.0, 0.0, 0.0, -1.5, -2.5, -2.5, -2.5),
            doubleArrayOf(-1.0, -1.0, 0.0, 0.0, 1.3, 1.6, 1.3, 1.5),
            doubleArrayOf(2.4, 1.8, 0.0, 0.0, -1.0, -1.0, -1.5, -1.5),
            doubleArrayOf(-2.0, -2.0, 0.0, 1.0, 1.5, 2.0, 2.0, 2.0),
            doubleArrayOf(3.0, 3.0, 0.0, 0.0, -1.0, -2.0, -2.0, -2.0),
            doubleArrayOf(-3.0, -3.0, -1.0, 0.0, 2.0, 3.0, 4.0, 4.0),
            doubleArrayOf(0.0, 0.0, 2.0, 2.0, 1.0, 0.0, -1.0, -1.0),
            doubleArrayOf(3.0, 2.0, -3.0, -3.0, -1.0, 0.0, 1.0, 2.0),
            doubleArrayOf(-1.0, 1.0, 3.0, 3.0, 1.0, -1.0, -2.0, -3.0),
            doubleArrayOf(2.0, 1.0, -2.0, -1.5, 0.0, 1.5, 2.0, 2.5),
            doubleArrayOf(4.0, 0.0, -3.0, -3.0, 0.0, 0.0, 0.0, 1.0),
            doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, -0.1, -0.2),
        )
    }
}
