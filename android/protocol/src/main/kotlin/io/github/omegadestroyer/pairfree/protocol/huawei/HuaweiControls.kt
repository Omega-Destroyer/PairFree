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
    val fallbackTag: Int? = null,
) {
    fun options(report: HuaweiPacket): Map<Int, String> =
        report.field(availabilityTag ?: return choices)?.let { data ->
            choices.filterKeys { it.toByte() in data }
        } ?: if (requiresOptions) emptyMap() else choices

    fun row(report: HuaweiPacket): Setting? {
        if (capabilityTag != null && (report.scalar(capabilityTag) ?: 0) == 0) return null
        val value = report.scalar(tag) ?: fallbackTag?.let(report::scalar) ?: return null
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
        has("shark_tap") -> taps("hw_double", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, shark)
        has("walrus_tap") -> taps("hw_double", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, walrus, needsMask = true)
        has("double") -> taps("hw_double", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, playback)
    }
    if (has("double_call")) gesture("touch_double_call", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, 4, call)
    if (has("triple")) taps("hw_triple", HuaweiCommand.TRIPLE_TAP_READ, HuaweiCommand.TRIPLE_TAP_SET, playback.filterKeys { it in setOf(2, 7, 255) })
    if (has("walrus_hold")) {
        val options = mapOf(off, 3 to "hw_off_anc", 5 to "hw_off_anc_aware", 6 to "hw_anc_aware", 9 to "hw_off_aware")
        gesture("hw_hold", HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, 2, options, 3, needsMask = true)
    }
    if (has("hold")) gesture("hw_hold_left", HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, 1, hold)
    if (has("hold_right")) gesture("hw_hold_right", HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, 2, hold)
    if (has("hold_call")) gesture("hw_hold_call", HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, 4, call)
    if (has("hold_reject")) gesture("hw_hold_call", HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, 4, mapOf(off, 0 to "hw_reject_call"))
    if (has("hold") && "two_modes" !in features && has("anc")) gesture("hw_cycle_left", HuaweiCommand.HOLD_CYCLE_READ, HuaweiCommand.HOLD_CYCLE_SET, 1, cycle)
    if (has("hold_right") && "two_modes" !in features && has("anc")) gesture("hw_cycle_right", HuaweiCommand.HOLD_CYCLE_READ, HuaweiCommand.HOLD_CYCLE_SET, 2, cycle)
    if (has("single_hold")) {
        val options = mapOf(off, 3 to "hw_off_anc", 5 to "hw_off_anc_aware", 6 to "hw_anc_aware", 9 to "hw_off_aware")
        add(HuaweiControl("hw_hold", SettingTab.CONTROLS, HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, tag = 2, writeTags = listOf(1),
            choices = options, fallbackTag = 1))
    }
    if (has("power_tap")) gesture("hw_power_tap", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, 1, mapOf(off, 12 to "hw_switch_device"), 3)
    if (has("swipe")) gesture("hw_swipe", HuaweiCommand.SWIPE_READ, HuaweiCommand.SWIPE_SET, 1, mapOf(off, 0 to "hw_change_volume"), both = true)
    if (has("wear")) add(HuaweiControl("hw_auto_pause", SettingTab.DEVICE, HuaweiCommand.WEAR_READ, HuaweiCommand.WEAR_SET))
    if (has("latency")) add(HuaweiControl("hw_low_latency", SettingTab.SOUND, HuaweiCommand.LOW_LATENCY, HuaweiCommand.LOW_LATENCY, tag = 2, writeTags = listOf(1)))
    if (has("eyewear")) {
        val eyewearTap = mapOf(off, 0 to "hw_assistant", 1 to "hw_play_pause")
        gesture("hw_glasses_double_left", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, 1, eyewearTap)
        gesture("hw_glasses_double_right", HuaweiCommand.DOUBLE_TAP_READ, HuaweiCommand.DOUBLE_TAP_SET, 2, eyewearTap)
        add(HuaweiControl("hw_greeting", SettingTab.DEVICE, HuaweiCommand.GREETING_READ, HuaweiCommand.GREETING_SET))
        add(HuaweiControl("hw_glasses_wear", SettingTab.DEVICE, HuaweiCommand.WEAR_READ, HuaweiCommand.WEAR_SET))
    }
    if (has("fiji")) {
        val eyewearSwipe = mapOf(off, 0 to "hw_change_volume", 1 to "hw_change_track")
        gesture("hw_swipe_left", HuaweiCommand.SWIPE_READ, HuaweiCommand.SWIPE_SET, 1, eyewearSwipe)
        gesture("hw_swipe_right", HuaweiCommand.SWIPE_READ, HuaweiCommand.SWIPE_SET, 2, eyewearSwipe)
        gesture("hw_glasses_press", HuaweiCommand.SHORT_PRESS_READ, HuaweiCommand.SHORT_PRESS_SET, 1,
            mapOf(off, 0 to "hw_face_translate", 1 to "hw_voice_memo", 2 to "hw_pair_glasses"))
        gesture("hw_glasses_hold", HuaweiCommand.HOLD_READ, HuaweiCommand.HOLD_SET, 1, mapOf(off, 9 to "hw_reject_call"))
        add(HuaweiControl("hw_saving", SettingTab.DEVICE, HuaweiCommand.SAVING_READ, HuaweiCommand.SAVING_SET))
        add(HuaweiControl("hw_adaptive_volume", SettingTab.SOUND, HuaweiCommand.SMART_VOLUME_READ, HuaweiCommand.SMART_VOLUME_SET))
    }
    if (has("studio_touch")) add(HuaweiControl("hw_touch_enabled", SettingTab.CONTROLS, HuaweiCommand.TOUCH_ENABLE_READ, HuaweiCommand.TOUCH_ENABLE_SET))
    if (has("quality")) add(HuaweiControl("hw_quality", SettingTab.SOUND, HuaweiCommand.QUALITY_READ, HuaweiCommand.QUALITY_SET, tag = 2,
        writeTags = listOf(1), capabilityTag = 1, readTags = listOf(1)))
    if (has("rest")) add(HuaweiControl("hw_rest", SettingTab.DEVICE, HuaweiCommand.REST_READ, HuaweiCommand.REST_SET))
    if (has("hd_call")) add(HuaweiControl("hw_hd_call", SettingTab.SOUND, HuaweiCommand.HD_CALL_READ, HuaweiCommand.HD_CALL_SET))
    if (has("pickup")) add(HuaweiControl("hw_pickup", SettingTab.SOUND, HuaweiCommand.PICKUP_READ, HuaweiCommand.PICKUP_SET,
        choices = mapOf(1 to "hw_pickup_voices", 0 to "hw_pickup_surroundings")))
    if (has("case_sound")) add(HuaweiControl("hw_case_sound", SettingTab.DEVICE, HuaweiCommand.CASE_SOUND, HuaweiCommand.CASE_SOUND,
        tag = 2, writeTags = listOf(1), readTags = listOf(2, 3)))
    if (has("noisy_volume")) add(HuaweiControl("hw_noisy_volume", SettingTab.SOUND, HuaweiCommand.NOISY_VOLUME_READ, HuaweiCommand.NOISY_VOLUME_SET))
    if (has("multipoint")) add(HuaweiControl("multipoint", SettingTab.DEVICE, HuaweiCommand.MULTIPOINT_READ, HuaweiCommand.MULTIPOINT_SET))
}

