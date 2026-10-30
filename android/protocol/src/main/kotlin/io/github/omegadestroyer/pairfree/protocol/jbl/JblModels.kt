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
    /** JBL's protocol 4 BLE security mode (1 to 3), which adds a connection step (docs/jbl/PROTOCOL.md section 7); 0 for none. */
    val security: Int = 0,
    /**
     * The other settings JBL's app shows for the model and its control class sends (devices.json settings:
     * smart_audio_video, voice_prompts, balance, voice_aware, safe_sound, spatial, sound_effect, audio_limiter,
     * speak_to_chat, hi_res, find_buds; [JblExtras]).
     */
    val extras: Set<String> = emptySet(),
    /** Smart Audio & Video's normal, audio and video settings: AAC, SBC and the latencies JBL's app writes (product_list.json). */
    val smartAudioVideo: Map<String, List<Int>> = emptyMap(),
    /** The sound effects the model lists, by name (standard, treble, bass). */
    val effects: List<String> = emptyList(),
    /** The noise cycle the ambient touch set starts with: 11 noise cancelling and ambient sound, 13 Ambient Aware and off. */
    val gestureNoise: Int = 11,
    /**
     * Airoha models: whether JBL's app turns on the push of changes and reads which earbud it talks to on connecting
     * (devices.json push_status; not on the Quantum 910 headsets).
     */
    val racePush: Boolean = true,
    /** Other names the device may carry, such as the "Wireless" JBL's app adds to the Quantum 910 headsets (devices.json names). */
    val names: List<String> = emptyList(),
) {
    /**
     * How the model's settings go: one command each ([DIRECT]), Harman's feature records, command 06
     * ([FEATURE]), JBL's protocol 4 ([P4]), or Airoha's RACE commands ([AIROHA], docs/jbl/PROTOCOL.md section 10).
     */
    public enum class Family { DIRECT, FEATURE, P4, AIROHA }

    /**
     * How noise control goes: command 91's modes, commands 31 and 32 ([OLD]; [CLUB] turns it off with 31
     * alone), protocol 4's ambient sound control and mode features ([ASC]), or protocol 4's mode feature
     * alone ([MODE4], the base General4Control write), or RACE's noise filters ([RACE]).
     */
    public enum class Noise { MODES, OLD, CLUB, ASC, MODE4, RACE }

    /** The noise modes JBL's app offers on the model, besides off. */
    public enum class Mode { ANC, AMBIENT_AWARE, TALK_THRU }

    /**
     * How auto power-off is written: one byte ([V1]), three bytes ([V2]), a command-06 feature record ([FEATURE]), a
     * protocol 4 feature ([P4]), or RACE's, which depends on the chip family the earbuds report ([RACE]).
     */
    public enum class AutoOff { V1, V2, FEATURE, P4, RACE }

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
        return JBL_MODELS.firstOrNull { normal(it.name) == key } ?: JBL_MODELS.firstOrNull { m -> m.names.any { normal(it) == key } }
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
