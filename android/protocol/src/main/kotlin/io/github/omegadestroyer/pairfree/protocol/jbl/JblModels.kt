package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One JBL model from data/jbl/devices.json: its name and product ids as JBL's app lists them, whether it
 * is controlled over Bluetooth LE ([gatt]) or the classic serial channel, and what PairFree can set on it
 * and how (docs/jbl/PROTOCOL.md section 5).
 */
public data class JblModel(
    val name: String,
    val pids: List<Int>,
    val form: Form,
    val gatt: Boolean,
    val family: Family,
    val noise: Noise?,
    val modes: List<Mode>,
    val autoOff: AutoOff?,
    val autoOffMinutes: List<Int>,
    val autoPlayPause: Boolean,
) {
    /**
     * How the model's settings go: one command each ([DIRECT]), Harman's feature records, command 06
     * ([FEATURE]), or JBL's protocol 4 ([P4]).
     */
    public enum class Family { DIRECT, FEATURE, P4 }

    /**
     * How noise control goes: command 91's modes, commands 31 and 32 ([OLD]; [CLUB] turns it off with 31
     * alone), or protocol 4's ambient sound control and mode features ([ASC]).
     */
    public enum class Noise { MODES, OLD, CLUB, ASC }

    /** The noise modes JBL's app offers on the model, besides off. */
    public enum class Mode { ANC, AMBIENT_AWARE, TALK_THRU }

    /** How auto power-off is written: one byte ([V1]), three bytes ([V2]) or as a feature record. */
    public enum class AutoOff { V1, V2, FEATURE }

    /** The name without "JBL ", as the screens show it beside the brand. */
    public val shortName: String get() = name.removePrefix("JBL ")
}

/** Every JBL model PairFree knows, and how a paired device is recognised as one. */
public object JblModels {
    public val ALL: List<JblModel> get() = JBL_MODELS

    /** The model with product id [pid], or null for one PairFree does not know. */
    public fun of(pid: Int?): JblModel? = JBL_MODELS.firstOrNull { pid != null && pid in it.pids }

    /**
     * The model a paired device's Bluetooth name names, or null. JBL's devices carry the model's name
     * ("JBL Tune Flex 2"); case, spaces and dashes do not count, so "JBL LIVE460NC" is the Live 460NC.
     * Two models that share a name are told apart by the product id the device reports once connected.
     */
    public fun fromName(name: String): JblModel? {
        val key = normal(name)
        return JBL_MODELS.firstOrNull { normal(it.name) == key }
    }

    /** The screens' [Model] for a model name as PairFree shows it, with [form] for one PairFree does not know. */
    public fun model(name: String?, form: Form? = null): Model? {
        val n = name?.takeIf { it.isNotBlank() } ?: return null
        val known = JBL_MODELS.firstOrNull { it.name == n } ?: fromName(n)
        return Model(n, listOf(n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.EARBUDS)
    }

    private fun normal(name: String): String = name.uppercase().filter { it.isLetterOrDigit() }

    private const val FAMILY = "JBL"
}
