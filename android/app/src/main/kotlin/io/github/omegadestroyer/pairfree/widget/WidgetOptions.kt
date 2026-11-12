package io.github.omegadestroyer.pairfree.widget

import android.content.Context
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.brands.Brands
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.hub
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice
import io.github.omegadestroyer.pairfree.protocol.widget.Sub
import io.github.omegadestroyer.pairfree.ui.modelOf

/**
 * What a widget button can be set to for one pair of earbuds. [noise] and [eq] may be empty; [needsConnection] says the
 * earbuds have choices PairFree only learns while connected, and has not yet.
 */
data class WidgetChoices(val noise: List<NoiseChoice>, val eq: List<Pair<String, String>>, val needsConnection: Boolean)

/**
 * The widget's choices for a paired pair of earbuds, built from the tables compiled into the app and the user's saved EQ
 * curves, so the widget can be set up without connecting (owner, 2026-11-07). Where a brand's table does not say
 * (docs/DESIGN.md, Widget), it falls back to what these earbuds showed while connected ([WidgetCache]).
 */
object WidgetOptions {
    private val OFF = NoiseChoice("off")

    fun of(context: Context, device: Paired): WidgetChoices {
        val hub = context.hub
        val cache = WidgetCache.load(hub.prefs, device.address)
        val noise = noiseOffline(device) ?: cache?.takeIf { it.noise }?.let { fromCache(device, it) }
        val eq = eqOffline(device)?.let { (layout, target) ->
            val code = modelOf(device, EarbudState())?.code
            Equalizer.builtIns(layout, target) + code?.let { hub.equalizer.loadProfiles(it, layout) }.orEmpty()
        }?.map { it.id to it.name } ?: cache?.eq
        val noiseUnknown = noise == null && brandMayHaveNoise(device)
        val eqUnknown = eq == null && brandMayHaveEq(device)
        return WidgetChoices(noise.orEmpty(), eq.orEmpty(), needsConnection = noiseUnknown || eqUnknown)
    }

    /** Off, cancelling and transparency, each with the sub-modes given for it. */
    private fun three(cancelling: List<Sub> = emptyList(), transparency: List<Sub> = emptyList()): List<NoiseChoice> =
        listOf(OFF, NoiseChoice("anc")) + cancelling.map { NoiseChoice("anc", it) } +
            listOf(NoiseChoice("transparency")) + transparency.map { NoiseChoice("transparency", it) }

    private fun fromCache(device: Paired, cache: WidgetCache): List<NoiseChoice> {
        // Scenes are known as ANC_LEVEL and TRANSPARENCY_LEVEL codes on SOUNDPEATS only.
        val levels = device.brand == Brand.SOUNDPEATS
        return three(
            if (levels) cache.ancLevels.map { Sub.Level(Command.ANC_LEVEL, it) } else emptyList(),
            if (levels) cache.transparencyLevels.map { Sub.Level(Command.TRANSPARENCY_LEVEL, it) } else emptyList(),
        )
    }

    /** The noise choices the brand's tables give ([io.github.omegadestroyer.pairfree.brands.BrandSpec.noiseOffline]), or null where only the earbuds' replies say. Empty: the model has none. */
    private fun noiseOffline(device: Paired): List<NoiseChoice>? = try {
        Brands[device.brand].noiseOffline(device)
    } catch (_: RuntimeException) {
        null
    }

    private fun brandMayHaveNoise(device: Paired) = Brands[device.brand].mayHaveNoise

    private fun brandMayHaveEq(device: Paired) = Brands[device.brand].mayHaveEq(device)

    /** The brand's EQ layout and target built from its tables alone, or null where it needs what the earbuds report. */
    private fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? = try {
        Brands[device.brand].eqOffline(device)
    } catch (_: RuntimeException) {
        null
    }
}
