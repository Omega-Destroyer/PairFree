package io.github.omegadestroyer.pairfree.protocol.onemore

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One 1MORE model from data/1more/devices.json: the Bluetooth [name] 1MORE's app matches on, the product
 * [model], and what 1MORE's product list says it has (docs/1more/PROTOCOL.md section 5). [listenModes] are
 * 1MORE's noise modes by name (off, adaptive, strong, mild, wind, transparent, pass_through,
 * voice_enhancement); [connection] the connection modes beside standard (game, ldac); [touch] whether the
 * touch controls are set per earbud ("each") or for both alike ("both"); [eq] the kinds of EQ the product list
 * gives it; [eqPresets] its preset sounds and [eqBands] its custom EQ, from 1MORE's app code. [tuya] marks a model 1MORE's app reaches through Tuya's platform instead,
 * which PairFree does not control.
 */
public data class OneMoreModel(
    val name: String,
    val model: String,
    val form: Form,
    val case: Boolean,
    val tuya: Boolean,
    val listenModes: List<String>,
    val connection: List<String>,
    val autoplay: Boolean,
    val spatialAudio: Boolean,
    val sleepDetection: Boolean,
    val dualDevice: Boolean,
    val hotWord: Boolean,
    val touch: String?,
    val touchAssistant: Boolean,
    val eq: List<String>,
    val earReturn: Boolean,
    val volumeLimit: Boolean,
    val eqPresets: List<String> = emptyList(),
    val eqBands: OneMoreEqBands? = null,
)

/**
 * A model's custom EQ as 1MORE's app writes it (docs/1more/PROTOCOL.md section 4): the bands' frequencies (Hz) and Qs
 * (hundredths), the raw gain that is 0 dB, per-band offsets on top of it (the HQ36's), and whether the gains go in
 * whole dB ([wholeDb]) or hundredths. Gains go from -3 to 3 dB.
 */
public data class OneMoreEqBands(val hz: List<Int>, val q: List<Int>, val zero: Int, val offsets: List<Int>, val wholeDb: Boolean)

/** Every 1MORE model PairFree knows, and how a paired device is recognised as one. */
public object OneMoreModels {
    public val ALL: List<OneMoreModel> get() = ONEMORE_MODELS

    /** The model a Bluetooth [name] is, as 1MORE's app matches it (the whole name), or null. */
    public fun fromName(name: String): OneMoreModel? {
        val n = name.trim()
        return ONEMORE_MODELS.firstOrNull { it.name.equals(n, ignoreCase = true) }
    }

    /** The model PairFree controls under a Bluetooth [name], or null: the Tuya models are left out. */
    public fun identify(name: String): OneMoreModel? = fromName(name)?.takeUnless { it.tuya }

    /** The screens' [Model] for a model name or product model as PairFree shows it, with [form] for one PairFree does not know. */
    public fun model(codeOrName: String?, form: Form? = null): Model? {
        val n = codeOrName?.takeIf { it.isNotBlank() } ?: return null
        val known = fromName(n) ?: ONEMORE_MODELS.firstOrNull { it.model.equals(n, ignoreCase = true) }
        return Model(known?.model ?: n, listOfNotNull(known?.name ?: n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.EARBUDS)
    }

    private const val FAMILY = "1MORE"
}
