package io.github.omegadestroyer.pairfree.protocol.oneplus

/** One noise mode of a model: its protocol index, a key for its kind ("off", "noise_cancelling", "smart", ...) and its sub-modes. */
public data class OneplusNoise(val index: Int, val type: String, val children: List<OneplusNoise> = emptyList())

/** One equalizer preset: the byte HeyMelody writes for it and the name its English strings give. */
public data class OneplusPreset(val index: Int, val name: String)

/**
 * One OnePlus or OPPO earbuds model from HeyMelody's bundled catalog (data/oneplus/devices.json). [id] is the product id the earbuds
 * report (three bytes as hex); [names] are the Bluetooth names the catalog lists.
 */
public data class OneplusModel(
    val id: String,
    val name: String,
    val names: List<String>,
    val brand: String,
    val hidden: Boolean,
    val noise: List<OneplusNoise>,
    val presets: List<OneplusPreset>,
    val customEq: Boolean,
    val gameMode: Boolean,
)

public object OneplusModels {
    public val ALL: List<OneplusModel> get() = ONEPLUS_MODELS

    /** The model for a product id the earbuds reported (case does not matter), preferring a listed model over a hidden one. */
    public fun byId(id: String): OneplusModel? = ALL.filter { it.id.equals(id, ignoreCase = true) }.let { l -> l.firstOrNull { !it.hidden } ?: l.firstOrNull() }

    /** The model whose catalog name is the whole Bluetooth [name] or the name with the owner's part around it, longest name first. */
    public fun fromName(name: String?): OneplusModel? {
        val n = normal(name ?: return null)
        val matches = ALL.flatMap { m -> m.names.map { it to m } }.sortedByDescending { it.first.length }
        return matches.firstOrNull { name.equals(it.first, ignoreCase = true) }?.second
            ?: matches.firstOrNull { n == normal(it.first) }?.second
            ?: matches.firstOrNull { n.contains(normal(it.first)) }?.second
    }

    /** The screens' [Model] for [name], or null when no catalog model has it. */
    public fun model(name: String): io.github.omegadestroyer.pairfree.protocol.Model? = fromName(name)?.let {
        io.github.omegadestroyer.pairfree.protocol.Model(it.name, listOf(it.name), if (it.brand == "OPPO") "OPPO" else "OnePlus", fitKeepsAnc = true, form = io.github.omegadestroyer.pairfree.protocol.Form.EARBUDS)
    }

    private fun normal(s: String) = s.lowercase().replace(Regex("""[^a-z0-9+]+"""), " ").trim()
}
