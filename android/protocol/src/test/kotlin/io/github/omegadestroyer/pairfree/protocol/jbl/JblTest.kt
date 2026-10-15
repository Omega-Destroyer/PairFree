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

private fun JblFrame.hex() = bytes().joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

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
            s.start().filterIsInstance<JblPacket>().filter { it.command == JblCommand.STATUS && it.sub != JblStatus.ALL },
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

    /** A protocol 4 answer as JBL's app reads it: identifier, command, one packet, length, status 0, then [records]. */
    private fun p4Reply(command: Int, records: String): ByteArray {
        val payload = "0000$records".hexBytes()
        return JblP4Packet(command, payload).bytes()
    }

    @Test
    fun protocol4FramesAreBuiltAsJblsAppBuildsThem() {
        assertEquals("00dd010001000600180019001a00", JblP4Packet.get(listOf(JblP4.FEATURE_ANC, JblP4.FEATURE_AMBIENT_AWARE, JblP4.FEATURE_TALK_THRU)).hex())
        // General4Control's noise cancelling on: the ANC feature, status 1, level 127 (left as it is).
        assertEquals("00dd0200010005001800" + "0100ff", JblP4Packet.set(JblP4.FEATURE_ANC to JblP4Packet.statusLevel(true, JblP4.IGNORE_LEVEL)).hex())
        val s = JblSession(model("JBL Tune Flex 3"))
        // Host type first, then the reads.
        assertEquals("00dd020001000500" + "2f00" + "0100" + "01", s.start().first().hex())
        assertTrue(s.start().all(s::allowed))
        assertFalse(s.allowed(JblP4Packet(0x00FF)), "factory reset")
    }

    @Test
    fun aProtocol4AnswerFillsTheScreens() {
        val s = JblSession(model("JBL Tune Flex 3"))
        val records = "04000200b621" + "0c0003000102 03".replace(" ", "") + "0d00010050" + "0e000100c6" + "031f010064" +
            "1800010085" + "1900010003" + "1a00010001" + "49000100ff"
        assertTrue(s.receive(JblReader().feed(p4Reply(JblP4.GET, records)).single()))
        assertEquals(0x21B6, s.productId)
        assertEquals("1.2.3", s.firmware)
        assertEquals(80, s.state.batteryLeft)
        assertEquals(70, s.state.batteryRight, "the charging bit is not the level")
        assertEquals(100, s.state.batteryCase)
        assertEquals(anc("anc"), s.state.number(Command.ANC_MODE))
        // TalkThru: ambient sound control on, and TalkThru with the level it reported (1).
        assertEquals(
            JblP4Packet.set(JblP4.FEATURE_AMBIENT_SOUND_CONTROL to byteArrayOf(0xFF.toByte()), JblP4.FEATURE_TALK_THRU to byteArrayOf(0x81.toByte())).hex(),
            s.change(SettingChange.Toggle("talk_thru", true))!!.single().hex(),
        )
        // Off: ambient sound control off, keeping the current mode.
        assertEquals(
            JblP4Packet.set(JblP4.FEATURE_AMBIENT_SOUND_CONTROL to byteArrayOf(0x7F), JblP4.FEATURE_TALK_THRU to byteArrayOf(0x81.toByte())).hex(),
            s.set(Command.ANC_MODE, anc("off"))!!.single().hex(),
        )
        assertEquals(listOf(JblP4Packet.get(listOf(JblP4.FEATURE_ANC, JblP4.FEATURE_AMBIENT_AWARE, JblP4.FEATURE_TALK_THRU, JblP4.FEATURE_AMBIENT_SOUND_CONTROL))), s.takeReadBacks())
        // A notification that ambient sound control went off shows off.
        s.receive(JblReader().feed(p4Reply(JblP4.NOTIFICATION, "490001007f")).single())
        assertEquals(anc("off"), s.state.number(Command.ANC_MODE))
    }

    @Test
    fun protocol4RepliesInSeveralPacketsAreJoined() {
        val payload = "0000" + "0d00010050" + "0e00010046"
        val whole = payload.hexBytes()
        fun part(index: Int, bytes: ByteArray) =
            byteArrayOf(0x00, 0xDD.toByte(), 0x01, 0x00, 2, index.toByte(), bytes.size.toByte(), 0) + bytes
        val reader = JblReader()
        assertTrue(reader.feed(part(0, whole.copyOfRange(0, 7))).isEmpty())
        val frame = reader.feed(part(1, whole.copyOfRange(7, whole.size))).single() as JblP4Packet
        assertEquals(mapOf(JblP4.FEATURE_BATTERY_LEFT to 0x50, JblP4.FEATURE_BATTERY_RIGHT to 0x46), frame.features().mapValues { it.value[0].toInt() })
        // A failed status gives no records; an AA frame still reads as one.
        assertTrue((JblReader().feed(JblP4Packet(JblP4.GET, "0100".hexBytes()).bytes()).single() as JblP4Packet).features().isEmpty())
        assertEquals(JblPacket.of(JblCommand.BATTERY, 0), JblReader().feed("aa250100".hexBytes()).single())
    }

    @Test
    fun protocol4ModelsWithoutNoiseControlReadOnlyTheBasics() {
        val s = JblSession(model("JBL Sense Lite"))
        assertEquals(JblModel.Family.P4, s.model.family)
        assertNull(s.model.noise)
        val read = s.start()[1] as JblP4Packet
        assertFalse(read.payload.toList().windowed(2, 2).any { (it[0].toInt() and 0xFF) == JblP4.FEATURE_ANC }, "no noise reads")
        assertNull(s.set(Command.ANC_MODE, anc("anc")))
    }

    @Test
    fun protocol4AdvertsAreReadByTheirFlags() {
        val m = model("JBL Tune Flex 3")
        val mac = "12:34:56:78:9A:BC"
        val crc = JblAdvert.crc16(JblAdvert.macBytes(mac)!!)
        // Product id 21B6, flags: colour and address CRC (bits 15 and 12), then the colour and the CRC.
        val advert = byteArrayOf(0xB6.toByte(), 0x21, 0x00, 0x90.toByte(), 3, crc.toByte(), (crc shr 8).toByte())
        assertEquals(2, JblAdvert.score(advert, m, mac, null))
        // With the name CRCs flagged too (bits 14 and 13), the address CRC comes after them.
        val phone = JblAdvert.crc16("Pixel 9".encodeToByteArray())
        val withNames = byteArrayOf(0xB6.toByte(), 0x21, 0x00, 0x70, phone.toByte(), (phone shr 8).toByte(), 0, 0, 0, 0)
        assertEquals(1, JblAdvert.score(withNames, m, mac, "Pixel 9"))
    }

    private fun anc(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    private companion object {
        /** Spatial sound's feature id, as JblTray captured it. */
        const val SPATIAL = 0x0C
    }
}
