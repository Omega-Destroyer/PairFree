package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceChip
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import kotlin.math.roundToInt

/**
 * The strength of noise cancelling and of transparency on Skullcandy's Airoha models without an SDK configuration (docs/skullcandy/PROTOCOL.md section 9.7).
 *
 * The app's two sliders (`adjustableNoiseCancellation`, `adjustableStayAwareMode`) turn a percentage into dB with `convertPercentageToAirohaGain` (`percent * 20 / 100 - 20`, so -20 to 0 dB) and give
 * it to the Airoha SDK's `setAncSetting`. The SDK writes the gain as the ANC command `0E06` with `00 0F`, the dB times 100 as a short (low byte first) and, on the AB1562 only, a trailing `01`
 * (`libmmi1562.stage.j`; `libmmi.stage.k` leaves the byte out unless a sync mode is set, which the app never does). The state is NV key `E0E0`, 12 bytes: on, the filter, two unused,
 * the noise cancelling gain and the transparency gain (dB times 100, low byte first), then the mode as an int (`saveAncSettingToNv`).
 *
 * PairFree sets the gain live and does not write the NV key afterwards, as the app does.
 */
internal class SkullcandyAirohaStrength(product: SkullcandyProduct) {
    private val sdk = product.usesAirohaSdk
    val anc: Boolean = sdk && "adjustableNoiseCancellation" in product.features
    val transparency: Boolean = sdk && "adjustableStayAwareMode" in product.features
    val has: Boolean get() = anc || transparency

    /** The read of the ANC state's NV key. */
    val read: RacePacket = RacePacket.nvRead(NV_KEY, NV_LENGTH)

    private var ancDb: Double? = null
    private var passDb: Double? = null

    /** The key's bytes: true when a gain changed. */
    fun answer(data: ByteArray): Boolean {
        if (data.size < 8) return false
        fun short(at: Int) = (((data[at + 1].toInt() shl 8) or (data[at].toInt() and 0xFF)).toShort().toInt()) / 100.0
        val a = short(4)
        val t = short(6)
        val changed = ancDb != a || passDb != t
        ancDb = a
        passDb = t
        return changed
    }

    /** The slider of [mode] (`anc` or `transparency`) in tenths, null when the model has no slider for it or nothing has been read. */
    fun tenths(mode: String?): Int? {
        val db = when (mode) {
            "anc" -> if (anc) ancDb else null
            "transparency" -> if (transparency) passDb else null
            else -> null
        } ?: return null
        return ((db + 20.0) / 2.0).roundToInt().coerceIn(0, 10)
    }

    /** The gain write of [tenths] for [mode], null when the model has no slider for the mode or the chip is not known (the trailing byte depends on it). */
    fun write(mode: String?, tenths: Int, chip: RaceChip?): RacePacket? {
        if (chip == null || (mode == "anc" && !anc) || (mode == "transparency" && !transparency) || (mode != "anc" && mode != "transparency")) return null
        val db = tenths.coerceIn(0, 10) * 10 * 20.0 / 100.0 - 20.0
        if (mode == "anc") ancDb = db else passDb = db
        val v = (db * 100.0).toInt().toShort().toInt()
        val lo = v and 0xFF
        val hi = (v shr 8) and 0xFF
        return if (chip == RaceChip.AB1562) RacePacket.of(JblRace.ANC, 0, GAIN, lo, hi, 1) else RacePacket.of(JblRace.ANC, 0, GAIN, lo, hi)
    }

    companion object {
        const val NV_KEY = 0xE0E0
        const val NV_LENGTH = 12
        const val GAIN = 0x0F
    }
}
