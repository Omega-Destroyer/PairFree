package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelSession
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelValues
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Pixel Buds Pro, Pro 2 and 2a (docs/pixel/PROTOCOL.md section 6): five bands, low bass to upper treble, from -6
 * to 6 dB, and Google's six presets, which the app writes as curves like any other; the user's curve the earbuds
 * last saved shows as Last saved where they hold one. The earbuds report the curve in use, so the screen follows
 * them. Google's sliders move in half steps; the screen's in whole ones, so a curve set in Google's app shows rounded
 * until it is changed here.
 *
 * [frames] are the session's messages as bytes; the connection queues the messages themselves (PixelSession.eqMessages).
 */
class PixelEqTarget(val session: PixelSession) : EqTarget {
    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = false

    private val presets: List<Pair<String, List<Float>>> =
        PixelValues.EQ_PRESETS.map { it.name to it.gains } + listOfNotNull(session.savedEq?.let { LAST_SAVED to it })

    private val bands: List<EqBand> = PixelValues.EQ_BANDS.mapIndexed { i, key -> EqBand(i + 1, 0.0, 0.0, key) }

    val layout: EqLayout = EqLayout(
        command = 0,
        masterGainDb = 0.0,
        userRangeDb = PixelValues.EQ_RANGE_DB,
        bands = bands,
        presets = presets.map { (name, gains) -> EqPreset(name, bands.mapIndexed { i, b -> b.copy(gainDb = gains[i].toDouble()) }) },
    )

    /** True when the layout holds the earbuds' saved curve as Last saved. */
    val hasSaved: Boolean = session.savedEq != null

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> {
        val gains = preset?.bands?.map { it.gainDb.toFloat() } ?: setting.gains.map { it.toFloat() }
        return session.eqWrite(gains).orEmpty().map { it.bytes }
    }

    /** What [gains] say the earbuds hold, as the screen's profile (a preset's id, or null for a curve) and whole-dB gains. */
    fun reported(gains: List<Float>): Pair<String?, List<Int>> {
        val match = presets.firstOrNull { (_, p) -> p.zip(gains).all { (a, b) -> abs(a - b) < SAME_DB } }
        val whole = gains.map { it.roundToInt() }
        return (match?.let { EqProfile.PRESET + it.first }) to whole
    }

    private companion object {
        /** Google's name for the curve the user saved on the earbuds. */
        const val LAST_SAVED = "Last saved"
        const val SAME_DB = 0.01f
    }
}
