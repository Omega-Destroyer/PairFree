package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.qcy.Capsule

/**
 * SOUNDPEATS' QCY-built Capsule3 Pro and Pro+ (docs/soundpeats/PROTOCOL.md section 10.5): the model's curve from
 * data/soundpeats/devices.json, as on SOUNDPEATS' other models, but sent whole in one write on QCY's EQ characteristic.
 * The earbuds' EQ read is not used, as PeatsAudio does not use it, so the phone remembers what it sent.
 *
 * [frames] are GATT messages for the QCY link's queue.
 */
object CapsuleEqTarget : EqTarget {
    override val reportsEq: Boolean = false
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = true

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val bands = preset?.bands ?: (layout.fixedBands + layout.bands.mapIndexed { i, b -> b.copy(gainDb = b.gainDb + setting.gains[i]) })
        return listOf(Capsule.eqMessage(layout.masterGainDb, bands))
    }
}
