package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.airoha.AirohaPeq
import io.github.omegadestroyer.pairfree.protocol.airoha.AirohaPeqBand
import kotlin.math.floor

/**
 * Airoha's PEQ on JBL's Airoha models (docs/jbl/PROTOCOL.md section 11.2), the way Airoha's SDK in JBL's app writes
 * it (AirohaPEQControl and AirohaPEQControl1568, AirohaPeqMgr.UpdateAndSavePeqData and its stages): a sync of the curve
 * to the firmware, the coefficients applied at once, then the curve, its extra data, its peak and the coefficients
 * written to their NV keys, the PEQ subset brought up to date, and the curve's group picked. On earbuds every write is
 * relayed to the other earbud first. Each step waits for the earbuds' answer to the one before; a step that fails
 * ends it, as the SDK's manager does.
 *
 * The AB155x keeps JBL's presets on the earbuds and picks them by group; a curve of the user's own goes to custom slot
 * 2 (category 103). The other chips take every curve, presets too, into custom slot 0.
 */
internal class JblAirohaEq private constructor(
    private val chip: RaceChip,
    private val earbuds: Boolean,
    private var nvdmVersion: Int?,
    private val category: Int,
    private val bands: List<JblEqBand>,
    private val masterDb: Float,
) {
    /** One step: the command (built when its turn comes, since it may need what earlier steps read) and what to do with the answer. */
    private class Step(val relay: Boolean, val build: () -> RacePacket?, val answer: (RacePacket) -> Boolean)

    private val steps = ArrayDeque<Step>()
    private var waiting: Step? = null
    private var partner: ByteArray? = null

    // What the reads found: the audio path's PEQ subset key, the subset's content and the records it lists.
    private var subsetKey = 0
    private var subset = ByteArray(0)
    private var records: List<Record> = emptyList()

    /** One group the PEQ subset lists: its coefficient NV key, custom or not, its slot and the category it stands for. */
    private class Record(val coefKey: Int, val custom: Boolean, val offset: Int, val category: Int)

    /** Every command this write has sent, which [JblSession.allowed] lets through although some are never sent otherwise. */
    val sent: MutableSet<RacePacket> = mutableSetOf()

    /** True once every step has been answered. */
    var done: Boolean = false
        private set

    /** True when a step failed or could not be built; nothing more is sent. */
    var failed: Boolean = false
        private set

    private val v3: Boolean get() = chip == RaceChip.AB158X || (chip == RaceChip.AB1568 && (nvdmVersion ?: 0) > 1)
    private val audioPathKey: Int get() = if (v3) JblRace.PEQ_AUDIO_PATH_V3 else JblRace.PEQ_AUDIO_PATH
    private val coefBase: Int get() = if (v3) JblRace.PEQ_COEF_BASE_V3 else JblRace.PEQ_COEF_BASE
    private val groupBase: Int get() = if (v3) JblRace.PEQ_GROUP_BASE_V3 else JblRace.PEQ_GROUP_BASE

    /** The Airoha category: JBL's preset numbers 0 to 99 move up one, as the SDK's adapter moves them; the rest stay. */
    private val airohaCategory: Int = when {
        category in 0 until 100 -> category + 1
        category < 0 -> 0
        else -> category
    }

    /** The SDK's band data (PeqBandInfo): bandwidth = frequency / Q in single precision; the 1568 control enables only the first band of category 1. */
    private class UiBand(val enable: Boolean, val hz: Float, val gain: Float, val bw: Float) {
        val q: Float = hz / bw
        fun native(): AirohaPeqBand = AirohaPeqBand(hz.toString().toDouble(), gain.toString().toDouble(), bw.toString().toDouble())
    }

    private val uiBands: List<UiBand> = List(UI_BANDS) { i ->
        val b = bands.getOrNull(i)
        if (b == null) {
            UiBand(false, 0f, 0f, 0f)
        } else {
            UiBand(chip == RaceChip.AB155X || airohaCategory != 1 || i == 0, b.hz, b.gainDb, b.hz / b.q)
        }
    }

    private val enabled: List<AirohaPeqBand> get() = uiBands.filter { it.enable }.map { it.native() }

    private fun rates(): List<Pair<Int, Double>> = buildList {
        add(RATE_441 to 44100.0)
        add(RATE_48 to 48000.0)
        if (chip == RaceChip.AB158X) {
            add(RATE_882 to 88200.0)
            add(RATE_96 to 96000.0)
        }
    }

    /** The coefficient blocks (CoefParamStruct): the rate's id, the count of shorts, the shorts, all low byte first; null when the library refuses the curve. */
    private fun coefBlocks(): List<ByteArray>? = rates().map { (id, fs) ->
        val r = AirohaPeq.coefficients(fs, enabled, masterDb.toDouble())
        val shorts = r.shorts ?: return null
        val out = ByteArray(4 + shorts.size * 2)
        out[0] = id.toByte()
        out[1] = (id shr 8).toByte()
        out[2] = shorts.size.toByte()
        out[3] = (shorts.size shr 8).toByte()
        shorts.forEachIndexed { i, s ->
            out[4 + 2 * i] = s.toByte()
            out[5 + 2 * i] = (s.toInt() shr 8).toByte()
        }
        out
    }

    /** PeqUiDataStru: keep-rescale (1), the rescale value (0), then ten bands of 18 bytes. */
    private fun uiData(): ByteArray {
        val out = ArrayList<Byte>(5 + UI_BANDS * 18)
        out += listOf(1, 0, 0, 0, 0)
        for (b in uiBands) {
            out += (if (b.enable) 1 else 0).toByte()
            out += 2
            for (v in listOf(b.hz, b.gain, b.bw, b.q)) out += int(javaRound(v * 100f)).toList()
        }
        return out.toByteArray()
    }

    /** PeqUiExtDataStru: the category, the payload id (0), the sample rate (48 kHz, as JBL's app sets it), the band count and the master gain in hundredths. */
    private fun uiExtData(): ByteArray =
        int(airohaCategory) + int(0) + int(48000) + byteArrayOf(bands.size.toByte(), (bands.size shr 8).toByte()) + int((masterDb.toDouble() * 100.0).toInt())

    private val peak100: Int by lazy { (AirohaPeq.peakDb(enabled, masterDb.toDouble()) * 100.0).toInt() }

    private fun nvWrite(key: Int, data: ByteArray): RacePacket = RacePacket(JblRace.NV_WRITE, byteArrayOf(key.toByte(), (key shr 8).toByte()) + data)

    private fun nvRead(key: Int): RacePacket = RacePacket.nvRead(key, JblRace.PEQ_READ_LENGTH)

    private fun ok(p: RacePacket): Boolean = p.status == 0

    /** A write to both earbuds where there are two: relayed first, then to the one PairFree talks to, as addSave*StagesToQueue order them. */
    private fun both(build: () -> RacePacket?, answer: (RacePacket) -> Boolean = ::ok) {
        if (earbuds) steps += Step(true, build, answer)
        steps += Step(false, build, answer)
    }

    private fun plan(): Boolean {
        val coefs = coefBlocks() ?: return false
        if (chip == RaceChip.AB1568 && nvdmVersion == null) {
            steps += Step(false, { nvRead(JblRace.NV_NVDM_VERSION) }) { p ->
                nvdmVersion = p.data.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                true
            }
        }
        val presetOnEarbuds = chip == RaceChip.AB155X && airohaCategory in 1..100
        if (presetOnEarbuds) {
            readPath(0)
            pickGroup { records.indexOfFirst { it.category == airohaCategory } + 1 }
            return true
        }
        val offset = if (chip == RaceChip.AB155X) airohaCategory - CUSTOM_FIRST else 0
        if (offset !in 0..3) return false
        val coefKey: () -> Int = { coefBase + JblRace.PEQ_CUSTOM_OFFSET + offset }
        steps += Step(false, { RacePacket(JblRace.PEQ_REALTIME, byteArrayOf(0) + realtime(coefs)) }, ::ok)
        if (earbuds) {
            steps += Step(false, { RacePacket(JblRace.RELAY_DST) }) { p ->
                val d = p.payload
                partner = (0 until d.size - 1 step 2).map { d.copyOfRange(it, it + 2) }.firstOrNull { (it[0].toInt() and 0xFF) == JblRace.PEQ_PARTNER_DST }
                partner != null
            }
        }
        both({ nvWrite(JblRace.PEQ_UI_DATA + offset, uiData()) })
        both({ nvWrite(JblRace.PEQ_UI_EXT + offset, uiExtData()) })
        both({ nvWrite(JblRace.PEQ_UI_PEAK + offset, int(peak100)) })
        readPath(coefKey())
        both({ nvWrite(coefKey(), realtime(coefs)) })
        both({ nvWrite(subsetKey, updatedSubset(coefKey())) })
        both({ RacePacket(JblRace.PEQ_SAVE_STATUS, byteArrayOf(0, coefKey().toByte(), (coefKey() shr 8).toByte())) })
        readPath(coefKey())
        pickGroup {
            val i = records.indexOfFirst { it.custom && it.offset == offset }
            if (i >= 0) i + 1 else records.size + 1
        }
        return true
    }

    /** The coefficient blocks as the save and the real-time update take them: the block count, 00 00, then the blocks. */
    private fun realtime(coefs: List<ByteArray>): ByteArray =
        byteArrayOf(coefs.size.toByte(), (coefs.size shr 8).toByte(), 0, 0) + coefs.reduce { a, b -> a + b }

    /**
     * PeqStageReadAudiPath and PeqStageReadPeqSubset: which NV key holds the PEQ subset, and what it lists; an empty
     * subset counts as one group for [coefKey], the key the coefficients go to.
     */
    private fun readPath(coefKey: Int) {
        steps += Step(false, { nvRead(audioPathKey) }) { p ->
            val d = p.data
            subsetKey = groupBase
            if (d.any { it.toInt() != 0 } && d.size >= 2) {
                val count = u16(d, 0)
                for (i in 0 until count) {
                    val at = i * 4
                    if (at + 5 < d.size && u16(d, at + 2) == 0) {
                        subsetKey = u16(d, at + 4)
                        break
                    }
                }
            }
            true
        }
        steps += Step(false, { nvRead(subsetKey) }) { p ->
            val d = p.data
            subset = if (d.any { it.toInt() != 0 }) d else byteArrayOf(1, 0, 1, 0, 0, 0) + key(coefKey)
            records = records(subset)
            true
        }
    }

    private fun pickGroup(index: () -> Int) {
        steps += Step(false, {
            val i = index()
            if (i <= 0) null else RacePacket.of(JblRace.MMI_SET, 0, 0, i)
        }, ::ok)
        steps += Step(false, { RacePacket.mmiGet(0) }, ::ok)
    }

    /** MgrPeqData's records, as AirohaPeqMgr.getPeqDefaultGroupNum reads the subset: a count, then each group's entries. */
    private fun records(c: ByteArray): List<Record> {
        if (c.size < 2) return emptyList()
        val count = u16(c, 0)
        var at = 2
        val out = ArrayList<Record>()
        repeat(count) {
            if (at + 5 >= c.size) return out
            val n = u16(c, at)
            val key = u16(c, at + 4)
            val custom = key - coefBase - JblRace.PEQ_CUSTOM_OFFSET
            out += if (custom in 0..3) Record(key, true, custom, custom + CUSTOM_FIRST) else Record(key, false, 0, out.size + 1)
            at += n * 4 + 2
        }
        return out
    }

    /** PeqStageUpdatePeqSubset: the subset as read, with one more group for [coefKey] unless its hex already appears. */
    private fun updatedSubset(coefKey: Int): ByteArray {
        if (hex(subset).contains(hex(key(coefKey)))) return subset
        val c = subset.copyOf()
        val n = u16(c, 0) + 1
        c[0] = n.toByte()
        c[1] = (n shr 8).toByte()
        return c + byteArrayOf(1, 0, 0, 0) + key(coefKey)
    }

    /** The first frames: the sync of the curve with the firmware (no answer awaited) and the first step. */
    fun start(): List<RacePacket> {
        val sync = RacePacket(JblRace.PEQ_SYNC_UI, uiData() + int(peak100))
        val first = advance() ?: return emptyList()
        val presetOnEarbuds = chip == RaceChip.AB155X && airohaCategory in 1..100
        return (if (presetOnEarbuds) listOf(first) else listOf(sync, first)).onEach { sent += it }
    }

    /**
     * Takes [p] if it is the answer the current step waits for: true when taken, with the next command (if any) in
     * [next]. An answer taken is this write's alone; the session leaves it at that.
     */
    fun receive(p: RacePacket, next: (RacePacket) -> Unit): Boolean {
        val step = waiting ?: return false
        val answer = if (step.relay) {
            relayAnswer(p) ?: return false
        } else {
            if (p.type != JblRace.TYPE_RESPONSE || p.id != pendingId) return false
            p
        }
        waiting = null
        if (!step.answer(answer)) {
            failed = true
            return true
        }
        advance()?.let {
            sent += it
            next(it)
        }
        return true
    }

    private var pendingId = 0

    private fun advance(): RacePacket? {
        while (true) {
            val step = steps.removeFirstOrNull() ?: run {
                done = true
                return null
            }
            val cmd = step.build() ?: run {
                failed = true
                return null
            }
            waiting = step
            if (!step.relay) {
                pendingId = cmd.id
                return cmd
            }
            val dst = partner ?: run {
                failed = true
                return null
            }
            pendingId = JblRace.RELAY
            return RacePacket(JblRace.RELAY, dst + cmd.bytes())
        }
    }

    /** A relayed answer: an indication of 0D01 whose payload is the destination, then the other earbud's own frame. */
    private fun relayAnswer(p: RacePacket): RacePacket? {
        if (p.type != JblRace.TYPE_INDICATION || p.id != JblRace.RELAY || p.payload.size < 8) return null
        val raw = p.payload.copyOfRange(2, p.payload.size)
        val n = u16(raw, 2)
        if (raw.size < 4 + n || n < 2) return null
        return RacePacket(u16(raw, 4), raw.copyOfRange(6, 4 + n), raw[1].toInt() and 0xFF)
    }

    private fun u16(d: ByteArray, at: Int): Int = (d[at].toInt() and 0xFF) or ((d[at + 1].toInt() and 0xFF) shl 8)
    private fun key(k: Int): ByteArray = byteArrayOf(k.toByte(), (k shr 8).toByte())
    private fun int(v: Int): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** Java's Math.round(float): floor(x + 0.5) in single precision, NaN 0. */
    private fun javaRound(v: Float): Int = if (v.isNaN()) 0 else floor(v + 0.5f).toInt()

    companion object {
        private const val UI_BANDS = 10
        private const val CUSTOM_FIRST = 101

        /** The custom category JBL's app writes a curve of the user's own under on Airoha (EqCatIndex.CUSTOM_EQ). */
        const val CUSTOM = 103

        private const val RATE_441 = 1
        private const val RATE_48 = 2
        private const val RATE_882 = 5
        private const val RATE_96 = 6

        /**
         * The write of [bands] under JBL's [category] with the master gain [masterDb] (the curve's left gain) on [chip];
         * [earbuds] relays every write to the other earbud. Null when Airoha's library refuses the curve.
         */
        fun of(chip: RaceChip, earbuds: Boolean, nvdmVersion: Int?, category: Int, bands: List<JblEqBand>, masterDb: Float): JblAirohaEq? {
            val w = JblAirohaEq(chip, earbuds, nvdmVersion, category, bands, masterDb)
            return if (w.plan()) w else null
        }
    }
}
