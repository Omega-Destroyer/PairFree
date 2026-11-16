package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * A Sennheiser model on GAIA v3 (data/sennheiser/devices.json). [properties] are the PairFree properties (data/sennheiser/commands.json ids) the app's own
 * product file lists for it, or null for a model only Smart Control Plus names: PairFree then reads every property and shows the ones that answer.
 */
public class SennheiserModel(public val name: String, public val form: Form, public val names: List<String>, public val properties: Set<String>?, public val eq: SennheiserModelEq? = null)

public object SennheiserModels {
    public val ALL: List<SennheiserModel> get() = SENNHEISER_MODELS

    /** The model Smart Control maps the Bluetooth [name] to, or null. */
    public fun fromName(name: String): SennheiserModel? = ALL.firstOrNull { m -> m.names.any { it.equals(name.trim(), ignoreCase = true) } }

    public fun model(name: String): Model? = fromName(name)?.let { Model(it.name, listOf(it.name), "Sennheiser", fitKeepsAnc = true, form = it.form) }

    /** The RFCOMM service every one of them opens. */
    public fun service(): String = SennheiserWire.CLASSIC_UUID
}
