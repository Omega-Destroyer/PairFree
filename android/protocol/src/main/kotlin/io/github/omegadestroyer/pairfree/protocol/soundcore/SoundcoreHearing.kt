package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * The Soundcore app's HearID hearing test (docs/soundcore/PROTOCOL.md, HearID), from the Soundcore app 5.0.35: the tones and
 * their levels per model, the staircase that finds each tone's threshold (`HearingAlgorithm`), the app's correction of the
 * sixteen results and the write that puts them in the earbuds. Not tried with PairFree.
 *
 * The user's phone plays the tones with media volume at the top, as the app does. The models here are the ones whose
 * screen in that app was read: the Space A40, the Liberty Air 2 Pro, the Liberty 2 Pro+ and the Liberty 3 Pro.
 */
public class SoundcoreHearingSpec private constructor(
    public val model: String,
    /** The tones in the order they are tested, each with the level it starts from. */
    private val tones: List<Tone>,
    /** True for the Liberty 3 Pro's three short beeps; the others play a steady tone. */
    public val pulsed: Boolean,
    /** How many bands each earbud's curve has in the write: 8, or 10 with two that are always sent as their lowest. */
    public val bands: Int,
) {
    private class Tone(val hz: Int, val base: Int, val step: Int)

    public val count: Int get() = tones.size
    public fun hz(test: Int): Int = tones[test].hz
    internal fun base(test: Int): Int = tones[test].base
    internal fun firstStep(test: Int): Int = tones[test].step

    /** The tone's peak level in dB below full scale when the staircase stands at [step] over its starting level. */
    public fun level(test: Int, step: Int): Double {
        val tone = tones[test]
        return if (pulsed) {
            // The app picks a recording made at -12 to +12 dB and plays it at a fixed gain; these are the recordings' own peaks.
            val file = when (tone.hz) {
                100 -> -44.7
                12800 -> -46.7
                else -> -49.8
            }
            file + tone.base + step.coerceIn(-12, 12)
        } else {
            (tone.base + step).toDouble().coerceAtMost(LOUDEST)
        }
    }

    public companion object {
        /** No tone goes above this, whatever the staircase asks. */
        private const val LOUDEST = -27.0
        private val ORDER = listOf(3200, 1600, 6400, 800, 400, 200, 100, 12800)

        private fun classic(model: String, bands: Int): SoundcoreHearingSpec {
            val base = listOf(-75, -64, -72, -71, -69, -59, -54, -72)
            val step = listOf(6, 0, 6, 0, 3, 6, 6, 6)
            return SoundcoreHearingSpec(model, ORDER.indices.map { Tone(ORDER[it], base[it], step[it]) }, false, bands)
        }

        private fun beeps(model: String): SoundcoreHearingSpec {
            val base = listOf(-27, -19, -25, -22, -22, -17, -14, -25)
            return SoundcoreHearingSpec(model, ORDER.indices.map { Tone(ORDER[it], base[it], 0) }, true, 10)
        }

        public fun of(model: String?): SoundcoreHearingSpec? = when (model) {
            "A3936" -> classic(model, 10)
            "A3930", "A3951" -> classic(model, 8)
            "A3952" -> beeps(model)
            else -> null
        }
    }
}

/** The app's search for one tone's threshold: louder in 3 dB steps while unheard, quieter while heard, then checked again. */
internal class SoundcoreStaircase(private val base: Int, firstStep: Int) {
    private class Answer(var heard: Boolean, var level: Int) {
        var result = 0
    }

    var step: Int = firstStep
        private set
    private val answers = mutableListOf<Answer>()
    private var checks = 0
    private val heardAt = mutableSetOf<Int>()
    private var candidate: Answer? = null

    /** The result, 0 (heard far below the starting level) to 24 (not heard far above it), once the search has ended. */
    var result: Int? = null
        private set

    private fun index(level: Int): Int {
        val below = level < base
        return when (abs(level - base)) {
            3 -> if (below) 9 else 15
            6 -> if (below) 6 else 18
            9 -> if (below) 3 else 21
            12 -> if (below) 0 else 24
            else -> 12
        }
    }

