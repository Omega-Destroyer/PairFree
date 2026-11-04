package io.github.omegadestroyer.pairfree.protocol.hearing

import kotlin.math.abs
import kotlin.math.roundToInt

/** One tone of a hearing test: its frequency, its level in dB below full scale, and the EQ gain it stands for. */
public data class HearingTone(val hz: Float, val levelDb: Float, val gainDb: Float)

/**
 * A brand's hearing test (data/<brand>/hearing_test.json): per frequency the same number of levels from loud to quiet,
 * in [tones] one frequency after another; the level index each frequency starts at; every band's Q; each tone's length.
 */
public data class HearingTable(val tones: List<HearingTone>, val start: Int, val q: Float, val seconds: Int) {
    public val frequencies: List<Float> get() = tones.map { it.hz }.distinct()
    public val levels: Int get() = tones.size / frequencies.size
}

/** The hearing tests PairFree runs, by brand. */
public object HearingTables {
    public val SOUNDPEATS: HearingTable get() = SOUNDPEATS_HEARING
}

/**
 * SOUNDPEATS' hearing test (AdaptiveEqTestViewModel.processTestResult; docs/soundpeats/PROTOCOL.md section 13): each
 * frequency starts at the table's start level. Heard there, it steps quieter until a tone is missed (the result is the
 * last one heard) or the quietest is heard; missed there, it steps louder until one is heard, or the loudest stands. A
 * tone not answered before it ends counts as missed.
 */
public class StaircaseTest(public val table: HearingTable) {
    private val per = table.levels
    private val count = table.frequencies.size
    private var band = 0
    private var index = table.start
    private var heardFirst = false

    /** The quietest level heard at each frequency done so far, as its tone. */
    public val results: List<HearingTone> get() = _results
    private val _results = mutableListOf<HearingTone>()

    /** The tone to play now, or null once the test is over. */
    public val current: HearingTone? get() = if (finished) null else table.tones[index]

    public val finished: Boolean get() = band >= count

    /** How far the test is, from 0 to 1, by frequency. */
    public val progress: Float get() = band.toFloat() / count

    /** The user's answer to [current]: heard it, or it ended unheard. */
    public fun answer(heard: Boolean) {
        if (finished) return
        if (index % per == table.start && !heardFirst) heardFirst = heard
        if (heardFirst) {
            when {
                !heard -> done(index - 1)
                (index + 1) % per == 0 -> done(index)
                else -> index++
            }
        } else {
            when {
                heard -> done(index)
                (index - 1) % per == 0 -> done(index - 1)
                else -> index--
            }
        }
    }

    private fun done(at: Int) {
        _results += table.tones[at]
        band++
        heardFirst = false
        index = band * per + table.start
    }
}

/** A hearing test's result as an EQ on a model's own bands. */
public object HearingEq {
    /**
     * SOUNDPEATS' merge onto a model's bands, a port of PeatsAudio's `mergeQualcommEQ` (docs/soundpeats/PROTOCOL.md section
     * 13), quirks kept because users' recordings of the C30 match them: each result finds its band by a binary search
     * that only weighs the band it stops on against the next one up (so 500 Hz can land on 800 Hz rather than 300); where
     * several results land on one band, only the first one's gain is added, and the average of their gains is written
     * into the result at that band's index. Whole dB within [rangeDb].
     */
    public fun onBands(results: List<HearingTone>, bandsHz: List<Int>, rangeDb: Int): List<Int> {
        if (bandsHz.isEmpty()) return emptyList()
        val order = bandsHz.indices.sortedBy { bandsHz[it] }
        val a = order.map { bandsHz[it].toFloat() }
        val hz = results.map { it.hz.toInt() }
        val gain = results.map { it.gainDb.toDouble() }.toMutableList()
        val at = hz.map { f -> closest(a, f) }
        for ((band, points) in at.indices.groupBy { at[it] }) {
            if (points.size > 1 && band < gain.size) gain[band] = points.map { gain[it] }.average()
        }
        val gains = DoubleArray(bandsHz.size)
        var last = -1
        for (i in at.indices) {
            if (at[i] != last) gains[order[at[i]]] += gain[i]
            last = at[i]
        }
        return gains.map { it.roundToInt().coerceIn(-rangeDb, rangeDb) }
    }

    /** PeatsAudio's `findClosestElements` for one frequency: an index into the sorted band frequencies [a]. */
    private fun closest(a: List<Float>, f: Int): Int {
        var lo = 0
        var hi = a.size - 1
        var i = -1
        while (lo <= hi) {
            i = (lo + hi) / 2
            if (a[i] == f.toFloat()) break
            if (a[i] < f) lo = i + 1 else hi = i - 1
        }
        if (i == -1) i = 0
        i = i.coerceIn(0, a.size - 1)
        if (i < a.size - 1 && abs(f - a[i + 1]) < abs(f - a[i])) i++
        return i
    }
}
