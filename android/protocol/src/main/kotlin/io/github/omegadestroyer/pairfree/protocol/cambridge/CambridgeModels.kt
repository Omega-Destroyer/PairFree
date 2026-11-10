package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/** Legacy models directly controlled by Melomania 2.8.12, not the newer Melomania Connect family. */
public data class CambridgeModel(val name: String, val names: List<String>, val ambient: Boolean, val controlCount: Int)

public object CambridgeModels {
    public val ALL: List<CambridgeModel> get() = CAMBRIDGE_MODELS

    public fun fromName(name: String): CambridgeModel? = ALL.firstOrNull { m ->
        m.name.equals(name.trim(), ignoreCase = true) || m.names.any { it.equals(name.trim(), ignoreCase = true) }
    }

    public fun model(name: String): Model? = fromName(name)?.let {
        Model(it.name, listOf(it.name), "Cambridge Audio", fitKeepsAnc = true, form = Form.EARBUDS)
    }

    /** Neither service is brand-specific. Only use this after identifying a known model by name. */
    public fun service(uuids: List<String>): String =
        uuids.firstOrNull { it.equals(CambridgeWire.GAIA_UUID, ignoreCase = true) } ?: CambridgeWire.SPP_UUID
}
