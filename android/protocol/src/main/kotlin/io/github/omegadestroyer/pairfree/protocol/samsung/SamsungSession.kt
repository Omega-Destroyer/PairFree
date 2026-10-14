package io.github.omegadestroyer.pairfree.protocol.samsung

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.FitResult
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to Galaxy Buds, shown on PairFree's screens. The earbuds send their whole state
 * on connecting and after changes ([SamsungStatus]); the battery, the noise mode (on models with
 * noise controls), the firmware and the fit test go to [state] for the shared screens, and every
 * other setting the model has goes to [groups] as generic rows. Each change becomes the message
 * GalaxyBudsClient sends for it (github.com/timschneeb/GalaxyBudsClient, GPL-3.0) and shows at
 * once; the earbuds' acknowledgement or next state confirms it.
 *
 * [phoneIsSamsung] and [androidSdk] go to the earbuds in the manager info, as Samsung's app sends them.
 */
public class SamsungSession(
    public val model: SamsungModel,
    private val phoneIsSamsung: Boolean = false,
    private val androidSdk: Int = 34,
) {
    /** What the shared screens show. */
    public var state: EarbudState = EarbudState(fitTest = model.supports("gear_fit_test", null))
        private set

    /** The last full state, with the changes since applied. */
    public var status: SamsungStatus? = null
        private set

    private var firmware: String? = null
    private var finding = false
    private var mutedLeft = false
    private var mutedRight = false
    private val outbox = mutableListOf<SamsungPacket>()

    /** Packets the session wants sent: the manager info and the version request after the first state. */
    public fun takeOutbox(): List<SamsungPacket> = outbox.toList().also { outbox.clear() }

    /** Applies one packet from the earbuds. Returns true when [state] or [groups] changed. */
    public fun receive(packet: SamsungPacket): Boolean {
        val p = packet.payload
        val before = status to state
        when (packet.id) {
            SamsungMessage.EXTENDED_STATUS_UPDATED -> {
                val first = status == null
                status = SamsungStatus.parse(model, p) ?: return false
                if (first) {
                    outbox += SamsungPacket(SamsungMessage.MANAGER_INFO, byteArrayOf(1, if (phoneIsSamsung) 1 else 2, androidSdk.toByte()))
                    // The Buds3 and later do not answer the version request (GalaxyBudsClient).
                    if (!model.atLeast("Buds3")) outbox += SamsungPacket(SamsungMessage.VERSION_INFO, ByteArray(0))
                }
            }
            SamsungMessage.STATUS_UPDATED -> status = status?.with(basicStatus(p) ?: return false)
            SamsungMessage.NOISE_CONTROLS_UPDATE -> if (p.isNotEmpty()) update("noise_control_mode", p.u8(0))
            SamsungMessage.AMBIENT_MODE_UPDATED -> if (p.isNotEmpty()) update("ambient_sound_enabled", p.u8(0) == 1)
            SamsungMessage.NOISE_REDUCTION_MODE_UPDATE -> if (p.isNotEmpty()) update("noise_cancelling", p.u8(0) == 1)
            SamsungMessage.AMBIENT_VOLUME -> if (p.isNotEmpty()) update("ambient_sound_volume", p.u8(0))
            SamsungMessage.TOUCH_UPDATED -> if (p.isNotEmpty()) update("touchpad_lock", p.u8(0) != 0)
            SamsungMessage.UNIVERSAL_ACK -> if (p.size >= 2) acknowledged(p.u8(0), p.u8(1))
            SamsungMessage.VERSION_INFO -> if (p.size >= 8) firmware = version(p, 2)
            SamsungMessage.CHECK_THE_FIT_OF_EARBUDS_RESULT -> if (p.size >= 2) state = state.copy(fit = FitResult(fit(p.u8(0)), fit(p.u8(1))))
            SamsungMessage.MUTE_EARBUD_STATUS_UPDATED -> if (p.size >= 2) {
                mutedLeft = p.u8(0) != 0
                mutedRight = p.u8(1) != 0
            }
            else -> return false
        }
        refresh()
        return (status to state) != before || packet.id == SamsungMessage.MUTE_EARBUD_STATUS_UPDATED
    }

    private fun update(name: String, value: Any) {
        status = status?.with(name, value)
    }

    /** What an acknowledgement confirms: the message it answers and the value set. */
    private fun acknowledged(id: Int, v: Int) {
        val field = ACKS[id] ?: return
        if (model.legacy && id == SamsungMessage.EQUALIZER) return
        update(field.first, field.second(v))
    }

    /** The battery update: batteries, where each earbud is, and on newer models whether each charges. */
    private fun basicStatus(p: ByteArray): Map<String, Any>? {
        if (model.legacy) {
            if (p.size < 6) return null
            return mapOf("battery_l" to p.u8(1), "battery_r" to p.u8(2), "is_coupled" to (p.u8(3) != 0))
        }
        if (p.size < 7) return null
        val out = mutableMapOf<String, Any>(
            "battery_l" to p.u8(1),
            "battery_r" to p.u8(2),
            "is_coupled" to (p.u8(3) != 0),
            "placement_l" to ((p.u8(5) and 0xF0) shr 4),
            "placement_r" to (p.u8(5) and 0x0F),
            "battery_case" to p.u8(6),
        )
        val revision = status?.revision
        if (model.supports("charging_state", revision) && p.size >= 8) {
            val c = p.u8(7)
            out["is_left_charging"] = c and 0x10 != 0
            out["is_right_charging"] = c and 0x04 != 0
            out["is_case_charging"] = c and 0x01 != 0
            if (c and 0x10 != 0) out["placement_l"] = SamsungStatus.CHARGING
            if (c and 0x04 != 0) out["placement_r"] = SamsungStatus.CHARGING
        }
        return out
    }

    /** Puts the battery, the noise mode and the firmware where the shared screens read them. */
    private fun refresh() {
        val s = status ?: return
        val values = buildMap {
            put(Command.FIRMWARE_VERSION, Value.Text("${model.model}_${firmware ?: "unknown"}"))
            put(Command.BATTERY_LEFT, Value.Number(battery(s, "battery_l", "placement_l")))
            put(Command.BATTERY_RIGHT, Value.Number(battery(s, "battery_r", "placement_r")))
            if (model.supports("case_battery", s.revision)) put(Command.BATTERY_CASE, Value.Number(s.int("battery_case")?.takeIf { it in 0..100 }))
            if (supports(s, "noise_control")) put(Command.ANC_MODE, Value.Number(s.int("noise_control_mode")?.let(::ancNumberOf)))
        }
        state = state.copy(values = state.values + values)
    }

    /** An earbud's battery in percent; unknown while it is not connected. */
    private fun battery(s: SamsungStatus, field: String, placement: String): Int? {
        val level = s.int(field)?.takeIf { it in 0..100 } ?: return null
        return if (!model.legacy && s.int(placement) == PLACEMENT_DISCONNECTED) null else level
    }

    private fun supports(s: SamsungStatus, feature: String) = model.supports(feature, s.revision)

    /** Every setting the model has beyond the shared screens, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() {
            val s = status ?: return emptyList()
            return listOfNotNull(
                noiseGroup(s),
                group("sound", SettingTab.SOUND, listOfNotNull(level(s, "balance", "hearing_enhancements", 0, BALANCE_MAX, "stereo_pan"))),
                touchGroup(s),
                ambientCustomGroup(s),
                group(
                    "calls", SettingTab.DEVICE,
                    listOfNotNull(
                        toggle(s, "extra_clear_call", "extra_clear_call_sound", "extra_clear_call_sound"),
                        toggle(s, "sidetone", "side_tone_enabled", "ambient_sidetone"),
                        toggle(s, "call_path", "call_path_control", "call_path_control"),
                    ),
                ),
                group(
                    "connection", SettingTab.DEVICE,
                    listOfNotNull(
                        toggle(s, "seamless_connection", "seamless_connection_enabled", "seamless_connection"),
                        toggle(s, "voice_wakeup", "voice_wake_up", "bixby_wakeup"),
                    ),
                ),
                group(
                    "find", SettingTab.DEVICE,
                    buildList {
                        add(Setting.Action("find", active = finding))
                        if (finding) {
                            add(Setting.Toggle("find_mute_left", mutedLeft))
                            add(Setting.Toggle("find_mute_right", mutedRight))
                        }
                    },
                ),
            )
        }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    private fun toggle(s: SamsungStatus, key: String, field: String, feature: String): Setting.Toggle? =
        if (supports(s, feature)) s.flag(field)?.let { Setting.Toggle(key, it) } else null

    private fun level(s: SamsungStatus, key: String, field: String, min: Int, max: Int, feature: String): Setting.Level? =
        if (supports(s, feature)) s.int(field)?.let { Setting.Level(key, it.coerceIn(min, max), min, max) } else null

    private fun ambientMax(s: SamsungStatus): Int =
        model.maxAmbientVolume + if (supports(s, "ambient_extra_loud") && s.flag("extra_high_ambient_enabled") == true) 1 else 0

    private fun noiseGroup(s: SamsungStatus): SettingGroup? {
        val rows = buildList {
            if (!supports(s, "noise_control") && supports(s, "ambient_sound")) s.flag("ambient_sound_enabled")?.let { add(Setting.Toggle("ambient_sound", it)) }
            if (!supports(s, "noise_control") && supports(s, "anc")) s.flag("noise_cancelling")?.let { add(Setting.Toggle("anc", it)) }
            if (supports(s, "ambient_passthrough")) s.flag("relieve_ambient")?.let { add(Setting.Toggle("relieve_pressure", it)) }
            if (model.maxAmbientVolume > 0 && supports(s, "ambient_sound")) {
                s.int("ambient_sound_volume")?.let { add(Setting.Level("ambient_volume", it.coerceIn(0, ambientMax(s)), 0, ambientMax(s))) }
            }
            if (supports(s, "ambient_voice_focus")) s.int("ambient_sound_mode")?.let { add(Setting.Toggle("voice_focus", it == 1)) }
            toggle(s, "extra_loud", "extra_high_ambient_enabled", "ambient_extra_loud")?.let(::add)
            if (supports(s, "anc_noise_reduction_levels")) {
                s.int("noise_reduction_level")?.let { add(Setting.Choice("anc_level", if (it == 1) "high" else "low", listOf("low", "high"))) }
            }
            toggle(s, "noise_one_earbud", "noise_controls_with_one_earbud", "noise_controls_with_one_earbud")?.let(::add)
            toggle(s, "voice_detect", "detect_conversations", "detect_conversations")?.let { add(it) }
            if (supports(s, "detect_conversations")) {
                s.int("detect_conversations_duration")?.let { add(Setting.Choice("voice_detect_wait", VOICE_DETECT_WAITS.getOrElse(it) { "10" }, VOICE_DETECT_WAITS)) }
            }
        }
        return group("noise", SettingTab.HOME, rows)
    }

    private fun ambientCustomGroup(s: SamsungStatus): SettingGroup? {
        if (!supports(s, "ambient_customize")) return null
        val on = s.flag("ambient_custom_volume_on") ?: return null
        val sideMax = ambientCustomSideMax(s)
        return group(
            "ambient_customize", SettingTab.DEVICE,
            listOf(
                Setting.Toggle("ambient_custom", on),
                Setting.Level("ambient_custom_left", (s.int("ambient_custom_volume_left") ?: 0).coerceIn(0, sideMax), 0, sideMax),
                Setting.Level("ambient_custom_right", (s.int("ambient_custom_volume_right") ?: 0).coerceIn(0, sideMax), 0, sideMax),
                Setting.Level("ambient_custom_tone", (s.int("ambient_custom_sound_tone") ?: 0).coerceIn(0, TONE_MAX), 0, TONE_MAX),
            ),
        )
    }

    private fun ambientCustomSideMax(s: SamsungStatus): Int =
        if (supports(s, "ambient_customize_legacy") || (supports(s, "ambient_extra_loud") && s.flag("extra_high_ambient_enabled") == true)) 4 else 2

    private fun touchGroup(s: SamsungStatus): SettingGroup? {
        val rows = buildList {
            val locked = s.flag("touchpad_lock")
            if (locked != null) add(Setting.Toggle("touch_controls", !locked))
            if (supports(s, "advanced_touch_lock")) {
                for ((key, field) in TOUCH_GESTURES) {
                    if (field.endsWith("for_call_on") && !supports(s, "advanced_touch_lock_for_calls")) continue
                    s.flag(field)?.let { add(Setting.Toggle(key, it, enabled = locked != true)) }
                }
            }
            val options = model.touchHold.map { it.second }.filter { it != CUSTOM_OPTION }.distinct()
            val left = s.int("touchpad_option_l")?.let(::holdOption)
            val right = s.int("touchpad_option_r")?.let(::holdOption)
            if (left != null || right != null) {
                add(Setting.Choice("hold_left", left, options))
                add(Setting.Choice("hold_right", right, options))
            }
            cycleChoices(s, left, right).forEach(::add)
            toggle(s, "edge_double_tap", "outside_double_tap", "double_tap_volume")?.let(::add)
        }
        return group("touch", SettingTab.CONTROLS, rows)
    }

    private fun holdOption(byte: Int): String? = model.touchHold.firstOrNull { it.first == byte }?.second

    private fun holdByte(option: String, side: Int): Int? =
        model.touchHold.filter { it.second == option }.getOrNull(if (option == CUSTOM_OPTION) side else 0)?.first

    /**
     * Which modes touch and hold switches between, while it is set to noise controls. Offered on
     * models before the Buds3 only: for those, GalaxyBudsClient's tests confirm how the state holds
     * it; for the Buds3 and later its writer is unfinished.
     */
    private fun cycleChoices(s: SamsungStatus, left: String?, right: String?): List<Setting> {
        if (!supports(s, "noise_control") || SamsungStatus.newCycleLayout(model) || s.int(SamsungStatus.NOISE_CYCLE) == null) return emptyList()
        return if (supports(s, "noise_control_mode_dual_side")) {
            listOfNotNull(
                Setting.Choice("hold_cycle_left", cycle(s, left = true), CYCLES).takeIf { left == NOISE_OPTION },
                Setting.Choice("hold_cycle_right", cycle(s, left = false), CYCLES).takeIf { right == NOISE_OPTION },
            )
        } else {
            listOfNotNull(Setting.Choice("hold_cycle", cycle(s, left = false), CYCLES).takeIf { left == NOISE_OPTION || right == NOISE_OPTION })
        }
    }

    private fun cycle(s: SamsungStatus, left: Boolean): String? {
        val side = if (left) "left_" else ""
        val anc = s.flag("noise_control_touch_${side}anc") == true
        val ambient = s.flag("noise_control_touch_${side}ambient") == true
        val off = s.flag("noise_control_touch_${side}off") == true
        return when {
            anc && off -> "anc_off"
            ambient && off -> "ambient_off"
            ambient && anc -> "anc_ambient"
            else -> null
        }
    }

    /**
     * The packets for a change from the screens, or null when the model or the state does not have
     * the setting. The change shows in [groups] and [state] at once.
     */
    public fun change(change: SettingChange): List<SamsungPacket>? {
        val s = status ?: return null
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        fun flag(on: Boolean) = byteArrayOf(if (on) 1 else 0)
        fun send(id: Int, vararg values: Int) = SamsungPacket(id, ByteArray(values.size) { values[it].toByte() })
        val packets: List<SamsungPacket> = when (change) {
            is SettingChange.Toggle -> {
                val on = change.on
                if (setting !is Setting.Toggle || !setting.enabled) return null
                when (change.key) {
                    "ambient_sound" -> listOf(SamsungPacket(SamsungMessage.SET_AMBIENT_MODE, flag(on))).also { update("ambient_sound_enabled", on) }
                    "anc" -> listOf(SamsungPacket(SamsungMessage.SET_NOISE_REDUCTION, flag(on))).also { update("noise_cancelling", on) }
                    "relieve_pressure" -> listOf(SamsungPacket(SamsungMessage.PASS_THROUGH, flag(on))).also { update("relieve_ambient", on) }
                    "voice_focus" -> listOf(SamsungPacket(SamsungMessage.AMBIENT_VOICE_FOCUS, flag(on))).also { update("ambient_sound_mode", if (on) 1 else 0) }
                    "extra_loud" -> extraLoud(s, on)
                    "noise_one_earbud" -> listOf(SamsungPacket(SamsungMessage.SET_ANC_WITH_ONE_EARBUD, flag(on))).also { update("noise_controls_with_one_earbud", on) }
                    "voice_detect" -> listOf(SamsungPacket(SamsungMessage.SET_DETECT_CONVERSATIONS, flag(on))).also { update("detect_conversations", on) }
                    "ambient_custom" -> ambientCustom(s, on = on)
                    "touch_controls" -> touchLock(s, "touchpad_lock", !on)
                    "extra_clear_call" -> listOf(SamsungPacket(SamsungMessage.EXTRA_CLEAR_SOUND_CALL, flag(on))).also { update("extra_clear_call_sound", on) }
                    "sidetone" -> listOf(SamsungPacket(SamsungMessage.SET_SIDETONE, flag(on))).also { update("side_tone_enabled", on) }
                    "call_path" -> listOf(send(SamsungMessage.SET_CALL_PATH_CONTROL, if (on) 0 else 1)).also { update("call_path_control", on) }
                    "seamless_connection" -> listOf(send(SamsungMessage.SET_SEAMLESS_CONNECTION, if (on) 0 else 1)).also { update("seamless_connection_enabled", on) }
                    "voice_wakeup" -> listOf(SamsungPacket(SamsungMessage.SET_VOICE_WAKE_UP, flag(on))).also { update("voice_wake_up", on) }
                    "edge_double_tap" -> listOf(SamsungPacket(SamsungMessage.OUTSIDE_DOUBLE_TAP, flag(on))).also { update("outside_double_tap", on) }
                    "find_mute_left", "find_mute_right" -> {
                        if (change.key == "find_mute_left") mutedLeft = on else mutedRight = on
                        listOf(send(SamsungMessage.MUTE_EARBUD, if (mutedLeft) 1 else 0, if (mutedRight) 1 else 0))
                    }
                    else -> TOUCH_GESTURES[change.key]?.let { touchLock(s, it, on) } ?: return null
                }
            }
            is SettingChange.Level -> {
                if (setting !is Setting.Level) return null
                val v = change.value.coerceIn(setting.min, setting.max)
                when (change.key) {
                    "ambient_volume" -> listOf(send(SamsungMessage.AMBIENT_VOLUME, v)).also { update("ambient_sound_volume", v) }
                    "balance" -> listOf(send(SamsungMessage.SET_HEARING_ENHANCEMENTS, v)).also { update("hearing_enhancements", v) }
                    "ambient_custom_left" -> ambientCustom(s, left = v)
                    "ambient_custom_right" -> ambientCustom(s, right = v)
                    "ambient_custom_tone" -> ambientCustom(s, tone = v)
                    else -> return null
                }
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice || change.option !in setting.options) return null
                when (change.key) {
                    "anc_level" -> (if (change.option == "high") 1 else 0).let { listOf(send(SamsungMessage.NOISE_REDUCTION_LEVEL, it)).also { _ -> update("noise_reduction_level", it) } }
                    "voice_detect_wait" -> VOICE_DETECT_WAITS.indexOf(change.option).let {
                        listOf(send(SamsungMessage.SET_DETECT_CONVERSATIONS_DURATION, it)).also { _ -> update("detect_conversations_duration", it) }
                    }
                    "hold_left", "hold_right" -> holdOptions(s, change.key == "hold_left", change.option) ?: return null
                    "hold_cycle", "hold_cycle_left", "hold_cycle_right" -> cycleChange(s, change.key, change.option) ?: return null
                    else -> return null
                }
            }
            is SettingChange.Press -> when (change.key) {
                "find" -> find(s)
                else -> return null
            }
        }
        refresh()
        return packets
    }

    private fun extraLoud(s: SamsungStatus, on: Boolean): List<SamsungPacket> {
        // As GalaxyBudsClient: turning it on, or a volume already at 3, moves the volume to the new highest step.
        val max = model.maxAmbientVolume + if (on) 1 else 0
        val now = s.int("ambient_sound_volume") ?: 0
        val volume = (if (on || now >= 3) max else now).coerceIn(0, max)
        update("extra_high_ambient_enabled", on)
        update("ambient_sound_volume", volume)
        return listOf(
            SamsungPacket(SamsungMessage.EXTRA_HIGH_AMBIENT, byteArrayOf(if (on) 1 else 0)),
            SamsungPacket(SamsungMessage.AMBIENT_VOLUME, byteArrayOf(volume.toByte())),
        )
    }

    private fun ambientCustom(s: SamsungStatus, on: Boolean? = null, left: Int? = null, right: Int? = null, tone: Int? = null): List<SamsungPacket> {
        val o = on ?: (s.flag("ambient_custom_volume_on") ?: false)
        val l = left ?: s.int("ambient_custom_volume_left") ?: 0
        val r = right ?: s.int("ambient_custom_volume_right") ?: 0
        val t = tone ?: s.int("ambient_custom_sound_tone") ?: 0
        status = s.with(mapOf("ambient_custom_volume_on" to o, "ambient_custom_volume_left" to l, "ambient_custom_volume_right" to r, "ambient_custom_sound_tone" to t))
        return listOf(SamsungPacket(SamsungMessage.CUSTOMIZE_AMBIENT_SOUND, byteArrayOf(if (o) 1 else 0, l.toByte(), r.toByte(), t.toByte())))
    }

    /** The touch lock: one switch on older models; on models with per-gesture locks every switch at once. */
    private fun touchLock(s: SamsungStatus, field: String, value: Boolean): List<SamsungPacket> {
        val next = s.with(field, value)
        status = next
        if (!supports(s, "advanced_touch_lock")) {
            return listOf(SamsungPacket(SamsungMessage.LOCK_TOUCHPAD, byteArrayOf(if (value) 1 else 0)))
        }
        fun b(name: String) = (if (next.flag(name) == true) 1 else 0).toByte()
        val payload = mutableListOf(
            (if (next.flag("touchpad_lock") == true) 0 else 1).toByte(),
            b("single_tap_on"), b("double_tap_on"), b("triple_tap_on"), b("touch_hold_on"),
        )
        if (supports(s, "advanced_touch_lock_for_calls")) payload += listOf(b("double_tap_for_call_on"), b("touch_hold_on_for_call_on"))
        return listOf(SamsungPacket(SamsungMessage.LOCK_TOUCHPAD, payload.toByteArray()))
    }

    private fun holdOptions(s: SamsungStatus, left: Boolean, option: String): List<SamsungPacket>? {
        val l = if (left) holdByte(option, 0) else s.int("touchpad_option_l")
        val r = if (left) s.int("touchpad_option_r") else holdByte(option, 1)
        if (l == null || r == null) return null
        update("touchpad_option_l", l)
        update("touchpad_option_r", r)
        return listOf(SamsungPacket(SamsungMessage.SET_TOUCHPAD_OPTION, byteArrayOf(l.toByte(), r.toByte())))
    }

    /** As GalaxyBudsClient's TouchAndHoldNoiseControls for models before the Buds3: off, ambient, ANC flags per side. */
    private fun cycleChange(s: SamsungStatus, key: String, option: String): List<SamsungPacket>? {
        val dual = supports(s, "noise_control_mode_dual_side")
        val leftNow = cycle(s, left = true)
        val rightNow = cycle(s, left = false)
        val left = if (key == "hold_cycle_left") option else leftNow
        val right = if (key == "hold_cycle_right" || key == "hold_cycle") option else rightNow
        fun bytes(mode: String?): List<Byte>? = when (mode) {
            "anc_off" -> listOf(1, 0, 1)
            "ambient_off" -> listOf(0, 1, 1)
            "anc_ambient" -> listOf(1, 1, 0)
            else -> null
        }?.map { it.toByte() }
        val payload = if (dual) (bytes(left) ?: return null) + (bytes(right) ?: return null) else bytes(right) ?: return null
        fun flags(prefix: String, mode: String) = mapOf(
            "noise_control_touch_${prefix}anc" to (mode != "ambient_off"),
            "noise_control_touch_${prefix}ambient" to (mode != "anc_off"),
            "noise_control_touch_${prefix}off" to (mode != "anc_ambient"),
        )
        status = s.with(flags("", right!!) + if (dual) flags("left_", left!!) else emptyMap())
        return listOf(SamsungPacket(SamsungMessage.SET_TOUCH_AND_HOLD_NOISE_CONTROLS, payload.toByteArray()))
    }

    private fun find(s: SamsungStatus): List<SamsungPacket> {
        finding = !finding
        if (!finding) {
            mutedLeft = false
            mutedRight = false
            return listOf(SamsungPacket(SamsungMessage.FIND_MY_EARBUDS_STOP, ByteArray(0)))
        }
        val start = if (supports(s, "fmg_ring_while_wearing")) SamsungMessage.FIND_MY_EARBUDS_ON_WEARING_START else SamsungMessage.FIND_MY_EARBUDS_START
        return listOf(SamsungPacket(start, ByteArray(0)))
    }

    /** The noise mode from the shared screens, on models with noise controls. */
    public fun set(command: Command, value: Int): List<SamsungPacket>? {
        val s = status ?: return null
        if (command != Command.ANC_MODE || !supports(s, "noise_control")) return null
        val mode = modeOf(value) ?: return null
        update("noise_control_mode", mode)
        refresh()
        state = state.expect(command, value)
        return listOf(SamsungPacket(SamsungMessage.NOISE_CONTROLS, byteArrayOf(mode.toByte())))
    }

    /** Starts the fit test, on models with it; the result arrives in [state]. */
    public fun startFitTest(): List<SamsungPacket>? {
        if (!state.fitTest) return null
        state = state.copy(fit = null)
        return listOf(SamsungPacket(SamsungMessage.CHECK_THE_FIT_OF_EARBUDS, byteArrayOf(1)))
    }

    public fun stopFitTest(): List<SamsungPacket>? =
        if (state.fitTest) listOf(SamsungPacket(SamsungMessage.CHECK_THE_FIT_OF_EARBUDS, byteArrayOf(0))) else null

    /** [packets] as frames for these earbuds; never one of [SamsungMessage.NEVER_SEND]. */
    public fun frames(packets: List<SamsungPacket>): List<ByteArray> =
        packets.filter { it.id !in SamsungMessage.NEVER_SEND }.map { SamsungPacket.build(it.id, it.payload, model.legacy) }

    /** The left earbud's software version, as GalaxyBudsClient spells it out from the version reply. */
    private fun version(p: ByteArray, at: Int): String {
        val variant = if (p.u8(at) == 0) "E" else "U"
        val year = YEARS.getOrNull((p.u8(at + 1) and 0xF0) shr 4) ?: "?"
        val month = MONTHS.getOrNull(p.u8(at + 1) and 0x0F) ?: "?"
        val release = p.u8(at + 2).let { i ->
            if (model.legacy) (if (i <= 15) i.toString(16).uppercase() else RELEASES.getOrNull(i - 16)?.toString() ?: "")
            else RELEASES.getOrNull(i)?.toString() ?: ""
        }
        val prefix = model.model.removePrefix("SM-").removePrefix("R")
        return "R${prefix}XX${variant}0A$year$month$release"
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    public companion object {
        private const val PLACEMENT_DISCONNECTED = 0
        private const val BALANCE_MAX = 32
        private const val TONE_MAX = 4
        private const val NOISE_OPTION = "noise_control"
        private const val CUSTOM_OPTION = "custom"

        private val VOICE_DETECT_WAITS = listOf("5", "10", "15")
        private val CYCLES = listOf("anc_ambient", "anc_off", "ambient_off")
        private const val YEARS = "OPQRSTUVWXYZ"
        private const val MONTHS = "ABCDEFGHIJKL"
        private const val RELEASES = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        /** Per-gesture touch switches: the screens' key and the state's field. */
        private val TOUCH_GESTURES = linkedMapOf(
            "touch_single" to "single_tap_on",
            "touch_double" to "double_tap_on",
            "touch_triple" to "triple_tap_on",
            "touch_hold" to "touch_hold_on",
            "touch_double_call" to "double_tap_for_call_on",
            "touch_hold_call" to "touch_hold_on_for_call_on",
        )

        /** What an acknowledgement confirms, by the message it answers (GalaxyBudsClient's AcknowledgementDecoder). */
        private val ACKS: Map<Int, Pair<String, (Int) -> Any>> = mapOf(
            SamsungMessage.NOISE_CONTROLS to ("noise_control_mode" to { v: Int -> v }),
            SamsungMessage.AMBIENT_VOLUME to ("ambient_sound_volume" to { v: Int -> v }),
            SamsungMessage.NOISE_REDUCTION_LEVEL to ("noise_reduction_level" to { v: Int -> v }),
            SamsungMessage.SET_NOISE_REDUCTION to ("noise_cancelling" to { v: Int -> v == 1 }),
            SamsungMessage.EQUALIZER to ("equalizer_mode" to { v: Int -> v }),
            SamsungMessage.OUTSIDE_DOUBLE_TAP to ("outside_double_tap" to { v: Int -> v == 1 }),
            SamsungMessage.EXTRA_HIGH_AMBIENT to ("extra_high_ambient_enabled" to { v: Int -> v == 1 }),
            SamsungMessage.SET_SIDETONE to ("side_tone_enabled" to { v: Int -> v == 1 }),
            SamsungMessage.SET_ANC_WITH_ONE_EARBUD to ("noise_controls_with_one_earbud" to { v: Int -> v == 1 }),
            SamsungMessage.SET_DETECT_CONVERSATIONS to ("detect_conversations" to { v: Int -> v == 1 }),
            SamsungMessage.SET_DETECT_CONVERSATIONS_DURATION to ("detect_conversations_duration" to { v: Int -> v }),
            SamsungMessage.EXTRA_CLEAR_SOUND_CALL to ("extra_clear_call_sound" to { v: Int -> v == 1 }),
            SamsungMessage.PASS_THROUGH to ("relieve_ambient" to { v: Int -> v == 1 }),
            SamsungMessage.SET_VOICE_WAKE_UP to ("voice_wake_up" to { v: Int -> v == 1 }),
            SamsungMessage.SET_SEAMLESS_CONNECTION to ("seamless_connection_enabled" to { v: Int -> v == 0 }),
            SamsungMessage.SET_CALL_PATH_CONTROL to ("call_path_control" to { v: Int -> v == 0 }),
        )

        private fun fit(v: Int): FitResult.Fit = when (v) {
            1 -> FitResult.Fit.GOOD
            0 -> FitResult.Fit.NOT_FIT
            else -> FitResult.Fit.UNKNOWN
        }

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        /** Samsung's mode (0 off, 1 noise cancelling, 2 ambient sound) as the screens' number; adaptive (3) has none. */
        private fun ancNumberOf(mode: Int): Int? = when (mode) {
            0 -> ancNumber("off")
            1 -> ancNumber("anc")
            2 -> ancNumber("transparency")
            else -> null
        }

        private fun modeOf(value: Int): Int? = when (value) {
            ancNumber("off") -> 0
            ancNumber("anc") -> 1
            ancNumber("transparency") -> 2
            else -> null
        }
    }
}
