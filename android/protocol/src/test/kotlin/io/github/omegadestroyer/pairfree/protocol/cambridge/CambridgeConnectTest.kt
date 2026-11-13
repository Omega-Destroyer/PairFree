package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Value
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Melomania Connect 2.6.0's GAIA v3 custom API, with frames built from the layouts read in the app (docs/cambridge/PROTOCOL.md section 9). */
class CambridgeConnectTest {
    private val model = CambridgeModels.fromName("Melomania A100")!!

    private fun response(feature: Int, command: Int, payload: String): ByteArray =
        ConnectPacket(feature, CambridgeConnectWire.TYPE_RESPONSE, command, payload.hexBytes()).bytes()

    @Test fun framesFollowTheAppsCommandWord() {
        // Misc, command, 2: GET_API_LEVEL.
        assertArrayEquals("ff0300000b9e0002".hexBytes(), ConnectPacket(0, 0, 2).bytes())
        // Modes (1), command, 0: SET_GAMING_MODE on.
        assertArrayEquals("ff0300010b9e020001".hexBytes(), ConnectPacket(1, 0, 0, byteArrayOf(1)).bytes())
        // Response word: feature << 9 | 2 << 7 | command.
        assertArrayEquals("ff0300020b9e010201 11".replace(" ", "").hexBytes(), ConnectPacket(0, 2, 2, byteArrayOf(1, 0x11)).bytes())
        val checked = ConnectPacket(0, 0, 3).bytes(checksum = true)
        assertEquals(0, checked.fold(0) { x, b -> x xor (b.toInt() and 0xff) })
        val parsed = ConnectParser().feed(byteArrayOf(5, 6) + "ff0300020b9e0102".hexBytes().let { it + byteArrayOf(1, 0x11) } + checked)
        assertEquals(2, parsed.size)
        assertEquals(listOf(0, 0), parsed.map { it.feature })
        assertTrue(parsed[0].isResponse)
        for (split in 1 until checked.size) {
            val p = ConnectParser()
            assertTrue(p.feed(checked.copyOfRange(0, split)).isEmpty())
            assertEquals(1, p.feed(checked.copyOfRange(split, checked.size)).size)
        }
        val long = ConnectPacket(2, 0, 0, ByteArray(300)).bytes()
        assertEquals(300, ConnectParser().feed(long).single().payload.size)
        // Another vendor's frame is dropped.
        assertTrue(ConnectParser().feed("ff0300000001 0002".replace(" ", "").hexBytes()).isEmpty())
    }

    @Test fun rowsShowOnceTheirReadAnsweredAndLaterReadsWaitForTheApiLevel() {
        val s = CambridgeConnectSession(model)
        val first = s.start()
        assertArrayEquals("ff0300000b9e0002".hexBytes(), first[0])
        s.feed(response(0, CambridgeConnectMisc.GET_API_LEVEL, "010d"))
        assertEquals(1, s.takeReadBacks().size) // 1.13: the toggle options (1.9); loudness (1.14) and the rest wait
        val s2 = CambridgeConnectSession(model)
        s2.start()
        s2.feed(response(0, CambridgeConnectMisc.GET_API_LEVEL, "0111"))
        assertEquals(3, s2.takeReadBacks().size) // the A100 has toggle options, loudness and the device list, no side tone or spatial audio
        s2.feed(response(0, CambridgeConnectMisc.GET_BATTERY_STATE, "00000000" + "32" + "28" + "64" + "0000"))
        assertEquals(Value.Number(0x32), s2.state.values[Command.BATTERY_LEFT])
        assertEquals(Value.Number(0x28), s2.state.values[Command.BATTERY_RIGHT])
        assertTrue(s2.groups.isEmpty())
        s2.feed(response(1, CambridgeConnectModes.GET_GAMING_MODE, "01"))
        s2.feed(response(0, CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE, "03"))
        s2.feed(response(0, CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT, "000000"))
        val rows = s2.groups.flatMap { it.settings }.associateBy { it.key }
        assertEquals(Setting.Toggle("ca_gaming", true), rows["ca_gaming"])
        assertEquals("anc_med", (rows["ca_noise"] as Setting.Choice).value)
        assertEquals("never", (rows["ca_auto_off"] as Setting.Choice).value)
    }

