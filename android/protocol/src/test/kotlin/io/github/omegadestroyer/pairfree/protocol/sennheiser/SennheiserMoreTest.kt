package io.github.omegadestroyer.pairfree.protocol.sennheiser

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun String.bytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * Find device, personalised sound, podcast mode, the fit test, the paired devices and the Momentum Sport's sensors
 * (docs/sennheiser/PROTOCOL.md sections 4.4 to 4.8): the frames are the product files' command words, the earbuds' answers are built as Smart Control reads them.
 * Not tried on earbuds.
 */
class SennheiserMoreTest {
    private fun reply(word: Int, payload: String) = Gaia3Packet(0x0495, word + 0x100, payload.bytes()).bytes()
    private fun event(word: Int, payload: String) = Gaia3Packet(0x0495, word + 0x80, payload.bytes()).bytes()
    private fun words(frames: List<ByteArray>) = frames.map { f -> Gaia3Parser().feed(f).single().let { "%04x:%s".format(it.word, it.payload.joinToString("") { b -> "%02x".format(b) }) } }
    private fun model(name: String) = SennheiserModels.ALL.first { it.name == name }
    private fun SennheiserSession.row(key: String): Setting? = groups.flatMap { it.settings }.firstOrNull { it.key == key }
    private fun SennheiserSession.firmware(v: String) {
        val c = SennheiserCommands.ALL.first { it.id == SennheiserCommands.FIRMWARE_VERSION }
        feed(reply(c.get!!, v.split('.').joinToString("") { "%04x".format(it.toInt()) }))
    }

    @Test
    fun findDeviceRingsOneEarbudAndStopsBoth() {
        val s = SennheiserSession(model("Accentum True Wireless"))
        val start = words(s.start())
        assertTrue("0007:16" in start, "feature 22 is registered: $start")
        assertTrue("2c03:00" in start && "2c03:01" in start, "each side is asked whether it rings")
        assertNull(s.row(SennheiserSession.KEY_FIND_LEFT))
        s.feed(reply(0x2c03, ""))
        assertEquals(Setting.Action(SennheiserSession.KEY_FIND_LEFT, false), s.row(SennheiserSession.KEY_FIND_LEFT))
        // The event names whether it rings, then the side (right 0, left 1).
        s.feed(event(0x2c03, "0101"))
        assertEquals(Setting.Action(SennheiserSession.KEY_FIND_LEFT, true), s.row(SennheiserSession.KEY_FIND_LEFT))
        assertEquals(Setting.Action(SennheiserSession.KEY_FIND_RIGHT, false), s.row(SennheiserSession.KEY_FIND_RIGHT))
        assertEquals(listOf("2c01:"), words(s.change(SettingChange.Press(SennheiserSession.KEY_FIND_LEFT))!!))
        assertEquals(listOf("2c02:00"), words(s.change(SettingChange.Press(SennheiserSession.KEY_FIND_RIGHT))!!))
    }

    @Test
    fun theFitTestAsksThePreconditionThenChecksAndShowsTheAppsThreeQualities() {
        val s = SennheiserSession(model("Momentum Sport"))
        assertTrue("0007:0f" in words(s.start()), "feature 15 is registered")
        assertEquals(listOf("1e01:"), words(s.change(SettingChange.Press(SennheiserSession.KEY_FIT_TEST))!!))
        assertEquals(Setting.Action(SennheiserSession.KEY_FIT_TEST, true), s.row(SennheiserSession.KEY_FIT_TEST))
        s.feed(reply(0x1e01, ""))
        s.feed(event(0x1e01, "0101"))
        assertEquals(listOf("1e00:"), words(s.takeOutbox()))
        s.feed(event(0x1e00, "4637"))
        assertEquals(Setting.Information(SennheiserSession.KEY_FIT_LEFT, "good"), s.row(SennheiserSession.KEY_FIT_LEFT))
        assertEquals(Setting.Information(SennheiserSession.KEY_FIT_RIGHT, "acceptable"), s.row(SennheiserSession.KEY_FIT_RIGHT))
        assertEquals(Setting.Action(SennheiserSession.KEY_FIT_TEST, false), s.row(SennheiserSession.KEY_FIT_TEST))
        // A precondition that fails (an earbud not in the ear) stops it, and the screen says so.
        s.change(SettingChange.Press(SennheiserSession.KEY_FIT_TEST))
        s.feed(event(0x1e01, "0001"))
        assertTrue(s.takeOutbox().isEmpty())
        assertEquals(Setting.Information(SennheiserSession.KEY_FIT_LEFT, "not_ready"), s.row(SennheiserSession.KEY_FIT_LEFT))
        // 29 is bad, 30 acceptable, 60 good, 254 an invalid result.
        for ((v, word) in listOf("1d" to "bad", "1e" to "acceptable", "3c" to "good", "fe" to "invalid", "ff" to "none")) {
            s.change(SettingChange.Press(SennheiserSession.KEY_FIT_TEST))
            s.feed(event(0x1e01, "0101"))
            s.takeOutbox()
            s.feed(event(0x1e00, v + "ff"))
            val left = s.row(SennheiserSession.KEY_FIT_LEFT) as Setting.Information
            assertEquals(word, left.value, "result $v")
        }
    }

