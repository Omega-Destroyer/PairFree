package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.airoha.AirohaPeq
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAirohaEq
import io.github.omegadestroyer.pairfree.protocol.jbl.JblEqBand
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceChip
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser

/**
 * The conversation over Skullcandy's second serial channel, the Airoha RACE one (docs/skullcandy/PROTOCOL.md sections 11 and 13): Skullcandy's own custom commands (multipoint, wear
 * detection, the Crusher PLYR 720's bass boost) and the custom equaliser, written as Airoha's SDK writes a PEQ curve ([JblAirohaEq] with the library variant Skullcandy's own library is:
 * the Q as each band's third value and a strict comparator). A message is a RACE frame; the chip family is read first, as JBL's session reads it, since the PEQ's NV keys depend on it.
 */
public class SkullcandyRaceTalk(public val product: SkullcandyProduct) : SessionTalk<RacePacket>() {
    override val pacing: Pacing = Pacing(replyMs = 1500, gapMs = 100, startGapMs = 100, resends = 1)
    override val state: EarbudState = EarbudState()

    private val parser = RaceParser()
    private var pending: RacePacket? = null
    private val kinds = HashMap<RacePacket, String>()
    private var readBacks: List<RacePacket> = emptyList()
    private val outbox = ArrayList<RacePacket>()

    /** The chip family the earbuds named, null until they have. */
    private var chip: RaceChip? = null
    private var chipRead: RacePacket? = null

    /** The curve write in progress, whose commands [allowed] lets through. */
    private var write: JblAirohaEq? = null

    private var multipoint: Boolean? = null
    private var wearDetect: Boolean? = null
    private var bassBoost: Boolean? = null

    private val airoha = product.chip == "airoha"
    public val hasMultipoint: Boolean = airoha && ("multipointPairingToggle" in product.features || "multipointPairing" in product.features)
    public val hasWearDetect: Boolean = airoha && "wearDetect" in product.features
    public val hasBassBoost: Boolean = airoha && "crusherBassBoost" in product.features

    /** True when the model has a custom equaliser the app sets through Airoha's PEQ. */
    public val hasCustomEq: Boolean = airoha && "customEqualizer" in product.features

    /** True when the model has anything this channel carries. */
    public val hasRaceFeatures: Boolean get() = hasMultipoint || hasWearDetect || hasBassBoost || hasCustomEq

    /** The equaliser's bands in Hz and the gain range, for the Sound tab. */
    public val eqBandsHz: List<Int> = SkullcandyEq.bandsHz(product.enumName)
    public val eqRangeDb: Int = SkullcandyEq.rangeDb(product.enumName)

    /** True once the earbuds have named their chip, which a curve's write needs. */
    public val chipKnown: Boolean get() = chip != null

    /** The gains last written to the earbuds in this connection, by band; null before a write. */
    public var eqGains: List<Double>? = null
        private set

    /** True once a curve has gone out and every step has been answered. */
    public val eqWritten: Boolean get() = write?.done == true

