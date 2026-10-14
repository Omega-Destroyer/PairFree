package io.github.omegadestroyer.soundpeter.protocol.soundcore

/** One EQ preset: its number, OpenSCQ30's key, an English name, and the curve sent, in the model's units. */
public class SoundcorePreset(public val id: Int, public val key: String, public val name: String, public val values: IntArray)

/** A model's presets and custom curve (data/soundcore/eq_presets.json). */
public class SoundcorePresetSet(
    /** The preset number of a custom curve, or null when the model has none. */
    public val customPreset: Int?,
    /** The bands a custom curve sets; the rest take [hiddenBands]. */
    public val bandHz: List<Int>,
    public val hiddenBands: List<Int>,
    public val customMin: Int,
    public val customMax: Int,
    public val presets: List<SoundcorePreset>,
)

/**
 * Where a model's HearID block is in its state and what it holds, part by part (docs/soundcore/PROTOCOL.md
 * section 8). [emptyCurveEndsInZero]: a curve the earbuds do not hold goes out as FF bytes ending in 00, as
 * the Soundcore app sends it to the A40, rather than all FF.
 */
public class SoundcoreHearIdLayout(
    public val field: String,
    public val channels: Int,
    public val bands: Int,
    public val parts: List<String>,
    public val emptyCurveEndsInZero: Boolean = false,
)

/**
 * A model's equalizer (data/soundcore/devices.json eq): the packet format [write], the curves it
 * holds ([channels] of [bands]) and their scale (values from [min] to [max], [digits] decimal
 * digits: tenths of a dB for most), its [presets], and its HearID block when the EQ write passes
 * one on.
 */
public class SoundcoreEqSpec(
    public val model: String,
    public val write: String,
    public val channels: Int,
    public val bands: Int,
    public val min: Int,
    public val max: Int,
    public val digits: Int,
    public val presets: String,
    public val presetBytes: Int = 2,
    /** How many curves the state holds; some models report one and are sent two. */
    public val stateChannels: Int = channels,
    /** The state holds only the preset number (the Liberty 2 Pro). */
    public val presetOnly: Boolean = false,
    public val hearId: SoundcoreHearIdLayout? = null,
    /** The gender byte to send when the state has none. */
    public val gender: Int? = null,
    /** What confirms the write: `hardware` (a recording) or `openscq30_tests`; null when only OpenSCQ30's code backs it. */
    public val checked: String? = null,
) {
    public val presetSet: SoundcorePresetSet get() = SOUNDCORE_PRESET_SETS.getValue(presets)

    /** True when the model takes a custom curve as well as presets. */
    public val hasCustom: Boolean get() = presetSet.customPreset != null

    public fun preset(key: String): SoundcorePreset? = presetSet.presets.firstOrNull { it.key == key }

    public companion object {
        public val ALL: List<SoundcoreEqSpec> get() = SOUNDCORE_EQ

        public fun of(model: String?): SoundcoreEqSpec? = SOUNDCORE_EQ.firstOrNull { it.model == model }
    }
}

/** An EQ as the earbuds hold it: a preset number and each earbud's curve, null for one the earbuds left out. */
public class SoundcoreEqConfig(public val preset: Int, public val channels: List<IntArray?>) {
    override fun equals(other: Any?): Boolean =
        other is SoundcoreEqConfig && preset == other.preset && channels.size == other.channels.size &&
            channels.indices.all { channels[it].contentEquals(other.channels[it]) }

    override fun hashCode(): Int = 31 * preset + channels.fold(0) { h, c -> 31 * h + c.contentHashCode() }

    override fun toString(): String = "SoundcoreEqConfig($preset, ${channels.map { it?.toList() }})"
}

/** A HearID block read from the state; the EQ writes pass it on with HearID turned off, as OpenSCQ30 does. */
public class SoundcoreHearId(
    public val curve: List<IntArray?>,
    public val time: Long,
    public val type: Int,
    public val genre: Int,
    public val custom: List<IntArray?>,
)

/**
 * Reading and writing a Soundcore model's EQ. Ported from OpenSCQ30
 * (github.com/Oppzippy/OpenSCQ30 by Kyle Scheuing, GPL-3.0-or-later, the same licence as
 * SoundPeter): its equalizer module, its shared EQ packets (`set_equalizer`,
 * `set_equalizer_with_drc`, `SetEqualizerAndCustomHearId`) and each model's own EQ packet.
 * data/soundcore/test_vectors.json holds OpenSCQ30's EQ tests and the Space A40's recorded writes,
 * and the tests rebuild every one byte for byte.
 */
public object SoundcoreEqualizer {
    /** The EQ that [preset] sets: its number, and its curve on every earbud. */
    public fun forPreset(spec: SoundcoreEqSpec, preset: SoundcorePreset): SoundcoreEqConfig =
        SoundcoreEqConfig(preset.id, List(spec.channels) { fill(spec, preset.values) })