/** A setting behind the one keyed command: a switch, or a choice when [choices] holds its values. */
internal enum class HuaweiFeature(val feature: String, val row: String, val code: Int, val choices: Map<Int, String> = emptyMap()) {
    ADAPTIVE_VOLUME("adaptive_volume", "hw_adaptive_volume", 2),
    SINGLE_ANC("single_anc", "hw_single_anc", 5),
    DROP("drop", "hw_drop", 7),
    EAR_TIP("ear_tip", "hw_ear_tip", 8, mapOf(1 to "hw_tips_silicone", 2 to "hw_tips_foam")),
    HEAD("head", "hw_head_control", 11),
    VOICE_CONTROL("voice_control", "hw_voice_control", 3),
    SPATIAL("spatial", "hw_spatial", 24, mapOf(0 to "hw_spatial_off", 2 to "hw_spatial_fixed", 1 to "hw_spatial_tracking")),
    WIND("wind", "hw_wind_noise", 29),
    CONVERSATION("conversation", "hw_conversation", 27);

    fun read(): HuaweiPacket = huaweiPacket(OP, buildList {
        add(HuaweiTlv(1, byteArrayOf(code.toByte())))
        if (this@HuaweiFeature != HEAD) add(HuaweiTlv(2))
        // Spatial audio carries its sound scene beside the mode.
        if (this@HuaweiFeature == SPATIAL) add(HuaweiTlv(3))
    })

    fun write(on: Boolean): HuaweiPacket = write(if (on) 1 else 0)
    fun write(value: Int): HuaweiPacket = huaweiWrite(OP, 1 to code, 2 to value)
    fun accepts(value: Int?): Boolean = if (choices.isEmpty()) value == 0 || value == 1 else value in choices

    companion object {
        const val OP = HuaweiCommand.KEYED_SETTING
    }
}
