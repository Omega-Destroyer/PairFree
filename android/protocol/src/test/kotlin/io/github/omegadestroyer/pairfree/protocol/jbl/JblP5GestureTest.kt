package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The protocol 4 models' customized button page (docs/jbl/PROTOCOL.md section 6.1), from data/jbl/gesture_config.json. */
class JblP5GestureTest {
    private fun model(name: String) = JblModels.ALL.first { it.name == name }

    /** A read's answer: status 0, then each feature's id, its value's length and the value. */
    private fun answer(vararg records: Pair<Int, ByteArray>) = JblP4Packet(
        JblP4.GET,
        byteArrayOf(0, 0) + records.fold(ByteArray(0)) { acc, (id, v) -> acc + byteArrayOf(id.toByte(), (id shr 8).toByte(), v.size.toByte(), 0) + v },
    )

    @Test
    fun theLiveBuds4ReadShowAndWriteTheirGestures() {
        val m = model("JBL Live Buds 4")
        assertEquals("217B", m.p5Gestures)
        val leftTap = m.p5GestureList.first { it.place == "left" && it.gesture == "tap" }
        assertEquals(0x0F04, leftTap.feature)
        val s = JblSession(m)
        val start = s.start().filterIsInstance<JblP4Packet>().first { it.command == JblP4.GET }
        assertTrue(start.payload.toList().windowed(2, 2).any { it == listOf(0x04.toByte(), 0x0F.toByte()) }, "the gestures are read with the rest")
        s.receive(answer(0x0F04 to byteArrayOf(0x0B)))
        val row = s.groups.flatMap { it.settings }.first { it.key == "jbl_p5_left_tap" } as Setting.Choice
        assertEquals("ambientSound", row.value)
        assertEquals(leftTap.functions.keys.toList(), row.options)
        val out = s.change(SettingChange.Choice("jbl_p5_left_tap", "playPause"))!!.single() as JblP4Packet
        assertEquals(JblP4Packet.set(0x0F04 to byteArrayOf(0x08)).bytes().toList(), out.bytes().toList())
        assertTrue(s.allowed(out))
    }

    @Test
    fun noiseControlKeepsTheCycleTheGestureHas() {
        val s = JblSession(model("JBL Live Buds 4"))
        s.start()
        // Ambient Aware and off (0D), one of the cycles JBL's config lists for the noise control function.
        s.receive(answer(0x0F04 to byteArrayOf(0x0D)))
        assertEquals("ambientSound", (s.groups.flatMap { it.settings }.first { it.key == "jbl_p5_left_tap" } as Setting.Choice).value)
        s.change(SettingChange.Choice("jbl_p5_left_tap", "volumeUp"))
        val back = s.change(SettingChange.Choice("jbl_p5_left_tap", "ambientSound"))!!.single() as JblP4Packet
        assertEquals(JblP4Packet.set(0x0F04 to byteArrayOf(0x0B)).bytes().toList(), back.bytes().toList(), "from volume up, JBL's page's value")
    }

    @Test
    fun modelsWithoutTheirOwnEntryTakeTheCommonOne() {
        assertEquals("common", model("JBL Vibe Buds 3").p5Gestures)
        assertTrue(model("JBL Vibe Buds 3").p5GestureList.isNotEmpty())
        assertTrue(JBL_P5_GESTURES.values.flatten().none { "eQControl" in it.functions }, "the EQ preset cycle is left out")
    }
}
