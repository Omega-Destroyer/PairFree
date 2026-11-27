package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * The Soundcore app's HearID hearing tests (docs/soundcore/PROTOCOL.md, HearID), from the Soundcore app 5.0.35: the tones and
 * their levels per model, how each tone's threshold is found, the app's correction of the results and the write that puts them
 * in the earbuds. Not tried with PairFree.
 *
 * The user's phone plays the tones with media volume at the top, as the app does. The models here are the ones whose
 * screen in that app was read.
 */
public class SoundcoreHearingSpec private constructor(
    public val model: String,
    public val style: Style,
    /** The tones in the order they are tested, each with the level it starts from. */
    private val tones: List<Tone>,
    /** How many bands each earbud's curve has in the write: 8, or 10 with two that are always sent as their lowest. */
    public val bands: Int,
) {
    public enum class Style {
        /** A steady tone in one ear; the user says whether they hear it (`BaseHearId2VM`). */
        STEADY,

        /** The same, with three short beeps for each tone (the Liberty 3 Pro's, `A3952HearIdVM`). */
        BEEPS,

        /** HearID 3: beeps in both ears, which the user makes louder or quieter until just heard (`A3968HearidVM`). */
        CHOSEN,

        /** The oldest test (`BaseHearIdVM`): seven steady tones in each ear, from 6.4 kHz down. */
        FIRST,
    }

    private class Tone(val hz: Int, val base: Int, val step: Int)

    /** True when the tones are beeps rather than steady. */
    public val pulsed: Boolean get() = style == Style.BEEPS || style == Style.CHOSEN

    /** True when each ear is tested by itself. */
    public val perEar: Boolean get() = style != Style.CHOSEN

    /** True for the models whose app writes the room's measured noise after the curves. PairFree measures none and sends 0. */
    internal val noiseByte: Boolean get() = model !in setOf("A3936", "A3951", "A3930")

    public val count: Int get() = tones.size
    public fun hz(test: Int): Int = tones[test].hz
    internal fun base(test: Int): Int = tones[test].base
    internal fun firstStep(test: Int): Int = tones[test].step

    /** The tone's peak level in dB below full scale when it stands [step] dB from its starting level. */
    public fun level(test: Int, step: Int): Double {
        val tone = tones[test]
        return if (pulsed) {
            // The app picks a recording made at -12 to +12 dB and plays it at a fixed gain; these are the recordings' own peaks.
            val file = when (tone.hz) {
                100 -> -44.7
                12800 -> -46.7
                else -> -49.8
            }
            file + tone.base + step.coerceIn(-STEPS, STEPS)
        } else {
            (tone.base + step).toDouble().coerceAtMost(LOUDEST)
        }
    }

    public companion object {
        /** No tone goes above this, whatever the staircase asks. */
        private const val LOUDEST = -27.0

        /** How far from the starting level a recorded beep goes, in dB. */
        internal const val STEPS: Int = 12
        private val ORDER = listOf(3200, 1600, 6400, 800, 400, 200, 100, 12800)

        private fun spec(model: String, style: Style, bands: Int, base: List<Int>, step: List<Int> = List(8) { 0 }) =
            SoundcoreHearingSpec(model, style, ORDER.indices.map { Tone(ORDER[it], base[it], step[it]) }, bands)

        private fun first(model: String, base: List<Int>): SoundcoreHearingSpec =
            SoundcoreHearingSpec(model, Style.FIRST, listOf(6400, 3200, 1600, 800, 400, 200, 100).mapIndexed { i, hz -> Tone(hz, base[i], 6) }, 8)

        public fun of(model: String?): SoundcoreHearingSpec? = when (model) {
            "A3909" -> first(model, listOf(-75, -75, -70, -75, -70, -60, -55))
            "A3910" -> first(model, listOf(-73, -80, -70, -70, -63, -55, -52))
            "A3913" -> first(model, listOf(-75, -75, -70, -72, -67, -55, -55))
            "A3936", "A3930", "A3951" -> spec(
                model, Style.STEADY, if (model == "A3936") 10 else 8,
                listOf(-75, -64, -72, -71, -69, -59, -54, -72), listOf(6, 0, 6, 0, 3, 6, 6, 6),
            )
            "A3952" -> spec(model, Style.BEEPS, 10, listOf(-27, -19, -25, -22, -22, -17, -14, -25))
            "A3035", "A3040", "A3947", "A3953", "A3955", "A3968" -> spec(model, Style.CHOSEN, 10, listOf(-38, -39, -39, -34, -25, -25, -44, -39))
            // The Space One Pro's own levels (`A3062ProductModel.getHearIdVolumeCurve`; its half dB is rounded to the louder).
            "A3062" -> spec(model, Style.CHOSEN, 10, listOf(-32, -32, -20, -36, -30, -28, -30, -24))
            // The Space 2's (`D1402ProductModel`).
            "D1402" -> spec(model, Style.CHOSEN, 10, listOf(-38, -37, -17, -38, -36, -29, -29, -32))
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

/** The result of a test: for each earbud, eight values from 100 Hz to 12.8 kHz in tenths of a dB, 0 changing nothing. */
public class SoundcoreHearingResult(public val left: IntArray, public val right: IntArray)

/** One test as it runs: the left earbud's eight tones and then the right's, or in HearID 3 eight tones for both at once. */
public class SoundcoreHearingRun(public val spec: SoundcoreHearingSpec) {
    private val chosen = spec.style == SoundcoreHearingSpec.Style.CHOSEN

    /** How many tones are done, of [total]. */
    public var done: Int = 0
        private set
    public val total: Int = if (chosen) spec.count else spec.count * 2
    private val raw = IntArray(total) { FLAT }

    /** Where the listener left each tone of HearID 3, in dB from its starting level. */
    private val offsets = IntArray(spec.count)

    public val left: Boolean get() = done < spec.count
    private val test: Int get() = done % spec.count
    public val hz: Int get() = spec.hz(test.coerceAtMost(spec.count - 1))
    private var staircase = fresh(0)

    /** HearID 3: how far the tone stands from its starting level, in steps of 3 dB, -4 to 4. */
    public val steps: Int get() = if (finished) 0 else offsets[test] / STEP

    public val level: Double get() = spec.level(test, if (chosen) offsets[test] else staircase.step)
    public val finished: Boolean get() = done >= total

    private fun fresh(test: Int) = SoundcoreStaircase(spec.base(test), spec.firstStep(test))
    private fun band(hz: Int) = (0 until spec.count).count { spec.hz(it) < hz }

    private fun next(value: Int) {
        raw[(if (left) 0 else spec.count) + band(hz)] = value
        done++
        if (!finished) staircase = fresh(test)
    }

    /** The listener's answer to the tone playing (not for HearID 3). */
    public fun answer(heard: Boolean) {
        if (finished || chosen) return
        staircase.answer(heard)
        next(staircase.result ?: return)
    }

    /** HearID 3: one step louder or quieter. */
    public fun louder(up: Boolean) {
        if (finished || !chosen) return
        offsets[test] = (offsets[test] + if (up) STEP else -STEP).coerceIn(-SoundcoreHearingSpec.STEPS, SoundcoreHearingSpec.STEPS)
    }

    /** HearID 3: the tone is just audible where it stands. */
    public fun keep() {
        if (!finished && chosen) next(FLAT + offsets[test])
    }

    /** HearID 3: back to the tone before. */
    public fun back() {
        if (chosen && done > 0 && !finished) done--
    }

    /** Starts the tone being tested over. */
    public fun repeat() {
        if (finished) return
        if (chosen) offsets[test] = 0 else staircase = fresh(test)
    }

    /** The corrected result, once every tone is done. */
    public fun result(): SoundcoreHearingResult? {
        if (!finished) return null
        if (chosen) {
            val both = halved(raw)
            return SoundcoreHearingResult(both, both.copyOf())
        }
        val values = when (spec.style) {
            SoundcoreHearingSpec.Style.BEEPS -> unify(raw)
            SoundcoreHearingSpec.Style.FIRST -> againstEach(raw)
            else -> correct(raw)
        }
        val tenths = IntArray(values.size) { (values[it] - FLAT) * 10 }
        return SoundcoreHearingResult(tenths.copyOfRange(0, spec.count), tenths.copyOfRange(spec.count, total))
    }

    internal companion object {
        /** The value that changes nothing in the app's scale, in which 1 is 1 dB. */
        private const val FLAT = 12
        private const val STEP = 3

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

        /** `BaseHearIdVM.changeDataByAlgorithm`: each ear moved against its own 1.6 kHz result, then kept in range. */
        fun againstEach(raw: IntArray): IntArray {
            val n = raw.size / 2
            return IntArray(raw.size) { i ->
                val moved = raw[i] - raw[i / n * n + 4] + 12
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

        /**
         * HearID 3 (`A3953HearId3TestVM.dataUnification`): what each tone needed above its starting level, against the best
         * tone, halved. Already in tenths of a dB.
         */
        fun halved(raw: IntArray): IntArray {
            val over = IntArray(raw.size) { maxOf(raw[it] - FLAT, 0) }
            val least = over.min()
            return IntArray(raw.size) { (over[it] - least) * 5 }
        }
    }
}

/**
 * HearID as the earbuds hold it: the test's result (stored with the EQ), whether it is on, and the writes that store a new
 * result, switch it and delete it. All three are the model's EQ write with its HearID part filled in (`A3936CmdService.p6`,
 * `A3951DeviceManager.d2`, `CmmBtCmdService.p6` and, for the one-curve Space One, `o6`): command `03 87`.
 */
public object SoundcoreHearingId {
    public const val SWITCH: String = "sc_hear_id"
    public const val DELETE: String = "sc_hear_id_delete"

    private class Held(val eq: SoundcoreEqSpec, val layout: SoundcoreHearIdLayout, val config: SoundcoreEqConfig, val data: SoundcoreHearId, val on: Boolean)

    private fun isFirst(model: String) = SoundcoreHearingSpec.of(model)?.style == SoundcoreHearingSpec.Style.FIRST

    /** The oldest models' block: the switch, eight values for each ear and the time. */
    private fun firstBlock(state: SoundcoreState?): ByteArray? = state?.field("hear_id")?.takeIf { it.size >= 21 }

    private fun firstHasResult(block: ByteArray) = (1..16).any { block[it] != 0.toByte() && block[it] != 0xFF.toByte() }

    /** The oldest models' result (`03 82`, `A3909DeviceManager.S0`): gender, age, the two ears in the app's scale, the time. */
    private fun firstSave(state: SoundcoreState?, result: SoundcoreHearingResult, time: Long): SoundcorePacket? {
        if (firstBlock(state) == null) return null
        val body = ByteArray(22)
        body[0] = state?.field("gender")?.get(0)?.takeIf { it != 0xFF.toByte() } ?: 0
        body[1] = state?.field("age_range")?.get(0)?.takeIf { it != 0xFF.toByte() } ?: 0
        for ((ear, values) in listOf(result.left, result.right).withIndex()) {
            // The eighth band has no tone and is sent flat.
            repeat(8) { band -> body[2 + ear * 8 + band] = (FLAT + values.getOrElse(band) { 0 } / 10).toByte() }
        }
        for (i in 0 until 4) body[18 + i] = (time shr (24 - 8 * i)).toByte()
        return SoundcorePacket(SoundcoreCommand.HEARING_RESULT, body)
    }

    private fun held(model: String, state: SoundcoreState?): Held? {
        // The P41i has no test in its app but holds and switches a result.
        if (state == null || (SoundcoreHearingSpec.of(model) == null && model != "A3937")) return null
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
        if (isFirst(model)) {
            val block = firstBlock(state)?.takeIf(::firstHasResult) ?: return emptyList()
            return listOf(Setting.Toggle(SWITCH, block[0] == 1.toByte()), Setting.Action(DELETE))
        }
        val h = held(model, state) ?: return emptyList()
        if (!hasResult(h.data)) return emptyList()
        return listOf(Setting.Toggle(SWITCH, h.on), Setting.Action(DELETE))
    }

    public fun change(model: String, state: SoundcoreState?, change: SettingChange): SoundcorePacket? {
        if (isFirst(model)) {
            if (firstBlock(state)?.let(::firstHasResult) != true) return null
            return when {
                change is SettingChange.Toggle && change.key == SWITCH -> SoundcorePacket(SoundcoreCommand.HEARING_SWITCH, byteArrayOf(if (change.on) 1 else 0))
                change is SettingChange.Press && change.key == DELETE -> SoundcorePacket(SoundcoreCommand.HEARING_DELETE, ByteArray(0))
                else -> null
            }
        }
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
        if (isFirst(model)) return firstSave(state, result, time)
        val h = held(model, state) ?: return null
        val ears = listOf(result.left, result.right)
        val curves: List<IntArray?> = List(h.layout.channels) { c -> IntArray(h.layout.bands) { band -> ears[c].getOrElse(band) { LOWEST } } }
        val real: List<IntArray?> = List(h.config.channels.size) { c -> applied(h.eq, ears[c]) }
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
    private fun flat(eq: SoundcoreEqSpec, curve: IntArray?): IntArray? =
        curve?.let { if (eq.write == AS_IS) it else SoundcoreEq.drc(it, eq.digits, eq.min, eq.max) }

    /**
     * The curve the earbuds play for HearID. As [flat], except that the ninth of ten bands comes out as -1.2 dB: the app
     * leaves that band at its lowest in a HearID curve and still divides it (`HearId2Utils.l`).
     */
    private fun applied(eq: SoundcoreEqSpec, curve: IntArray): IntArray {
        // The Space One Pro's app sends the curve as it is, with the bands it has none for at their lowest.
        if (eq.write == AS_IS) return IntArray(eq.bands) { curve.getOrElse(it) { LOWEST } }
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
        // The Space Q45 has one curve, sent as both earbuds' (`CmmBtCmdService.p6`).
        fun both(curves: List<IntArray?>) = if (h.eq.write == TWICE && curves.size == 1) curves + curves else curves
        both(h.config.channels).forEach(::playing)
        byte(gender)
        byte(age)
        byte(if (on) 1 else 0)
        stored(curve)
        for (shift in 24 downTo 0 step 8) byte((time shr shift and 0xFF).toInt())
        byte(type)
        stored(custom)
        both(real).forEach(::playing)
        if (SoundcoreHearingSpec.of(h.eq.model)?.noiseByte == true) byte(0)
        return SoundcorePacket(SoundcoreCommand.EQUALIZER_HEAR_ID, out.toByteArray())
    }

    private const val LOWEST = -120
    private const val FLAT = 12
    private const val HIGHEST = 134

    /** The EQ write formats (data/soundcore/devices.json) that differ here. */
    private const val AS_IS = "a3062"
    private const val TWICE = "a3040"
}
