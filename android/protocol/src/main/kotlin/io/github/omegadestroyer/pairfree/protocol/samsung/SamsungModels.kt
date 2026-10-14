package io.github.omegadestroyer.pairfree.protocol.samsung

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One Galaxy Buds model from data/samsung/devices.json, with GalaxyBudsClient's specification of it
 * (github.com/timschneeb/GalaxyBudsClient, Tim Schneeberger, GPL-3.0).
 *
 * @property model Samsung's model number, such as `SM-R630`.
 * @property device GalaxyBudsClient's name for the model, such as `Buds3Pro`.
 * @property generation GalaxyBudsClient's order of the models; newer ones are higher.
 * @property baseName the part of the Bluetooth name GalaxyBudsClient matches.
 * @property legacy the Galaxy Buds (2019) framing: start FE, end EE, a type and a one-byte length.
 * @property maxAmbientVolume the highest ambient sound volume step (one more with extra loud).
 * @property touchHold the touch-and-hold options as (byte, option).
 * @property colors the colours by the device id the state reports.
 * @property features each feature with the lowest state revision that has it (null: every revision).
 */
public data class SamsungModel(
    val model: String,
    val names: List<String>,
    val device: String,
    val generation: Int,
    val baseName: String,
    val serviceUuid: String,
    val legacy: Boolean,
    val maxAmbientVolume: Int,
    val touchHold: List<Pair<Int, String>>,
    val colors: Map<Int, String>,
    val features: Map<String, Int?>,
) {
    /** True when the model has [feature] at state [revision], as GalaxyBudsClient decides it. */
    public fun supports(feature: String, revision: Int?): Boolean {
        if (feature !in features) return false
        val min = features[feature] ?: return true
        return revision == null || revision >= min
    }

    /** At least as new as [other], by GalaxyBudsClient's order. */
    public fun atLeast(other: String): Boolean = generation >= (SamsungModels.byDevice(other)?.generation ?: Int.MAX_VALUE)

    public val name: String get() = names.first()
}

/** Every Galaxy Buds model PairFree knows, and how a paired device is matched to one. */
public object SamsungModels {
    public val ALL: List<SamsungModel> get() = SAMSUNG_MODELS

    /** Every model as a [Model] for the screens. */
    public val TABLE: List<Model> by lazy { SAMSUNG_MODELS.map { Model(it.model, it.names, "SAMSUNG", form = Form.EARBUDS) } }

    public fun of(model: String?): SamsungModel? = SAMSUNG_MODELS.firstOrNull { it.model == model }

    public fun byDevice(device: String): SamsungModel? = SAMSUNG_MODELS.firstOrNull { it.device == device }

    public fun model(code: String?): Model? = TABLE.firstOrNull { it.code == code }

    /**
     * The model for a paired device's Bluetooth [name], or null when it is not a Galaxy Buds name.
     * As in GalaxyBudsClient, the name must contain the model's base name, and longer base names are
     * tried first, so `Buds3 Pro` wins over `Buds3`. The Galaxy Buds2 and later can be renamed;
     * a renamed pair is not found.
     */
    public fun fromName(name: String): SamsungModel? =
        SAMSUNG_MODELS.sortedByDescending { it.baseName.length }.firstOrNull { name.contains(it.baseName, ignoreCase = true) }

    /** The service the earbuds offer for their settings, as GalaxyBudsClient opens it. */
    public fun serviceFor(model: SamsungModel): String = model.serviceUuid
}
