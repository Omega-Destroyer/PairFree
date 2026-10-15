package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import io.github.omegadestroyer.pairfree.protocol.repoFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun load(name: String): JsonObject = Json.parseToJsonElement(repoFile("data/jbl/$name").readText()).jsonObject

private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content

private fun JblPacket.hex() = bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Frames, the tables and a session, against frames JblTray and FajkPes recorded from JBL earbuds and headphones. */
class JblTest {
    private val vectors = load("test_vectors.json")
    private val requests = vectors.getValue("requests").jsonArray.associate { it.jsonObject.str("name") to it.jsonObject.str("hex") }
    private val replies = vectors.getValue("replies").jsonArray.associate { it.jsonObject.str("name") to it.jsonObject }

    private fun packet(hex: String): JblPacket = JblParser().feed(hex.hexBytes()).single()

    private fun model(name: String, pid: Int? = null) = JblModels.ALL.first { it.name == name && (pid == null || pid in it.pids) }

    private fun setting(s: JblSession, key: String) = s.groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun requestsAreBuiltByteForByte() {
        val built = mapOf(
            "device_info" to JblPacket.of(JblCommand.DEVICE_INFO),
            "battery" to JblPacket.of(JblCommand.BATTERY, 0),
            "noise_get" to JblPacket.of(JblCommand.NOISE_MODES, JblNoise.GET),
            "noise_off" to JblPacket.of(JblCommand.NOISE_MODES, JblNoise.OFF),
            "feature_get_spatial" to JblPacket.featureGet(listOf(SPATIAL)),
            "status_all" to JblPacket.of(JblCommand.STATUS, JblStatus.ALL),
            "set_anc_on" to JblPacket.of(JblCommand.SET_ANC, 1),
            "set_ambient_talk_thru" to JblPacket.of(JblCommand.SET_AMBIENT, JblAmbient.TALK_THRU),
            "set_auto_play_pause_on" to JblPacket.of(JblCommand.SET_AUTO_PLAY_PAUSE, 1),
            "feature_set_auto_off_on_60" to JblPacket.featureSet(JblFeature.AUTO_OFF, 1, 60, 0),
            "feature_set_auto_play_pause_off" to JblPacket.featureSet(JblFeature.AUTO_PLAY_PAUSE, 0),
        )
        for ((name, p) in built) assertEquals(requests.getValue(name), p.hex(), name)
    }

    @Test
    fun noiseModesGoOutAsJblTraySawThem() {
        val s = JblSession(model("JBL Tune Flex 2"))
        s.receive(packet("aa910712010002000300"))
        assertEquals(requests.getValue("noise_anc"), s.set(Command.ANC_MODE, anc("anc"))!!.single().hex())
        assertEquals(requests.getValue("noise_ambient_aware"), s.set(Command.ANC_MODE, anc("transparency"))!!.single().hex())
        assertEquals(requests.getValue("noise_talk_thru"), s.change(SettingChange.Toggle("talk_thru", true))!!.single().hex())
        assertEquals(requests.getValue("noise_off"), s.change(SettingChange.Toggle("talk_thru", false))!!.single().hex())
        assertEquals(listOf(JblPacket.of(JblCommand.NOISE_MODES, JblNoise.GET)), s.takeReadBacks())
    }

    @Test
    fun theNoiseReplyNamesTheModeThatIsOn() {
        val s = JblSession(model("JBL Tune Flex 2"))
        assertFalse(s.state.supports(Command.ANC_MODE))
        assertTrue(s.receive(packet("aa910712010002010300")))
        assertEquals(anc("transparency"), s.state.number(Command.ANC_MODE))
        s.receive(packet("aa910712010002000301"))
        assertNull(s.state.number(Command.ANC_MODE))
        assertEquals(Setting.Toggle("talk_thru", true), setting(s, "talk_thru"))
        s.receive(packet("aa910712010002000300"))
        assertEquals(anc("off"), s.state.number(Command.ANC_MODE))
    }

    @Test
    fun theTune720btStatusAndBatteryReadAsFajkPesRecordedThem() {
        val s = JblSession(model("JBL Tune 720BT"))
        val status = replies.getValue("status_all")
        s.receive(packet(status.str("hex")))
        val autoOff = status.getValue("auto_off").jsonObject
        assertTrue(autoOff.getValue("on").jsonPrimitive.boolean)
        assertEquals(Setting.Choice("auto_off", autoOff.getValue("minutes").jsonPrimitive.int.toString(), listOf("never", "30", "60")), setting(s, "auto_off"))
        // The 720BT has no noise control and no auto play/pause in JBL's app.
        assertNull(setting(s, "pause_taken_off"))
        assertFalse(s.state.supports(Command.ANC_MODE))

        val battery = replies.getValue("battery")
        s.receive(packet(battery.str("hex")))
        assertEquals(battery.getValue("left").jsonPrimitive.int, s.state.batteryLeft)
        assertNull(s.state.batteryRight)
    }

