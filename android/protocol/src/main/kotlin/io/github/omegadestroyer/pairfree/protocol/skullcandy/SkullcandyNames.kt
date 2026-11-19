package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/** The SDK configurations (`data/skullcandy/sdk_devices.json`) whose features all come over AT lines: [SkullcandyAtSession] talks to these. */
private val AT_SDK_KEYS = setOf("GRIND", "GRIND_FUEL", "PUSH_ACTIVE", "CHP200")

/** True when Skullcandy's app drives this product with AT lines over the serial service (the Grind, Grind Fuel, Push Active and Crusher ANC 2). */
public val SkullcandyProduct.usesAt: Boolean get() = sdk in AT_SDK_KEYS

/** True when the app reaches this Airoha product with AT lines carried through RACE: the Airoha models without an SDK configuration of their own (32 of the 41). */
public val SkullcandyProduct.usesAtOverRace: Boolean get() = chip == "airoha" && sdk == null

/** The serial service PairFree opens for the product, or null while no session of ours speaks its protocol (docs/skullcandy/PROTOCOL.md sections 9 to 12). */
public val SkullcandyProduct.service: String? get() = if (usesAt) SkullcandyServices.AT else null

/** The services an AT-over-RACE product may list: the Bragi one, Airoha's own third-party one and the generic Airoha one; the first present is the one to open. */
public val RACE_SERVICES: List<String> = listOf(SkullcandyServices.RACE, "FFEEDDCC-BBAA-9988-7766-554433221100")

private val OVER_EAR = listOf("Crusher", "Hesh", "Aviator", "Riff", "PLYR", "SLYR", "Icon", "Venue", "Cassette")

/** Headphones by the names of the line (the app's manifest does not say); the rest are earbuds. */
public val SkullcandyProduct.form: Form get() = if (OVER_EAR.any { name.startsWith(it, ignoreCase = true) }) Form.HEADPHONES else Form.EARBUDS

/** The shared screens' model for a Skullcandy product. */
public fun SkullcandyModels.model(name: String): Model? = fromName(name)?.let { p ->
    Model(p.name, listOf(p.name) + p.alternateNames, "Skullcandy", fitKeepsAnc = false, form = p.form)
}