    private fun finish(a: Answer) {
        result = a.result
    }

    fun answer(heard: Boolean) {
        if (result != null) return
        val checked = candidate
        if (checked != null) check(checked, heard) else search(heard)
    }

    private fun search(heard: Boolean) {
        val level = base + step
        val previous = answers.lastOrNull()
        val first = answers.size < 2
        val now = Answer(heard, level)
        answers += now
        if (first) {
            if (heard) heardAt += level
            step += if (heard) -3 else 3
            return
        }
        val far = abs(step)
        if (heard && level in heardAt) {
            now.result = index(level)
            if (far >= 12) finish(now) else candidate = now
            return
        }
        if (far < 12) {
            if (heard) heardAt += level
            step += if (heard) -3 else 3
            return
        }
        if (previous!!.heard) {
            if (!heard) {
                step += 3
                return
            }
            now.result = 0
            finish(now)
            return
        }
        if (heard) {
            heardAt += level
            step -= 3
        } else {
            now.result = 24
            finish(now)
        }
    }

    private fun check(c: Answer, heard: Boolean) {
        when (checks++) {
            0 -> if (!heard) step += 3 else {
                c.result = index(c.level)
                finish(c)
            }
            1 -> if (!heard) step -= 6 else {
                c.level += 3
                c.result = index(c.level)
                finish(c)
            }
            else -> {
                if (!heard) c.level += 3
                c.result = index(c.level)
                finish(c)
            }
        }
    }
}

/** The result of a test: for each earbud, eight values from 100 Hz to 12.8 kHz in the app's scale, where 12 is flat. */
public class SoundcoreHearingResult(public val left: IntArray, public val right: IntArray)

/** One test as it runs: the left earbud's eight tones, then the right's. */
public class SoundcoreHearingRun(public val spec: SoundcoreHearingSpec) {
    private val raw = IntArray(spec.count * 2)

    /** How many tones are done, of [total]. */
    public var done: Int = 0
        private set
    public val total: Int get() = spec.count * 2
    private var staircase = fresh(0)

    public val left: Boolean get() = done < spec.count
    private val test: Int get() = done % spec.count
    public val hz: Int get() = spec.hz(test)
    public val level: Double get() = spec.level(test, staircase.step)
    public val finished: Boolean get() = done >= total

    private fun fresh(test: Int) = SoundcoreStaircase(spec.base(test), spec.firstStep(test))

    public fun answer(heard: Boolean) {
        if (finished) return
        staircase.answer(heard)
        val value = staircase.result ?: return
        val band = (0 until spec.count).count { spec.hz(it) < hz }
        raw[(if (left) 0 else spec.count) + band] = value
        done++
        if (!finished) staircase = fresh(test)
    }

    /** Starts the tone being tested over. */
    public fun repeat() {
        if (!finished) staircase = fresh(test)
    }

    /** The corrected result, once every tone is done. */
    public fun result(): SoundcoreHearingResult? {
        if (!finished) return null
        val values = if (spec.pulsed) unify(raw) else correct(raw)
        return SoundcoreHearingResult(values.copyOfRange(0, spec.count), values.copyOfRange(spec.count, total))
    }

    internal companion object {
        /** `BaseHearId2VM.changeDataByAlgorithm`: both ears moved against the better 1.6 kHz result, then kept in range. */
        fun correct(raw: IntArray): IntArray {
            val n = raw.size / 2
            val reference = minOf(raw[4], raw[n + 4])
            return IntArray(raw.size) { i ->
                val moved = raw[i] - reference + 12
                when (i % n) {
                    0, 1 -> moved.coerceIn(12, 15)
                    2 -> moved.coerceIn(12, 18)
                    4 -> 12
                    else -> moved.coerceIn(6, 18)
                }
            }
        }

        /** The Liberty 3 Pro's: each ear against its own best tone, then squeezed into 12 to 18 along an S curve. */
        fun unify(raw: IntArray): IntArray {
            val n = raw.size / 2
            val best = intArrayOf(raw.copyOfRange(0, n).min(), raw.copyOfRange(n, raw.size).min())
            return IntArray(raw.size) { i ->
                val moved = raw[i] - best[i / n] + 12
                floor(6.0 / (E.pow(15 - moved) + 1.0) + 12.0 + 0.5).toInt()
            }
        }
    }
}

