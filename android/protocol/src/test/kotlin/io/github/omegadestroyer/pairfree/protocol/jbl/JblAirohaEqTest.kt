package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Airoha's PEQ write on JBL's Airoha models (docs/jbl/PROTOCOL.md section 11.2), against earbuds scripted from Airoha's
 * SDK: the order of the steps, the NV keys, the relays and the group picked.
 */
class JblAirohaEqTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    private fun response(id: Int, vararg payload: Int) = RacePacket(id, bytes(*payload), JblRace.TYPE_RESPONSE)

    private fun nvAnswer(data: ByteArray) = RacePacket(JblRace.NV_READ, byteArrayOf(data.size.toByte(), (data.size shr 8).toByte()) + data, JblRace.TYPE_RESPONSE)

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    // The AB158x family's keys: audio path E401 naming the subset at E402, which lists a preset (coefficients E410) and
    // custom slot 0 (E410 + 28 = E42C).
    private val audioPath = bytes(1, 0, 0, 0, 0x02, 0xE4)
    private val subset = bytes(2, 0, 1, 0, 0, 0, 0x10, 0xE4, 1, 0, 0, 0, 0x2C, 0xE4)

    /** The scripted earbud's answer to [cmd], and for a relay the other earbud's, wrapped as the SDK reads it. */
    private fun answer(cmd: RacePacket): RacePacket = when (cmd.id) {
        JblRace.NV_READ -> when (u16(cmd.payload, 0)) {
            JblRace.PEQ_AUDIO_PATH_V3 -> nvAnswer(audioPath)
            JblRace.PEQ_GROUP_BASE_V3 -> nvAnswer(subset)
            else -> nvAnswer(ByteArray(0))
        }
        JblRace.RELAY_DST -> response(JblRace.RELAY_DST, JblRace.PEQ_PARTNER_DST, 1)
        JblRace.RELAY -> {
            val inner = cmd.payload.copyOfRange(2, cmd.payload.size)
            val id = u16(inner, 4)
            val reply = response(id, 0).bytes()
            RacePacket(JblRace.RELAY, cmd.payload.copyOfRange(0, 2) + reply, JblRace.TYPE_INDICATION)
        }
        JblRace.MMI_SET -> response(JblRace.MMI_SET, 0, 0, 0)
        JblRace.MMI_GET -> response(JblRace.MMI_GET, 0, 0, 0, 2)
        else -> response(cmd.id, 0)
    }

    private fun session(name: String, chipName: String): JblSession {
        val s = JblSession(JblModels.ALL.first { it.name == name })
        s.start()
        s.receive(nvAnswer(chipName.encodeToByteArray()))
        s.takeOutbox()
        return s
    }

    /** Runs a write to the end; returns every command sent, the sync included. */
    private fun run(s: JblSession, first: List<JblFrame>): List<RacePacket> {
        val sent = mutableListOf<RacePacket>()
        var pending = first.map { it as RacePacket }
        while (pending.isNotEmpty()) {
            for (cmd in pending) {
                assertTrue(s.allowed(cmd), cmd.toString())
                sent += cmd
                if (cmd.id != JblRace.PEQ_SYNC_UI) s.receive(answer(cmd))
            }
            pending = s.takeOutbox().map { it as RacePacket }
        }
        return sent
    }

    private fun nvKey(p: RacePacket) = u16(if (p.id == JblRace.RELAY) p.payload.copyOfRange(8, p.payload.size) else p.payload, 0)

    @Test
    fun customCurveOnEarbuds() {
        val s = session("JBL Live Buds 3", "AB1577S_Earbuds")
        val sent = run(s, s.eqWrite(null, listOf(3f, 2f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f))!!)
        assertEquals(
            listOf(
                JblRace.PEQ_SYNC_UI, JblRace.PEQ_REALTIME, JblRace.RELAY_DST,
                JblRace.RELAY, JblRace.NV_WRITE, JblRace.RELAY, JblRace.NV_WRITE, JblRace.RELAY, JblRace.NV_WRITE,
                JblRace.NV_READ, JblRace.NV_READ,
                JblRace.RELAY, JblRace.NV_WRITE, JblRace.RELAY, JblRace.NV_WRITE, JblRace.RELAY, JblRace.PEQ_SAVE_STATUS,
                JblRace.NV_READ, JblRace.NV_READ, JblRace.MMI_SET, JblRace.MMI_GET,
            ),
            sent.map { it.id },
        )
        // The UI data, its extra data and peak in slot 0, then the coefficients and the subset, each relayed first.
        val writes = sent.filter { it.id == JblRace.NV_WRITE || it.id == JblRace.RELAY }.map { nvKey(it) }
        assertEquals(listOf(0xEF00, 0xEF00, 0xEF10, 0xEF10, 0xEF20, 0xEF20, 0xE42C, 0xE42C, 0xE402, 0xE402), writes.take(10))
        // The subset already lists custom slot 0, so it goes back as it was; the group picked is that record, the second.
        val subsetWrite = sent.first { it.id == JblRace.NV_WRITE && nvKey(it) == 0xE402 }
        assertEquals(subset.toList(), subsetWrite.payload.copyOfRange(2, subsetWrite.payload.size).toList())
        assertEquals(listOf(0, 0, 2), sent.first { it.id == JblRace.MMI_SET }.payload.map { it.toInt() })
        // The coefficients: four rates on the AB158x (44.1, 48, 88.2 and 96 kHz), each with 103 shorts for ten bands.
        val coef = sent.first { it.id == JblRace.NV_WRITE && nvKey(it) == 0xE42C }.payload
        assertEquals(4, u16(coef, 2))
        assertEquals(2 + 4 + 4 * (4 + 2 * 103), coef.size)
        // The curve shows at once; Airoha's PEQ is not read back, so the choice stays on the phone.
        assertEquals(JblEqWire.CUSTOM, s.eq?.category)
        assertFalse(s.model.eq!!.reports)
    }

    @Test
    fun headphonesNeedNoRelay() {
        val s = session("JBL Tune 770NC", "AB1577S_Headset")
        val sent = run(s, s.eqWrite(null, List(10) { 1f })!!)
        assertTrue(sent.none { it.id == JblRace.RELAY || it.id == JblRace.RELAY_DST })
        assertEquals(JblRace.MMI_GET, sent.last().id)
    }

    @Test
    fun ab155xPicksPresetsByGroup() {
        val s = session("JBL CLUB PRO+ TWS", "AB155x")
        val preset = s.model.eq!!.presets.first { it.category in 1..99 }
        // The AB155x subset lists presets first: catID is the record's position, and JBL's category moves up one.
        val sent = mutableListOf<RacePacket>()
        var pending = s.eqWrite(preset, emptyList())!!.map { it as RacePacket }
        val subset155 = ByteArray(2 + 6 * 12).also { b ->
            b[0] = 12
            for (i in 0 until 12) {
                b[2 + 6 * i] = 1
                val key = JblRace.PEQ_COEF_BASE + i
                b[6 + 6 * i] = key.toByte()
                b[7 + 6 * i] = (key shr 8).toByte()
            }
        }
        while (pending.isNotEmpty()) {
            for (cmd in pending) {
                sent += cmd
                s.receive(
                    when {
                        cmd.id == JblRace.NV_READ && u16(cmd.payload, 0) == JblRace.PEQ_AUDIO_PATH -> nvAnswer(bytes(1, 0, 0, 0, JblRace.PEQ_GROUP_BASE and 0xFF, JblRace.PEQ_GROUP_BASE shr 8))
                        cmd.id == JblRace.NV_READ -> nvAnswer(subset155)
                        else -> answer(cmd)
                    },
                )
            }
            pending = s.takeOutbox().map { it as RacePacket }
        }
        assertEquals(listOf(JblRace.NV_READ, JblRace.NV_READ, JblRace.MMI_SET, JblRace.MMI_GET), sent.map { it.id })
        assertEquals(preset.category + 1, sent[2].payload[2].toInt())
    }

    @Test
    fun outsideAWriteTheBanStands() {
        val s = session("JBL Live Buds 3", "AB1577S_Earbuds")
        assertFalse(s.allowed(RacePacket(JblRace.NV_WRITE, bytes(0x00, 0xEF, 1))))
        assertFalse(s.allowed(RacePacket(JblRace.RELAY, bytes(5, 1))))
    }

    @Test
    fun protocol4CurveMatchesTheLibrary() {
        val cases = Json.parseToJsonElement(repoFile("data/jbl/test_vectors.json").readText()).jsonObject
            .getValue("airoha_peq").jsonObject.getValue("serialize").jsonArray.map { it.jsonObject }
        for (c in cases) {
            val gains = c.getValue("gains").jsonArray.map { it.jsonPrimitive.float }
            val q = c.getValue("q").jsonArray.map { it.jsonPrimitive.float }
            val bands = JblEq.BANDS_HZ.mapIndexed { i, hz -> JblEqBand(2, hz.toFloat(), gains[i], q[i]) }
            val master = c.getValue("master").jsonPrimitive.double.toFloat()
            val curve = JblEqCurve(JblEqWire.CUSTOM, -1f, 48, master, master, bands)
            val hex = JblEq.airohaCurve(curve, c.getValue("rates").jsonArray.map { it.jsonPrimitive.int })!!.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            assertEquals(c.getValue("hex").jsonPrimitive.content, hex, c.toString())
        }
    }

    @Test
    fun protocol4SendsTheCurveInParts() {
        val s = JblSession(JblModels.ALL.first { it.name == "JBL Tune 680NC" })
        val frames = s.eqWrite(null, listOf(3f, 2f, 0f, -1f, 0f, 0f, 1f, 2f, 3f, 4f))!!
        // EQ info and the curve at four rates do not fit one packet: parts of 497 bytes, each with the count and its index.
        assertTrue(frames.size >= 2)
        val payload = frames.flatMapIndexed { i, f ->
            val b = f.bytes()
            assertEquals(frames.size, b[4].toInt())
            assertEquals(i, b[5].toInt())
            assertTrue(s.allowed(f))
            b.copyOfRange(8, b.size).toList()
        }.toByteArray()
        // Feature 0E02 (EQ info) with every band as type 2, then 0E7F (the curve).
        assertEquals(JblP4.FEATURE_EQ_INFO, u16(payload, 0))
        val infoLength = u16(payload, 2)
        assertTrue((0 until 10).all { payload[4 + 19 + 13 * it].toInt() == 2 })
        assertEquals(JblP4.FEATURE_EQ_CURVE, u16(payload, 4 + infoLength))
    }
}
