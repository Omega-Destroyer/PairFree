package io.github.omegadestroyer.soundpeter.protocol

/**
 * One model from data/soundpeats/devices.json.
 *
 * @property code the firmware code, the first field of the firmware-version reply.
 * @property names the Bluetooth names the model uses.
 * @property family the chip family (docs/soundpeats/PROTOCOL.md section 5).
 * @property ldacExcludes features the official app offers only while LDAC is off.
 * @property ldacOrMultipoint LDAC and multipoint are one either-or setting: turning one off turns the other on.
 * @property eq the EQ layout the official app writes to this model, when it is known.
 * @property confirmed commands marked per_model that the official app offers on this model.
 * @property fitKeepsAnc the official app leaves the noise mode alone during the fit test;
 *   on other models it turns ANC off first.
 * @property form what the product is, for its battery label and drawing.
 */
public data class Model(
    val code: String,
    val names: List<String>,
    val family: String,
    val ldacExcludes: Set<Command> = emptySet(),
    val ldacOrMultipoint: Boolean = false,
    val eq: EqLayout? = null,
    val confirmed: Set<Command> = emptySet(),
    val fitKeepsAnc: Boolean = false,
    val form: Form = Form.EARBUDS,
) {
    /** Qualcomm chips also answer GAIA v3 on vendor `00 1D` ([Gaia]). */
    val isQualcomm: Boolean get() = family == QUALCOMM_FAMILY

    private companion object {
        const val QUALCOMM_FAMILY = "QUALCOMM"
    }
}

/**
 * What a product is. Earbuds report each earbud's battery and often the case's; headphones
 * and speakers report one battery.
 */
public enum class Form { EARBUDS, HEADPHONES, SPEAKER }

/**
 * True when the app should offer [command]: the earbuds answered its read, and for a
 * command marked per_model, the model also lists it under confirmed. The H3 answers the
 * LE Audio read, yet the official app does not offer LE Audio on it.
 */
public fun EarbudState.offers(command: Command, model: Model?): Boolean =
    supports(command) && (!command.perModel || model?.confirmed?.contains(command) == true)

public object Models {
    public val ALL: List<Model> get() = MODEL_TABLE

    /**
     * Finds a model by firmware code. While the code is not known yet, or is not in the
     * table, falls back to the Bluetooth name.
     */
    public fun find(code: String?, name: String?): Model? =
        MODEL_TABLE.firstOrNull { it.code == code }
            ?: name?.let { n -> MODEL_TABLE.firstOrNull { m -> m.names.any { it.equals(n, ignoreCase = true) } } }

    /**
     * True when the app can control a device shown with this Bluetooth [name]: a SoundPEATS
     * device on the common protocol (docs/soundpeats/PROTOCOL.md sections 1 to 4).
     *
     * A model in the table qualifies unless it is the QCY-built Capsule3 Pro, whose protocol
     * the app does not speak. A name not in the table qualifies when it starts with a known
     * SoundPEATS brand: the table is deliberately incomplete, newer models use the common
     * protocol too, and the connection then probes what the earbuds actually support. A
     * non-SoundPEATS device (another maker's headphones) matches nothing and is left out.
     */
    public fun isSupported(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val model = MODEL_TABLE.firstOrNull { m -> m.names.any { it.equals(trimmed, ignoreCase = true) } }
        if (model != null) return model.family != QCY_FAMILY
        return BRANDS.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    /** The Capsule3 Pro's family: QCY's own protocol over BLE, which this app does not implement. */
    private const val QCY_FAMILY = "QCY-WUQI"

    /** SoundPEATS' own and sub-brand names, for models not yet in the table. */
    private val BRANDS = listOf("SOUNDPEATS", "TRUEFREE", "LE-SOUNDPEATS")
}

/**
 * True while [command] is turned off by LDAC on this model: the official app offers it
 * only with LDAC off. False when the model or the LDAC state is not known.
 */
public fun EarbudState.blockedByLdac(command: Command, model: Model?): Boolean =
    model != null && command in model.ldacExcludes && switch(Command.LDAC) == true
