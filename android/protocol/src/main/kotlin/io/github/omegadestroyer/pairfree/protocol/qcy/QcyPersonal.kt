package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * QCY's personalised listening test (docs/qcy/PROTOCOL.md section 8; read from QCY's app 4.0.7, `CustomEQEdit2Activity`, `BeepTool2`, `QCYConnectManager.getMaxGain` and
 * `setSideEQData`): the panel's `individuation_eq` lists the bands to test ([bands], 63 Hz to 8 kHz on the 8 models that have it), each with a reference level in dB
 * full scale ([QcyPersonal.Band.db]). A tone at a band is played to one earbud, louder or quieter until the user just hears it, and the gain that took is that band's gain
 * for that ear. The two curves go to the earbuds as the left and right parametric EQ (items 46 and 47).
 */
public data class QcyPersonal(val bands: List<Band>, val volume: Double, val base: List<QcyParamPreset.Band>) {
    /** One band of the test: [startGain] is the gain the test starts at; [db] the tone's level at gain 0 in dB full scale. */
    public data class Band(val hz: Int, val q: Double, val type: Int, val startGain: Double, val db: Double)

    public companion object {
        private const val ID = 11
        private const val RATE = 44100.0
        private const val LAST_BASE = -10.0

        /** Reads the generated form: `bands|db|volume|base`, bands and base as "hertz/gain/Q/type" joined by spaces, the levels as numbers joined by commas. Null for none. */
        public fun parse(data: String): QcyPersonal? {
            if (data.isEmpty()) return null
            val f = data.split('|')
            val levels = f[1].split(',').map(String::toDouble)
            val bands = f[0].split(' ').mapIndexed { i, b -> b.split('/').let { Band(it[0].toInt(), it[2].toDouble(), it[3].toInt(), it[1].toDouble(), levels[i]) } }
            val base = f[3].split(' ').map { b -> b.split('/').let { QcyParamPreset.Band(it[0].toInt(), it[1].toDouble(), it[2].toDouble(), it[3].toInt()) } }
            return QcyPersonal(bands, f[2].toDouble(), base)
        }

        /** The earbuds' test mode (item 45): on while the test runs, off when the screen closes, as QCY's app does. */
        public fun testMode(on: Boolean): ByteArray = GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.of(QcyItem.CUSTOM_EQ_TEST, if (on) 1 else 0))
    }

    /** The test's two curves as the messages that put them on the earbuds: [left] and [right] hold a gain for each of [bands]. */
    public fun frames(left: List<Int>, right: List<Int>): List<ByteArray> = listOf(side(QcyItem.MULTI_EQ_LEFT, left), side(QcyItem.MULTI_EQ_RIGHT, right))

    private fun side(item: Int, gains: List<Int>): ByteArray {
        val tested = bands.mapIndexed { i, b -> QcyParamPreset.Band(b.hz, (gains.getOrNull(i) ?: 0).toDouble(), b.q, b.type) }
        val master = maxGain(base + tested)
        val out = QcyParametric(item, ID, (master * 100).toInt(), tested.map { QcyParametric.Band(it.hz, (it.gainDb * 100).roundToInt(), (it.q * 100).roundToInt(), it.type) })
        return GattEnvelope.write(QcyWire.COMMAND_SHORT, QcyPacket.build(QcyEntry(item, out.bytes())))
    }

    /**
     * The master gain, as `QCYConnectManager.getMaxGain` works it out: the bands' peaking filters (RBJ, at 44.1 kHz) added up in dB from 20 Hz to 20 kHz in steps of 5 Hz,
     * the highest point plus half a dB against the panel's `volumelevel` (and the last master gain, which is -10 when none is known).
     */
    internal fun maxGain(all: List<QcyParamPreset.Band>, lastBase: Double = LAST_BASE): Double {
        val sum = DoubleArray((20000 - 20) / 5 + 1)
        for (b in all) {
            if (b.hz <= 0) continue
            val w0 = 2 * PI * b.hz / RATE
            val a = 10.0.pow(b.gainDb / 40.0)
            val alpha = sin(w0) / (2.0 * b.q)
            val b0 = 1 + alpha * a
            val b1 = -2 * cos(w0)
            val b2 = 1 - alpha * a
            val a0 = 1 + alpha / a
            val a2 = 1 - alpha / a
            for (k in sum.indices) {
                val f = 20 + k * 5
                val w = ((f * 20000.0 + 1) * PI) / (RATE * 10000.0)
                val c = cos(w)
                val s = -sin(w)
                // z^-1 = (c, s); z^-2 = (c2, s2)
                val c2 = c * c - s * s
                val s2 = 2 * c * s
                val nr = b0 + b1 * c + b2 * c2
                val ni = b1 * s + b2 * s2
                val dr = a0 + b1 * c + a2 * c2
                val di = b1 * s + a2 * s2
                sum[k] += 20 * log10(sqrt((nr * nr + ni * ni) / (dr * dr + di * di)))
            }
        }
        var top = 0.0
        var at = 0
        for (k in sum.indices) if (top < sum[k]) {
            top = sum[k]
            at = k * 5
        }
        val d3 = top + 0.5
        val vl = if (d3 > volume && at < 1500) volume else if (d3 <= volume + 4.0 || at < 1500) return lastBase else volume + 4.0
        return lastBase + (vl - d3)
    }

}