    @Test
    fun earbudsShowBothSidesAndTheCase() {
        val s = JblSession(model("JBL TUNE FLEX"))
        s.receive(packet("aa25070100015046015a"))
        assertEquals(0x50, s.state.batteryLeft)
        assertEquals(0x46, s.state.batteryRight)
        assertEquals(0x5a, s.state.batteryCase)
    }

    @Test
    fun autoOffIsWrittenTheWayEachModelWritesIt() {
        val v2 = JblSession(model("JBL Live 770NC"))
        v2.receive(packet(replies.getValue("status_all").str("hex")))
        assertEquals("aa3303013c00", v2.change(SettingChange.Choice("auto_off", "60"))!!.single().hex())
        assertEquals("aa3303003c00", v2.change(SettingChange.Choice("auto_off", "never"))!!.single().hex())
        assertEquals(listOf(JblPacket.of(JblCommand.STATUS, JblStatus.ALL)), v2.takeReadBacks())

        val club = JblSession(model("JBL CLUB ONE"))
        club.receive(packet("aa22043000000a"))
        assertEquals(Setting.Toggle("auto_off", false), setting(club, "auto_off"))
        assertEquals(requests.getValue("set_auto_off_v1_on_10"), club.change(SettingChange.Toggle("auto_off", true))!!.single().hex())

        val feature = JblSession(model("JBL Junior 470NC"))
        feature.receive(packet("aa06070002000b03011e00"))
        assertEquals(Setting.Choice("auto_off", "30", listOf("never", "30", "60")), setting(feature, "auto_off"))
        assertEquals(requests.getValue("feature_set_auto_off_on_60"), feature.change(SettingChange.Choice("auto_off", "60"))!!.single().hex())
    }

    @Test
    fun featureRepliesGiveTheFirmwareAndModel() {
        val s = JblSession(model("JBL Tune Flex 2"))
        // Product id 210E (low byte first), firmware 0.3.5, left 80 %, right unknown.
        s.receive(packet("aa061600" + "02" + "0002020e21" + "000503000305" + "001a020050" + "001b0200ff"))
        assertEquals(0x210E, s.productId)
        assertEquals("JBL Tune Flex 2", s.state.firmware?.code)
        assertEquals("0.3.5", s.state.firmware?.version)
        assertEquals(80, s.state.batteryLeft)
        assertNull(s.state.batteryRight)
    }

    @Test
    fun theOldNoiseCommandsAreUsedOnTheLive460nc() {
        val s = JblSession(model("JBL LIVE460NC"))
        assertEquals(
            listOf(JblPacket.of(JblCommand.STATUS, JblStatus.ANC), JblPacket.of(JblCommand.STATUS, JblStatus.AMBIENT)),
            s.start().filter { it.command == JblCommand.STATUS && it.sub != JblStatus.ALL },
        )
        s.receive(packet("aa22023100"))
        s.receive(packet("aa22023202"))
        assertEquals(anc("transparency"), s.state.number(Command.ANC_MODE))
        assertEquals(listOf("aa310101"), s.set(Command.ANC_MODE, anc("anc"))!!.map { it.hex() })
        assertEquals(listOf("aa310100", "aa320100"), s.set(Command.ANC_MODE, anc("off"))!!.map { it.hex() })
        assertEquals(listOf(requests.getValue("set_ambient_talk_thru")), s.change(SettingChange.Toggle("talk_thru", true))!!.map { it.hex() })
    }

    @Test
    fun modelsWithoutNoiseCancellingGetSwitchesForTheirModes() {
        val s = JblSession(model("JBL Wave Flex 2"))
        s.receive(packet("aa910712010002010300"))
        assertFalse(s.state.supports(Command.ANC_MODE))
        assertEquals(listOf(Setting.Toggle("ambient_aware", true), Setting.Toggle("talk_thru", false)), s.groups.single { it.key == "noise" }.settings)
        assertNull(s.change(SettingChange.Toggle("anc", true)))
    }

