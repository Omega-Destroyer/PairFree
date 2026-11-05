package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.GattEnvelope
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private fun packets(messages: List<ByteArray>?): List<String> = messages!!.map { hex(GattEnvelope.out(it)!!.value) }

/** SOUNDPEATS' QCY-built Capsule3 Pro on QCY's session, against docs/soundpeats/PROTOCOL.md section 10. */
class CapsuleTest {
    private val model = Models.find("SPTS04", null)!!

    private fun session(report: String): QcySession =
        QcySession(Capsule.qcyModel(model)).also { it.receive(GattEnvelope.received(QcyWire.SETTINGS_SHORT, report.hexBytes())) }

    @Test
    fun isOneOfQcysModels() {
        assertTrue(Capsule.of(model))
        assertTrue(Capsule.of(Models.find("QCYTS04", null)))
    }

    @Test
    fun noiseControlTakesTheOneByteValues() {
        val s = session("ff0c0c0102090102100102240101")
        // ANC on: FF 03 0C 01 63 (section 10.2's example).
        assertEquals(listOf("ff030c0163"), packets(s.set(Command.ANC_MODE, Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key)))
    }

    @Test
    fun multipointSendsLdacTheOtherWayFirst() {
        val s = session("ff0c0c0102090102100102240100")
        assertEquals(listOf("ff03230100", "ff03240101"), packets(s.set(Command.MULTIPOINT, 1)))
        assertEquals(listOf("ff03230101", "ff03240100"), packets(s.set(Command.MULTIPOINT, 0)))
    }

    @Test
    fun eqIsOneWriteOfEveryBand() {
        // Section 10.5's example: one band, 31 Hz, +3 dB, Q 1.8, master gain 0.
        assertEquals("0000001f002c01b400", hex(Capsule.eqPayload(0.0, listOf(EqBand(31, 1.8, 3.0)))))
        // The master gain is truncated to whole dB; negative gains are two's complement.
        assertEquals("0038ff1f00d4feb400", hex(Capsule.eqPayload(-2.5, listOf(EqBand(31, 1.8, -3.0)))))
    }

    @Test
    fun theEqWriteIsAllowed() {
        val s = session("ff0c0c0102090102100102240100")
        assertTrue(s.allowed(Capsule.eqMessage(-2.0, model.eq!!.bands)))
    }
}
