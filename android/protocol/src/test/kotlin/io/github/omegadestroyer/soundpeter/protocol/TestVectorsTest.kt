package io.github.omegadestroyer.soundpeter.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Every case in data/test_vectors.json, byte for byte. */
class TestVectorsTest {
    private val vectors = loadData("test_vectors.json")

    private fun cases(section: String): List<JsonObject> = vectors.getValue(section).jsonArray.map { it.jsonObject }

    private fun expectedFrames(case: JsonObject): List<String> =
        case.getValue("frames").jsonArray.map { it.jsonObject }
            .map { "${it.str("vendor")} ${it.str("command")} ${it.str("payload")}" }

    private fun Frame.describe(): String = "${vendor.hex4()} ${command.hex4()} ${payload.hex()}"

    @Test
    fun build() {
        for (case in cases("build")) {
            val frame = Frame.build(case.str("command").toInt(16), case.str("payload").hexBytes(), case.str("vendor").toInt(16))
            assertEquals(case.str("frame"), frame.hex(), case.str("name"))
        }
    }

    @Test
    fun parse() {
        for (case in cases("parse")) {
            val parser = FrameParser()
            val frames = parser.feed(case.str("stream").hexBytes())
            assertEquals(expectedFrames(case), frames.map { it.describe() }, case.str("name"))
            assertEquals(case.str("leftover"), parser.pending.hex(), case.str("name"))
        }
    }

    @Test
    fun parseOneByteAtATime() {
        for (case in cases("parse")) {
            val parser = FrameParser()
            val frames = case.str("stream").hexBytes().flatMap { parser.feed(byteArrayOf(it)) }
            assertEquals(expectedFrames(case), frames.map { it.describe() }, case.str("name"))
            assertEquals(case.str("leftover"), parser.pending.hex(), case.str("name"))
        }
    }

    @Test
    fun eqBand0E01() {
        for (case in cases("eq_band_0E01")) {
            val payload = Eq.band0E01(
                case.int("count"), case.int("index"), case.double("freq_hz"),
                case.double("gain_db"), case.double("q"), case.double("master_gain_db"),
            )
            assertEquals(case.str("payload"), payload.hex(), case.toString())
        }
    }

    @Test
    fun eqBand0E02() {
        for (case in cases("eq_band_0E02")) {
            val payload = Eq.band0E02(
                case.int("count"), case.int("index"), case.double("freq_hz"),
                case.double("gain_db"), case.double("q"), case.double("master_gain_db"),
            )
            assertEquals(case.str("payload"), payload.hex(), case.toString())
        }
    }

    @Test
    fun blePacket() {
        for (case in cases("ble_qualcomm")) {
            val frame = case.str("frame").hexBytes()
            val packet = case.str("ble_packet").hexBytes()
            assertEquals(case.str("ble_packet"), Frame.toBlePacket(frame).hex(), case.str("name"))
            assertEquals(FrameParser().feed(frame).single(), Frame.fromBlePacket(packet), case.str("name"))
        }
    }
}
