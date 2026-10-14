package io.github.omegadestroyer.pairfree.protocol

/**
 * Everything the earbuds have reported, one decoded [Value] per command.
 *
 * Every reply is a state update, whether it answers a GET or arrives unasked: after a
 * SET, and after a change made by touch, the H3 sends the new state as a GET reply.
 * A command the firmware does not support never answers, so it never appears here.
 */
public data class EarbudState(
    val values: Map<Command, Value> = emptyMap(),
    /** The GAIA v3 features a Qualcomm model reported, or null before it has. */
    val gaiaFeatures: Set<Int>? = null,
    /** The last fit test result, or null before one has arrived. */
    val fit: FitResult? = null,
    /** The earbuds have a fit test by their model, not by a GAIA feature (Galaxy Buds). */
    val fitTest: Boolean = false,
) {
    /** Applies one received frame. Frames that are not replies the app understands are ignored. */
    public fun update(frame: Frame): EarbudState {
        if (frame.vendor == Gaia.VENDOR) {
            FitResult.of(frame)?.let { return copy(fit = it) }
            gaiaFeaturesOf(frame)?.let { return copy(gaiaFeatures = it) }
            return this
        }
        val command = Replies.commandFor(frame) ?: return this
        val value = Replies.decode(command, frame.payload) ?: return this
        return copy(values = values + (command to value))
    }

    public fun supports(command: Command): Boolean = command in values

    /** True when the earbuds have a fit test: the GAIA feature on Qualcomm models, or by model ([fitTest]). */
    public val hasFitTest: Boolean get() = fitTest || gaiaFeatures?.contains(Gaia.FIT_FEATURE) == true

    public val firmware: FirmwareVersion?
        get() = (values[Command.FIRMWARE_VERSION] as? Value.Text)?.let { FirmwareVersion(it.text) }

    /** Battery levels in percent, whatever scale the earbuds report in; see [battery] for the scale. */
    public val batteryLeft: Int? get() = battery(Command.BATTERY_LEFT)?.percent
    public val batteryRight: Int? get() = battery(Command.BATTERY_RIGHT)?.percent
    public val batteryCase: Int? get() = battery(Command.BATTERY_CASE)?.percent

    /** A battery as the earbuds report it: a percentage, or steps such as fifths. Null before it has answered. */
    public fun battery(command: Command): BatteryLevel? = when (val v = values[command]) {
        is Value.Number -> BatteryLevel(v.value)
        is Value.Steps -> BatteryLevel(v.value, v.steps)
        else -> null
    }

    public fun number(command: Command): Int? = (values[command] as? Value.Number)?.value

    public fun switch(command: Command): Boolean? = (values[command] as? Value.Switch)?.on

    public fun levels(command: Command): List<Level> = (values[command] as? Value.Levels)?.levels.orEmpty()

    public val keys: List<KeyBinding> get() = (values[Command.CUSTOM_KEYS] as? Value.Keys)?.keys.orEmpty()

    /**
     * The state as it should be once the earbuds have applied a SET of [command] with
     * [payload], so the change can show at once. The reply that follows replaces it.
     * Commands that have not answered yet, and SETs whose effect is not a value, leave
     * the state as it is.
     */
    public fun expect(command: Command, vararg payload: Int): EarbudState {
        if (command !in values) return this
        val value: Value = when (command.kind) {
            Kind.SWITCH -> Value.Switch(payload.singleOrNull() == 1)
            Kind.ENUM, Kind.PERCENT, Kind.MINUTES -> Value.Number(payload.singleOrNull() ?: return this)
            Kind.LEVEL_LIST -> {
                val code = payload.singleOrNull() ?: return this
                Value.Levels(levels(command).map { it.copy(active = it.code == code) })
            }
            Kind.KEY_MAP -> {
                if (payload.size != 2) return this
                val (gesture, action) = payload
                Value.Keys(keys.map { if (it.gesture == gesture) it.copy(action = action) else it })
            }
            Kind.TEXT, Kind.RAW, Kind.ACTION -> return this
        }
        return copy(values = values + (command to value))
    }
}
