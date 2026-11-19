package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/** The SDK configurations (`data/skullcandy/sdk_devices.json`) whose features all come over AT lines: [SkullcandyAtSession] talks to these. */
private val AT_SDK_KEYS = setOf("GRIND", "GRIND_FUEL", "PUSH_ACTIVE", "CHP200")

/** True when Skullcandy's app drives this product with AT lines over the serial service (the Grind, Grind Fuel, Push Active and Crusher ANC 2). */
public val SkullcandyProduct.usesAt: Boolean get() = sdk in AT_SDK_KEYS

/** True when the app's own AT client reaches this Airoha product over the standard serial port (`00001101-...`): the Airoha models without an SDK configuration, 28 of the 41 once the four Session speakers are left out (docs/skullcandy/PROTOCOL.md section 9.4). */
public val SkullcandyProduct.usesAtSerial: Boolean get() = chip == "airoha" && sdk == null && enumName !in SPEAKERS

/** True for the five BES models (Icon 180, Dime 540 ANC, Smokin' Buds 360 H and S, Push 180 Open), whose app speaks BES's TOTA protocol over a serial service of its own (docs/skullcandy/PROTOCOL.md section 12). */
public val SkullcandyProduct.usesBes: Boolean get() = chip == "bes"

/** The serial service PairFree opens for the product, or null while no session of ours speaks its protocol (docs/skullcandy/PROTOCOL.md sections 9 to 12). */
public val SkullcandyProduct.service: String? get() = if (usesAt) SkullcandyServices.AT else if (usesBes) BesTota.SERVICE else null

/** The serial services Skullcandy's Airoha RACE channel may list (for the models' custom commands, which need a second channel of their own): Bragi's and Airoha's. */
public val RACE_SERVICES: List<String> = listOf(SkullcandyServices.RACE, "FFEEDDCC-BBAA-9988-7766-554433221100")

/** The Session speakers: speakers are held back (owner, 2026-10-30), so they are found but get no session. */
private val SPEAKERS = setOf("session360", "session540", "session720", "session900")

private val OVER_EAR = listOf("Crusher", "Hesh", "Aviator", "Riff", "PLYR", "SLYR", "Icon", "Venue", "Cassette")

/** Headphones by the names of the line (the app's manifest does not say); the rest are earbuds. */
public val SkullcandyProduct.form: Form get() = if (enumName in SPEAKERS) Form.SPEAKER else if (OVER_EAR.any { name.startsWith(it, ignoreCase = true) }) Form.HEADPHONES else Form.EARBUDS

/** The shared screens' model for a Skullcandy product. */
public fun SkullcandyModels.model(name: String): Model? = fromName(name)?.let { p ->
    Model(p.name, listOf(p.name) + p.alternateNames, "Skullcandy", fitKeepsAnc = false, form = p.form)
}
