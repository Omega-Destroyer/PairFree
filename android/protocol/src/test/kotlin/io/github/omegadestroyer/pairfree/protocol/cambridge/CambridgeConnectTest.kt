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
        assertEquals(2, s2.takeReadBacks().size) // the A100 has toggle options and loudness, no side tone or spatial audio
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
}
