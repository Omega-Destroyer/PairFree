package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.sony.SonyCommand
import io.github.omegadestroyer.pairfree.protocol.sony.SonyEq
import io.github.omegadestroyer.pairfree.protocol.sony.SonyOut
import io.github.omegadestroyer.pairfree.protocol.sony.SonySession

/**
 * Sony earbuds and headphones (docs/sony/PROTOCOL.md section 6): the presets they list, which they
 * apply themselves, and on models that take one a curve of the user's own over the frequency bands
 * they report. Clear Bass is a setting of its own on the Sound tab. They report what they hold, so
 * the screen follows them.
 *
 * Sony's messages carry sequence numbers and wait for acknowledgements, so [frames] are not frames:
 * each is a message payload led by its table number ([encode]), which the connection queues on the
 * Sony link ([decode]).
 */
class SonyEqTarget(val session: SonySession, eq: SonyEq) : EqTarget {
    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false
    override val hasCustom: Boolean = eq.customizable && eq.frequencies.isNotEmpty()

    /** The earbuds do not report their presets' curves, so none is drawn. */
    override val presetCurves: Boolean = false

    /** The ids of the presets offered, by the name the screen shows. */
    private val presetIds: Map<String, Int> = eq.offered.associate { (id, name) -> name to id }

    /** The screen's view: the frequency bands, and the presets by the earbuds' names. */
    val layout: EqLayout = EqLayout(
        command = SonyCommand.Set1.EQEBB_SET_PARAM,
        masterGainDb = 0.0,
        userRangeDb = eq.center,
        bands = eq.frequencies.map { EqBand(it, 0.0, 0.0) },
        presets = eq.offered.map { (_, name) -> EqPreset(name, eq.frequencies.map { EqBand(it, 0.0, 0.0) }) },
    )

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val out = if (preset != null) {
            session.eqPreset(presetIds[preset.name] ?: return emptyList())
        } else {
            session.eqCurve(setting.gains)
        }
        return out.orEmpty().map(::encode)
    }

    /** What [eq] says the earbuds hold, as the screen's profile (null for their custom curve) and gains. */
    fun reported(eq: SonyEq): Pair<String?, List<Int>>? {
        val gains = eq.gains.takeIf { it.size == layout.bands.size } ?: return null
        val name = eq.presets.firstOrNull { it.first == eq.preset }?.second
        val profile = if (eq.holdsCustom || name == null || name !in presetIds) null else EqProfile.PRESET + name
        return profile to gains
    }

    companion object {
        fun encode(out: SonyOut): ByteArray = byteArrayOf(out.table.toByte()) + out.payload

        fun decode(bytes: ByteArray): SonyOut? = if (bytes.size < 2) null else SonyOut(bytes[0].toInt(), bytes.copyOfRange(1, bytes.size))
    }
}
