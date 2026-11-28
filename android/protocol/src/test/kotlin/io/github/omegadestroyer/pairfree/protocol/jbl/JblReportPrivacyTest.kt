package io.github.omegadestroyer.pairfree.protocol.jbl

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What the device report keeps of the Airoha models' messages. A Tour Pro 3 sent `0055`, a list of other devices' names
 * and addresses, which 0.18.0 put in its report whole (issue #1). The bytes here are made up.
 */
class JblReportPrivacyTest {
    private fun session(): JblSession = JblSession(JblModels.ALL.first { it.name == "JBL Tour Pro 3" }).also { it.start() }

    private fun JblSession.receiveRace(id: Int, payload: ByteArray) = receive(RacePacket(id, payload, JblRace.TYPE_RESPONSE))

    @Test
    fun aMessageNobodyAskedForShowsFourBytes() {
        val s = session()
        val names = byteArrayOf(0, 0, 0, 0) + "Somebody Phone".encodeToByteArray()
        s.receiveRace(0x0055, names)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), s.replies.getValue("race.0055"))
    }

    @Test
    fun aLongNvKeyIsNotKept() {
        val s = session()
        s.receiveRace(JblRace.NV_READ, byteArrayOf(0, 1) + ByteArray(64) { 7 })
        assertEquals(null, s.replies["race.0a00"])
    }

    @Test
    fun theCasesAddressIsBlanked() {
        val s = session()
        val payload = ByteArray(28) { 0x11 }.also { it[0] = 0 }
        s.receiveRace(JblRace.CASE_FEATURE_GET, payload)
        val kept = s.replies.values.single()
        assertEquals(28, kept.size)
        assertArrayEquals(ByteArray(6), kept.copyOfRange(13, 19))
        assertEquals(0x11, kept[12].toInt())
        assertEquals(0x11, kept[19].toInt())
    }
}
