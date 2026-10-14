package io.github.omegadestroyer.pairfree.protocol.soundcore

/** Which earbud a touch control is on; the number is the side byte the writes carry. */
public enum class TouchSide(public val byte: Int) { LEFT(0), RIGHT(1) }

public enum class TouchPress { SINGLE, DOUBLE, TRIPLE, LONG, SLIDE_UP, SLIDE_DOWN }

/** One action a press can trigger: the number the earbuds store, and its name in data/soundcore/button_actions.json. */
public data class SoundcoreAction(val id: Int, val name: String)

/** How a button's enabled or action byte is laid out. TWS: the low four bits with both earbuds in, the high four with one. */
public enum class ButtonByte { NONE, SINGLE, TWS }

/** When a button can be turned off: never, one side at a time, or both sides together. */
public enum class TurnOff { NEVER, EACH_SIDE, BOTH_SIDES }

/** One kind of press on a model: the button number the writes use, its bytes, and what it can do. */
public data class SoundcorePressSpec(
    val press: TouchPress,
    val id: Int,
    val enabled: ButtonByte,
    val action: ButtonByte,
    val off: TurnOff,
    val actions: List<SoundcoreAction>,
)

/**
 * A set of buttons the state holds together, in [fields] (concatenated), in [order]. A change to
 * several buttons goes as one [SoundcoreCommand.ALL_BUTTONS] when [setAll]. With [ignoreEnabled],
 * an action of F turns a button off and the enabled byte only follows it.
 */
public data class SoundcoreButtonGroup(
    val fields: List<String>,
    val order: List<Pair<TouchSide, TouchPress>>,
    val presses: Map<TouchPress, SoundcorePressSpec>,
    val setAll: Boolean = false,
    val ignoreEnabled: Boolean = false,
    val actionCommand: Int = SoundcoreCommand.BUTTON_ACTION,
    /** The actions used during calls (the A3330). */
    val duringCall: Boolean = false,
)

/** A model's touch controls (data/soundcore/devices.json buttons), read from OpenSCQ30. */
public data class SoundcoreButtonSpec(
    val model: String,
    val groups: List<SoundcoreButtonGroup>,
    /** The model takes [SoundcoreCommand.RESET_BUTTONS]. */
    val reset: Boolean,
    /** Presses the model has that cannot be changed, with the action they do. */
    val fixed: Map<TouchPress, String> = emptyMap(),
    /** What confirms the writes: `hardware` or `openscq30_tests`; null when nothing does yet. */
    val checked: String? = null,
) {
    public companion object {
        public fun of(model: String?): SoundcoreButtonSpec? = SOUNDCORE_BUTTONS.firstOrNull { it.model == model }

        public val ALL: List<SoundcoreButtonSpec> get() = SOUNDCORE_BUTTONS
    }
}

/** One button as the screens show it. [actionId] is null while it is off; [action] is null while off or unknown. */
public data class TouchButton(
    val side: TouchSide,
    val press: TouchPress,
    val actionId: Int?,
    val action: SoundcoreAction?,
    val choices: List<SoundcoreAction>,
    val canTurnOff: Boolean,
)

/**
 * Reads and writes touch controls as OpenSCQ30 does (`button_configuration` in its Soundcore
 * modules, GPL-3.0-or-later): a button's enabled and action bytes, each either one value or a
 * value for "both earbuds in" (low four bits) and "one earbud in" (high four bits). The screens
 * show and change the value for how the earbuds are worn now, from the TWS status.
 */
public object SoundcoreButtons {
    private const val OFF = 0xF

    /** One part of a button: for TWS bytes the value with both earbuds in, then with one. */
    internal data class Part(val kind: ButtonByte, val connected: Int, val disconnected: Int) {
        fun current(tws: Boolean): Int = if (kind == ButtonByte.TWS && !tws) disconnected else connected

        fun withCurrent(tws: Boolean, value: Int): Part = when {
            kind != ButtonByte.TWS -> copy(connected = value, disconnected = value)
            tws -> copy(connected = value)
            else -> copy(disconnected = value)
        }

        fun byte(): Int = if (kind == ButtonByte.TWS) (disconnected shl 4) or connected else connected
    }

    internal data class Status(val enabled: Part?, val action: Part) {
        fun isEnabled(tws: Boolean): Boolean = enabled?.let { it.current(tws) == 1 } ?: (action.current(tws) != OFF)

        fun withCurrent(tws: Boolean, actionId: Int?): Status = if (actionId != null) {
            Status(enabled?.withCurrent(tws, 1), action.withCurrent(tws, actionId))
        } else {
            Status(enabled?.withCurrent(tws, 0), if (enabled != null) action else action.withCurrent(tws, OFF))
        }

        fun bytes(): List<Int> = listOfNotNull(enabled?.byte(), action.byte())
    }

    /** The group's bytes from [state], or null when the reply does not hold them all. */
    internal fun bytes(group: SoundcoreButtonGroup, state: SoundcoreState): ByteArray? {
        val parts = group.fields.map { state.field(it) ?: return null }
        return parts.fold(ByteArray(0)) { all, p -> all + p }
    }

