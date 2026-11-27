package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * A Sennheiser model on GAIA v3 (data/sennheiser/devices.json). [properties] are the PairFree properties (data/sennheiser/commands.json ids) the app's own
 * product file lists for it, or null for a model only Smart Control Plus names: PairFree then reads every property and shows the ones that answer.
 */
public class SennheiserModel(
    public val name: String,
    public val form: Form,
    public val names: List<String>,
    public val properties: Set<String>?,
    public val eq: SennheiserModelEq? = null,
    /** The highest sidetone level the product's screen offers, or null (no sidetone). */
    public val sidetoneMax: Int? = null,
    /** How the product's screen sets the transparency level: "slider" (`ANC_Transparency`, 0 to 100) or "levels" (`TransparentHearing`: 1, 50 or 100), or null. */
    public val transparency: String? = null,
    /** The touch and button functions the product's class offers (`availableActions`), or null when it offers none. */
    public val touch: List<SennheiserTouchAction>? = null,
    /** The shared feature files the product lists (`find-device-1`, `sound-personalization-2`, `audio-mode-2`...) with the firmware each needs. */
    public val features: Map<String, String> = emptyMap(),
    /** The product's file has the paired devices commands (multipoint), the fit test, the sport sensors. */
    public val peers: Boolean = false,
    public val fitTest: Boolean = false,
    public val sensors: Boolean = false,
    /** Set for the products Android opens over Bluetooth LE only (`DeviceConfig`): the service and characteristics of their GATT channel. */
    public val ble: SennheiserBle? = null,
)

/** The GATT channel of a Sennheiser product: service, write, notify, the encrypted data characteristic whose read starts the bonding, and the pairing characteristic of Sennheiser's own bonding steps. */
public class SennheiserBle(public val service: String, public val write: String, public val notify: String, public val data: String, public val pairing: String)

/** A function a product's touch controls can be set to, the gestures it is allowed on (none listed: any) and the firmware it needs where its class says so. */
public class SennheiserTouchAction(public val action: String, public val gestures: List<String>, public val minFirmware: String?)

public object SennheiserModels {
    public val ALL: List<SennheiserModel> get() = SENNHEISER_MODELS

    /** The model Smart Control maps the Bluetooth [name] to, or null. */
    public fun fromName(name: String): SennheiserModel? = ALL.firstOrNull { m -> m.names.any { it.equals(name.trim(), ignoreCase = true) } }

    public fun model(name: String): Model? = fromName(name)?.let { Model(it.name, listOf(it.name), "Sennheiser", fitKeepsAnc = true, form = it.form) }

    /** The RFCOMM service every one of them opens. */
    public fun service(): String = SennheiserWire.CLASSIC_UUID
}