/**
 * HearID as the earbuds hold it: the test's result (stored with the EQ), whether it is on, and the writes that store a new
 * result, switch it and delete it. All three are the model's EQ write with its HearID part filled in (`A3936CmdService.p6`,
 * `A3951DeviceManager.d2`): command `03 87`.
 */
public object SoundcoreHearingId {
    public const val SWITCH: String = "sc_hear_id"
    public const val DELETE: String = "sc_hear_id_delete"

    private class Held(val eq: SoundcoreEqSpec, val layout: SoundcoreHearIdLayout, val config: SoundcoreEqConfig, val data: SoundcoreHearId, val on: Boolean)

    private fun held(model: String, state: SoundcoreState?): Held? {
        if (state == null || SoundcoreHearingSpec.of(model) == null) return null
        val eq = SoundcoreEqSpec.of(model) ?: return null
        val layout = eq.hearId ?: return null
        val config = SoundcoreEqualizer.read(eq, state) ?: return null
        val data = SoundcoreEqualizer.hearId(layout, state) ?: return null
        val on = layout.parts.firstOrNull() == "enabled" && state.field(layout.field)?.firstOrNull() == 1.toByte()
        return Held(eq, layout, config, data, on)
    }

    private fun hasResult(data: SoundcoreHearId) = data.curve.isNotEmpty() && data.curve.all { it != null }

    /** The switch and the delete action, when the earbuds hold a test's result. */
    public fun rows(model: String, state: SoundcoreState?): List<Setting> {
        val h = held(model, state) ?: return emptyList()
        if (!hasResult(h.data)) return emptyList()
        return listOf(Setting.Toggle(SWITCH, h.on), Setting.Action(DELETE))
    }

    public fun change(model: String, state: SoundcoreState?, change: SettingChange): SoundcorePacket? {
        val h = held(model, state) ?: return null
        if (!hasResult(h.data)) return null
        return when {
            change is SettingChange.Toggle && change.key == SWITCH -> switched(h, state!!, change.on)
            change is SettingChange.Press && change.key == DELETE -> deleted(h)
            else -> null
        }
    }

    /** Stores [result], made at [time] (seconds since 1970), and turns HearID on. */
    public fun save(model: String, state: SoundcoreState?, result: SoundcoreHearingResult, time: Long): SoundcorePacket? {
        val h = held(model, state) ?: return null
        val curves = listOf(result.left, result.right).map { values ->
            IntArray(h.layout.bands) { band -> if (band < values.size) (values[band] - 12) * 10 else LOWEST }
        }
        val real = listOf(result.left, result.right).map { values -> applied(h.eq, IntArray(values.size) { (values[it] - 12) * 10 }) }
        // The A40 reports no gender; the app writes 0 there after a test. The others keep what they report.
        val gender = state?.field("gender")?.get(0)?.toInt()?.and(0xFF)?.takeIf { it != 0xFF } ?: 0
        val age = state?.field("age_range")?.get(0)?.toInt()?.and(0xFF)?.takeIf { it != 0xFF } ?: 0
        return packet(h, h.data.genre, gender, age, true, curves, time, 0, curves, real)
    }

