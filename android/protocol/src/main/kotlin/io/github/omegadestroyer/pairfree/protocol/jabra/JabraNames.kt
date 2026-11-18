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

/** Which noise modes a variant's firmware snapshot offers: cancelling and HearThrough. */
public class JabraNoise(public val anc: Boolean, public val transparency: Boolean)

/** From the snapshots' forms: the ambience command (named in the loop), the older noise command (`13/87`) or only HearThrough's switch (`13/76`); none for the rest. */
public fun JabraVariant.noise(): JabraNoise {
    // The GAIA models have no snapshot: the definition's flags and loop say what they have.
    if (protocol == "gaia") return JabraNoise(anc = has("ffanc") || "anc" in noiseLoop, transparency = has("adjustable_ambience_mode") || "hearThrough" in noiseLoop)
    val profiles = JabraProfiles.ALL.filter { it.snapshot in snapshots }
    val settings = profiles.flatMap { p -> p.settings.mapNotNull { JabraSettings.BY_ID[it] } }
    if (profiles.any { "ambiencemodes" in it.caps }) return JabraNoise(anc = "anc" in noiseLoop || has("ffanc"), transparency = "hearThrough" in noiseLoop || has("adjustable_ambience_mode"))
    val anc = settings.firstOrNull { it.command == "ANC" && it.main == 0x13 && it.virtual == null }
    if (anc != null) return JabraNoise(true, anc.options.any { it.code == 2 || it.code == 3 })
    return JabraNoise(false, settings.any { it.command == "HEAR_THROUGH" && it.widget == JabraWidget.BOOL })
}

/** The products PairFree can talk to over GNP (the others speak GAIA, which comes later). */
public val JabraModel.gnp: Boolean get() = variants.any { it.protocol == "gnp" }
