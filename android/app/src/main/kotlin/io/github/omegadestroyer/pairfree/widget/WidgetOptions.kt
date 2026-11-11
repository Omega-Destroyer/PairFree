package io.github.omegadestroyer.pairfree.widget

import android.content.Context
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.CapsuleEqTarget
import io.github.omegadestroyer.pairfree.eq.EarfunEqTarget
import io.github.omegadestroyer.pairfree.eq.EqTarget
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.eq.JblEqTarget
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.NothingBandEq
import io.github.omegadestroyer.pairfree.eq.OneMoreBandEq
import io.github.omegadestroyer.pairfree.eq.PixelBandEq
import io.github.omegadestroyer.pairfree.eq.SamsungEqTarget
import io.github.omegadestroyer.pairfree.eq.SoundcoreEqTarget
import io.github.omegadestroyer.pairfree.eq.SoundpeatsEqTarget
import io.github.omegadestroyer.pairfree.hub
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.NoiseModeInfo
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunModels
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunSession
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingSession
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreModels
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreSession
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelFamily
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelSession
import io.github.omegadestroyer.pairfree.protocol.qcy.Capsule
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyModels
import io.github.omegadestroyer.pairfree.protocol.qcy.QcySession
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreLayout
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

    /** SOUNDPEATS' server list for the model: the modes it offers, with the scenes under cancelling and transparency. */
    private fun fromServer(modes: List<NoiseModeInfo>): List<NoiseChoice> = buildList {
        for (info in modes) when (info.mode) {
            0 -> add(OFF)
            1 -> {
                add(NoiseChoice("anc"))
                info.scenes.forEach { add(NoiseChoice("anc", Sub.Level(Command.ANC_LEVEL, it))) }
            }
            2 -> {
                add(NoiseChoice("transparency"))
                info.scenes.forEach { add(NoiseChoice("transparency", Sub.Level(Command.TRANSPARENCY_LEVEL, it))) }
            }
        }
    }

    private fun fromCache(device: Paired, cache: WidgetCache): List<NoiseChoice> {
        // Scenes are known as ANC_LEVEL and TRANSPARENCY_LEVEL codes on SOUNDPEATS only.
        val levels = device.brand == Brand.SOUNDPEATS
        return three(
            if (levels) cache.ancLevels.map { Sub.Level(Command.ANC_LEVEL, it) } else emptyList(),
            if (levels) cache.transparencyLevels.map { Sub.Level(Command.TRANSPARENCY_LEVEL, it) } else emptyList(),
        )
    }

    /** The noise choices the brand's tables give, or null where only the earbuds' replies say. Empty: the model has none. */
    private fun noiseOffline(device: Paired): List<NoiseChoice>? = try {
        when (device.brand) {
            Brand.SOUNDPEATS -> Models.find(null, device.name)?.noiseModes?.let(::fromServer)
            // The Capsule3 Pro runs on QCY's protocol: all three modes.
            Brand.QCY -> if (device.soldAs == Brand.SOUNDPEATS) {
                if (Capsule.of(Models.find(device.model, device.name))) three() else null
            } else {
                QcyModels.fromName(device.name).singleOrNull()?.takeIf { QcySession(it).sharedNoise }?.let { m ->
                    // Several scenes: the scene row. One scene with a range: its strength slider. Transparency: its strength slider.
                    val scenes = m.mode("cancelling")?.scenes.orEmpty()
                    val cancelling = if (scenes.size > 1) scenes.map { Sub.Row("anc_scene", it.scene) } else {
                        scenes.singleOrNull()?.takeIf { it.to > it.from }?.let { s -> (1..s.to - s.from + 1).map { Sub.Step("anc_scene_level", it) } }.orEmpty()
                    }
                    val transparency = m.mode("transparency")?.takeIf { it.to > it.from }?.let { t -> (1..t.to - t.from + 1).map { Sub.Step("transparency_strength", it) } }.orEmpty()
                    three(cancelling, transparency)
                }
            }
            Brand.SOUNDCORE -> if (SoundcoreLayout.of(device.model)?.has("sound_modes") == true) three() else emptyList()
            Brand.SAMSUNG -> SamsungModels.of(device.model)?.let { m ->
                if (!m.supports("noise_control", null)) emptyList()
                else three(if (m.supports("anc_noise_reduction_levels", null)) listOf("low", "high").map { Sub.Row("anc_level", it) } else emptyList())
            }
            Brand.JBL -> JblModels.fromName(device.name)?.let { if (JblSession(it).sharedNoise) three() else emptyList() }
            Brand.NOTHING -> NothingModels.of(device.model)?.let { m ->
                NothingSession(m).let { s -> if (s.sharedNoise) three(s.levels.takeIf { it.size > 1 }.orEmpty().map { Sub.Row("noise_strength", it) }) else emptyList() }
            }
            Brand.PIXEL -> PixelModels.of(device.model?.toIntOrNull())?.let { if (it.family == PixelFamily.ALLEGRO) three() else emptyList() }
            Brand.ONEMORE -> OneMoreModels.fromName(device.name)?.let { m ->
                OneMoreSession(m).let { s ->
                    if (!s.sharedNoise) emptyList()
                    else three(s.strengths.map { Sub.Row("noise_strength", it) }, s.transparencyKinds.map { Sub.Row("transparency_mode", it) })
                }
            }
            Brand.EARFUN -> EarfunModels.fromName(device.name)?.let { m ->
                if (m.noise == null) emptyList()
                else EarfunSession(m).let { s ->
                    three(s.cancellingChoices.map { Sub.Row(EarfunSession.ANC_MODE_KEY, it) }, s.ambientChoices.map { Sub.Row(EarfunSession.AMBIENT_MODE_KEY, it) })
                }
            }
            // Bose has no shared noise mode; Sony and Apple say only in their replies.
            Brand.BOSE, Brand.CAMBRIDGE, Brand.ONEPLUS -> emptyList()
            Brand.SONY, Brand.APPLE -> null
        }
    } catch (_: RuntimeException) {
        null
    }

    private fun brandMayHaveNoise(device: Paired) = device.brand == Brand.SOUNDPEATS || device.brand == Brand.SONY || device.brand == Brand.APPLE

    private fun brandMayHaveEq(device: Paired) = device.brand == Brand.SONY || (device.brand == Brand.QCY && device.soldAs == null)

    /** The brand's EQ layout and target built from its tables alone, or null where it needs what the earbuds report. */
    private fun eqOffline(device: Paired): Pair<EqLayout, EqTarget>? = try {
        when (device.brand) {
            Brand.SOUNDPEATS -> Models.find(null, device.name)?.eq?.let { it to SoundpeatsEqTarget }
            Brand.QCY -> if (device.soldAs == Brand.SOUNDPEATS) Models.find(device.model, device.name)?.eq?.let { it to CapsuleEqTarget } else null
            Brand.SOUNDCORE -> SoundcoreEqSpec.of(device.model)?.let { SoundcoreEqTarget(it, { null }, true) }?.let { it.layout to it }
            Brand.SAMSUNG -> SamsungModels.of(device.model)?.let { SamsungEqTarget(it) }?.let { it.layout to it }
            Brand.JBL -> JblModels.fromName(device.name)?.takeIf { it.eq?.written == true }?.let { JblEqTarget(JblSession(it)) }?.let { it.layout to it }
            Brand.NOTHING -> NothingModels.of(device.model)?.let { BandEqTarget(NothingBandEq(NothingSession(it))) }?.takeIf { it.hasCustom || it.layout.presets.isNotEmpty() }?.let { it.layout to it }
            Brand.ONEMORE -> OneMoreModels.fromName(device.name)?.let { BandEqTarget(OneMoreBandEq(OneMoreSession(it))) }?.let { it.layout to it }
            Brand.EARFUN -> EarfunModels.fromName(device.name)?.let { m -> m.eq?.let { EarfunEqTarget(EarfunSession(m), it) } }?.let { it.layout to it }
            // Google's presets and five bands; "Last saved" joins them once the earbuds have reported it.
            Brand.PIXEL -> PixelModels.of(device.model?.toIntOrNull())?.takeIf { it.family == PixelFamily.ALLEGRO }?.let { BandEqTarget(PixelBandEq(PixelSession(it.transport, it))) }?.let { it.layout to it }
            else -> null
        }
    } catch (_: RuntimeException) {
        null
    }
}
