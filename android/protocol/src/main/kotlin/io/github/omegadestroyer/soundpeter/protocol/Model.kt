package io.github.omegadestroyer.soundpeter.protocol

/**
 * One model from data/devices.json.
 *
 * @property code the firmware code, the first field of the firmware-version reply.
 * @property names the Bluetooth names the model uses.
 * @property family the chip family (docs/PROTOCOL.md section 5).
 * @property ldacExcludes features the official app offers only while LDAC is off.
 * @property ldacOrMultipoint LDAC and multipoint are one either-or setting: turning one off turns the other on.
 * @property eq the EQ layout the official app writes to this model, when it is known.
 */
public data class Model(
    val code: String,
    val names: List<String>,
    val family: String,
    val ldacExcludes: Set<Command> = emptySet(),
    val ldacOrMultipoint: Boolean = false,
    val eq: EqLayout? = null,
)

public object Models {
    public val ALL: List<Model> get() = MODEL_TABLE

    /**
     * Finds a model by firmware code. While the code is not known yet, or is not in the
     * table, falls back to the Bluetooth name.
     */
    public fun find(code: String?, name: String?): Model? =
        MODEL_TABLE.firstOrNull { it.code == code }
            ?: name?.let { n -> MODEL_TABLE.firstOrNull { m -> m.names.any { it.equals(n, ignoreCase = true) } } }
}

/**
 * True while [command] is turned off by LDAC on this model: the official app offers it
 * only with LDAC off. False when the model or the LDAC state is not known.
 */
public fun EarbudState.blockedByLdac(command: Command, model: Model?): Boolean =
    model != null && command in model.ldacExcludes && switch(Command.LDAC) == true
