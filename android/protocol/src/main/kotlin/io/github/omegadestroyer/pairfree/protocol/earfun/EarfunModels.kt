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
    /** The codecs the sound quality screen offers: ldac, aptx, aptx_adaptive, aptx_lossless. The earbuds restart after a change. */
    val quality: List<String> = emptyList(),
    /** Turning LDAC on turns aptX off, and the other way (isLdacAptxConfict). */
    val ldacAptxConflict: Boolean = true,
    /** LDAC and two devices cannot both be on (isLdacDoubleConnectConflictDialog). */
    val ldacMultipointConflict: Boolean = false,
    /** Game mode cannot go on while LDAC is (isLdacGameModeConfict). */
    val ldacGameConflict: Boolean = false,
    /** EarFun's app restarts the earbuds 3 s after turning two devices on (isOpenDoubleConnectNeedReset). */
    val multipointRestart: Boolean = false,
    /** The actions EarFun's app has: restarting (the Qualcomm models) and powering off (isSupportTurnOff). */
    val restart: Boolean = false,
    val powerOff: Boolean = false,
    /** The voice prompt languages built into the earbuds (getInnerVoiceList). */
    val voiceLanguages: List<String> = emptyList(),
    /** The voice prompt volume steps (getVoiceLevelList), in percent. */
    val promptVolumes: List<String> = emptyList(),
    /** The headphones' buttons. */
    val buttons: EarfunButtons? = null,
    /** The settings EarFun's app enables per model beyond the shared ones: bass, manner, game_eq, dac, balance, usb_charge, ambient_voice, call_env (DeviceType's getFunctionEnable and combine groups). */
    val extras: List<String> = emptyList(),
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

/**
 * Touch controls: the key byte [map], the actions per gesture (single, double, triple, hold, and the call gestures triple_call
 * and hold_call, whose bytes [callMap] names), and how they turn off ([disable]: both or each).
 */
public data class EarfunTouch(val map: String, val gestures: Map<String, List<String>>, val disable: String?, val callMap: String? = null)

/**
 * The headphones' buttons: the read and write [layout] (head: EarFun's frame, six bytes; wave_pro_x: the Wave Pro X's own),
 * the key byte [map], and the actions per button and gesture (plus_single, nc_hold_call, ...).
 */
public data class EarfunButtons(val layout: String, val map: String, val gestures: Map<String, List<String>>)

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
