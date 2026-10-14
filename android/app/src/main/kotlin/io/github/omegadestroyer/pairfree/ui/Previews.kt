package io.github.omegadestroyer.pairfree.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.EqProfile
import io.github.omegadestroyer.pairfree.eq.EqSetting
import io.github.omegadestroyer.pairfree.eq.EqState
import io.github.omegadestroyer.pairfree.eq.Equalizer
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Frame
import io.github.omegadestroyer.pairfree.protocol.Gaia
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.ui.theme.Studio
import io.github.omegadestroyer.pairfree.ui.theme.StudioTheme

// The connected screens as they look with an H3 attached, tab bar included. Every reply
// below was recorded from an H3 (data/soundpeats/reports/S5_0.4.9.txt); ANC mode is 1 as after the
// SET test, so the scenes show.

private val H3 = Paired("00:00:00:00:00:00", "SOUNDPEATS H3")

private val H3_STATE: EarbudState = listOf(
    Command.FIRMWARE_VERSION to "0053355f32303235303630355f56302e342e39",
    Command.BATTERY_LEFT to "0064",
    Command.BATTERY_RIGHT to "0064",
    Command.BATTERY_CASE to "002f",
    Command.ANC_MODE to "0001",
    Command.ANC_LEVEL to "001101120013001400",
    Command.TRANSPARENCY_LEVEL to "0001000201",
    Command.GAME_MODE to "0000",
    Command.MULTIPOINT to "0000",
    Command.LDAC to "0001",
    Command.LE_AUDIO to "0000",
    Command.TOUCH_DISABLED to "0000",
    Command.CUSTOM_KEYS to "0001020201030304030505060607070806",
    Command.PROMPT_VOLUME to "000b",
    Command.VOICE_LANGUAGE to "0000",
    Command.FIND_LEFT to "0000",
    Command.FIND_RIGHT to "0000",
).fold(EarbudState()) { s, (command, hex) ->
    s.update(Frame(Wire.VENDOR_MAIN, command.get!! or Wire.REPLY_MASK, bytes(hex)))
}.update(Frame(Gaia.VENDOR, Gaia.GET_FEATURES + Gaia.RESPONSE, bytes("000d010c010901050108070103060207010005")))

/** The H3's result with both earbuds in (data/soundpeats/reports/S5_S2_fit_drain_leaudio.txt). */
private val H3_FITTED: EarbudState = H3_STATE.update(Frame(Gaia.VENDOR, Gaia.FIT_RESULT, bytes("0101")))

private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

/** The owner's Customize1 curve from PeatsAudio, saved as a profile. */
private val H3_EQ: EqState = run {
    val model = Models.find("S5", null)!!
    val layout = model.eq!!
    val mine = EqProfile(EqProfile.USER + "Customize1", "Customize1", listOf(2, 1, 0, 0, 0, 0, 0, 1, 2, 2))
    EqState(model, layout, Equalizer.builtIns(layout) + mine, EqSetting(mine.id, mine.gains))
}

@Composable
private fun Connected(tab: Tab, eq: EqState = H3_EQ) {
    StudioTheme {
        // Padded for the system bars as in PairFreeApp, so a preview run on a device matches the app.
        Box(Modifier.background(Studio.colors.background).safeDrawingPadding()) {
            ConnectedScreen(H3, H3_STATE, NoActions, eq, NoEqActions, tab)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun HomePreview() = Connected(Tab.HOME)

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun SoundPreview() = Connected(Tab.SOUND)

/** A preset is on: drawn read-only, on a wider scale when it reaches past the sliders' range. */
@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun SoundPresetPreview() = Connected(Tab.SOUND, H3_EQ.copy(setting = H3_EQ.setting.copy(profileId = EqProfile.PRESET + "Book Whisper")))

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun ControlsPreview() = Connected(Tab.CONTROLS)

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun DevicePreview() = Connected(Tab.DEVICE)

@Preview(widthDp = 390, heightDp = 844, backgroundColor = 0xFF101214, showBackground = true)
@Composable
private fun FitTestPreview() {
    StudioTheme {
        Box(Modifier.background(Studio.colors.background).safeDrawingPadding()) {
            FitTestScreen(H3, H3_FITTED, NoActions, onClose = {}, initialRun = 1)
        }
    }
}