/**
 * One run of the test: the bands in turn, left earbud first, then the right. [heard] and [notHeard] are the user's two buttons; the tone to play is [toneHz] at [toneDb] in
 * [ear]. The rules are those of QCY's app (`CustomEQEdit2Activity`): a gain moves a whole step at a time between -8 and 8, two at a time below 500 Hz on the quiet side; a band
 * is settled once the user hears it twice at a gain low enough, hears it again at the same gain, or fails to hear it twice at 6 or more.
 */
public class QcyPersonalTest(private val spec: QcyPersonal) {
    public val left: IntArray = IntArray(spec.bands.size) { spec.bands[it].startGain.toInt() }
    public val right: IntArray = IntArray(spec.bands.size) { spec.bands[it].startGain.toInt() }

    /** 0 for the left earbud, 1 for the right. */
    public var ear: Int = 0
        private set
    public var band: Int = 0
        private set
    public var finished: Boolean = false
        private set

    private var endCount = 0
    private var lastHeard: Int? = null

    private val gains get() = if (ear == 0) left else right
    public val gain: Int get() = gains[band]
    public val toneHz: Int get() = spec.bands[band].hz

    /** The level of the tone now in dB full scale (`getResultDB`): the band's reference level, 5 dB for each step, 2.5 dB below 500 Hz on the quiet side. */
    public val toneDb: Double
        get() {
            val ref = spec.bands[band].db
            val g = gain
            return when {
                g > 0 -> ref + g * 5
                g < 0 -> if (toneHz <= 500) ref + (g / 2) * 5 else ref + g * 5
                else -> ref
            }
        }

    /** How many bands of both ears are settled, out of [total]. */
    public val done: Int get() = if (finished) total else ear * spec.bands.size + band
    public val total: Int get() = spec.bands.size * 2

    /** The user hears the tone. */
    public fun heard() {
        if (finished) return
        val g = gain
        val f = toneHz
        if ((f <= 500 && g <= -4) || (f > 500 && g <= -2) || lastHeard == g) {
            endCount++
            if (endCount >= 2 || lastHeard == g) settle()
            return
        }
        lastHeard = g
        var next = (g - 1).coerceIn(MIN, MAX)
        if (f <= 500 && g <= 0) next = (next - 1).coerceIn(MIN, MAX)
        gains[band] = next
    }

    /** The user does not hear the tone. */
    public fun notHeard() {
        if (finished) return
        val g = gain
        if (g >= 6) {
            endCount++
            if (endCount >= 2) settle()
            return
        }
        var next = (g + 1).coerceIn(MIN, MAX)
        if (toneHz <= 500 && g < 0) next = (next + 1).coerceIn(MIN, MAX)
        gains[band] = next
    }

    private fun settle() {
        endCount = 0
        lastHeard = null
        if (band < spec.bands.size - 1) {
            band++
        } else if (ear == 0) {
            ear = 1
            band = 0
        } else {
            finished = true
        }
    }

    /** The messages that put the finished test's two curves on the earbuds. */
    public fun frames(): List<ByteArray> = spec.frames(left.toList(), right.toList())

    private companion object {
        const val MIN = -8
        const val MAX = 8
    }
}