    @Test fun writesAreExactAndOnlyOfferedRowsGo() {
        val s = CambridgeConnectSession(model)
        s.start()
        s.feed(response(0, CambridgeConnectMisc.GET_API_LEVEL, "0111"))
        s.feed(response(1, CambridgeConnectModes.GET_GAMING_MODE, "00"))
        s.feed(response(0, CambridgeConnectMisc.GET_AMBIENT_NOISE_MODE, "00"))
        s.feed(response(0, CambridgeConnectMisc.GET_AUTO_POWER_DOWN_TIMEOUT, "010000"))
        s.feed(response(0, CambridgeConnectMisc.GET_LANGUAGE, "01"))
        assertNull(s.change(SettingChange.Toggle("ca_mono", true)), "not read, so not offered")
        val gaming = s.change(SettingChange.Toggle("ca_gaming", true))!!.single()
        assertArrayEquals("ff0300010b9e020001".hexBytes(), gaming)
        assertTrue(s.allowed(gaming))
        assertEquals(1, s.takeReadBacks().size)
        val noise = s.change(SettingChange.Choice("ca_noise", "transparency_high"))!!.single()
        assertArrayEquals("ff0300010b9e000f04".hexBytes(), noise)
        assertTrue(s.allowed(noise))
        val off = s.change(SettingChange.Choice("ca_auto_off", "30"))!!.single()
        assertArrayEquals("ff0300030b9e000800 1e00".replace(" ", "").hexBytes(), off)
        assertTrue(s.allowed(off))
        assertNull(s.change(SettingChange.Choice("ca_noise", "anc_super")))
    }

    @Test fun gesturesFollowTheSupportedMaskAndNeverReachTheResetsOrPower() {
        val s = CambridgeConnectSession(model)
        s.start()
        s.feed(response(3, CambridgeConnectGestureCmd.GET_SUPPORTED_GESTURE_CONTROLS, "3f05"))
        s.feed(response(3, CambridgeConnectGestureCmd.GET_GESTURE_CONTROLS_ON_OFF, "1100"))
        val keys = s.groups.single { it.key == "touch_buttons" }.settings
        assertEquals(listOf(
            "ca_gesture_play_pause", "ca_gesture_next", "ca_gesture_previous", "ca_gesture_mic_mute", "ca_gesture_volume_up",
            "ca_gesture_volume_down", "ca_gesture_end_call", "ca_gesture_transparency",
        ), keys.map { it.key })
        assertEquals(true, (keys[0] as Setting.Toggle).on)
        assertEquals(false, (keys[1] as Setting.Toggle).on)
        assertEquals(true, (keys[4] as Setting.Toggle).on)
        val frame = s.change(SettingChange.Toggle("ca_gesture_next", true))!!.single()
        assertArrayEquals("ff0300020b9e06030101".hexBytes(), frame)
        assertTrue(s.allowed(frame))
        // A gesture the earbuds did not list (pairing mode, bit 12) is not switched; the case gestures are, as switches with no state to read.
        val pairing = ConnectPacket(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectWire.TYPE_COMMAND, CambridgeConnectGestureCmd.SET_GESTURE_CONTROL_ON_OFF,
            byteArrayOf(CambridgeConnectGesture.PAIRING_MODE.toByte(), 1)).bytes()
        assertFalse(s.allowed(pairing))
        val reset = s.change(SettingChange.Choice("ca_case_reset", "off"))!!.single()
        assertArrayEquals("ff0300020b9e0603f200".hexBytes(), reset)
        assertTrue(s.allowed(reset))
        assertEquals("off", (s.groups.single { it.key == "ca_case" }.settings.first { it.key == "ca_case_reset" } as Setting.Choice).value)
        assertNull((s.groups.single { it.key == "ca_case" }.settings.first { it.key == "ca_case_power" } as Setting.Choice).value)
        assertFalse(s.allowed(ConnectPacket(3, 0, CambridgeConnectGestureCmd.SET_GESTURE_CONTROL_ON_OFF, byteArrayOf(0xf3.toByte(), 1)).bytes()), "no such gesture")
    }

