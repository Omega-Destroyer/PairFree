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
    /** Sounds the maker's app makes itself: ten gains in tenths of a dB, written as a curve of the user's own. */
    internal val curves: Map<String, List<Int>> = emptyMap(),
    /** The slot the maker's app saves each of [curves] in. */
    internal val curveIds: Map<String, Int> = emptyMap(),
    /** The values the maker's app can write for each touch gesture; empty where PairFree has no file of the app's for the model. */
    internal val gestures: Map<String, List<Int>> = emptyMap(),
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

    /** The words only Huawei's and Honor's audio products carry: a newer one than the table is listed for a report alone. */
    private val LINES = listOf("FreeBuds", "FreeClip", "FreeLace", "FreeArc", "FlyPods")

    /** A name of Huawei's that the table does not hold, as a product that is only read; null for any other name. */
    public fun unknown(name: String): HuaweiProduct? {
        val words = name.split(' ', '-', '_')
        return if (words.none { word -> LINES.any { it.equals(word, ignoreCase = true) } }) null
        else HuaweiProduct(UNKNOWN, name.trim(), emptyList(), Form.EARBUDS, setOf("report_only", "modern_battery"), emptyMap())
    }

    private const val UNKNOWN = "unknown"

    /** Standard SPP is used by many vendors. Only the known product names identify Huawei. */
    public fun fromName(name: String): HuaweiProduct? {
        val trimmed = name.trim()
        val unbranded = if (trimmed.startsWith("HUAWEI ", ignoreCase = true)) trimmed.drop(7) else trimmed
        return all.firstOrNull { product ->
            product.names.any { it.equals(trimmed, ignoreCase = true) || it.equals(unbranded, ignoreCase = true) }
        }
    }
}
