package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The touch controls of JBL's Airoha models (docs/jbl/PROTOCOL.md section 10.1) against JBL's own code run for the same
 * gesture lists (data/jbl/test_vectors.json airoha_gesture), and the order of the write's steps.
 */
class JblAirohaGestureTest {
    private val cases = Json.parseToJsonElement(repoFile("data/jbl/test_vectors.json").readText()).jsonObject
        .getValue("airoha_gesture").jsonObject.getValue("cases").jsonArray.map { it.jsonObject }

    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun keyMapsAreJblsOwn() {
        assertTrue(cases.size >= 90)
        for (c in cases) {
            val variant = c.getValue("variant").jsonPrimitive.content
            val right = c.getValue("agent_right").jsonPrimitive.boolean
            val relay = c.getValue("relay").jsonPrimitive.boolean
            val list = c.getValue("gestures").jsonArray.map { g -> g.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int } }
            val keyId = AIROHA_GESTURE_VARIANTS.getValue(variant).keyId
            assertEquals(c.getValue("own").jsonPrimitive.content, hex(JblAirohaGesture.encode(variant, keyId, list, right, relay, peer = false)), c.toString())
            c["peer"]?.let { assertEquals(it.jsonPrimitive.content, hex(JblAirohaGesture.encode(variant, keyId, list, right, relay, peer = true)), c.toString()) }
            val read = c.getValue("read").jsonArray.associate { g -> g.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int } }
            assertEquals(read, JblAirohaGesture.decode(variant, unhex(c.getValue("own").jsonPrimitive.content), right, relay), c.toString())
        }
    }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    private fun response(id: Int, vararg payload: Int) = RacePacket(id, bytes(*payload), JblRace.TYPE_RESPONSE)

    /** The other earbud's answer to a relayed command, as the earbuds wrap it in 0D01. */
    private fun relayed(inner: RacePacket) = RacePacket(JblRace.RELAY, bytes(5, 1) + inner.bytes(), JblRace.TYPE_INDICATION)

    @Test
    fun aWriteGoesToBothEarbudsAsJblsAppSendsIt() {
        val known = mapOf(1 to 10, 3 to 5, 7 to 6, 49 to 161, 5 to 0, 2 to 1, 4 to 2, 8 to 0, 50 to 161, 6 to 0)
        val write = JblAirohaGesture.write(JblAirohaGesture.V3, earbuds = true, agentRight = false, known = known, keyId = 0x7E,
            list = listOf(1 to 1, 3 to 2, 7 to 0, 49 to 161, 5 to 0))
        val sent = mutableListOf<RacePacket>()
        sent += write.start()!!
        assertEquals(JblRace.RELAY_DST, sent.last().id)
        write.receive(response(JblRace.RELAY_DST, 5, 1)) { sent += it }
        // The earbud's own key map, its copy of the other's, the reload; then the same relayed to the other earbud.
        val ids = mutableListOf<Int>()
        while (!write.done && !write.failed) {
            val last = sent.last()
            if (last.id == JblRace.RELAY) {
                val innerId = (last.payload[6].toInt() and 0xFF) or ((last.payload[7].toInt() and 0xFF) shl 8)
                ids += -innerId
                write.receive(relayed(RacePacket(innerId, bytes(0), JblRace.TYPE_RESPONSE))) { sent += it }
            } else {
                ids += last.id
                write.receive(response(last.id, 0)) { sent += it }
            }
        }
        assertEquals(listOf(JblRace.NV_WRITE, JblRace.NV_WRITE, JblRace.NV_RELOAD, -JblRace.NV_WRITE, -JblRace.NV_WRITE, -JblRace.NV_RELOAD), ids)
        assertTrue(write.done)
        // The left earbud (the agent) now holds the volume set; the right keeps what it had.
        assertEquals(1, write.gestures[1])
        assertEquals(1, write.gestures[2])
        // The own key is F2D6 on a v3 chip.
        assertEquals(0xD6, sent[1].payload[0].toInt() and 0xFF)
        assertEquals(0xF2, sent[1].payload[1].toInt() and 0xFF)
    }

    /** The NV key read's answer: the data's length, then the data. */
    private fun nvAnswer(data: ByteArray) = RacePacket(JblRace.NV_READ, byteArrayOf(data.size.toByte(), (data.size shr 8).toByte()) + data, JblRace.TYPE_RESPONSE)

    /** Answers whatever the session sent last, as a Live Buds 3 (AB1585, left earbud the agent) would, until it sends nothing. */
    private fun answerAll(s: JblSession, own: ByteArray, partner: ByteArray): List<RacePacket> {
        val sent = mutableListOf<RacePacket>()
        var out = s.takeOutbox().filterIsInstance<RacePacket>()
        while (out.isNotEmpty()) {
            // The connection asks before it sends each frame.
            out.forEach { assertTrue(s.allowed(it), it.toString()) }
            sent += out
            val last = out.last()
            val answer = when (last.id) {
                JblRace.AGENT_CHANNEL -> response(JblRace.AGENT_CHANNEL, 0, 0)
                JblRace.RELAY_DST -> response(JblRace.RELAY_DST, 5, 1)
                JblRace.NV_READ -> nvAnswer(own)
                JblRace.NV_WRITE, JblRace.NV_RELOAD -> response(last.id, 0)
                JblRace.RELAY -> {
                    val inner = (last.payload[6].toInt() and 0xFF) or ((last.payload[7].toInt() and 0xFF) shl 8)
                    relayed(if (inner == JblRace.NV_READ) nvAnswer(partner) else RacePacket(inner, bytes(0), JblRace.TYPE_RESPONSE))
                }
                else -> null
            } ?: break
            s.receive(answer)
            out = s.takeOutbox().filterIsInstance<RacePacket>()
        }
        return sent
    }

    @Test
    fun theLiveBuds3ShowAndWriteTheirTouchSets() {
        val s = JblSession(JblModels.ALL.first { it.name == "JBL Live Buds 3" })
        s.start()
        s.takeOutbox()
        val chip = "AB1585".encodeToByteArray()
        s.receive(RacePacket(JblRace.NV_READ, byteArrayOf(chip.size.toByte(), 0) + chip, JblRace.TYPE_RESPONSE))
        s.takeOutbox().dropLast(1)
        // JBL's default sets: the left earbud (the agent) noise control, the right one playback.
        val left = JblAirohaGesture.encode(JblAirohaGesture.V3, 0x7E, listOf(1 to 11, 3 to 4, 7 to 0, 49 to 161, 5 to 0), agentRight = false, relay = false, peer = false)
        val right = JblAirohaGesture.encode(JblAirohaGesture.V3, 0x7E, listOf(2 to 10, 4 to 5, 8 to 6, 50 to 161, 6 to 0), agentRight = false, relay = true, peer = false)
        // The read was queued last; feed it from its first command, the agent side.
        s.receive(response(JblRace.AGENT_CHANNEL, 0, 0))
        answerAll(s, left, right)
        val rows = s.groups.flatMap { it.settings }
        assertEquals("ambient", (rows.first { it.key == JblExtras.TOUCH_LEFT } as io.github.omegadestroyer.pairfree.protocol.Setting.Choice).value)
        assertEquals("playback", (rows.first { it.key == JblExtras.TOUCH_RIGHT } as io.github.omegadestroyer.pairfree.protocol.Setting.Choice).value)
        // Volume on the left: both earbuds' key maps written, the reload, all relayed, each one let through.
        val frames = s.change(io.github.omegadestroyer.pairfree.protocol.SettingChange.Choice(JblExtras.TOUCH_LEFT, "volume"))!!
        val first = frames.single() as RacePacket
        assertTrue(s.allowed(first))
        val sent = listOf(first) + answerAll(s.also { it.receive(response(JblRace.RELAY_DST, 5, 1)) }, left, right)
        assertEquals(listOf(JblRace.NV_WRITE, JblRace.NV_WRITE, JblRace.NV_RELOAD), sent.drop(1).take(3).map { it.id })
        assertEquals(3, sent.count { it.id == JblRace.RELAY })
        assertEquals("volume", (s.groups.flatMap { it.settings }.first { it.key == JblExtras.TOUCH_LEFT } as io.github.omegadestroyer.pairfree.protocol.Setting.Choice).value)
        // A key map write of anyone else's is still refused.
        assertTrue(!s.allowed(RacePacket(JblRace.NV_WRITE, bytes(0xD6, 0xF2, 0, 0))))
    }

    @Test
    fun theChipPicksTheVariant() {
        assertEquals(JblAirohaGesture.V3, JblAirohaGesture.variantOf(RaceChip.AB158X, null))
        assertEquals(JblAirohaGesture.V3, JblAirohaGesture.variantOf(RaceChip.AB1568, 2))
        assertEquals(JblAirohaGesture.AB1568, JblAirohaGesture.variantOf(RaceChip.AB1568, 1))
        assertEquals(JblAirohaGesture.PLAIN, JblAirohaGesture.variantOf(RaceChip.AB155X, null))
        assertEquals(JblAirohaGesture.AB1562, JblAirohaGesture.variantOf(RaceChip.AB1562, null))
    }
}