    @Test fun nothingOutsideTheAllowlistGoesOut() {
        val s = CambridgeConnectSession(model)
        s.start()
        s.feed(response(2, CambridgeConnectCodecCmd.GET_AVAILABLE_CODECS, "000109"))
        s.feed(response(2, CambridgeConnectCodecCmd.GET_ENABLED_CODECS, "0001"))
        val ldac = s.change(SettingChange.Toggle("ca_codec_ldac", true))!!.single()
        assertTrue(s.allowed(ldac))
        assertFalse(s.allowed(ConnectPacket(2, 0, CambridgeConnectCodecCmd.SET_ENABLED_CODECS, byteArrayOf(0x0b)).bytes()), "a codec the earbuds do not list")
        for ((feature, command) in listOf(0 to CambridgeConnectMisc.CONNECT_DEVICE, 0 to CambridgeConnectMisc.DISCONNECT_DEVICE,
            0 to CambridgeConnectMisc.GET_BLUETOOTH_ADDRESSES,
            1 to CambridgeConnectModes.SET_FLIGHT_MODE, 3 to CambridgeConnectGestureCmd.SET_GESTURE_CONTROLS_ON_OFF, 5 to 0, 0 to 127)) {
            assertFalse(s.allowed(ConnectPacket(feature, 0, command, byteArrayOf(1, 1)).bytes()), "$feature/$command")
        }
    }

    private fun qtil(command: Int, payload: String, type: Int = CambridgeConnectWire.TYPE_RESPONSE): ByteArray =
        ConnectPacket(CambridgeConnectWire.FEATURE_MUSIC, type, command, payload.hexBytes(), CambridgeConnectWire.QTIL_VENDOR).bytes()

    @Test fun theEqIsQualcommsUserSetAndGoesOutWholeWithTheBandsTheEarbudsReport() {
        val s = CambridgeConnectSession(model)
        val start = s.start()
        // Vendor 001D, feature 5 (music processing), command 4 (number of bands): word 0x0A04.
        assertTrue(start.any { it.contentEquals("ff030000001d0a04".hexBytes()) })
        assertNull(s.eqBands)
        s.feed(qtil(CambridgeConnectMusic.GET_BAND_COUNT, "07"))
        // It then asks for all seven: start 0, end 6.
        val ask = s.takeReadBacks().single()
        assertArrayEquals("ff030002001d0a050006".hexBytes(), ask)
        assertTrue(s.allowed(ask))
        val hz = listOf(60, 120, 500, 1000, 2000, 4000, 10000)
        val gains = listOf(0, 60, -60, 0, 30, 0, -540) // in 1/60 dB
        val body = StringBuilder("0006")
        for (i in hz.indices) body.append("%04x".format(hz[i])).append("1000").append("0d").append("%04x".format(gains[i] and 0xffff))
        s.feed(qtil(CambridgeConnectMusic.GET_BAND_CONFIG, body.toString()))
        assertEquals(hz, s.eqBands!!.map { it.first })
        assertEquals(listOf(0.0, 1.0, -1.0, 0.0, 0.5, 0.0, -9.0), s.eqBands!!.map { it.second })
        assertEquals(6, s.eqPresets.size)
        // A curve: select the user set (63), then the gains (start, end, signed 16-bit in 1/60 dB), the last held to the app's -9 dB floor and +3 dB ceiling.
        val frames = s.eqCurve(listOf(0.0, 1.0, -1.0, 0.0, 5.0, 0.0, -12.0))!!
        assertArrayEquals("ff030001001d0a033f".hexBytes(), frames[0])
        val ints = listOf(0, 60, -60, 0, 180, 0, -540).map { "%04x".format(it and 0xffff) }.joinToString("")
        assertArrayEquals("ff030010001d0a060006$ints".hexBytes(), frames[1])
        for (f in frames) assertTrue(s.allowed(f))
        // Anything beyond the app's range, or for another number of bands, is refused.
        assertFalse(s.allowed(ConnectPacket(5, 0, CambridgeConnectMusic.SET_GAINS, "0006${"0000".repeat(6)}".hexBytes(), CambridgeConnectWire.QTIL_VENDOR).bytes()))
        assertFalse(s.allowed(ConnectPacket(5, 0, CambridgeConnectMusic.SET_SET, byteArrayOf(0), CambridgeConnectWire.QTIL_VENDOR).bytes()), "only the user set")
        assertFalse(s.allowed(ConnectPacket(4, 0, 1, byteArrayOf(), CambridgeConnectWire.QTIL_VENDOR).bytes()), "no other Qualcomm feature")
        assertNull(CambridgeConnectSession(model).eqCurve(listOf(0.0)))
    }

