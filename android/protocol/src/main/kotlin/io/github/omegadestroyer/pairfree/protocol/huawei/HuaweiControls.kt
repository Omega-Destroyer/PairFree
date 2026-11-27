package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingTab

internal fun HuaweiPacket.field(tag: Int): ByteArray? = fields.singleOrNull { it.tag == tag }?.value
internal fun HuaweiPacket.scalar(tag: Int): Int? = field(tag)?.singleOrNull()?.toInt()?.and(0xff)
internal val HuaweiPacket.op: Int get() = service shl 8 or command
internal fun huaweiPacket(op: Int, fields: List<HuaweiTlv>): HuaweiPacket = HuaweiPacket(op shr 8, op and 0xff, fields)
internal fun huaweiRead(op: Int, vararg tags: Int): HuaweiPacket = huaweiPacket(op, tags.map(::HuaweiTlv))
internal fun huaweiWrite(op: Int, vararg fields: Pair<Int, Int>): HuaweiPacket =
    huaweiPacket(op, fields.map { (tag, value) -> HuaweiTlv(tag, byteArrayOf(value.toByte())) })

internal class HuaweiControl(
    val key: String,
    val tab: SettingTab,
    val read: Int,
    val write: Int,
    val tag: Int = 1,
    val writeTags: List<Int> = listOf(tag),
    val choices: Map<Int, String> = emptyMap(),
    val availabilityTag: Int? = null,
    val requiresOptions: Boolean = false,
    val capabilityTag: Int? = null,
    val readTags: List<Int> = listOf(tag),
) {
    fun options(report: HuaweiPacket): Map<Int, String> =
        report.field(availabilityTag ?: return choices)?.let { data ->
            choices.filterKeys { it.toByte() in data }
        } ?: if (requiresOptions) emptyMap() else choices

    fun row(report: HuaweiPacket): Setting? {
        if (capabilityTag != null && (report.scalar(capabilityTag) ?: 0) == 0) return null
        val value = report.scalar(tag) ?: return null
        if (choices.isEmpty()) return if (value in 0..1) Setting.Toggle(key, value == 1) else null
        val options = options(report)
        return if (options.isEmpty()) null else Setting.Choice(key, choices[value], options.values.distinct())
    }
}

