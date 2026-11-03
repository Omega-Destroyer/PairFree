package io.github.omegadestroyer.pairfree.protocol.earfun

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One EarFun model from data/earfun/devices.json (docs/earfun/PROTOCOL.md section 5): the Bluetooth [name] and
 * EarFun's own model [codes], the [protocol] it speaks (qualcomm or earfun; the others are not [covered] yet),
 * and what EarFun's app offers for it. [autoOffTable], the [noise] sub-modes' tables and the [touch] key map
 * name tables in [EarfunValues].
 */
public data class EarfunModel(
    val codes: List<String>,
    val name: String,
    val names: List<String>,
    val protocol: String,
    val chip: String,
    val form: Form,
    val case: Boolean,
    val covered: Boolean,
    val noise: EarfunNoise?,
    val game: Boolean,
    val multipoint: Boolean,
    val mic: Boolean,
    val promptVolume: Boolean,
    val find: Boolean,
    val hearingProtection: Boolean,
    val spatial: Boolean,
    /** Pausing and answering calls when the earbuds go in and out (the Air Pro 4+ and 5). */
    val inEar: Boolean,
    /** The case's battery has charging in bit 7 (the Air Pro 4+ and 5). */
    val caseChargeBit: Boolean,
    val autoOff: List<String>,
    val autoOffTable: String?,
    val touch: EarfunTouch?,
    val eq: EarfunEqSpec?,
    /** The firmware version comes as text, not as three numbers (EarFun's own frame). */
    val versionText: Boolean,
    /** EarFun's app opens GAIA v3 on connecting (QualcommProtocolParser.initCommand); not on the Air Pro 4. */
    val gaiaInit: Boolean,
) {
    val qualcomm: Boolean get() = protocol == QUALCOMM

    public companion object {
        public const val QUALCOMM: String = "qualcomm"
        public const val EARFUN: String = "earfun"
    }
}

/**
 * A model's noise control: the noise mode's byte for each of off, ambient and the cancelling modes it has
 * (anc, adaptive, wind, anc1, anc2), and the cancelling and transparency sub-modes it offers, if any.
 */
public data class EarfunNoise(val values: Map<String, Int>, val cancelling: EarfunSubMode?, val ambient: EarfunSubMode?)

/** A sub-mode's [options], whose bytes [table] names in [EarfunValues]. */
public data class EarfunSubMode(val table: String, val options: List<String>)

/** Touch controls: the key byte [map], the actions per gesture (single, double, triple, hold), and how they turn off ([disable]: both or each). */
public data class EarfunTouch(val map: String, val gestures: Map<String, List<String>>, val disable: String?)

/**
 * A model's EQ as EarFun's app writes it (docs/earfun/PROTOCOL.md section 6): the bands, the band [frame] (index,
 * index_cr or count), whether a slider position is the gain in dB ([positionGain]) or a step in [EarfunValues.GAIN_DB],
 * the [master] gain, the preset list, and bands sent after the user's ([fixedAfter]).
 */
public data class EarfunEqSpec(
    val hz: List<Double>,
    val q: List<Double>,
    val frame: String,
    val positionGain: Boolean,
    val master: EarfunMaster,
    val presets: String,
    val fixedAfter: List<EarfunFixedBand>,
)

/** The master gain: [db] fixed; or [base] minus the highest gain above 0 (cut to whole dB when [whole]); or by the highest position ([byPeak]). */
public data class EarfunMaster(val db: Double?, val base: Double?, val whole: Boolean, val byPeak: List<Double>?)

public data class EarfunFixedBand(val hz: Double, val q: Double, val db: Double)

/** Every EarFun model PairFree knows, and how a paired device is recognised as one. */
public object EarfunModels {
    public val ALL: List<EarfunModel> get() = EARFUN_MODELS

    /** The model a Bluetooth [name] is (the whole name, or one of a model's other names), or null. */
    public fun fromName(name: String): EarfunModel? {
        val n = name.trim()
        return EARFUN_MODELS.firstOrNull { m -> m.name.equals(n, ignoreCase = true) || m.names.any { it.equals(n, ignoreCase = true) } }
    }

    /** The model by one of EarFun's codes (TW400) or its name, for the device page and reports. */
    public fun of(codeOrName: String?): EarfunModel? {
        val n = codeOrName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return fromName(n) ?: EARFUN_MODELS.firstOrNull { m -> m.codes.any { it.equals(n, ignoreCase = true) } }
    }

    /** The screens' [Model] for a model PairFree shows, with [form] for one it does not know. */
    public fun model(codeOrName: String?, form: Form? = null): Model? {
        val n = codeOrName?.takeIf { it.isNotBlank() } ?: return null
        val known = of(n)
        return Model(known?.codes?.first() ?: n, listOfNotNull(known?.name ?: n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.EARBUDS)
    }

    private const val FAMILY = "EarFun"
}
