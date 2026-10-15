package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungEq
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungMessage
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModel
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungPacket
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungStatus

/**
 * Galaxy Buds (docs/samsung/PROTOCOL.md section 6): Normal or one of five presets, which the
 * earbuds apply themselves, and on models that take one a nine-band custom curve. The earbuds
 * report which they hold, but not the custom curve, and forget the curve when they power off; so
 * this phone keeps the curve it last sent and sends it again once per connection. Where the
 * earbuds reported every preset's curve ([curves], the Buds4 family), the presets are drawn.
 */
class SamsungEqTarget(val model: SamsungModel, private val curves: List<List<Int>> = emptyList()) : EqTarget {
    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false
    override val hasCustom: Boolean = SamsungEq.hasCustom(model)
    override val presetCurves: Boolean = curves.size > SamsungEq.PRESETS.size && curves.all { it.size == SamsungEq.CUSTOM_BANDS_HZ.size }

    /** The screen's view: the custom curve's bands (also on models without one, for the profile lists) and the presets. */
    val layout: EqLayout = EqLayout(
        command = SamsungMessage.EQUALIZER,
        masterGainDb = 0.0,
        userRangeDb = SamsungEq.CUSTOM_RANGE,
        bands = SamsungEq.CUSTOM_BANDS_HZ.map { EqBand(it, 0.0, 0.0) },
        // By equalizer number: Normal, then the presets, as the earbuds' curves come.
        presets = (listOf(SamsungEq.NORMAL) + SamsungEq.PRESETS).mapIndexed { i, key ->
            val curve = curves.getOrNull(i).takeIf { presetCurves }
            EqPreset(NAMES.getValue(key), SamsungEq.CUSTOM_BANDS_HZ.mapIndexed { b, hz -> EqBand(hz, 0.0, curve?.get(b)?.toDouble() ?: 0.0) })
        },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val packets = if (preset != null) {
            val key = NAMES.entries.firstOrNull { it.value == preset.name }?.key ?: return emptyList()
            listOf(SamsungEq.preset(model, key))
        } else {
            SamsungEq.custom(model, setting.gains) ?: return emptyList()
        }
        return packets.map { SamsungPacket.build(it.id, it.payload, model.legacy) }
    }

    /** The preset [status] says the earbuds hold, as a profile id, or null for the custom curve; nothing while it does not say. */
    fun reported(status: SamsungStatus): Reported? = when (val key = SamsungEq.reported(status)) {
        null -> null
        SamsungEq.CUSTOM -> Reported(null, custom = true)
        else -> Reported(EqProfile.PRESET + NAMES.getValue(key), custom = false)
    }

    data class Reported(val profileId: String?, val custom: Boolean)

    private companion object {
        /** The presets as Samsung's app names them. */
        val NAMES = linkedMapOf(
            SamsungEq.NORMAL to "Normal",
            "bass_boost" to "Bass boost",
            "soft" to "Soft",
            "dynamic" to "Dynamic",
            "clear" to "Clear",
            "treble_boost" to "Treble boost",
        )
    }
}
