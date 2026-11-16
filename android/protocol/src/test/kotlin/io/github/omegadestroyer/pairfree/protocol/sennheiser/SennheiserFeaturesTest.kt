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
 * The settings Smart Control 4.9.4 offers beyond the first batch (docs/sennheiser/PROTOCOL.md sections 4.2 and 4.3): sidetone, the automatic power off
 * timer, the transparency level, Bluetooth compatibility, tap sensitivity, and the touch and button functions. The frames are the product files' own command words;
 * the earbuds' replies are built as the app reads them. Not tried on earbuds.
 */
class SennheiserFeaturesTest {
    private fun reply(word: Int, payload: String) = Gaia3Packet(0x0495, word + 0x100, payload.bytes()).bytes()
    private fun words(frames: List<ByteArray>) = frames.map { f -> Gaia3Parser().feed(f).single().let { "%04x:%s".format(it.word, it.payload.joinToString("") { b -> "%02x".format(b) }) } }
    private fun model(name: String) = SennheiserModels.ALL.first { it.name == name }
    private fun SennheiserSession.row(key: String): Setting? = groups.flatMap { it.settings }.firstOrNull { it.key == key }

    @Test
    fun theMomentum4HasTheSliderTheSidetoneAndTheTimer() {
        val s = SennheiserSession(model("Momentum 4 Wireless"))
        val start = words(s.start())
        assertTrue("0806:" in start, "sidetone is read: $start")
        assertTrue("1a03:" in start, "the slider is the app's transparency control")
        assertTrue("0406:" in start, "Bluetooth compatibility")
        assertTrue("0601:00" in start, "the timer is read for timer 0")
        assertTrue("1803:" !in start, "the transparency levels are the earbuds' control, not this headband's")
        s.feed(reply(0x0806, "03"))
        s.feed(reply(0x1a03, "64"))
        s.feed(reply(0x0406, "01"))
        s.feed(reply(0x0601, "000708"))
        assertEquals(Setting.Level(SennheiserSession.KEY_SIDETONE, 3, 0, 4), s.row(SennheiserSession.KEY_SIDETONE))
        assertEquals(Setting.Level(SennheiserSession.KEY_TRANSPARENCY_SLIDER, 100, 0, 100), s.row(SennheiserSession.KEY_TRANSPARENCY_SLIDER))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_BT_COMPAT, true), s.row(SennheiserSession.KEY_BT_COMPAT))
        assertEquals(Setting.Choice(SennheiserSession.KEY_AUTO_OFF, "30", listOf("0", "15", "30", "60")), s.row(SennheiserSession.KEY_AUTO_OFF))
        assertEquals(listOf("0805:02", "0806:"), words(s.change(SettingChange.Level(SennheiserSession.KEY_SIDETONE, 2))!!) + words(s.takeReadBacks()))
        assertEquals(listOf("1a02:32"), words(s.change(SettingChange.Level(SennheiserSession.KEY_TRANSPARENCY_SLIDER, 50))!!))
        assertEquals(listOf("0405:00"), words(s.change(SettingChange.Toggle(SennheiserSession.KEY_BT_COMPAT, false))!!))
        s.takeReadBacks()
        assertEquals(listOf("0600:000e10"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_AUTO_OFF, "60"))!!))
        assertEquals(listOf("0601:00"), words(s.takeReadBacks()))
        assertNull(s.change(SettingChange.Choice(SennheiserSession.KEY_AUTO_OFF, "45")), "only the app's four choices go out")
        assertEquals(listOf("0805:04"), words(s.change(SettingChange.Level(SennheiserSession.KEY_SIDETONE, 9))!!), "a level is held to the product's own range")
    }

    @Test
    fun theEarbudsWithTransparentHearingTakeTheThreeLevelsTheAppWrites() {
        val s = SennheiserSession(model("Momentum True Wireless 4"))
        assertTrue("1803:" in words(s.start()))
        assertNull(s.row(SennheiserSession.KEY_TRANSPARENCY_LEVEL))
        s.feed(reply(0x1803, "32"))
        assertEquals(Setting.Choice(SennheiserSession.KEY_TRANSPARENCY_LEVEL, "medium", listOf("low", "medium", "high")), s.row(SennheiserSession.KEY_TRANSPARENCY_LEVEL))
        assertEquals(listOf("1802:64"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_TRANSPARENCY_LEVEL, "high"))!!))
        assertEquals(listOf("1802:01"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_TRANSPARENCY_LEVEL, "low"))!!))
    }

    @Test
    fun theTouchFunctionsAreTheOnesTheProductsScreenOffers() {
        val s = SennheiserSession(model("Accentum True Wireless"))
        val start = words(s.start())
        // Each earbud's four gestures, left (1) then right (0), as the app reads them; then whether they are the defaults.
        assertEquals(listOf("1601:0101", "1601:0102", "1601:0103", "1601:0104", "1601:0001", "1601:0002", "1601:0003", "1601:0004", "1605:"), start.filter { it.startsWith("1601") || it.startsWith("1605") })
        s.feed(reply(0x1601, "010101"))
        s.feed(reply(0x1601, "010402"))
        s.feed(reply(0x1601, "010404"))
        s.feed(reply(0x1601, "000103"))
        s.feed(reply(0x1605, "00"))
        val left = s.groups.single { it.key == "touch_left" }.settings
        assertEquals(listOf("touch_single_left", "touch_hold_left"), left.map { it.key })
        val single = left.first { it.key == "touch_single_left" } as Setting.Choice
        assertEquals("play_pause", single.value)
        // A volume function is for a long press only, and a lock for a triple tap or a long press: the app's constraints.
        assertTrue("volume_up" !in single.options && "sh_lock" !in single.options)
        val hold = left.first { it.key == "touch_hold_left" } as Setting.Choice
        assertEquals("volume_up", hold.value)
        assertTrue("volume_down" in hold.options && "sh_lock" in hold.options)
        assertEquals(listOf("1600:010405"), words(s.change(SettingChange.Choice("touch_hold_left", "volume_down"))!!))
        assertEquals(listOf("1601:0104"), words(s.takeReadBacks()))
        assertNull(s.change(SettingChange.Choice("touch_single_left", "volume_down")), "a volume function is not offered on a single tap")
        assertNull(s.change(SettingChange.Choice("touch_single_left", "sh_open_ear_tip")), "a function the product's screen does not list")
        // The right earbud answered a function the product's screen offers on a single tap, and a number it does not list on a double tap: only the first is a row, as in the app.
        s.feed(reply(0x1601, "000299"))
        assertEquals(listOf("touch_single_right"), s.groups.single { it.key == "touch_right" }.settings.map { it.key })
        assertEquals("previous", (s.groups.single { it.key == "touch_right" }.settings.single() as Setting.Choice).value)
        // The defaults are offered once the gestures are not the defaults.
        assertTrue(s.groups.any { it.key == "touch_default" })
        assertEquals(listOf("1604:"), words(s.change(SettingChange.Press(SennheiserSession.KEY_TOUCH_DEFAULT))!!))
        assertTrue(words(s.takeReadBacks()).count { it.startsWith("1601") } == 8)
    }

    @Test
    fun aFunctionThatNeedsNewerFirmwareWaitsForIt() {
        val s = SennheiserSession(model("CX Sport True Wireless"))
        s.start()
        s.feed(reply(0x1601, "010101"))
        fun options() = (s.groups.single { it.key == "touch_left" }.settings.single { it.key == "touch_single_left" } as Setting.Choice).options
        assertTrue("sh_open_ear_tip" !in options(), "the firmware is not known")
        val c = SennheiserCommands.ALL.first { it.id == SennheiserCommands.FIRMWARE_VERSION }
        s.feed(reply(c.get!!, "000200090005"))
        assertTrue("sh_open_ear_tip" in options())
        // 2.9.5 gives the lock only on a triple tap.
        assertTrue("sh_lock" !in options())
    }

    @Test
    fun theHeadbandsWithFixedGesturesOfferNoTouchRows() {
        for (name in listOf("Accentum", "Accentum Plus", "Momentum 4 Wireless")) {
            val s = SennheiserSession(model(name))
            assertTrue(words(s.start()).none { it.startsWith("1601") }, name)
        }
        assertNotNull(model("Momentum Sport").touch)
    }

    @Test
    fun theMomentumSportHasTapSensitivityAndTheOpenEarTipIsTheCxSports() {
        val s = SennheiserSession(model("Momentum Sport"))
        s.start()
        s.feed(reply(0x2601, "02"))
        assertEquals(Setting.Choice(SennheiserSession.KEY_TAP_SENSITIVITY, "low", listOf("low", "mid", "high")), s.row(SennheiserSession.KEY_TAP_SENSITIVITY))
        assertEquals(listOf("2600:00"), words(s.change(SettingChange.Choice(SennheiserSession.KEY_TAP_SENSITIVITY, "high"))!!))
        val cx = SennheiserSession(model("CX Sport True Wireless"))
        cx.start()
        cx.feed(reply(0x0811, "01"))
        assertEquals(Setting.Toggle(SennheiserSession.KEY_OPEN_EARTIP, true), cx.row(SennheiserSession.KEY_OPEN_EARTIP))
        assertEquals(listOf("0810:00"), words(cx.change(SettingChange.Toggle(SennheiserSession.KEY_OPEN_EARTIP, false))!!))
    }

    @Test
    fun theReadOnlyFactsAreShownInTheAppsWords() {
        val s = SennheiserSession(model("Momentum True Wireless 4"))
        s.start()
        s.feed(reply(0x0402, "0103"))
        s.feed(reply(0x081a, "0000bb80"))
        s.feed(reply(0x0807, "01"))
        assertEquals(Setting.Information(SennheiserSession.KEY_WEAR_LEFT, "in_case"), s.row(SennheiserSession.KEY_WEAR_LEFT))
        assertEquals(Setting.Information(SennheiserSession.KEY_WEAR_RIGHT, "on_head"), s.row(SennheiserSession.KEY_WEAR_RIGHT))
        assertEquals(Setting.Information(SennheiserSession.KEY_SAMPLE_RATE, "48000 Hz"), s.row(SennheiserSession.KEY_SAMPLE_RATE))
        assertEquals(Setting.Information(SennheiserSession.KEY_PROMPT_LANGUAGE, "German"), s.row(SennheiserSession.KEY_PROMPT_LANGUAGE))
    }
}
