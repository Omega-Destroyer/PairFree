package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.CapsuleWire
import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * The SOUNDPEATS models QCY builds (family QCY-WUQI: the Capsule3 Pro and Pro+), which PeatsAudio drives with its copy of
 * QCY's library (docs/soundpeats/PROTOCOL.md section 10, data/soundpeats/qcy_commands.json). They speak QCY's protocol,
 * so [QcySession] runs them as a model of its own: noise control (one-byte values), game mode, touch controls off,
 * multipoint (LDAC the other way, as PeatsAudio sends it; its LDAC switch does nothing there) and the touch controls with
 * the noise control cycle. They advertise under SOUNDPEATS' company id, [CapsuleWire.COMPANY].
 */
public object Capsule {
    public const val FAMILY: String = "QCY-WUQI"

    /** Whether [model] is one of them. */
    public fun of(model: Model?): Boolean = model?.family == FAMILY

    /** The model [QcySession] runs for [model], named as SOUNDPEATS names it. */
    public fun qcyModel(model: Model): QcyModel = QcyModel(
        vendorId = 0,
        name = model.names.first(),
        names = model.names,
        family = FAMILY,
        protocol = QcyProtocol.QCY,
        form = Form.EARBUDS,
        anc = listOf(
            QcyNoiseMode("cancelling", CapsuleWire.NOISE_ANC, CapsuleWire.NOISE_ANC, CapsuleWire.NOISE_ANC, emptyList()),
            QcyNoiseMode("transparency", CapsuleWire.NOISE_TRANSPARENCY, CapsuleWire.NOISE_TRANSPARENCY, CapsuleWire.NOISE_TRANSPARENCY, emptyList()),
            QcyNoiseMode("off", CapsuleWire.NOISE_OFF, CapsuleWire.NOISE_OFF, CapsuleWire.NOISE_OFF, emptyList()),
        ),
        touch = QcyTouch(
            listOf("single", "double", "triple", "hold"),
            listOf("none", "play_pause", "previous", "next", "voice_assistant", "volume_up", "volume_down", "game_mode", "noise_control"),
            mapOf(CapsuleWire.ACTION_ANC_CYCLE to "noise_control"),
        ),
        balance = false,
        settings = listOf(
            QcySetting(CapsuleWire.GAME_MODE, "game_mode"),
            QcySetting(CapsuleWire.TOUCH_DISABLED, "sleep_mode"),
            QcySetting(CapsuleWire.MULTIPOINT, "multipoint"),
        ),
        zeroOff = setOf(CapsuleWire.LDAC, CapsuleWire.MULTIPOINT),
        multipointSwapsLdac = true,
        soundpeatsEq = true,
    )

    /**
     * The EQ write (section 10.5): 00, the master gain in dB x 100 (truncated to whole dB first),
     * then per band its frequency in Hz, gain in dB x 100 and Q x 100, two bytes each, low first, as QCY's library writes it (`QCYHeadsetClient`).
     */
    public fun eqPayload(masterGainDb: Double, bands: List<EqBand>): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out += (v and 0xFF).toByte(); out += ((v shr 8) and 0xFF).toByte() }
        out += 0
        u16(masterGainDb.toInt() * 100)
        for (b in bands) {
            u16(b.hz)
            // As QCY's library: a float times 100, truncated.
            u16((b.gainDb.toFloat() * 100f).toInt())
            u16((b.q.toFloat() * 100f).toInt())
        }
        return out.toByteArray()
    }

    /** [eqPayload] as the message for the session's link. */
    public fun eqMessage(masterGainDb: Double, bands: List<EqBand>): ByteArray = GattEnvelope.write(QcyWire.EQ_SHORT, eqPayload(masterGainDb, bands))
}
