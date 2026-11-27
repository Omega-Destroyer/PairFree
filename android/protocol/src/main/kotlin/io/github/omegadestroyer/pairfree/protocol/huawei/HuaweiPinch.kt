package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.Setting

/** A pinch slot is identified by gesture and context. Playback and call writes bind both sides. */
internal enum class HuaweiPinch(
    val key: String, val gesture: Int, val scene: Int, val action: Int, val option: String,
) {
    ANSWER("hw_pinch_answer", 0, 1, 0, "hw_answer_call"),
    REJECT("hw_pinch_reject", 1, 1, 1, "hw_reject_call"),
    PLAY("hw_pinch_play", 0, 2, 2, "hw_play_pause"),
    NEXT("hw_pinch_next", 1, 2, 4, "hw_next_track"),
    PREVIOUS("hw_pinch_previous", 2, 2, 3, "hw_previous_track"),
    HOLD("hw_pinch_hold", 3, 0, 6, "hw_cycle_noise");

    val choices: Map<Int, String> get() = mapOf(255 to "hw_gesture_off", action to option) +
        if (this == HOLD) mapOf(5 to "hw_assistant") else emptyMap()

    val keys: List<String> get() = if (this == HOLD) listOf("${key}_left", "${key}_right") else listOf(key)

    fun matches(packet: HuaweiPacket): Boolean = packet.scalar(1) == gesture && packet.scalar(2) == scene

    fun read(): HuaweiPacket = huaweiWrite(READ, 1 to gesture, 2 to scene)

    fun rows(packet: HuaweiPacket): List<Setting> = keys.mapIndexedNotNull { idx, row ->
        val value = packet.scalar(3 + idx) ?: return@mapIndexedNotNull null
        val option = if (this != HOLD && value != packet.scalar(4)) null else choices[value]
        Setting.Choice(row, option, choices.values.toList())
    }

    fun write(row: String, value: Int): HuaweiPacket? {
        if (row !in keys || value !in choices) return null
        val changed = when (row) {
            "${key}_left" -> arrayOf(3 to value)
            "${key}_right" -> arrayOf(4 to value)
            else -> arrayOf(3 to value, 4 to value)
        }
        return huaweiWrite(WRITE, 1 to gesture, 2 to scene, *changed)
    }

    fun allows(packet: HuaweiPacket, report: HuaweiPacket): Boolean =
        matches(packet) && rows(report).any { row ->
            val expected = write(row.key, packet.scalar(if (row.key.endsWith("_right")) 4 else 3) ?: return@any false)
            expected != null && expected.fields.size == packet.fields.size &&
                expected.fields.zip(packet.fields).all { (a, b) -> a.tag == b.tag && a.value.contentEquals(b.value) }
        }

    companion object {
        const val READ = 0x2b93
        const val WRITE = 0x2b92
    }
}
