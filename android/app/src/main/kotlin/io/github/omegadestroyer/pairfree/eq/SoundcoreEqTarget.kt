package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqScreen
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqualizer
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcorePacket
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreState

/**
 * Soundcore earbuds (docs/soundcore/PROTOCOL.md section 8): a preset sends its number and curve,
 * a custom curve its bands, and HearID models pass on the HearID block from [state], the latest
 * state the earbuds sent. They report their EQ, so the screen follows what they hold.
 */
class SoundcoreEqTarget(
    val spec: SoundcoreEqSpec,
    private val state: () -> SoundcoreState?,
    private val checksum: Boolean,
) : EqTarget {
    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false

    /** Models whose preset set has no custom preset (the P20i) take their presets only. */
    override val hasCustom: Boolean = spec.presetSet.customPreset != null

    /** Confirmed by a recording of the Soundcore app or by OpenSCQ30's tests; otherwise from OpenSCQ30's code alone. */
    override val confirmed: Boolean = spec.checked != null

    /** The screen's view of this model's EQ. */
    val layout: EqLayout = SoundcoreEqScreen.layout(spec)

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val config = preset?.let { p -> spec.presetSet.presets.firstOrNull { it.name == p.name } }
            ?.let { SoundcoreEqualizer.forPreset(spec, it) }
            ?: SoundcoreEqScreen.custom(spec, setting.gains)
            ?: return emptyList()
        return SoundcoreEqualizer.write(spec, state(), config).map { SoundcorePacket.build(it.command, it.body, checksum) }
    }

    /** What [state] says the earbuds hold, as the screen's profile and gains; null when it holds no EQ. */
    fun reported(state: SoundcoreState): Pair<String?, List<Int>>? {
        val config = SoundcoreEqualizer.read(spec, state) ?: return null
        val preset = SoundcoreEqScreen.preset(spec, config)
        return (preset?.let { EqProfile.PRESET + it.name }) to SoundcoreEqScreen.gainsDb(spec, config)
    }
}
