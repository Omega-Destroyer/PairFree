package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/** The SDK configurations (`data/skullcandy/sdk_devices.json`) whose features all come over AT lines: [SkullcandyAtSession] talks to these. */
private val AT_SDK_KEYS = setOf("GRIND", "GRIND_FUEL", "PUSH_ACTIVE", "CHP200")

/** True when Skullcandy's app drives this product with AT lines over the serial service (the Grind, Grind Fuel, Push Active and Crusher ANC 2). */
public val SkullcandyProduct.usesAt: Boolean get() = sdk in AT_SDK_KEYS

/**
 * True for the Airoha products without an SDK configuration, 28 of the 41 once the four Session speakers are left out: the app drives them through Airoha's own SDK alone, over Airoha's
 * serial service ([AIROHA_SPP]), as it does the Rail. Its AT client is only for the four models `getShouldUseATCommands` names (docs/skullcandy/PROTOCOL.md section 9.4, corrected 2026-11-20, issue #66).
 */
public val SkullcandyProduct.usesAirohaSdk: Boolean get() = chip == "airoha" && sdk == null && enumName !in SPEAKERS

/** True for the five BES models (Icon 180, Dime 540 ANC, Smokin' Buds 360 H and S, Push 180 Open), whose app speaks BES's TOTA protocol over a serial service of its own (docs/skullcandy/PROTOCOL.md section 12). */
public val SkullcandyProduct.usesBes: Boolean get() = chip == "bes"

/** True for the three products whose SDK configuration puts the settings over Bragi's own protobuf RPC (Method 360 ANC `T99_PLUS`, Method 540 ANC `T120`, Crusher 1080 ANC `CHP300`; docs/skullcandy/PROTOCOL.md section 15). */
public val SkullcandyProduct.usesRpc: Boolean get() = sdk in RPC_SDK_KEYS

private val RPC_SDK_KEYS = setOf("T99_PLUS", "T120", "CHP300")

/** True for the six older Qualcomm models, which Skullcandy's app drives over Bluetooth LE with its own C-Chip frames (docs/skullcandy/PROTOCOL.md section 17). */
public val SkullcandyProduct.usesQualcomm: Boolean get() = chip == "qualcomm"

/** True for the Rail (`T70`) and Rail ANC (`T80_PLUS`), whose SDK configurations put every feature on Airoha's RACE over Airoha's own serial service (docs/skullcandy/PROTOCOL.md section 16). */
public val SkullcandyProduct.usesRail: Boolean get() = sdk == "T70" || sdk == "T80_PLUS"

/** Airoha's own serial service, the Rail's control and RACE channel (the SDK's configuration of the Rail; the Rail ANC's own entry holds a variable the decompiler lost, so it is taken to be the same). */
public const val AIROHA_SERVICE: String = "FFEEDDCC-BBAA-9988-7766-554433221100"

/**
 * Airoha's serial service as Airoha's SDK names it (`com.airoha.liblinker.constant.c.b`): the app connects the SDK with it for every Airoha model but the Method 360 and 540 ANC
 * (`s2.java`, `ConnectionUUID`), and checks a device's services for it (`z4.v`). A user's Dime Evo lists it and no standard serial port (issue #66).
 */
public const val AIROHA_SPP: String = "00000000-0000-0000-0099-AABBCCDDEEFF"

/** The serial service PairFree opens for the product, or null while no session of ours speaks its protocol (docs/skullcandy/PROTOCOL.md sections 9 to 12). */
public val SkullcandyProduct.service: String? get() =
    if (usesAt) SkullcandyServices.AT else if (usesBes) BesTota.SERVICE else if (usesRpc) SkullcandyServices.RPC else if (usesRail) AIROHA_SERVICE else if (usesAirohaSdk) AIROHA_SPP else null

/** For the products on Airoha's SDK alone, the Airoha service the device lists ([AIROHA_SPP] first, as the app connects), else the product's own. */
public fun SkullcandyProduct.serviceFor(uuids: List<String>): String? =
    if (usesRail || usesAirohaSdk) listOf(AIROHA_SPP, AIROHA_SERVICE).firstOrNull { u -> uuids.any { it.equals(u, ignoreCase = true) } } ?: service else service

/** The serial services Skullcandy's Airoha RACE channel may list (for the models' custom commands, which need a second channel of their own): Bragi's and Airoha's. */
public val RACE_SERVICES: List<String> = listOf(SkullcandyServices.RACE, AIROHA_SPP, AIROHA_SERVICE)

/** The Session speakers: speakers are held back (owner, 2026-10-30), so they are found but get no session. */
private val SPEAKERS = setOf("session360", "session540", "session720", "session900")

private val OVER_EAR = listOf("Crusher", "Hesh", "Aviator", "Riff", "PLYR", "SLYR", "Icon", "Venue", "Cassette")

/** Headphones by the names of the line (the app's manifest does not say); the rest are earbuds. */
public val SkullcandyProduct.form: Form get() = if (enumName in SPEAKERS) Form.SPEAKER else if (OVER_EAR.any { name.startsWith(it, ignoreCase = true) }) Form.HEADPHONES else Form.EARBUDS

/** The shared screens' model for a Skullcandy product. */
public fun SkullcandyModels.model(name: String): Model? = fromName(name)?.let { p ->
    Model(p.name, listOf(p.name) + p.alternateNames, "Skullcandy", fitKeepsAnc = false, form = p.form)
}
