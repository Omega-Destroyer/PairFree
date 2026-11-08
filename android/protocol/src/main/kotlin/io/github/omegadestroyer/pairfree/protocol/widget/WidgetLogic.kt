package io.github.omegadestroyer.pairfree.protocol.widget

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting

// The home screen widget's logic that needs no Android (docs/DESIGN.md, Widget).

/** A sub-mode fixed in a noise choice, such as Cancelling in its Adaptive scene. */
public sealed interface Sub {
    /** SOUNDPEATS: a code of [command], `ANC_LEVEL` (scene) or `TRANSPARENCY_LEVEL`, sent as a SET of that command. */
    public data class Level(val command: Command, val code: Int) : Sub

    /** Every other brand: the [option] of the brand's setting row [key], sent as the Controls tab sends it. */
    public data class Row(val key: String, val option: String) : Sub
}

/** One noise state a widget button sets: [mode] (`off`, `anc` or `transparency`, the names of `Command.ANC_MODE`) and an optional fixed [sub]. */
public data class NoiseChoice(val mode: String, val sub: Sub? = null) {
    public val modeValue: Int get() = Command.ANC_MODE.labels.entries.first { it.value == mode }.key

    /** True when the earbuds are in this mode and, if a sub-mode is fixed, in that one ([rows]: the brand's current setting rows). */
    public fun isActive(state: EarbudState, rows: List<Setting>): Boolean =
        state.number(Command.ANC_MODE) == modeValue && when (sub) {
            null -> true
            is Sub.Level -> state.levels(sub.command).any { it.code == sub.code && it.active }
            is Sub.Row -> rows.any { it.key == sub.key && (it as? Setting.Choice)?.value == sub.option }
        }
}

/** What one of a widget's eight buttons does. */
public sealed interface WidgetSlot {
    public data object Empty : WidgetSlot
    public data class Noise(val choice: NoiseChoice) : WidgetSlot
    public data class NoiseCycle(val choices: List<NoiseChoice>) : WidgetSlot
    public data class Eq(val profileId: String) : WidgetSlot
    public data class EqCycle(val profileIds: List<String>) : WidgetSlot
}

/** The widget's grid: eight slots, slots 0 to 3 the first row and 4 to 7 the second. */
public object WidgetGrid {
    public const val SLOTS: Int = 8
    public const val PER_ROW: Int = 4

    /** The slots a widget [cols] cells wide and [rows] tall shows. */
    public fun visibleSlots(cols: Int, rows: Int): List<Int> {
        val c = cols.coerceIn(1, PER_ROW)
        return (0 until c).toList() + if (rows >= 2) (PER_ROW until PER_ROW + c).toList() else emptyList()
    }

    /** The width in dp at which a launcher gives a widget [cols] cells, a little under its 73n - 16. */
    public fun minWidthDp(cols: Int): Float = 73f * cols - 24f

    public fun minHeightDp(rows: Int): Float = if (rows >= 2) 160f else 40f
}

public object WidgetCycle {
    /**
     * The choice after the one the earbuds are in. A choice with a fixed sub-mode that matches wins over a bare mode of the
     * same kind; when none matches, the first.
     */
    public fun nextNoise(choices: List<NoiseChoice>, state: EarbudState, rows: List<Setting>): NoiseChoice {
        val exact = choices.indexOfFirst { it.sub != null && it.isActive(state, rows) }
        val i = if (exact >= 0) exact else choices.indexOfFirst { it.isActive(state, rows) }
        return choices[(i + 1) % choices.size]
    }

    /** The profile after [selectedId]; the first when it is none of [ids]. */
    public fun nextEq(ids: List<String>, selectedId: String?): String = ids[(ids.indexOf(selectedId) + 1) % ids.size]
}
