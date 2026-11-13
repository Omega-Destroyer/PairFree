package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/** How PairFree talks to a QCY model (docs/qcy/PROTOCOL.md section 1). */
public enum class QcyProtocol {
    /** QCY's own items over its Bluetooth LE service A001. */
    QCY,

    /** JieLi's RCSP, which QCY's app speaks through JieLi's SDK. */
    JIELI,

    /** QCY's own items inside JieLi's custom command (QCY's newer JieLi models). */
    JIELI_CUSTOM,
}

/**
 * One noise mode as QCY packs it: [mode] (cancelling, transparency or off) and its range of packed values,
 * three bytes (mode, scene, value) on most models and one byte on older ones, with its [scenes].
 */
public data class QcyNoiseMode(val mode: String, val from: Int, val to: Int, val default: Int, val scenes: List<QcyScene>) {
    /** True for a model's older one-byte noise values, which go out as item 0C. */
    val legacy: Boolean get() = to <= 0xFF

    public fun covers(value: Int): Boolean = value in from..to
}

/** One noise cancelling scene (adaptive, indoor, commuting, noisy, anti_wind) and its range of strengths. */
public data class QcyScene(val scene: String, val from: Int, val to: Int)

/**
 * The gestures (single, double, triple, hold) and actions a model's control panel offers; [extraActions] are action
 * numbers of the model's own beyond QCY's table (the Capsule3 Pro's noise control cycle).
 */
public data class QcyTouch(val gestures: List<String>, val actions: List<String>, val extraActions: Map<Int, String> = emptyMap()) {
    /** Every action number this model takes, and its name. */
    val table: Map<Int, String> get() = QcyKeys.ACTIONS + extraActions
}

/** One switch a model has: its item and PairFree's name for it. */
/** [options] is empty for a switch; a setting the panel picks from a list has each value byte and its name (music mode, touch sensitivity). */
public data class QcySetting(val item: Int, val setting: String, val options: List<Pair<Int, String>> = emptyList())

/**
 * A model's equalizer as its control panel lays it out: [bands] bands at [freqs] (Hz), each from minus to plus
 * [maxDb] in whole dB, and the names of the panel's presets, whose curves are QCY's server data.
 */
/**
 * A model's graphic EQ from its control panel: [presets] are all of QCY's preset names; [presetGains] holds the ones that are curves on the model's own bands (a gain in
 * dB per band, as the panel lists them), which PairFree offers. The rest are parametric (their own frequencies and Q) and are not offered.
 */
public data class QcyEqSpec(val bands: Int, val maxDb: Int, val freqs: List<Int>, val presets: List<String>, private val presetData: String = "") {
    /** The curves as `name=gain,gain;name=...` in the generated table, read once. */
    val presetGains: Map<String, List<Double>> by lazy {
        if (presetData.isEmpty()) emptyMap() else presetData.split(';').associate { part -> part.substringBefore('=') to part.substringAfter('=').split(',').map(String::toDouble) }
    }
}

/** One QCY model from data/qcy/devices.json, by the vendor id it advertises. */
public data class QcyModel(
    val vendorId: Int,
    val name: String,
    val names: List<String>,
    val family: String,
    val protocol: QcyProtocol,
    val form: Form,
    val anc: List<QcyNoiseMode>,
    val touch: QcyTouch?,
    val balance: Boolean,
    val settings: List<QcySetting>,
    /** The control panel has the timed power off (item 14): the earbuds turn off once the minutes are up. */
    val sleepTimer: Boolean = false,
    val eq: QcyEqSpec? = null,
    /** Items whose off value is 00 rather than QCY's 02 (the Capsule3 Pro's LDAC and multipoint). */
    val zeroOff: Set<Int> = emptySet(),
    /** Multipoint and LDAC are one either-or setting: a multipoint write first sends LDAC the other way (the Capsule3 Pro). */
    val multipointSwapsLdac: Boolean = false,
    /** The EQ goes as SOUNDPEATS' full curve on the EQ characteristic, type 00 (docs/soundpeats/PROTOCOL.md section 10.5). */
    val soundpeatsEq: Boolean = false,
) {
    /** The vendor id as four hex digits, as PairFree's tables and reports name the model. */
    val code: String get() = vendorId.toString(16).uppercase().padStart(4, '0')

    public fun has(setting: String): Boolean = settings.any { it.setting == setting }

    public fun item(setting: String): Int? = settings.firstOrNull { it.setting == setting }?.item

    public fun mode(name: String): QcyNoiseMode? = anc.firstOrNull { it.mode == name }
}

/** Every QCY model PairFree knows, and how a paired device is recognised as QCY's. */
public object QcyModels {
    public val ALL: List<QcyModel> get() = QCY_MODELS

    /** The model that advertises [vendorId], or null. */
    public fun of(vendorId: Int?): QcyModel? = QCY_MODELS.firstOrNull { it.vendorId == vendorId }

    /** The model with [code] (four hex digits), or null. */
    public fun ofCode(code: String?): QcyModel? = code?.toIntOrNull(16)?.let { v -> QCY_MODELS.firstOrNull { it.vendorId == v } }

    /** The models a Bluetooth [name] can be: several vendor ids share a name, and the advert decides. */
    public fun fromName(name: String): List<QcyModel> {
        val n = name.trim()
        return QCY_MODELS.filter { m -> m.names.any { it.equals(n, ignoreCase = true) } }
    }

    /** True for a paired device whose name is QCY's: it starts with "QCY", or it is a model's. */
    public fun identify(name: String): Boolean {
        val n = name.trim()
        return n.startsWith(BRAND, ignoreCase = true) || fromName(n).isNotEmpty()
    }

    /**
     * The screens' [Model] for a vendor id (four hex digits) or a name as PairFree shows it, with [form] for
     * one PairFree does not know.
     */
    public fun model(codeOrName: String?, form: Form? = null): Model? {
        val n = codeOrName?.takeIf { it.isNotBlank() } ?: return null
        val known = ofCode(n.takeIf { it.length == CODE_DIGITS }) ?: fromName(n).firstOrNull()
        return Model(known?.code ?: n, listOf(known?.name ?: n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.EARBUDS)
    }

    private const val BRAND = "QCY"
    private const val FAMILY = "QCY"
    private const val CODE_DIGITS = 4
}
