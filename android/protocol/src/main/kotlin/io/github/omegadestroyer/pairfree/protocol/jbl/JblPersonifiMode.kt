package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * PersoniFi's switch and the messages the hearing test wraps around its tones (docs/jbl/PROTOCOL.md section 4.14), as JBL Headphones 5.37.10 sends them.
 * On the Harman models they are command `A1` (`PersoniFiModeHearingTextCmd`: a write is 00 and pairs of id and value, id 6 in four bytes, low first);
 * on protocol 4 the five features 7680 to 7684 (`PersoniFiMode`, `HearingTestMode`, `PersoniFiSetupFlow`, `HearingTestFileSide`,
 * `HearingTestFileFrequency`), a status and level byte each (`StatusLevelSize1Feature`: the value as bit 7, 127 as the level) and the frequency in four bytes.
 */
internal class JblPersonifiMode(private val p4: Boolean) {
    /** Whether PersoniFi's curve is on, once the earbuds said. */
    var on: Boolean? = null
        private set

    /**
     * What the earbuds last said of the test: its status (1 on, 3 and 4 failed, as the wizard reads them: a call or lost focus; protocol 4's levels 1 to 4 are 2 to 5 there) and
     * the frequency of the tone they have ready, which the wizard waits to be the one it asked for before it plays the tone (`sk.q.Y1`).
     */
    var testStatus: Int? = null
        private set
    var confirmedHz: Int? = null
        private set

    /** The first read: the one command, or the five features (which [JblHarmanKit.p4Features] asks for on protocol 4). */
    fun reads(): List<JblFrame> = if (p4) emptyList() else listOf(JblPacket.of(JblCommand.PERSONIFI, READ))

    fun p4Features(): List<Int> = if (p4) P4_IDS.toList() else emptyList()

    /** `PersoniFiModeHearingTextCmd.parse2` on the reply's payload (02, then id and value: id 6 four bytes, the others one). */
    fun reply(p: ByteArray) {
        if (p.isEmpty() || (p[0].toInt() and 0xFF) != RET) return
        var at = 1
        while (at < p.size) {
            val id = p[at].toInt() and 0xFF
            val size = if (id == ID_HZ) 4 else if (id in 1..5) 1 else return
            if (at + 1 + size > p.size) return
            if (id == ID_ON) on = (p[at + 1].toInt() and 0xFF) == 1
            if (id == ID_TEST) testStatus = p[at + 1].toInt() and 0xFF
            if (id == ID_HZ) confirmedHz = (0 until 4).sumOf { (p[at + 1 + it].toInt() and 0xFF) shl (8 * it) }
            at += 1 + size
        }
    }

    /** A protocol 4 feature's value. */
    fun record(id: Int, v: ByteArray) {
        if (v.isEmpty()) return
        val byte = v[0].toInt() and 0xFF
        when (id) {
            FEATURE_MODE -> on = byte and 0x80 != 0
            // `gn.l0`: the level 1 to 4 are statuses 2 to 5, else the status bit.
            FEATURE_TEST -> testStatus = (byte and 0x7F).let { if (it in 1..4) it + 1 else byte shr 7 }
            FEATURE_HZ -> if (v.size >= 4) confirmedHz = (0 until 4).sumOf { (v[it].toInt() and 0xFF) shl (8 * it) }
        }
    }

    fun rows(): List<Setting> = on?.let { listOf(Setting.Toggle(KEY, it)) }.orEmpty()

    fun change(change: SettingChange): List<JblFrame>? {
        if (change !is SettingChange.Toggle || change.key != KEY) return null
        on = change.on
        return write(ID_ON to if (change.on) 1 else 0)
    }

    fun readBack(key: String): JblFrame? =
        if (key != KEY) null else if (p4) JblP4Packet.get(listOf(FEATURE_MODE)) else JblPacket.of(JblCommand.PERSONIFI, READ)

    /** The setup flow starts (`pk.e.startPersonifiFlow`). */
    fun begin(): List<JblFrame> = write(ID_FLOW to 1)

    /** A tone starts: which earbud (1 left, 2 right) and its frequency (`sk.q.q2`, sent each time the next tone is due). */
    fun tone(left: Boolean, hz: Int): List<JblFrame> {
        confirmedHz = null
        return write(ID_SIDE to if (left) 1 else 2, ID_HZ to hz)
    }

    /** The flow ends (`pk.e.stopPersonifiFlow`) and the hearing test mode is set to 0, as the app does when it finishes or is left. */
    fun end(): List<JblFrame> = write(ID_TEST to 0, ID_FLOW to 0)

    private fun write(vararg pairs: Pair<Int, Int>): List<JblFrame> {
        if (!p4) {
            val ordered = pairs.sortedBy { it.first }
            val out = ArrayList<Byte>()
            out += WRITE.toByte()
            for ((id, v) in ordered) {
                out += id.toByte()
                if (id == ID_HZ) repeat(4) { out += (v shr (8 * it)).toByte() } else out += v.toByte()
            }
            return listOf(JblPacket(JblCommand.PERSONIFI, out.toByteArray()))
        }
        // One set message with each feature (setDeviceInfoList4), the side as 0 or 1 and the frequency in four bytes.
        val features = pairs.sortedBy { it.first }.map { (id, v) ->
            when (id) {
                ID_ON -> FEATURE_MODE to JblP4Packet.statusLevel(v == 1, LEVEL)
                ID_TEST -> FEATURE_TEST to JblP4Packet.statusLevel(v == 1, LEVEL)
                ID_FLOW -> FEATURE_FLOW to JblP4Packet.statusLevel(v == 1, LEVEL)
                ID_SIDE -> FEATURE_SIDE to JblP4Packet.statusLevel(v > 1, LEVEL)
                else -> FEATURE_HZ to byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            }
        }
        return listOf(JblP4Packet.set(*features.toTypedArray()))
    }

    companion object {
        const val KEY = "jbl_personifi"
        private const val READ = 1
        private const val WRITE = 0
        private const val RET = 2
        private const val LEVEL = 127
        private const val ID_ON = 1
        private const val ID_TEST = 2
        private const val ID_FLOW = 3
        private const val ID_SIDE = 5
        private const val ID_HZ = 6
        val FEATURE_MODE = JblP4.FEATURE_PERSONIFI_MODE
        val FEATURE_TEST = JblP4.FEATURE_HEARING_TEST_MODE
        val FEATURE_FLOW = JblP4.FEATURE_PERSONIFI_SETUP_FLOW
        val FEATURE_SIDE = JblP4.FEATURE_HEARING_TEST_SIDE
        val FEATURE_HZ = JblP4.FEATURE_HEARING_TEST_FREQUENCY
        val P4_IDS = setOf(FEATURE_MODE, FEATURE_TEST, FEATURE_FLOW, FEATURE_SIDE, FEATURE_HZ)
    }
}
