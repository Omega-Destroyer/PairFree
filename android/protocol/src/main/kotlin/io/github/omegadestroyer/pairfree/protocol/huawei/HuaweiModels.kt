package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

public class HuaweiProduct internal constructor(
    public val id: String,
    public val name: String,
    aliases: List<String>,
    public val form: Form,
    internal val features: Set<String>,
    internal val presets: Map<Int, String>,
) {
    public val names: List<String> = listOf(name) + aliases
    public val model: Model = Model(id, names, "Huawei MBB", form = form, reportOnly = has("report_only"))
    public val noiseModes: List<Int>
        get() = when {
            has("anc") && has("two_modes") -> listOf(0, 1)
            has("anc") -> listOf(0, 1, 2)
            has("legacy_anc") -> listOf(0, 1)
            else -> emptyList()
        }
    public val mayHaveEq: Boolean get() = presets.isNotEmpty() || has("custom_eq")
    internal fun has(feature: String): Boolean = feature in features
}

public object HuaweiModels {
    public val all: List<HuaweiProduct> get() = HUAWEI_PRODUCTS

    public fun of(id: String?): HuaweiProduct? = all.firstOrNull { it.id == id }

    /** Standard SPP is used by many vendors. Only the known product names identify Huawei. */
    public fun fromName(name: String): HuaweiProduct? {
        val trimmed = name.trim()
        val unbranded = if (trimmed.startsWith("HUAWEI ", ignoreCase = true)) trimmed.drop(7) else trimmed
        return all.firstOrNull { product ->
            product.names.any { it.equals(trimmed, ignoreCase = true) || it.equals(unbranded, ignoreCase = true) }
        }
    }
}