    private fun switched(h: Held, state: SoundcoreState, on: Boolean): SoundcorePacket? {
        val d = h.data
        val real = if (!on) {
            h.config.channels.map { flat(h.eq, it) }
        } else {
            val chosen = when (d.type) {
                0 -> d.curve
                1 -> d.custom
                else -> d.curve.map { curve -> curve?.let { mixed(h.eq, it, d.genre) } }
            }
            chosen.map { c -> c?.let { applied(h.eq, it) } ?: return null }
        }
        val gender = state.field("gender")?.get(0)?.toInt()?.and(0xFF) ?: h.eq.gender ?: 0
        val age = state.field("age_range")?.get(0)?.toInt()?.and(0xFF) ?: 0
        return packet(h, d.genre, gender, age, on, d.curve, d.time, d.type, d.custom, real)
    }

    private fun deleted(h: Held): SoundcorePacket {
        val none = List<IntArray?>(h.layout.channels) { null }
        return packet(h, 0, 0xFF, 0xFF, false, none, 0, 0, none, h.config.channels.map { flat(h.eq, it) })
    }

    /** The curve the earbuds play for an ordinary EQ: [SoundcoreEq.drc] of it. */
    private fun flat(eq: SoundcoreEqSpec, curve: IntArray?): IntArray? = curve?.let { SoundcoreEq.drc(it, eq.digits, eq.min, eq.max) }

    /**
     * The curve the earbuds play for HearID. As [flat], except that the ninth of ten bands comes out as -1.2 dB: the app
     * leaves that band at its lowest in a HearID curve and still divides it (`HearId2Utils.l`).
     */
    private fun applied(eq: SoundcoreEqSpec, curve: IntArray): IntArray {
        val eight = IntArray(maxOf(eq.bands, 8)) { curve.getOrElse(it) { 0 } }
        val out = SoundcoreEq.drc(eight, 1, eq.min, eq.max)
        if (out.size >= 10) out[8] = -12
        return out
    }

    /**
     * A result mixed with the genre preset chosen with it (`HearId2Utils.c`): added band by band, the mean of 200 Hz to
     * 1.6 kHz put back to the result's own, and kept within 6 dB either way.
     */
    private fun mixed(eq: SoundcoreEqSpec, curve: IntArray, genre: Int): IntArray {
        val preset = eq.presetSet.presets.firstOrNull { it.id == genre }?.values ?: IntArray(0)
        val sum = IntArray(8) { curve.getOrElse(it) { 0 } + preset.getOrElse(it) { 0 } }
        val shift = (1..5).sumOf { curve.getOrElse(it) { 0 } - sum[it] } / 5.0
        return IntArray(8) { floor(sum[it] + shift + 0.5).toInt().coerceIn(-60, 60) }
    }

    private fun packet(
        h: Held,
        genre: Int,
        gender: Int,
        age: Int,
        on: Boolean,
        curve: List<IntArray?>,
        time: Long,
        type: Int,
        custom: List<IntArray?>,
        real: List<IntArray?>,
    ): SoundcorePacket {
        val out = ArrayList<Byte>()
        fun byte(v: Int) { out += v.toByte() }
        fun u16le(v: Int) { byte(v and 0xFF); byte(v shr 8 and 0xFF) }
        fun playing(values: IntArray?) {
            repeat(h.eq.bands) { i -> byte(values?.let { it[i].coerceIn(h.eq.min, h.eq.max) - h.eq.min } ?: 0xFF) }
        }
        fun stored(curves: List<IntArray?>) {
            for (c in curves) {
                repeat(h.layout.bands) { i ->
                    byte(c?.let { it[i].coerceIn(LOWEST, HIGHEST) - LOWEST } ?: if (h.layout.emptyCurveEndsInZero && i == h.layout.bands - 1) 0 else 0xFF)
                }
            }
        }
        u16le(h.config.preset)
        u16le(genre)
        h.config.channels.forEach(::playing)
        byte(gender)
        byte(age)
        byte(if (on) 1 else 0)
        stored(curve)
        for (shift in 24 downTo 0 step 8) byte((time shr shift and 0xFF).toInt())
        byte(type)
        stored(custom)
        real.forEach(::playing)
        return SoundcorePacket(SoundcoreCommand.EQUALIZER_HEAR_ID, out.toByteArray())
    }

    private const val LOWEST = -120
    private const val HIGHEST = 134
}