    @Test fun balanceAndModelConfigFollowTheApp() {
        val s = CambridgeConnectSession(model)
        s.start()
        s.feed(response(0, CambridgeConnectMisc.GET_LEFT_RIGHT_BALANCE, "0120"))
        assertEquals(-32, (s.groups.flatMap { it.settings }.first { it.key == "ca_balance" } as Setting.Level).value)
        val frame = s.change(SettingChange.Level("ca_balance", 40))!!.single()
        assertArrayEquals("ff0300020b9e000a0028".hexBytes(), frame)
        assertTrue(s.allowed(frame))
        assertFalse(s.allowed(ConnectPacket(0, 0, CambridgeConnectMisc.SET_LEFT_RIGHT_BALANCE, byteArrayOf(0, 101)).bytes()))
        // The A100 has mono, sleep and touch controls; the P100 (headphones) none of them, but side tone; the X100 has only the EQ's presets.
        val p100 = CambridgeConnectSession(CambridgeModels.fromName("Melomania P100")!!)
        assertEquals(12, p100.start().size) // no mono, sleep, codec or touch reads; the EQ's two
        assertEquals(false, CambridgeModels.fromName("Melomania X100")!!.config!!.eqCustom)
        assertEquals(true, CambridgeModels.fromName("Melomania A100 SE")!!.config!!.spatial)
    }

    private fun touchReply(command: Int, payload: String): ByteArray =
        ConnectPacket(CambridgeConnectWire.FEATURE_GESTURE_CONFIG, CambridgeConnectWire.TYPE_RESPONSE, command, payload.hexBytes(), CambridgeConnectWire.QTIL_VENDOR).bytes()

    @Test fun touchActionsAreQualcommsGestureConfigurationAssignedAsTheAppDoes() {
        val s = CambridgeConnectSession(model)
        val start = s.start()
        // Vendor 001D, feature 11: word 0x1600 (touchpads) and 0x1601 (gestures).
        assertTrue(start.any { it.contentEquals("ff030000001d1600".hexBytes()) })
        s.feed(touchReply(CambridgeConnectGestureConfig.GET_TOUCHPADS, "02"))
        s.feed(touchReply(CambridgeConnectGestureConfig.GET_GESTURES, "63")) // bits 0, 1, 5, 6: tap, swipe up, double tap, long press
        val asks = s.takeReadBacks()
        assertEquals(4, asks.size)
        assertArrayEquals("ff030002001d160400 00".replace(" ", "").hexBytes(), asks[0])
        // A configuration is touchpad << 14 | context << 7 | action; touchpad 2 is the left, 1 the right. Tap: left plays and pauses when idle or streaming, right skips while streaming.
        fun cfg(pad: Int, ctx: Int, action: Int) = "%04x".format((pad shl 14) or (ctx shl 7) or action)
        s.feed(touchReply(CambridgeConnectGestureConfig.GET_CONFIG, "00" + cfg(2, 2, 0) + cfg(2, 1, 0) + cfg(1, 1, 2)))
        s.feed(touchReply(CambridgeConnectGestureConfig.GET_CONFIG, "05"))
        s.feed(touchReply(CambridgeConnectGestureConfig.GET_CONFIG, "01"))
        s.feed(touchReply(CambridgeConnectGestureConfig.GET_CONFIG, "06"))
        val rows = s.groups.single { it.key == "ca_touch" }.settings.associateBy { it.key }
        assertEquals("play_pause", (rows["ca_tp_tap_left"] as Setting.Choice).value)
        assertEquals("next", (rows["ca_tp_tap_right"] as Setting.Choice).value)
        assertEquals("none", (rows["ca_tp_long_press_left"] as Setting.Choice).value)
        // Volume up on the left: the single-touchpad entries and the left's own go, the app's context for the action comes, the right's stay.
        val frame = s.change(SettingChange.Choice("ca_tp_tap_left", "volume_up"))!!.single()
        assertArrayEquals(("ff030006001d1605" + "0000" + cfg(1, 1, 2) + cfg(2, 0, 16)).hexBytes(), frame)
        assertTrue(s.allowed(frame))
        assertEquals("volume_up", (s.groups.single { it.key == "ca_touch" }.settings.first { it.key == "ca_tp_tap_left" } as Setting.Choice).value)
        // Nothing for the left: its configurations all go.
        val none = s.change(SettingChange.Choice("ca_tp_tap_left", "none"))!!.single()
        assertArrayEquals(("ff030004001d1605" + "0000" + cfg(1, 1, 2)).hexBytes(), none)
        // A reset, a gesture the earbuds do not list and an action the app does not offer are refused.
        assertFalse(s.allowed(ConnectPacket(11, 0, CambridgeConnectGestureConfig.RESET, byteArrayOf(), CambridgeConnectWire.QTIL_VENDOR).bytes()))
        assertFalse(s.allowed(ConnectPacket(11, 0, CambridgeConnectGestureConfig.SET_CONFIG, "0200".hexBytes(), CambridgeConnectWire.QTIL_VENDOR).bytes()))
        assertFalse(s.allowed(ConnectPacket(11, 0, CambridgeConnectGestureConfig.SET_CONFIG, "0000${cfg(2, 0, 9)}".hexBytes(), CambridgeConnectWire.QTIL_VENDOR).bytes()))
    }

