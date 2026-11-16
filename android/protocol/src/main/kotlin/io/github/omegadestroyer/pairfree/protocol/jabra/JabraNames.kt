package io.github.omegadestroyer.pairfree.protocol.jabra

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * Finding Jabra's products among the paired devices. Jabra's earbuds and headphones are Bluetooth Classic devices named after the product ("Jabra Elite 75t");
 * the GNP service rides the standard serial port, so the name is all there is to go by (docs/jabra/PROTOCOL.md). Those with a number in the name keep it exact:
 * "Jabra Elite 7 Pro" is not "Jabra Elite 7 Active".
 */
public fun JabraModels.fromName(name: String): JabraModel? {
    val n = name.trim().lowercase().replace(Regex("\\s+"), " ")
    // The longest product name the Bluetooth name starts with, as a whole word: "Jabra Elite Active 75t" is not "Jabra Elite 75t".
    return ALL.filter { m ->
        val p = m.name.lowercase()
        n == p || (n.startsWith(p) && !n[p.length].isLetterOrDigit() && n[p.length] != '+')
    }.maxByOrNull { it.name.length }
}

/** The shared screens' model for a Jabra product: earbuds, headphones or neckbands. */
public fun JabraModels.model(name: String): Model? = fromName(name)?.let { m ->
    Model(m.name, listOf(m.name), "Jabra", fitKeepsAnc = false, form = if (m.kind == "headset") Form.HEADPHONES else Form.EARBUDS)
}

/** The products PairFree can talk to over GNP (the others speak GAIA, which comes later). */
public val JabraModel.gnp: Boolean get() = variants.any { it.protocol == "gnp" }
