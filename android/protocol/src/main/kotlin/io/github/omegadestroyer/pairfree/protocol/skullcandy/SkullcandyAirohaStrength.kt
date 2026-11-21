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

    private val enumName = product.enumName
    private var savedFilter = 0
    private var savedMode = 0
    private var ancDb: Double? = null
    private var passDb: Double? = null

    /** The key's bytes: true when a gain changed. */
    fun answer(data: ByteArray): Boolean {
        if (data.size < 8) return false
        fun short(at: Int) = (((data[at + 1].toInt() shl 8) or (data[at].toInt() and 0xFF)).toShort().toInt()) / 100.0
        val a = short(4)
        val t = short(6)
        val changed = ancDb != a || passDb != t
        savedFilter = data[1].toInt() and 0xFF
        if (data.size >= 9) savedMode = data[8].toInt() and 0xFF
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

    /** The app's filter number of noise cancelling (`SKDYAncMode.ancFilterId`) and of transparency (`passThruFilterId`): the SDK's `UI_ANC_FILTER` ordinals. */
    private val ancOrdinal = if (enumName == "railANC") 3 else 1
    private val passOrdinal = when (enumName) {
        "hanc", "tanc" -> 11
        "hesh540ANC" -> 8
        "aviatorANC" -> 1
        "mod" -> 5
        else -> 4
    }

    /** The SDK's raw filter of an ordinal on [chip]: the tables of `libmmi1562`, `libmmi1568` and `libmmi158x` (section 9.7); null where the chip's SDK has none (it then turns the filter off). */
    private fun raw(chip: RaceChip, ordinal: Int): Int? = when {
        chip == RaceChip.AB155X -> null
        ordinal in 1..3 -> ordinal
        ordinal == 7 -> 4
        ordinal in 4..6 -> ordinal + 5
        ordinal in 8..10 -> if (chip == RaceChip.AB1568 || chip == RaceChip.AB158X) ordinal - 3 else null
        ordinal in 11..13 -> if (chip == RaceChip.AB158X) ordinal + 1 else null
        else -> null
    }

    /**
     * The command that turns [mode] (`off`, `anc`, `transparency`) on in the SDK's own form: `0E06` with `00 0A`, the raw filter and the mode byte (and on the AB1562 a trailing `01`; its pass-through filters
     * 9 to 11 always carry mode 4), or `00 0B` for off. The mode byte on the later chips is what the app takes from a per-product object that is not traced: the earbuds' own saved byte when the saved filter
     * is of the same kind, else 0 for noise cancelling and 8 for transparency (inferred).
     */
    fun modeFrame(mode: String, chip: RaceChip?): RacePacket? {
        chip ?: return null
        val tail = if (chip == RaceChip.AB1562) intArrayOf(1) else IntArray(0)
        if (mode == "off") return if (chip == RaceChip.AB155X) null else RacePacket.of(JblRace.ANC, 0, 0x0B, *tail)
        val transparencyMode = mode == "transparency"
        val filter = raw(chip, if (transparencyMode) passOrdinal else ancOrdinal) ?: return null
        val savedIsTransparency = savedFilter in 5..7 || savedFilter in 9..14
        val saved = if (savedIsTransparency == transparencyMode && savedFilter != 0) savedMode else if (transparencyMode) 8 else 0
        val byte = if (chip == RaceChip.AB1562 && filter in 9..11) 4 else saved
        savedFilter = filter
        savedMode = byte
        return RacePacket.of(JblRace.ANC, 0, 0x0A, filter, byte, *tail)
    }

    /** The state key's 12 bytes as the SDK saves them (`saveAncSettingToNv`): on, the filter, two zeros, the two gains in hundredths of a dB (low byte first) and the mode as an int. */
    private fun state(): ByteArray {
        val out = ByteArray(NV_LENGTH)
        out[0] = if (savedFilter != 0) 1 else 0
        out[1] = savedFilter.toByte()
        val a = ((ancDb ?: 0.0) * 100.0).toInt()
        val t = ((passDb ?: 0.0) * 100.0).toInt()
        out[4] = a.toByte(); out[5] = (a shr 8).toByte()
        out[6] = t.toByte(); out[7] = (t shr 8).toByte()
        out[8] = savedMode.toByte()
        return out
    }

    /**
     * What the app does after a change when it saves (`doSave`, true by default): the space for the key is reclaimed (`0A03` with the length) and the key written (`0A01`). The AB1568 and later
     * do the other earbud first, relayed through `0D01`; the AB1562 the near one first. Null before the chip or, for earbuds, the other earbud's address is known.
     */
    fun save(chip: RaceChip?, earbuds: Boolean, partner: ByteArray?): List<RacePacket>? {
        chip ?: return null
        if (savedFilter == 0 || ancDb == null) return null
        if (earbuds && partner == null) return null
        val reclaim = RacePacket(RECLAIM, byteArrayOf(NV_LENGTH.toByte(), 0))
        val write = RacePacket(JblRace.NV_WRITE, byteArrayOf((NV_KEY and 0xFF).toByte(), (NV_KEY shr 8).toByte()) + state())
        val local = listOf(reclaim, write)
        val far = if (earbuds) listOf(reclaim, write).map { RacePacket(JblRace.RELAY, partner!! + it.bytes()) } else emptyList()
        return if (chip == RaceChip.AB1562) local + far else far + local
    }


    companion object {
        const val NV_KEY = 0xE0E0
        const val NV_LENGTH = 12
        const val GAIN = 0x0F
        private const val RECLAIM = 0x0A03
    }
}