    @Test fun pairedDevicesListOneFramePerDeviceAndNeverReachTheReport() {
        val s = CambridgeConnectSession(model)
        s.start()
        s.feed(response(0, CambridgeConnectMisc.GET_API_LEVEL, "0111"))
        val reads = s.takeReadBacks()
        assertTrue(reads.any { it.contentEquals("ff0300000b9e0016".hexBytes()) }, "the A100 lists devices from API 1.15")
        fun device(id: Int, name: String, status: Int): String =
            "%02x".format(id) + name.toByteArray().joinToString("") { "%02x".format(it) }.padEnd(40, '0') + "01020304" + "%02x".format(status)
        s.feed(response(0, CambridgeConnectMisc.GET_DEVICES, device(0, "Phone", CambridgeConnectPeer.CONNECTED_PRIMARY)))
        s.feed(response(0, CambridgeConnectMisc.GET_DEVICES, device(1, "Laptop", CambridgeConnectPeer.PAIRED)))
        val rows = s.groups.flatMap { it.settings }.associateBy { it.key }
        assertEquals(listOf("Laptop"), (rows["ca_peer_connect"] as Setting.Choice).options)
        assertEquals(listOf("Phone"), (rows["ca_peer_disconnect"] as Setting.Choice).options)
        val connect = s.change(SettingChange.Choice("ca_peer_connect", "Laptop"))!!.single()
        assertArrayEquals("ff0300010b9e001701".hexBytes(), connect)
        assertTrue(s.allowed(connect))
        assertFalse(s.allowed(ConnectPacket(0, 0, CambridgeConnectMisc.CONNECT_DEVICE, byteArrayOf(0)).bytes()), "a device that is already connected")
        assertFalse(s.allowed(ConnectPacket(0, 0, CambridgeConnectMisc.CONNECT_DEVICE, byteArrayOf(9)).bytes()), "no such device")
        assertTrue(s.replies.keys.none { it == "0.22" })
        // The next list starts afresh.
        s.takeReadBacks()
        s.feed(response(0, CambridgeConnectMisc.GET_DEVICES, device(0, "Phone", CambridgeConnectPeer.CONNECTED_PRIMARY)))
        assertNull(s.groups.flatMap { it.settings }.firstOrNull { it.key == "ca_peer_connect" })
    }
}
