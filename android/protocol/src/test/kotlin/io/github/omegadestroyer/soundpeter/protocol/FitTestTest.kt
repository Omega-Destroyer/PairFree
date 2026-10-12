package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The fit test and GAIA feature list, against what the H3 and PeatsAudio exchanged. */
class FitTestTest {
    private val gaia = loadData("test_vectors.json").getValue("gaia").jsonObject

    private fun cases(key: String): List<JsonObject> = gaia.getValue(key).jsonArray.map { it.jsonObject }

    @Test
    fun requestsAreTheCapturedBytes() {
        val frames = cases("requests").associate { it.str("name") to it.str("frame") }
        assertEquals(frames.getValue("which GAIA features"), Requests.gaiaFeatures().hex())
        assertEquals(
            listOf(frames.getValue("fit test: subscribe to the fit feature"), frames.getValue("fit test: start")),
            Requests.fitTest().map { it.hex() },
        )
    }

    @Test
    fun featureListDecodes() {
        for (case in cases("features")) {
            val frame = Frame(Gaia.VENDOR, case.str("reply").toInt(16), case.str("payload").hexBytes())
            val state = EarbudState().update(frame)
            val expected = case.getValue("features").jsonArray.map { it.jsonPrimitive.content.toInt(16) }.toSet()
            assertEquals(expected, state.gaiaFeatures, case.str("name"))
            assertTrue(state.hasFitTest, case.str("name"))
        }
    }

    @Test
    fun fitResultsDecode() {
        for (case in cases("fit_results")) {
            val frame = Frame(Gaia.VENDOR, case.str("notification").toInt(16), case.str("payload").hexBytes())
            val fit = EarbudState().update(frame).fit
            assertEquals(FitResult.Fit.valueOf(case.str("left").uppercase()), fit?.left, case.str("name"))
            assertEquals(FitResult.Fit.valueOf(case.str("right").uppercase()), fit?.right, case.str("name"))
        }
    }

    @Test
    fun gaiaFramesDoNotTouchTheCommandValues() {
        // 12 80 on vendor 00 1D must not be read as a reply on the main vendor, and the reverse.
        val state = EarbudState().update(Frame(Gaia.VENDOR, 0x8309, "00".hexBytes()))
        assertTrue(state.values.isEmpty())
        assertNull(EarbudState().update(Frame(Wire.VENDOR_MAIN, Gaia.FIT_RESULT, "0101".hexBytes())).fit)
        assertFalse(EarbudState().hasFitTest)
    }

    @Test
    fun perModelCommandsNeedTheModelsConfirmation() {
        val answered = EarbudState().update(Frame.reply(0x8335, "0000"))
        assertTrue(answered.supports(Command.LE_AUDIO))
        val h3 = Models.find("S5", null)
        val air5 = Models.find("S2", null)
        assertFalse(answered.offers(Command.LE_AUDIO, h3), "the H3 answers the read, but PeatsAudio does not offer it")
        assertTrue(answered.offers(Command.LE_AUDIO, air5))
        assertFalse(answered.offers(Command.LE_AUDIO, null))
        // An ordinary command needs only the answer.
        val multipoint = EarbudState().update(Frame.reply(0x8314, "0000"))
        assertTrue(multipoint.offers(Command.MULTIPOINT, null))
    }
}
