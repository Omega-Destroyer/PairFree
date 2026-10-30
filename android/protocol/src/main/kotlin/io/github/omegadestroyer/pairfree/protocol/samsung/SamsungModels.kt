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
 * @property layout the state layout Samsung's own plugin reads, where PairFree has read it (`buds4` for the
 *   Buds4 family and the models only that plugin knows), else null for GalaxyBudsClient's.
 * @property matchNames more Bluetooth names Samsung's plugin matches, besides [baseName].
 * @property form earbuds, or a headset with one battery.
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
    val layout: String? = null,
    val matchNames: List<String> = emptyList(),
    val form: Form = Form.EARBUDS,
) {
    /** The layout and writes of Samsung's own plugin for the Buds4 family (docs/samsung/PROTOCOL.md section 12). */
    public val buds4Layout: Boolean get() = layout == "buds4"

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

    /** Every model as a [Model] for the screens. The earbuds run their fit test as they are, noise mode and all. */
    public val TABLE: List<Model> by lazy { SAMSUNG_MODELS.map { Model(it.model, it.names, "SAMSUNG", fitKeepsAnc = true, form = it.form) } }

    public fun of(model: String?): SamsungModel? = SAMSUNG_MODELS.firstOrNull { it.model == model }

    public fun byDevice(device: String): SamsungModel? = SAMSUNG_MODELS.firstOrNull { it.device == device }

    public fun model(code: String?): Model? = TABLE.firstOrNull { it.code == code }

    /**
     * The model of a paired device from its [uuids], the service list Android keeps for it, and its Bluetooth [name]
     * (docs/samsung/PROTOCOL.md section 4), or null when it is not Galaxy Buds:
     * 1. The model id a Buds2 or later publishes in its service list ([deviceIdIn]), as Samsung's plugin reads it.
     * 2. Else the name ([fromName]), when the device has a service only Samsung's earbuds have, or the name says
     *    Galaxy or Samsung. The Buds+, Buds Live and Buds Pro talk on the standard serial service, which many
     *    earbuds offer, and their names are matched by part ("Buds Pro"), so a "Pixel Buds Pro" with that service,
     *    or with no service list cached yet, passed for Galaxy Buds (GitHub issue #4).
     * 3. Else, for a pair with the Buds2-and-later service, what Samsung's plugin falls back to: a Buds2 Pro when
     *    it also has hands-free and LE Audio, otherwise a Buds2.
     */
    public fun identify(name: String, uuids: List<String>): SamsungModel? {
        val list = uuids.map { it.lowercase() }
        deviceIdIn(list)?.let { id -> fromDeviceId(id)?.let { return it } }
        fromName(name)?.let { m ->
            val service = m.serviceUuid.lowercase()
            val ownService = service != STANDARD_SERIAL && service in list
            val samsungName = SAMSUNG_WORDS.any { name.contains(it, ignoreCase = true) }
            if (ownService || (samsungName && (list.isEmpty() || service in list))) return m
        }
        if (SERVICE_NEW !in list) return null
        return of(if (HANDS_FREE in list && LE_AUDIO in list) "SM-R510" else "SM-R177")
    }

    /**
     * The model id in a service list: a Buds2 or later publishes one as the last four hex digits of a service
     * `d908aab5-7a90-4cbe-8641-86a553db....`. It is one of the colour codes in each model's [SamsungModel.colors].
     */
    public fun deviceIdIn(uuids: List<String>): Int? = uuids.firstNotNullOfOrNull { u ->
        u.lowercase().takeIf { it.startsWith(DEVICE_ID_PREFIX) }?.removePrefix(DEVICE_ID_PREFIX)?.toIntOrNull(16)
    }

    /** The model with the colour code [id], the model id a pair publishes and its state repeats. */
    public fun fromDeviceId(id: Int): SamsungModel? = SAMSUNG_MODELS.firstOrNull { id in it.colors }

    /**
     * The model for a paired device's Bluetooth [name], or null when it is not a Galaxy Buds name.
     * As in GalaxyBudsClient, the name must contain the model's base name, or one of the names Samsung's
     * plugin matches, and longer names are tried first, so `Buds3 Pro` wins over `Buds3`. Other brands' names can
     * hold a base name too (CMF Buds Pro 2), so [identify] checks the service list first.
     */
    public fun fromName(name: String): SamsungModel? =
        SAMSUNG_MODELS.flatMap { m -> (listOf(m.baseName) + m.matchNames).map { it to m } }
            .sortedByDescending { it.first.length }
            .firstOrNull { name.contains(it.first, ignoreCase = true) }?.second

    private const val DEVICE_ID_PREFIX = "d908aab5-7a90-4cbe-8641-86a553db"
    private const val STANDARD_SERIAL = "00001101-0000-1000-8000-00805f9b34fb"
    private val SAMSUNG_WORDS = listOf("Galaxy", "Samsung")
    private const val SERVICE_NEW = "2e73a4ad-332d-41fc-90e2-16bef06523f2"
    private const val HANDS_FREE = "0000111e-0000-1000-8000-00805f9b34fb"
    private const val LE_AUDIO = "0000184e-0000-1000-8000-00805f9b34fb"

    /** The service the earbuds offer for their settings, as GalaxyBudsClient opens it. */
    public fun serviceFor(model: SamsungModel): String = model.serviceUuid

    /**
     * [name] for a device report, with the four hex digits Samsung puts in the earbuds' name, such as
     * `Galaxy Buds2 Pro (A1B2)`, masked: they look like part of the Bluetooth address.
     */
    public fun maskedName(name: String): String = name.replace(ADDRESS_DIGITS, "(xxxx)")

    private val ADDRESS_DIGITS = Regex("""\([0-9A-Fa-f]{4}\)""")
}
