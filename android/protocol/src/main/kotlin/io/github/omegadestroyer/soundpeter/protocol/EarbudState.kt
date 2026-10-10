package io.github.omegadestroyer.soundpeter.protocol

/**
 * Everything the earbuds have reported, one decoded [Value] per command.
 *
 * Every reply is a state update, whether it answers a GET or arrives unasked: after a
 * SET, and after a change made by touch, the H3 sends the new state as a GET reply.
 * A command the firmware does not support never answers, so it never appears here.
 */
public data class EarbudState(val values: Map<Command, Value> = emptyMap()) {
    /** Applies one received frame. Frames that are not replies the app understands are ignored. */
    public fun update(frame: Frame): EarbudState {
        if (frame.vendor != Wire.VENDOR_MAIN || !frame.isReply) return this
        val command = BY_GET[frame.answers] ?: return this
        val value = Replies.decode(command, frame.payload) ?: return this
        return copy(values = values + (command to value))
    }

    public fun supports(command: Command): Boolean = command in values

    public val firmware: FirmwareVersion?
        get() = (values[Command.FIRMWARE_VERSION] as? Value.Text)?.let { FirmwareVersion(it.text) }

    public val batteryLeft: Int? get() = number(Command.BATTERY_LEFT)
    public val batteryRight: Int? get() = number(Command.BATTERY_RIGHT)
    public val batteryCase: Int? get() = number(Command.BATTERY_CASE)

    public fun number(command: Command): Int? = (values[command] as? Value.Number)?.value

    public fun switch(command: Command): Boolean? = (values[command] as? Value.Switch)?.on

    public fun levels(command: Command): List<Level> = (values[command] as? Value.Levels)?.levels.orEmpty()

    private companion object {
        val BY_GET: Map<Int, Command> = Command.entries.mapNotNull { c -> c.get?.let { it to c } }.toMap()
    }
}