    internal fun parse(group: SoundcoreButtonGroup, bytes: ByteArray): List<Status>? {
        var at = 0
        fun next(): Int? = bytes.getOrNull(at++)?.toInt()?.and(0xFF)
        return group.order.map { (_, press) ->
            val p = group.presses.getValue(press)
            val enabled = when (p.enabled) {
                ButtonByte.NONE -> null
                ButtonByte.SINGLE -> (next() ?: return null).let { b -> (if (b == 1) 1 else 0).let { Part(ButtonByte.SINGLE, it, it) } }
                ButtonByte.TWS -> (next() ?: return null).let { b ->
                    Part(ButtonByte.TWS, if (b and 0xF == 1) 1 else 0, if (b shr 4 == 1) 1 else 0)
                }
            }
            val b = next() ?: return null
            val action = if (p.action == ButtonByte.TWS) Part(ButtonByte.TWS, b and 0xF, b shr 4) else Part(ButtonByte.SINGLE, b, b)
            Status(enabled, action)
        }
    }

    /** Both earbuds in, as the TWS status says; models without one count as both in, as in OpenSCQ30. */
    internal fun tws(state: SoundcoreState): Boolean = state.field("tws_status")?.let { it.size < 2 || it[1].toInt() == 1 } ?: true

    /** The buttons of [group] as [state] holds them, or null when the reply does not hold them. */
    public fun read(group: SoundcoreButtonGroup, state: SoundcoreState): List<TouchButton>? {
        val statuses = parse(group, bytes(group, state) ?: return null) ?: return null
        val tws = tws(state)
        return group.order.mapIndexed { i, (side, press) ->
            val p = group.presses.getValue(press)
            val status = statuses[i]
            val id = if (!group.ignoreEnabled) {
                if (status.isEnabled(tws)) status.action.current(tws) else null
            } else {
                status.action.current(tws).takeIf { it != OFF }
            }
            TouchButton(side, press, id, id?.let { a -> p.actions.firstOrNull { it.id == a } }, p.actions, p.off != TurnOff.NEVER)
        }
    }

    /** What a change sends, and the state as it should be once the earbuds have taken it. */
    public class Change(public val packets: List<SoundcorePacket>, public val state: SoundcoreState)

    /**
     * The packets that set [press] on [side] to the action [actionId], or turn it off when null,
     * as OpenSCQ30 sends them. Null when the button is not in [group], the state does not hold
     * the group, or the button cannot be turned off and [actionId] is null.
     */
    public fun set(
        group: SoundcoreButtonGroup,
        state: SoundcoreState,
        side: TouchSide,
        press: TouchPress,
        actionId: Int?,
    ): Change? {
        val current = parse(group, bytes(group, state) ?: return null) ?: return null
        val tws = tws(state)
        val pos = group.order.indexOf(side to press).takeIf { it >= 0 } ?: return null
        val p = group.presses.getValue(press)
        if (actionId == null && p.off == TurnOff.NEVER) return null
        val target = current.toMutableList()
        val maybeId = if (!group.ignoreEnabled) actionId else actionId ?: OFF
        when (p.off) {
            TurnOff.NEVER -> target[pos] = target[pos].withCurrent(tws, actionId)
            TurnOff.EACH_SIDE -> target[pos] = target[pos].withCurrent(tws, maybeId)
            TurnOff.BOTH_SIDES -> {
                target[pos] = target[pos].withCurrent(tws, maybeId)
                val other = group.order.indexOf(TouchSide.entries.first { it != side } to press)
                if (other >= 0) {
                    if (!target[other].isEnabled(tws)) {
                        if (actionId != null) {
                            val now = target[other].action.current(tws)
                            target[other] = target[other].withCurrent(tws, if (now != OFF) now else actionId)
                        }
                    } else if (actionId == null) {
                        target[other] = target[other].withCurrent(tws, null)
                    }
                }
            }
        }
        val changed = target.indices.filter { target[it] != current[it] }
        val packets = if (group.setAll && changed.size > 1) {
            listOf(SoundcorePacket(SoundcoreCommand.ALL_BUTTONS, all(target)))
        } else {
            changed.flatMap { i ->
                val (s, pr) = group.order[i]
                val id = group.presses.getValue(pr).id
                listOfNotNull(
                    if (current[i].action != target[i].action) {
                        SoundcorePacket(group.actionCommand, body(s.byte, id, target[i].action.byte()))
                    } else {
                        null
                    },
                    target[i].enabled?.takeIf { current[i].enabled != it }?.let {
                        SoundcorePacket(SoundcoreCommand.BUTTON_ENABLED, body(s.byte, id, it.byte()))
                    },
                )
            }
        }
        return Change(packets, withBytes(group, state, all(target)))
    }

    private fun all(statuses: List<Status>): ByteArray = statuses.flatMap { it.bytes() }.map { it.toByte() }.toByteArray()

    private fun body(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    /** [state] with the group's fields replaced by [bytes], split back by field size. */
    private fun withBytes(group: SoundcoreButtonGroup, state: SoundcoreState, bytes: ByteArray): SoundcoreState {
        var at = 0
        var out = state
        for (f in group.fields) {
            val size = state.layout.locate(f)?.second ?: continue
            out = out.with(f, bytes.copyOfRange(at, at + size))
            at += size
        }
        return out
    }
}
