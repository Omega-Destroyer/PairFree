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
        assertEquals(2, s.takeReadBacks().size) // 1.13: the toggle options (1.9) and the spatial read only
        val s2 = CambridgeConnectSession(model)
        s2.start()
        s2.feed(response(0, CambridgeConnectMisc.GET_API_LEVEL, "0111"))
        assertEquals(4, s2.takeReadBacks().size)
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
        for (id in listOf(CambridgeConnectGesture.POWER_ON_OFF, CambridgeConnectGesture.CASE_PAIRING, CambridgeConnectGesture.CASE_FACTORY_RESET, CambridgeConnectGesture.PAIRING_MODE)) {
            val bad = ConnectPacket(CambridgeConnectWire.FEATURE_GESTURES, CambridgeConnectWire.TYPE_COMMAND, CambridgeConnectGestureCmd.SET_GESTURE_CONTROL_ON_OFF, byteArrayOf(id.toByte(), 1)).bytes()
            assertFalse(s.allowed(bad), "gesture $id")
        }
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
            0 to CambridgeConnectMisc.GET_BLUETOOTH_ADDRESSES, 0 to CambridgeConnectMisc.SET_LEFT_RIGHT_BALANCE,
            1 to CambridgeConnectModes.SET_FLIGHT_MODE, 3 to CambridgeConnectGestureCmd.SET_GESTURE_CONTROLS_ON_OFF, 5 to 0, 0 to 127)) {
            assertFalse(s.allowed(ConnectPacket(feature, 0, command, byteArrayOf(1, 1)).bytes()), "$feature/$command")
        }
    }
}
