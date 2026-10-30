package io.github.omegadestroyer.pairfree.protocol.bose

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One Bose model from data/bose/devices.json: the product id it reports (ProductInfo 0.3), and the
 * name and codename Bose's app gives it.
 */
public data class BoseModel(val productId: Int, val name: String, val codename: String, val form: Form) {
    /** The name without "Bose ", as the screens show it beside the brand. */
    public val shortName: String get() = name.removePrefix("Bose ")
}

/** Every Bose model PairFree knows, and how a paired device is recognised as Bose's. */
public object BoseModels {
    public val ALL: List<BoseModel> get() = BOSE_MODELS

    /** The model with [productId], or null for one PairFree does not know. */
    public fun of(productId: Int?): BoseModel? = BOSE_MODELS.firstOrNull { it.productId == productId }

    /**
     * True for a paired device that is Bose's: one of the services Bose's app looks for (BluetoothService
     * `isBoseDevice`: a 16-bit UUID `FEBE` or `FDD2`, or its "Bragi" UUID), else "Bose" in its
     * Bluetooth name. The model is known only once the device reports its product id.
     */
    public fun isBose(name: String, uuids: List<String>): Boolean = hasService(uuids) || name.contains(BRAND, ignoreCase = true)

    /** Whether the device lists one of the services Bose's app looks for. */
    public fun hasService(uuids: List<String>): Boolean =
        uuids.any { u -> val l = u.lowercase(); l.startsWith(BOSE_SHORT_1) || l.startsWith(BOSE_SHORT_2) || l.startsWith(BRAGI) }

    /**
     * The screens' [Model] for a model name as PairFree shows it ([BoseModel.name], or the name the
     * device gave), with [form] for one PairFree does not know.
     */
    public fun model(name: String?, form: Form? = null): Model? {
        val n = name?.takeIf { it.isNotBlank() } ?: return null
        val known = BOSE_MODELS.firstOrNull { it.name == n }
        return Model(n, listOf(n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.HEADPHONES)
    }

    private const val BOSE_SHORT_1 = "0000febe-"
    private const val BOSE_SHORT_2 = "0000fdd2-"
    private const val BRAGI = "f4c93a79-d04f-4565-"
    private const val BRAND = "Bose"
    private const val FAMILY = "BOSE"
}