    override val groups: List<SettingGroup> get() = buildList {
        multipoint?.let { add(SettingGroup("connection", SettingTab.DEVICE, listOf(Setting.Toggle(MULTIPOINT, it)))) }
        wearDetect?.let { add(SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle(WEAR_DETECT, it)))) }
        bassBoost?.let { add(SettingGroup("sound", SettingTab.SOUND, listOf(Setting.Toggle(BASS_BOOST, it)))) }
    }

    private fun packet(hex: String, kind: String): RacePacket {
        val p = RaceParser().feed(SkullcandyRace.bytes(hex)).single()
        kinds[p] = kind
        return p
    }

    override fun start(): List<RacePacket> = buildList {
        if (hasCustomEq) add(RacePacket.nvRead(JblRace.NV_CHIP_NAME, CHIP_NV_LENGTH).also { chipRead = it })
        if (hasMultipoint) add(packet(SkullcandyRace.multipointGet(product.enumName), "multipoint"))
        if (hasWearDetect) add(packet(SkullcandyRace.WEAR_DETECT_GET, "wear"))
        if (hasBassBoost) add(packet(SkullcandyRace.BASS_BOOST_GET, "bass"))
    }

    override fun poll(): List<RacePacket> = emptyList()

    override fun allowed(message: RacePacket): Boolean =
        message == chipRead || write?.sent?.contains(message) == true || SkullcandyRace.isAllowed(message.bytes().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() })

    override fun bytes(message: RacePacket): ByteArray {
        pending = message
        return message.bytes()
    }

    override fun describe(message: RacePacket): String = message.toString()

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val answers = mutableListOf<Any>()
        for (p in parser.feed(bytes)) {
            guarded {
                val eq = write
                if (eq != null && !eq.done && !eq.failed && eq.receive(p) { outbox += it }) {
                    if (eq.done || eq.failed) changed = true
                } else {
                    changed = apply(p) || changed
                }
            }
            answers += p
        }
        return Fed(changed, answers)
    }

    override fun answers(sent: RacePacket, got: Any): Boolean = got is RacePacket

    /** What an answer means: the chip name, or the value of the read that was asked. */
    private fun apply(p: RacePacket): Boolean {
        val asked = pending ?: return false
        if (asked == chipRead && p.id == JblRace.NV_READ) {
            val text = p.data.decodeToString().trimEnd('\u0000')
            if (text.length >= 4 && text.all { it in ' '..'~' }) {
                chip = RaceChip.of(text)
                return false
            }
            return false
        }
        val kind = kinds[asked] ?: return false
        if (p.type != 0x5B && p.type != 0x5D || p.payload.isEmpty()) return false
        val on = p.payload.last().toInt() == 1
        val before = Triple(multipoint, wearDetect, bassBoost)
        when (kind) {
            "multipoint" -> multipoint = on
            "wear" -> wearDetect = on
            "bass" -> bassBoost = on
        }
        return before != Triple(multipoint, wearDetect, bassBoost)
    }

    override fun takeOutbox(): List<RacePacket> = outbox.toList().also { outbox.clear() }

    override fun takeReadBacks(): List<RacePacket> = readBacks.also { readBacks = emptyList() }

    override fun change(change: SettingChange): List<RacePacket>? = when {
        change is SettingChange.Toggle && change.key == MULTIPOINT && hasMultipoint -> {
            multipoint = change.on
            readBacks = listOf(packet(SkullcandyRace.multipointGet(product.enumName), "multipoint"))
            listOf(packet(SkullcandyRace.multipointSet(product.enumName, change.on), "multipoint_set"))
        }
        change is SettingChange.Toggle && change.key == WEAR_DETECT && hasWearDetect -> {
            wearDetect = change.on
            readBacks = listOf(packet(SkullcandyRace.WEAR_DETECT_GET, "wear"))
            listOf(packet(SkullcandyRace.wearDetectSet(change.on), "wear_set"))
        }
        change is SettingChange.Toggle && change.key == BASS_BOOST && hasBassBoost -> {
            bassBoost = change.on
            readBacks = listOf(packet(SkullcandyRace.BASS_BOOST_GET, "bass"))
            listOf(packet(SkullcandyRace.bassBoostSet(change.on), "bass_set"))
        }
        else -> null
    }

    /**
     * The first frames of the write of the custom curve [gains] (one per band, in dB, within the model's range) with the master gain [masterDb]; the later steps follow as the earbuds
     * answer. Null when the chip is not known yet, the model has no custom curve, the gains do not fit the bands or the library refuses the curve.
     */
    public fun eqCurve(gains: List<Double>, masterDb: Double = 0.0): List<ByteArray>? {
        if (!hasCustomEq || gains.size != eqBandsHz.size) return null
        val c = chip ?: return null
        val range = eqRangeDb.toDouble()
        val bands = eqBandsHz.mapIndexed { i, hz -> JblEqBand(2, hz.toFloat(), gains[i].coerceIn(-range, range).toFloat(), SkullcandyEq.Q) }
        val earbuds = product.form != Form.HEADPHONES
        val w = JblAirohaEq.of(c, earbuds, null, SkullcandyEq.CATEGORY, bands, masterDb.toFloat(), AirohaPeq.Library.SERIALIZE) ?: return null
        write = w
        eqGains = gains
        return w.start().map { it.bytes() }
    }

    public companion object {
        public const val MULTIPOINT: String = "multipoint"
        public const val WEAR_DETECT: String = "sk_wear_detect"
        public const val BASS_BOOST: String = "bass_boost"
        private const val CHIP_NV_LENGTH = 1000
    }
}