    /** A custom curve, one value per visible band in the model's units, or null when the model has no custom curve. */
    public fun forCustom(spec: SoundcoreEqSpec, visible: IntArray): SoundcoreEqConfig? {
        val id = spec.presetSet.customPreset ?: return null
        val values = IntArray(spec.presetSet.bandHz.size) { visible.getOrElse(it) { 0 } }
        return SoundcoreEqConfig(id, List(spec.channels) { fill(spec, values) })
    }

    // values_to_volume_adjustments: the given bands, then the hidden bands' fixed values.
    private fun fill(spec: SoundcoreEqSpec, values: IntArray): IntArray = IntArray(spec.bands) { band ->
        (if (band < values.size) values[band] else spec.presetSet.hiddenBands.getOrElse(band - values.size) { 0 })
            .coerceIn(spec.min, spec.max)
    }

    /** The EQ the earbuds reported in [state], or null when the state does not hold it. */
    public fun read(spec: SoundcoreEqSpec, state: SoundcoreState): SoundcoreEqConfig? {
        if (spec.presetOnly) {
            val id = state.field("eq_preset_id")?.let { u16le(it, 0) } ?: return null
            val curve = spec.presetSet.presets.firstOrNull { it.id == id }?.let { fill(spec, it.values) } ?: IntArray(spec.bands)
            return SoundcoreEqConfig(id, List(spec.channels) { curve.copyOf() })
        }
        val bytes = state.field("equalizer") ?: return null
        val id = if (spec.presetBytes == 1) bytes[0].toInt() and 0xFF else u16le(bytes, 0)
        val read = List(spec.stateChannels) { c -> optional(bytes, spec.presetBytes + c * spec.bands, spec.bands, spec.min, spec.max) }
        return SoundcoreEqConfig(id, List(spec.channels) { read.getOrElse(it) { read[0] }?.copyOf() })
    }