internal fun HuaweiProduct.controls(): List<HuaweiControl> = buildList {
    val off = 255 to "hw_gesture_off"
    val playback = mapOf(off, 1 to "hw_play_pause", 2 to "hw_next_track", 7 to "hw_previous_track", 0 to "hw_assistant")
    val shark = mapOf(off, 4 to "hw_next_track", 1 to "hw_play_pause", 0 to "hw_assistant", 3 to "hw_cycle_noise")
    val walrus = mapOf(off, 0 to "hw_assistant", 1 to "hw_play_pause", 2 to "hw_next_track",
        3 to "hw_cycle_noise", 4 to "hw_next_track", 7 to "hw_previous_track", 8 to "hw_previous_track")
    val call = mapOf(off, 0 to "hw_answer_call")
    val hold = buildMap {
        put(off.first, off.second)
        put(if (has("hold_toggle")) 3 else 10, "hw_cycle_noise")
        if (has("hold_extra") || has("hold_assistant")) put(0, "hw_assistant")
        if (has("hold_extra")) {
            put(18, "hw_volume_up")
            put(19, "hw_volume_down")
        }
    }
    val cycle = mapOf(1 to "hw_off_anc", 2 to "hw_off_anc_aware", 3 to "hw_anc_aware", 4 to "hw_off_aware")
    fun gesture(key: String, read: Int, write: Int, tag: Int, options: Map<Int, String>, mask: Int? = null, both: Boolean = false, needsMask: Boolean = false) {
        add(HuaweiControl(key, SettingTab.CONTROLS, read, write, tag, if (both) listOf(1, 2) else listOf(tag), options, mask, requiresOptions = needsMask))
    }
    fun taps(key: String, read: Int, write: Int, options: Map<Int, String>, needsMask: Boolean = false) {
        gesture("${key}_left", read, write, 1, options, 3, needsMask = needsMask)
        gesture("${key}_right", read, write, 2, options, 3, needsMask = needsMask)
    }
    when {
        has("shark_tap") -> taps("hw_double", 0x0120, 0x011f, shark)
        has("walrus_tap") -> taps("hw_double", 0x0120, 0x011f, walrus, needsMask = true)
        has("double") -> taps("hw_double", 0x0120, 0x011f, playback)
    }
    if (has("double_call")) gesture("touch_double_call", 0x0120, 0x011f, 4, call)
    if (has("triple")) taps("hw_triple", 0x0126, 0x0125, playback.filterKeys { it in setOf(2, 7, 255) })
    if (has("walrus_hold")) {
        val options = mapOf(off, 3 to "hw_off_anc", 5 to "hw_off_anc_aware", 6 to "hw_anc_aware", 9 to "hw_off_aware")
        gesture("hw_hold", 0x2b17, 0x2b16, 2, options, 3, needsMask = true)
    }
    if (has("hold")) gesture("hw_hold_left", 0x2b17, 0x2b16, 1, hold)
    if (has("hold_right")) gesture("hw_hold_right", 0x2b17, 0x2b16, 2, hold)
    if (has("hold_call")) gesture("hw_hold_call", 0x2b17, 0x2b16, 4, call)
    if (has("hold_reject")) gesture("hw_hold_call", 0x2b17, 0x2b16, 4, mapOf(off, 0 to "hw_reject_call"))
    if (has("hold") && "two_modes" !in features && has("anc")) gesture("hw_cycle_left", 0x2b19, 0x2b18, 1, cycle)
    if (has("hold_right") && "two_modes" !in features && has("anc")) gesture("hw_cycle_right", 0x2b19, 0x2b18, 2, cycle)
    if (has("single_hold")) {
        val options = mapOf(off, 3 to "hw_off_anc", 5 to "hw_off_anc_aware", 6 to "hw_anc_aware", 9 to "hw_off_aware")
        gesture("hw_hold", 0x2b17, 0x2b16, 1, options)
    }
    if (has("power_tap")) gesture("hw_power_tap", 0x0120, 0x011f, 1, mapOf(off, 12 to "hw_switch_device"), 3)
    if (has("swipe")) gesture("hw_swipe", 0x2b1f, 0x2b1e, 1, mapOf(off, 0 to "hw_change_volume"), both = true)
    if (has("wear")) add(HuaweiControl("hw_auto_pause", SettingTab.DEVICE, 0x2b11, 0x2b10))
    if (has("latency")) add(HuaweiControl("hw_low_latency", SettingTab.SOUND, 0x2b6c, 0x2b6c, tag = 2, writeTags = listOf(1)))
    if (has("fiji")) {
        val eyewearTap = mapOf(off, 0 to "hw_assistant", 1 to "hw_play_pause")
        val eyewearSwipe = mapOf(off, 0 to "hw_change_volume", 1 to "hw_change_track")
        gesture("hw_glasses_double_left", 0x0120, 0x011f, 1, eyewearTap)
        gesture("hw_glasses_double_right", 0x0120, 0x011f, 2, eyewearTap)
        gesture("hw_swipe_left", 0x2b1f, 0x2b1e, 1, eyewearSwipe)
        gesture("hw_swipe_right", 0x2b1f, 0x2b1e, 2, eyewearSwipe)
        gesture("hw_glasses_hold", 0x2b21, 0x2b20, 1,
            mapOf(off, 0 to "hw_face_translate", 1 to "hw_voice_memo", 2 to "hw_pair_glasses"))
        add(HuaweiControl("hw_greeting", SettingTab.DEVICE, 0x2b0f, 0x2b0e))
        add(HuaweiControl("hw_glasses_wear", SettingTab.DEVICE, 0x2b11, 0x2b10))
        add(HuaweiControl("hw_saving", SettingTab.DEVICE, 0x2b1d, 0x2b1c))
        add(HuaweiControl("hw_adaptive_volume", SettingTab.SOUND, 0x2b23, 0x2b22))
    }
    if (has("quality")) add(HuaweiControl("hw_quality", SettingTab.SOUND, 0x2ba3, 0x2ba2, tag = 2,
        writeTags = listOf(1), capabilityTag = 1, readTags = listOf(1)))
    if (has("multipoint")) add(HuaweiControl("multipoint", SettingTab.DEVICE, 0x2b2f, 0x2b2e))
}

internal enum class HuaweiFeature(val feature: String, val row: String, val code: Int) {
    ADAPTIVE_VOLUME("adaptive_volume", "hw_adaptive_volume", 2),
    SINGLE_ANC("single_anc", "hw_single_anc", 5),
    DROP("drop", "hw_drop", 7),
    HEAD("head", "hw_head_control", 11),
    CONVERSATION("conversation", "hw_conversation", 27);

    fun read(): HuaweiPacket = huaweiPacket(OP, buildList {
        add(HuaweiTlv(1, byteArrayOf(code.toByte())))
        if (this@HuaweiFeature != HEAD) add(HuaweiTlv(2))
    })

    fun write(on: Boolean): HuaweiPacket = huaweiWrite(OP, 1 to code, 2 to if (on) 1 else 0)

    companion object {
        const val OP = 0x2bb4
    }
}
