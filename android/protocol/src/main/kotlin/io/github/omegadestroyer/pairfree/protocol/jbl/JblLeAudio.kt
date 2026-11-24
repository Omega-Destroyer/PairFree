package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The LE Audio switch (`LeAudioCard`, docs/jbl/PROTOCOL.md section 4.15): `BaseControl.setFeatures({1: on})`. On the Harman models it is command `B1`
 * (`GetSetFeatureCmd`: a read is 00, the key and 00; a write 01, the key, the value's size 1 and the value; the answer has the sub-command, then key, size and value); on protocol 4
 * feature 2976 (`LEAudioUniCastStatus`, a status bit and level byte); on the Airoha models the same `B1` frame goes through the passthrough (`0093`) as the clock does
 * (`writeHmCommand`). JBL's app asks for a confirmation first, since the earbuds restart their link to switch (`showLeOpenDialog`): PairFree's row says so.
 */
internal class JblLeAudio(private val p4: Boolean, private val race: Boolean) {
    var on: Boolean? = null
        private set

    fun reads(): List<JblFrame> = if (p4) emptyList() else listOf(frame(byteArrayOf(0, KEY.toByte(), 0)))

    fun p4Features(): List<Int> = if (p4) listOf(FEATURE) else emptyList()

    private fun frame(payload: ByteArray): JblFrame {
        val aa = JblPacket(JblCommand.GET_SET_FEATURE, payload)
        return if (race) RacePacket(JblRace.HM_PASSTHROUGH, aa.bytes()) else aa
    }

    /** A reply's payload (the sub-command first): pairs of key, size and value. */
    fun reply(p: ByteArray) {
        var at = 1
        while (at + 2 <= p.size) {
            val key = p[at].toInt() and 0xFF
            val size = p[at + 1].toInt() and 0xFF
            if (at + 2 + size > p.size) return
            if (key == KEY && size >= 1) on = p[at + 2].toInt() != 0
            at += 2 + size
        }
    }

    /** The Airoha models' answer to a passthrough: the Harman frame, AA, B1, the length and the payload. */
    fun passthroughReply(d: ByteArray) {
        if (d.size >= 4 && (d[0].toInt() and 0xFF) == 0xAA && (d[1].toInt() and 0xFF) == JblCommand.GET_SET_FEATURE) reply(d.copyOfRange(3, d.size))
    }

    fun record(id: Int, v: ByteArray) {
        if (id == FEATURE && v.isNotEmpty()) on = (v[0].toInt() and 0x80) != 0
    }

    fun rows(): List<Setting> = on?.let { listOf(Setting.Toggle(KEY_NAME, it)) }.orEmpty()

    fun change(change: SettingChange): List<JblFrame>? {
        if (change !is SettingChange.Toggle || change.key != KEY_NAME) return null
        on = change.on
        return if (p4) listOf(JblP4Packet.set(FEATURE to JblP4Packet.statusLevel(change.on, 127))) else listOf(frame(byteArrayOf(1, KEY.toByte(), 1, if (change.on) 1 else 0)))
    }

    fun readBack(key: String): JblFrame? =
        if (key != KEY_NAME) null else if (p4) JblP4Packet.get(listOf(FEATURE)) else frame(byteArrayOf(0, KEY.toByte(), 0))

    companion object {
        const val KEY_NAME = "jbl_le_audio"
        private const val KEY = 1
        val FEATURE = JblP4.FEATURE_LE_AUDIO_UNICAST

        /** The passthrough frames PairFree lets through for this: a read or a write of the switch's key. */
        fun allowed(frame: ByteArray): Boolean =
            frame.size >= 6 && (frame[0].toInt() and 0xFF) == 0xAA && (frame[1].toInt() and 0xFF) == JblCommand.GET_SET_FEATURE &&
                (frame[4].toInt() and 0xFF) == KEY && (frame[3].toInt() and 0xFF) <= 1
    }
}
