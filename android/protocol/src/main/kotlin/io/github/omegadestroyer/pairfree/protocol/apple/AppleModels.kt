package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One model from data/apple/devices.json: its name, the A numbers it reports in its information packet
 * (AirPods only, from LibrePods), its Bluetooth product id (from MagicPodsCore), and whether it has
 * Adaptive Audio.
 */
public data class AppleModel(val name: String, val form: Form, val modelNumbers: List<String>, val productId: Int, val adaptive: Boolean)

/** Every AirPods and Beats model PairFree knows, and how a paired device is recognised as one. */
public object AppleModels {
    public val ALL: List<AppleModel> get() = APPLE_MODELS

    /** The model that reports [number] ("A3048"), or null. */
    public fun byModelNumber(number: String?): AppleModel? = APPLE_MODELS.firstOrNull { number != null && number in it.modelNumbers }

    /**
     * True for a paired device that speaks Apple's accessory protocol: it lists Apple's accessory
     * service ([AppleWire.SERVICE_UUID]), as LibrePods checks, else "AirPods" is in its name. Beats are
     * known by the service alone: the ones on Beats' own chips use another protocol.
     */
    public fun identify(name: String, uuids: List<String>): Boolean =
        uuids.any { it.equals(AppleWire.SERVICE_UUID, ignoreCase = true) } || name.contains(AIRPODS, ignoreCase = true)

    /** The screens' [Model] for a model name as PairFree shows it, with [form] for one PairFree does not know. */
    public fun model(name: String?, form: Form? = null): Model? {
        val n = name?.takeIf { it.isNotBlank() } ?: return null
        val known = APPLE_MODELS.firstOrNull { it.name == n }
        return Model(n, listOf(n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.EARBUDS)
    }

    private const val AIRPODS = "AirPods"
    private const val FAMILY = "APPLE"
}
