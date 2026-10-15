package io.github.omegadestroyer.pairfree.protocol.sony

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One Sony model from data/sony/devices.json. The earbuds say themselves what they have, so a model
 * only names the product and says what it is.
 *
 * @property name the model as Sony writes it, such as `WH-1000XM4`.
 * @property names what its Bluetooth name contains.
 * @property form earbuds, or headphones (a neckband too) with one battery.
 * @property tableSet the table set it answered with in Gadgetbridge's notes, 1 or 2, or null where not known.
 */
public data class SonyModel(val name: String, val names: List<String>, val form: Form, val tableSet: Int? = null)

/** A paired device PairFree controls as Sony's: the service to open, and the model when its name says. */
public data class SonyDevice(val service: String, val model: SonyModel?)

/** Every Sony model PairFree knows, and how a paired device is recognised as Sony's. */
public object SonyModels {
    public val ALL: List<SonyModel> get() = SONY_MODELS

    public fun of(name: String?): SonyModel? = SONY_MODELS.firstOrNull { it.name == name }

    /**
     * The model for a paired device's Bluetooth [name]: the longest model name it contains, case
     * aside. A name starting with `LE_` is the earbuds' LE Audio side, which PairFree does not open.
     */
    public fun fromName(name: String): SonyModel? {
        if (name.startsWith(LE_PREFIX)) return null
        return SONY_MODELS.flatMap { m -> m.names.map { it to m } }
            .sortedByDescending { it.first.length }
            .firstOrNull { name.contains(it.first, ignoreCase = true) }?.second
    }

    /**
     * Sony's earbuds by their services ([uuids], as the phone lists them for a paired device) or their
     * [name]. Sony's app opens set 2's service where the earbuds offer it, else set 1's; PairFree does
     * the same. When the phone lists neither, the model's table set from data/sony/devices.json picks
     * one, else set 2, which the newer models use and Sony's app tries first.
     */
    public fun identify(name: String, uuids: List<String>): SonyDevice? {
        val model = fromName(name)
        val offers = uuids.map { it.lowercase() }
        val service = when {
            SonyWire.SET2_SERVICE.lowercase() in offers -> SonyWire.SET2_SERVICE
            SonyWire.SET1_SERVICE.lowercase() in offers -> SonyWire.SET1_SERVICE
            model == null || name.startsWith(LE_PREFIX) -> return null
            model.tableSet == 1 -> SonyWire.SET1_SERVICE
            else -> SonyWire.SET2_SERVICE
        }
        return SonyDevice(service, model)
    }

    /** The table set a service belongs to. */
    public fun tableSetOf(service: String?): Int = if (service.equals(SonyWire.SET1_SERVICE, ignoreCase = true)) 1 else 2

    /**
     * The screens' [Model] for a Sony model name, as the earbuds or their Bluetooth name give it. A model
     * not in the table still gets one, so the screens work; its form then comes from [form].
     */
    public fun model(name: String?, form: Form? = null): Model? {
        val n = name?.takeIf { it.isNotBlank() } ?: return null
        val known = of(n) ?: fromName(n)
        return Model(known?.name ?: n, listOf(known?.name ?: n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.HEADPHONES)
    }

    private const val LE_PREFIX = "LE_"
    private const val FAMILY = "SONY"
}