    /** The HearID block in [state], with OpenSCQ30's defaults for parts the model does not send. */
    public fun hearId(layout: SoundcoreHearIdLayout, state: SoundcoreState): SoundcoreHearId? {
        val bytes = state.field(layout.field) ?: return null
        if (state.field("hear_id_status")?.let { it[0] == 0xFF.toByte() } == true) return emptyHearId(layout)
        var at = 0
        var curve: List<IntArray?> = List(layout.channels) { null }
        var custom: List<IntArray?> = List(layout.channels) { null }
        var time = 0L
        var type = 0
        var genre = 0
        fun curves(): List<IntArray?> = List(layout.channels) {
            optional(bytes, at, layout.bands, HEAR_ID_MIN, HEAR_ID_MAX).also { at += layout.bands }
        }
        for (part in layout.parts) {
            when {
                part == "enabled" -> at += 1
                part == "curve" -> curve = curves()
                part == "custom" -> custom = curves()
                part == "time" -> {
                    time = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[at + i].toLong() and 0xFF) }
                    at += 4
                }
                part == "type" -> {
                    type = (bytes[at].toInt() and 0xFF).takeIf { it in 0..2 } ?: 0
                    at += 1
                }
                part == "genre1" -> {
                    genre = bytes[at].toInt() and 0xFF
                    at += 1
                }
                part == "genre2" -> {
                    genre = u16le(bytes, at)
                    at += 2
                }
                part.startsWith("skip") -> at += part.removePrefix("skip").toInt()
                else -> error("unknown HearID part $part")
            }
        }
        return SoundcoreHearId(curve, time, type, genre, custom)
    }

    private fun emptyHearId(layout: SoundcoreHearIdLayout) =
        SoundcoreHearId(List(layout.channels) { null }, 0, 0, 0, List(layout.channels) { null })

    /**
     * The packets that set [target] on the earbuds. [state] is the earbuds' last state: HearID
     * models pass its HearID block on, and the Motion+ sends only what changed from it.
     */
    public fun write(spec: SoundcoreEqSpec, state: SoundcoreState?, target: SoundcoreEqConfig): List<SoundcorePacket> =
        write(
            spec,
            target,
            hearId = spec.hearId?.let { layout -> state?.let { hearId(layout, it) } ?: emptyHearId(layout) },
            gender = state?.field("gender")?.let { it[0].toInt() and 0xFF } ?: spec.gender ?: 0,
            age = state?.field("age_range")?.let { it[0].toInt() and 0xFF } ?: 0,
            hearIdInitialized = state?.field("hear_id_status")?.let { it[0] != 0xFF.toByte() } ?: false,
            current = state?.let { read(spec, it) },
        )

    /** [write] with what it reads from the state given directly, as OpenSCQ30's packet tests give it. */
    internal fun write(
        spec: SoundcoreEqSpec,
        target: SoundcoreEqConfig,
        hearId: SoundcoreHearId?,
        gender: Int,
        age: Int,
        hearIdInitialized: Boolean,
        current: SoundcoreEqConfig?,
    ): List<SoundcorePacket> {
        val w = Writer(spec)
        return when (spec.write) {
            "plain", "a3909" -> listOf(w.packet(SET_EQUALIZER) { preset(target); eq(target) })
            "drc" -> listOf(w.packet(SET_EQUALIZER_WITH_DRC) { preset(target); eq(target); target.channels.forEach { drc(it) } })
            "a3388" -> listOf(w.packet(SET_EQUALIZER_WITH_DRC) { preset(target); eq(target) })
            "a3876" -> listOf(w.packet(SET_EQUALIZER_WITH_DRC) { preset(target); curve(first(target)); drc(first(target)) })
            "hear_id", "hear_id_forced" -> listOf(hearIdPacket(w, spec, target, hearId!!, gender, age))
            "a3035" -> listOf(
                w.packet(SET_EQUALIZER_WITH_HEAR_ID) {
                    val h = hearId!!
                    val hb = spec.hearId!!.bands
                    preset(target); u16le(h.genre); eq(target); bytes(0xFF, 0xFF); byte(0)
                    hearCurves(h.curve, hb, zeroLast = true); u32be(h.time); byte(h.type)
                    hearCurves(h.custom, hb, zeroLast = true); target.channels.forEach { drc(it) }; byte(0)
                },
            )
            "a3040" -> listOf(
                w.packet(SET_EQUALIZER_WITH_HEAR_ID) {
                    val h = hearId!!
                    val hb = spec.hearId!!.bands
                    preset(target); u16le(h.genre); curve(first(target)); curve(first(target)); bytes(0, 1); byte(0)
                    hearCurves(h.curve, hb); u32be(h.time); byte(h.type)
                    repeat(2) { hearCurves(listOf(h.custom.firstOrNull()), hb) }
                    repeat(2) { drc(first(target)) }; byte(0)
                },
            )
            "a3062" -> listOf(
                w.packet(SET_EQUALIZER_WITH_HEAR_ID) {
                    val h = hearId!!
                    val hb = spec.hearId!!.bands
                    preset(target); u16le(h.genre); eq(target); bytes(0, 0); byte(0)
                    hearCurves(h.curve, hb); u32be(h.time); byte(h.type); hearCurves(h.custom, hb); eq(target); byte(0)
                },
            )
            "a3947" -> listOf(
                w.packet(SET_EQUALIZER_WITH_HEAR_ID) {
                    val h = hearId!!
                    val hb = spec.hearId!!.bands
                    preset(target); u16le(h.genre); eq(target); bytes(0, 0); byte(0)
                    hearCurves(h.curve, hb); u32be(h.time); byte(h.type); hearCurves(h.custom, hb)
                    target.channels.forEach { drc(it, zeroTenth = true) }; byte(0)
                },
            )
            "interleaved" -> listOf(
                w.packet(SET_EQUALIZER_WITH_HEAR_ID) {
                    val h = hearId!!
                    val hb = spec.hearId!!.bands
                    preset(target); u16le(h.genre); eq(target); bytes(0, 0); byte(0)
                    hearCurves(h.curve, hb); u32be(h.time); byte(h.type); hearCurves(h.custom, hb)
                    for (b in eqBytes(target)) { out += b; byte(0) }
                    bytes(0, 0)
                },
            )
            "a3955" -> {
                val initialized = hearIdInitialized
                listOf(
                    w.packet(SET_EQUALIZER_WITH_HEAR_ID) {
                        val h = hearId!!
                        val hb = spec.hearId!!.bands
                        preset(target); u16le(h.genre); eq(target); if (initialized) bytes(0, 0) else bytes(0xFF, 0xFF); byte(0)
                        hearCurves(h.curve, hb, zeroLast = true); u32be(h.time); byte(h.type)
                        hearCurves(h.custom, hb, zeroLast = true); target.channels.forEach { drc(it) }; byte(0)
                    },
                )
            }
            "a3116" -> {
                buildList {
                    if (current == null || current.preset != target.preset) add(SoundcorePacket(SET_EQUALIZER, byteArrayOf(target.preset.toByte())))
                    if (current == null || !sameCurves(current, target)) {
                        add(w.packet(SET_EQUALIZER_WITH_DRC) { curve(first(target)) })
                    }
                }
            }
            else -> error("unknown EQ format ${spec.write}")
        }
    }

    private fun sameCurves(a: SoundcoreEqConfig, b: SoundcoreEqConfig) =
        a.channels.size == b.channels.size && a.channels.indices.all { a.channels[it].contentEquals(b.channels[it]) }

    // OpenSCQ30's SetEqualizerAndCustomHearId.
    private fun hearIdPacket(w: Writer, spec: SoundcoreEqSpec, target: SoundcoreEqConfig, h: SoundcoreHearId, gender: Int, age: Int): SoundcorePacket {
        val supports = age != 0xFF || spec.write == "hear_id_forced"
        val maxWave = target.channels.size * spec.bands
        val hb = spec.hearId!!.bands
        val zeroLast = spec.hearId.emptyCurveEndsInZero
        return w.packet(if (supports) SET_EQUALIZER_WITH_HEAR_ID else SET_EQUALIZER_WITHOUT_HEAR_ID) {
            preset(target)
            if (supports) u16le(h.genre)
            eq(target)
            byte(if (supports) gender else 0xFF)
            byte(if (supports) age else 0xFF)
            byte(0)
            if (supports) hearCurves(h.curve, hb, zeroLast) else repeat(maxWave) { byte(0xFF) }
            if (supports) u32be(h.time) else repeat(4) { byte(0) }
            byte(h.type)
            if (supports) hearCurves(h.custom, hb, zeroLast) else repeat(maxWave) { byte(0xFF) }
            target.channels.forEach { drc(it) }
        }
    }

    private fun first(config: SoundcoreEqConfig): IntArray? = config.channels.firstOrNull { it != null }

    private class Writer(val spec: SoundcoreEqSpec) {
        val out = ArrayList<Byte>()

        fun packet(command: Int, body: Writer.() -> Unit): SoundcorePacket {
            out.clear()
            body()
            return SoundcorePacket(command, out.toByteArray())
        }

        fun byte(v: Int) { out += v.toByte() }
        fun bytes(vararg v: Int) { v.forEach(::byte) }
        fun u16le(v: Int) { byte(v and 0xFF); byte(v shr 8 and 0xFF) }
        fun u32be(v: Long) { for (shift in 24 downTo 0 step 8) byte((v shr shift and 0xFF).toInt()) }

        fun preset(config: SoundcoreEqConfig) = if (spec.presetBytes == 1) byte(config.preset) else u16le(config.preset)

        fun encode(values: IntArray?): ByteArray = values?.let { v ->
            ByteArray(spec.bands) { (v[it].coerceIn(spec.min, spec.max) - spec.min).toByte() }
        } ?: ByteArray(spec.bands) { 0xFF.toByte() }

        fun curve(values: IntArray?) { out += encode(values).toList() }
        fun eqBytes(config: SoundcoreEqConfig): ByteArray = config.channels.fold(ByteArray(0)) { acc, c -> acc + encode(c) }
        fun eq(config: SoundcoreEqConfig) { out += eqBytes(config).toList() }

        /** The curve after OpenSCQ30's dynamic range correction; [zeroTenth] sets its tenth byte to 0, as the A3947 does. */
        fun drc(values: IntArray?, zeroTenth: Boolean = false) {
            val b = encode(values?.let { SoundcoreEq.drc(it, spec.digits, spec.min, spec.max) })
            if (zeroTenth) b[9] = 0
            out += b.toList()
        }

        /** HearID curves on the common scale; a missing one is FF bytes, with the last one 0 where [zeroLast]. */
        fun hearCurves(curves: List<IntArray?>, bands: Int, zeroLast: Boolean = false) {
            for (c in curves) {
                if (c == null) {
                    repeat(bands) { i -> byte(if (zeroLast && i == bands - 1) 0 else 0xFF) }
                } else {
                    for (v in c) byte(v.coerceIn(HEAR_ID_MIN, HEAR_ID_MAX) - HEAR_ID_MIN)
                }
            }
        }
    }

    // VolumeAdjustments::take_optional: FF first means the curve is not there; each value is clamped to the scale.
    private fun optional(bytes: ByteArray, at: Int, bands: Int, min: Int, max: Int): IntArray? {
        if (at + bands > bytes.size || bytes[at] == 0xFF.toByte()) return null
        return IntArray(bands) { ((bytes[at + it].toInt() and 0xFF) + min).coerceIn(min, max) }
    }

    private fun u16le(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private const val HEAR_ID_MIN = -120
    private const val HEAR_ID_MAX = 134

    private const val SET_EQUALIZER = SoundcoreCommand.EQUALIZER
    private const val SET_EQUALIZER_WITH_DRC = SoundcoreCommand.EQUALIZER_WITH_DRC
    private const val SET_EQUALIZER_WITHOUT_HEAR_ID = SoundcoreCommand.EQUALIZER_NO_HEAR_ID
    private const val SET_EQUALIZER_WITH_HEAR_ID = SoundcoreCommand.EQUALIZER_HEAR_ID
}