    @Test
    fun theProductIdPicksBetweenModelsThatShareAName() {
        val s = JblSession(model("JBL Tune 520BT", 0x20B3))
        // Device info: 16 bytes of name, product id 20F7 (low byte first), then the rest.
        s.receive(packet("aa1220" + "00".repeat(16) + "f720" + "00".repeat(14)))
        assertEquals(0x20F7, s.productId)
        assertTrue(0x20F7 in s.model.pids)
        val report = s.replies.getValue("12")
        assertTrue(report.copyOfRange(0, 16).all { it == 0.toByte() })
    }

    @Test
    fun theDeviceReportBlanksTheNameAndAddress() {
        val s = JblSession(model("JBL TUNE FLEX"))
        val info = "4a424c2054554e4520464c4558000000" + "7f20" + "01" + "50" + "112233445566" + "000305"
        s.receive(packet("aa121d$info"))
        val kept = s.replies.getValue("12")
        assertTrue(kept.copyOfRange(0, 16).all { it == 0.toByte() })
        assertTrue(kept.copyOfRange(20, 26).all { it == 0.toByte() })
        assertEquals("0.3.5", s.firmware)
    }

    @Test
    fun theParserSplitsByLengthAndSkipsNoise() {
        val p = JblParser()
        assertTrue(p.feed("00aa2501".hexBytes()).isEmpty())
        val out = p.feed("00aa0603000100".hexBytes() + "0c".hexBytes())
        assertEquals(listOf(JblPacket.of(JblCommand.BATTERY, 0), JblPacket.featureGet(listOf(SPATIAL))), out)
        assertEquals(0, p.pending)
        // A new notification drops a frame left incomplete.
        assertTrue(p.feed("aa2507".hexBytes(), newMessage = true).isEmpty())
        assertEquals(listOf(JblPacket.of(JblCommand.BATTERY, 0)), p.feed("aa250100".hexBytes(), newMessage = true))
    }

    @Test
    fun theTablesMatchTheData() {
        val devices = load("devices.json").getValue("devices").jsonArray.map { it.jsonObject }
        assertEquals(devices.size, JblModels.ALL.size)
        for ((d, m) in devices.zip(JblModels.ALL)) {
            assertEquals(d.str("name"), m.name)
            assertEquals(d.getValue("pids").jsonArray.map { it.jsonPrimitive.content.toInt(16) }, m.pids)
        }
        val never = load("commands.json").getValue("never_send").jsonObject.keys.map { it.toInt(16) }.toSet()
        assertEquals(never, JblCommand.NEVER_SEND)
        assertTrue(0x95 in JblCommand.NEVER_SEND, "factory reset")
        val s = JblSession(model("JBL TUNE FLEX"))
        assertFalse(s.allowed(JblPacket.of(0x95)))
        assertTrue(s.start().all(s::allowed))
    }

    @Test
    fun namesFindTheModel() {
        assertEquals("JBL LIVE460NC", JblModels.fromName("JBL Live 460NC")?.name)
        assertEquals("JBL Tune Flex 2", JblModels.fromName("JBL TUNE FLEX 2")?.name)
        assertEquals("JBL TUNE FLEX", JblModels.fromName("JBL Tune Flex")?.name)
        assertNull(JblModels.fromName("JBL Tune 770NC"))
        assertEquals(Form.HEADPHONES, JblModels.model("JBL Live 770NC")?.form)
        assertNotNull(JblModels.of(0x210E))
    }

    @Test
    fun advertsAreMatchedByProductIdAndCrc() {
        // CRC-16/ARC's check value.
        assertEquals(0xBB3D, JblAdvert.crc16("123456789".encodeToByteArray()))
        val m = model("JBL Tune Flex 2")
        val mac = "12:34:56:78:9A:BC"
        val crc = JblAdvert.crc16(JblAdvert.macBytes(mac)!!)
        val phone = JblAdvert.crc16("Pixel 9".encodeToByteArray())
        fun advert(pid: Int, source: Int, address: Int) = byteArrayOf(
            pid.toByte(), (pid shr 8).toByte(), 1, source.toByte(), (source shr 8).toByte(), 0, 0, address.toByte(), (address shr 8).toByte(), 0,
        )
        assertEquals(2, JblAdvert.score(advert(0x210E, 0, crc), m, mac, "Pixel 9"))
        assertEquals(1, JblAdvert.score(advert(0x210E, phone, 0), m, mac, "Pixel 9"))
        assertEquals(0, JblAdvert.score(advert(0x210E, 0, 0), m, mac, "Pixel 9"))
        assertEquals(0, JblAdvert.score(advert(0x210C, 0, crc), m, mac, "Pixel 9"))
    }

    private fun anc(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    private companion object {
        /** Spatial sound's feature id, as JblTray captured it. */
        const val SPATIAL = 0x0C
    }
}