    @Test
    fun personalisedSoundFollowsTheFirmwaresGeneration() {
        // The Momentum 4 has the first generation from firmware 2.12.0: on is the mode `enabled` (3).
        val m4 = SennheiserSession(model("Momentum 4 Wireless"))
        assertTrue("2001:" in words(m4.start()))
        m4.feed(reply(0x2001, "03"))
        assertNull(m4.row(SennheiserSession.KEY_PERSONALIZED), "the firmware is not known yet")
        m4.firmware("2.13.0")
        assertEquals(Setting.Toggle(SennheiserSession.KEY_PERSONALIZED, true), m4.row(SennheiserSession.KEY_PERSONALIZED))
        assertEquals(listOf("0803:0001"), words(m4.change(SettingChange.Toggle(SennheiserSession.KEY_PERSONALIZED, false))!!))
        m4.takeReadBacks()
        assertEquals(listOf("2000:03"), words(m4.change(SettingChange.Toggle(SennheiserSession.KEY_PERSONALIZED, true))!!))
        // Not set up (not_parameterized, 0): nothing to switch.
        val none = SennheiserSession(model("Momentum 4 Wireless"))
        none.start()
        none.feed(reply(0x2001, "00"))
        none.firmware("2.13.0")
        assertNull(none.row(SennheiserSession.KEY_PERSONALIZED))
        // The Accentum Plus is the second generation from 3.16.4: on is the audio mode 3.
        val acp = SennheiserSession(model("Accentum Plus"))
        acp.start()
        acp.firmware("3.17.0")
        acp.feed(reply(0x2001, "02"))
        acp.feed(reply(0x0804, "0003"))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_PERSONALIZED, true), acp.row(SennheiserSession.KEY_PERSONALIZED))
        assertEquals(listOf("0803:0001"), words(acp.change(SettingChange.Toggle(SennheiserSession.KEY_PERSONALIZED, false))!!))
        assertEquals(listOf("2009:"), words(acp.change(SettingChange.Press(SennheiserSession.KEY_PERSONALIZED_RESET))!!))
    }

    @Test
    fun podcastModeIsTheSpeechAudioMode() {
        val s = SennheiserSession(model("Momentum 4 Wireless"))
        s.start()
        s.feed(reply(0x0804, "0001"))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_PODCAST, false), s.row(SennheiserSession.KEY_PODCAST))
        assertEquals(listOf("0803:0002"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_PODCAST, true))!!))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_PODCAST, true), s.row(SennheiserSession.KEY_PODCAST))
        assertEquals(listOf("0803:0001"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_PODCAST, false))!!))
    }

    @Test
    fun pairedDevicesCanBeConnectedAndDisconnectedExceptThePhoneItself() {
        val s = SennheiserSession(model("Momentum True Wireless 4"))
        val start = words(s.start())
        assertTrue("0007:0a" in start && "1400:" in start && "1407:" in start, "device management is registered and the list is asked for: $start")
        s.feed(reply(0x1400, "0002"))
        assertEquals(listOf("1401:00", "1401:01", "1401:02"), words(s.takeOutbox()))
        s.feed(reply(0x1407, "00"))
        s.feed(reply(0x1401, "0000" + "01" + "506978656c"))
        s.feed(reply(0x1401, "0100" + "00" + "4c6170746f70"))
        s.feed(reply(0x1401, "02" + "00" + "ff"))
        assertEquals(listOf("Pixel", "Laptop"), s.pairedDevices.map { it.name })
        val group = s.groups.single { it.key == "connection" }.settings
        assertEquals(Setting.Choice(SennheiserSession.KEY_PEER_CONNECT, null, listOf("Laptop")), group.first { it.key == SennheiserSession.KEY_PEER_CONNECT })
        assertTrue(group.none { it.key == SennheiserSession.KEY_PEER_DISCONNECT }, "the earbuds' own phone is never offered")
        assertEquals(listOf("1402:01"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_PEER_CONNECT, "Laptop"))!!))
        assertEquals(listOf("1404:01"), words(s.takeReadBacks()))
        s.feed(event(0x1404, "0101"))
        val after = s.groups.single { it.key == "connection" }.settings
        assertEquals(listOf("Laptop"), (after.single { it.key == SennheiserSession.KEY_PEER_DISCONNECT } as Setting.Choice).options)
        assertEquals(listOf("1403:01"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_PEER_DISCONNECT, "Laptop"))!!))
        assertNull(s.change(SettingChange.Choice(SennheiserSession.KEY_PEER_DISCONNECT, "Nothing")))
    }

    @Test
    fun theMomentumSportsSensorsAreSwitchedOnAndReadAsTheAppReadsThem() {
        val s = SennheiserSession(model("Momentum Sport"))
        assertTrue("2202:00" in words(s.start()))
        s.feed(reply(0x2202, "0000"))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_SENSORS, false), s.row(SennheiserSession.KEY_SENSORS))
        assertNull(s.row(SennheiserSession.KEY_HEART_RATE))
        assertEquals(listOf("2201:0001", "2201:0101"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_SENSORS, true))!!))
        assertEquals(listOf("2200:00", "2200:01"), words(s.takeOutbox()))
        // Heart rate: the second byte of the data. Temperature: a value with a power of ten, celsius when the flags say so.
        s.feed(event(0x2200, "00" + "00" + "48"))
        s.feed(reply(0x2200, "01" + "00" + "00" + "016c" + "ff"))
        assertEquals(Setting.Information(SennheiserSession.KEY_HEART_RATE, "72"), s.row(SennheiserSession.KEY_HEART_RATE))
        assertEquals(Setting.Information(SennheiserSession.KEY_BODY_TEMPERATURE, "36.4 °C"), s.row(SennheiserSession.KEY_BODY_TEMPERATURE))
        assertTrue("2200:00" in words(s.poll()), "the readings are asked for again with the battery")
        assertEquals(listOf("2201:0000", "2201:0100"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_SENSORS, false))!!))
        assertNotNull(model("Momentum Sport").features["find-device-1"])
    }

    @Test
    fun auracastScansAddsAndRemovesAsTheAppDoes() {
        val s = SennheiserSession(model("Momentum True Wireless 4"))
        val start = words(s.start())
        assertTrue("0007:15" in start && "2a22:" in start, "feature 21 is registered and the sources are asked for: $start")
        assertNull(s.row(SennheiserSession.KEY_AURACAST_SCAN))
        // No sources yet: a count of zero.
        s.feed(reply(0x2a22, "00"))
        assertEquals(Setting.Action(SennheiserSession.KEY_AURACAST_SCAN, false), s.row(SennheiserSession.KEY_AURACAST_SCAN))
        assertEquals(listOf("2a23:0005"), words(s.change(SettingChange.Press(SennheiserSession.KEY_AURACAST_SCAN))!!))
        // A broadcast the earbuds found: signal, name length and name, address type and address, set, id and the encryption flag, then the rest.
        val name = "Cafe".toByteArray().joinToString("") { "%02x".format(it) }
        val open = "c8" + "04" + name + "00" + "aabbccddeeff" + "01" + "010203" + "00" + "0000" + "01" + "00000001"
        val locked = "c0" + "04" + "4c6f636b" + "00" + "112233445566" + "02" + "040506" + "01" + "0000" + "01" + "00000001"
        s.feed(event(0x2a20, open))
        s.feed(event(0x2a20, locked))
        val add = s.row(SennheiserSession.KEY_AURACAST_ADD) as Setting.Choice
        assertEquals(listOf("Cafe"), add.options, "a broadcast that needs a code is not offered")
        // The app's AddSource: type, address, set, broadcast id, synchronise without past info, interval unknown, one subgroup that takes every stream, no metadata.
        assertEquals(listOf("2a02:00aabbccddeeff01010203" + "02ffff01ffffffff00"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_AURACAST_ADD, "Cafe"))!!))
        assertEquals(listOf("2a22:"), words(s.takeReadBacks()))
        s.feed(reply(0x2a22, "0102"))
        assertEquals(listOf("2a21:02"), words(s.takeOutbox()))
        // The receive state: source id, address type and address, set, broadcast id, PA sync (2 synced), encryption (0 none), then subgroups and their stream bits.
        s.feed(reply(0x2a21, "02" + "00" + "aabbccddeeff" + "01" + "010203" + "02" + "00" + "01" + "00000001" + "00"))
        assertEquals(Setting.Information(SennheiserSession.KEY_AURACAST_SOURCE, "receiving:Cafe"), s.row(SennheiserSession.KEY_AURACAST_SOURCE))
        assertEquals(listOf("2a05:02"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_AURACAST_REMOVE, "Cafe"))!!))
    }
}
