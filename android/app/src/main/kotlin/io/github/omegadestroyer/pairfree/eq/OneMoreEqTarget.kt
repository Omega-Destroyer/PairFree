package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreCommand
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreEq
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreFrame
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreSession

/**
 * 1MORE earbuds and headphones (docs/1more/PROTOCOL.md section 4): the preset sounds the model lists, which the
 * earbuds apply themselves, and on the models with one the custom EQ, from -3 to 3 dB a band. The earbuds report
 * the preset and the curve they hold, so the screen follows them.
 *
 * [frames] are the session's frames as bytes, with their sequence numbers; the connection queues them on the 1MORE
 * link.
 */
class OneMoreEqTarget(val session: OneMoreSession) : EqTarget {
    private val presetNames: List<String> = session.model?.eqPresets.orEmpty()
    private val bands = session.model?.eqBands

    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false
    override val hasCustom: Boolean = bands != null

    /** The earbuds apply their presets themselves, and 1MORE does not publish the curves. */
    override val presetCurves: Boolean = false

    private val bandsHz: List<Int> = bands?.hz ?: DEFAULT_HZ

    /** The screen's view: the model's bands, and its presets by the names 1MORE's app gives them. */
    val layout: EqLayout = EqLayout(
        command = OneMoreCommand.SET_EQ_PARAMETERS,
        masterGainDb = 0.0,
        userRangeDb = OneMoreSession.EQ_RANGE,
        bands = bandsHz.map { EqBand(it, 0.0, 0.0) },
        presets = presetNames.map { EqPreset(NAMES[it] ?: it, bandsHz.map { hz -> EqBand(hz, 0.0, 0.0) }) },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val out = if (preset != null) {
            session.eqPreset(presetNames.firstOrNull { (NAMES[it] ?: it) == preset.name } ?: return emptyList())
        } else {
            session.eqCustom(setting.gains)
        }
        return out.orEmpty().map(OneMoreFrame::bytes)
    }

    /** What [eq] says the earbuds hold, as the screen's profile (null for the custom curve) and gains; null while it says neither. */
    fun reported(eq: OneMoreEq, current: List<Int>): Pair<String?, List<Int>>? {
        if (eq.custom) return null to (eq.gains ?: return null)
        val name = eq.preset ?: return null
        return EqProfile.PRESET + (NAMES[name] ?: name) to current
    }

    private companion object {
        /** The preset sounds as 1MORE's app names them in English; "loud" is its Voice Enhancement. */
        val NAMES = mapOf(
            "studio" to "Studio", "bass_reducer" to "Bass reducer", "bass_booster" to "Bass booster", "acoustic" to "Acoustic",
            "luca" to "Sound by Luca", "classical" to "Classical", "podcast" to "Podcast", "deep" to "Deep", "electronic" to "Electronic",
            "hiphop" to "Hip-hop", "lounge" to "Lounge", "pop" to "Pop", "loud" to "Voice enhancement",
        )

        /** For the profile lists of a model with presets only: 1MORE's usual bands. */
        val DEFAULT_HZ = listOf(69, 282, 682, 1234, 2013, 3107, 4641, 6794, 9815, 14053)
    }
}
