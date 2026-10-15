package io.github.omegadestroyer.pairfree.protocol.samsung

/**
 * The earbuds' full state ([SamsungMessage.EXTENDED_STATUS_UPDATED]), field by field as
 * GalaxyBudsClient's ExtendedStatusUpdateDecoder reads it (github.com/timschneeb/GalaxyBudsClient,
 * Tim Schneeberger, GPL-3.0). The layout depends on the model and on the state's revision
 * (docs/samsung/PROTOCOL.md section 5). Fields keep GalaxyBudsClient's names in snake case, so
 * its tests can be checked one for one (data/samsung/test_vectors.json); a field the model or the
 * revision does not send is missing. A reply shorter than its layout keeps what it holds.
 */
public class SamsungStatus private constructor(public val model: SamsungModel, public val values: Map<String, Any>) {
    public val revision: Int get() = int(REVISION) ?: 0

    public fun int(name: String): Int? = values[name] as? Int

    public fun flag(name: String): Boolean? = values[name] as? Boolean

    /** This state with [name] set to [value], for showing a change before the earbuds report it. */
    public fun with(name: String, value: Any): SamsungStatus = SamsungStatus(model, values + (name to value))

    public fun with(changes: Map<String, Any>): SamsungStatus = SamsungStatus(model, values + changes)

    public companion object {
        public const val REVISION: String = "revision"

        /** Placement of an earbud, as the state gives it; [CHARGING] is GalaxyBudsClient's own value for one charging. */
        public const val PLACEMENT_WEARING: Int = 1
        public const val PLACEMENT_IDLE: Int = 2
        public const val PLACEMENT_CASE: Int = 3
        public const val CHARGING: Int = 100

        private val KNOWN_COLORS: Set<Int> by lazy { SAMSUNG_MODELS.flatMap { it.colors.keys }.toSet() }

        public fun parse(model: SamsungModel, payload: ByteArray): SamsungStatus? {
            if (payload.size < 6) return null
            val f = LinkedHashMap<String, Any>()
            try {
                read(model, payload, f)
            } catch (_: IndexOutOfBoundsException) {
                // A shorter reply than the layout: keep what was read, as GalaxyBudsClient's guarded reads do.
            }
            // An earbud charging shows as charging rather than in the case, as in GalaxyBudsClient.
            if (model.supports("charging_state", f[REVISION] as Int)) {
                if (f["is_left_charging"] == true) f["placement_l"] = CHARGING
                if (f["is_right_charging"] == true) f["placement_r"] = CHARGING
            }
            return SamsungStatus(model, f)
        }

        private fun read(model: SamsungModel, p: ByteArray, f: MutableMap<String, Any>) {
            fun u8(i: Int): Int = p[i].toInt() and 0xFF
            fun bool(i: Int): Boolean = u8(i) != 0
            fun bit(i: Int, b: Int): Boolean = u8(i) and (1 shl b) != 0
            fun color(i: Int): Int = (u8(i) or (u8(i + 1) shl 8)).let { if (it in KNOWN_COLORS) it else 0 }
            fun charging(b: Int) {
                f["is_left_charging"] = b and 0x10 != 0
                f["is_right_charging"] = b and 0x04 != 0
                f["is_case_charging"] = b and 0x01 != 0
            }

            val revision = u8(0)
            val device = model.device
            fun supports(feature: String) = model.supports(feature, revision)
            f[REVISION] = revision
            f["ear_type"] = u8(1)
            f["battery_l"] = u8(2)
            f["battery_r"] = u8(3)
            f["is_coupled"] = bool(4)
            f["main_connection"] = u8(5)

            if (device == "Buds") {
                val wear = u8(6)
                f["wear_state"] = wear
                f["ambient_sound_enabled"] = bool(7)
                f["ambient_sound_mode"] = u8(8)
                f["ambient_sound_volume"] = u8(9)
                f["equalizer_enabled"] = bool(10)
                f["equalizer_mode"] = u8(11)
                // GalaxyBudsClient tells the two layouts apart by the length: id, payload and CRC over 13.
                if (p.size + 3 > 13) {
                    f["touchpad_lock"] = bool(12)
                    f["touchpad_option_l"] = (u8(13) and 0xF0) shr 4
                    f["touchpad_option_r"] = u8(13) and 0x0F
                    if (revision >= 3) f["seamless_connection_enabled"] = u8(14) == 0
                } else {
                    f["touchpad_lock"] = (u8(12) and 0xF0) shr 4 != 0
                    f["touchpad_option_l"] = u8(12) and 0x0F
                    f["touchpad_option_r"] = u8(12) and 0x0F
                    if (revision >= 3) f["seamless_connection_enabled"] = u8(13) == 0
                }
                // The legacy wear state stands in for each earbud's placement.
                f["placement_l"] = if (wear == WEAR_BOTH || wear == WEAR_LEFT) PLACEMENT_WEARING else PLACEMENT_IDLE
                f["placement_r"] = if (wear == WEAR_BOTH || wear == WEAR_RIGHT) PLACEMENT_WEARING else PLACEMENT_IDLE
                return
            }

            val placementL = (u8(6) and 0xF0) shr 4
            val placementR = u8(6) and 0x0F
            f["placement_l"] = placementL
            f["placement_r"] = placementR
            f["wear_state"] = when {
                placementL == PLACEMENT_WEARING && placementR == PLACEMENT_WEARING -> WEAR_BOTH
                placementL == PLACEMENT_WEARING -> WEAR_LEFT
                placementR == PLACEMENT_WEARING -> WEAR_RIGHT
                else -> 0
            }
            f["battery_case"] = u8(7)

            if (device == "BudsPlus") {
                f["ambient_sound_enabled"] = bool(8)
                f["ambient_sound_volume"] = u8(9)
                f["adjust_sound_sync"] = u8(10) == 1
                f["equalizer_mode"] = u8(11)
                f["touchpad_lock"] = bool(12)
                f["touchpad_option_l"] = (u8(13) and 0xF0) shr 4
                f["touchpad_option_r"] = u8(13) and 0x0F
                f["outside_double_tap"] = u8(14) == 1
                f["color_l"] = color(15)
                f["color_r"] = color(17)
                if (revision >= 8) f["side_tone_enabled"] = u8(19) == 1
                if (revision >= 9) f["extra_high_ambient_enabled"] = u8(20) == 1
                if (revision >= 11) f["seamless_connection_enabled"] = u8(21) == 0
                if (revision >= 12) f["fmm_revision"] = u8(22)
                if (revision >= 13) f["call_path_control"] = u8(23) == 0
                return
            }

            // The Galaxy Buds Live and later.
            f["adjust_sound_sync"] = u8(8) == 1
            f["equalizer_mode"] = u8(9)
            if (!supports("advanced_touch_lock")) {
                f["touchpad_lock"] = u8(10) == 1
            } else {
                f["touch_hold_on"] = bit(10, 0)
                f["triple_tap_on"] = bit(10, 1)
                f["double_tap_on"] = bit(10, 2)
                f["single_tap_on"] = bit(10, 3)
                f["touchpad_lock"] = !bit(10, 7)
                if (supports("advanced_touch_lock_for_calls")) {
                    f["touch_hold_on_for_call_on"] = bit(10, 5)
                    f["double_tap_for_call_on"] = bit(10, 4)
                }
                if (supports("advanced_touch_lock_swipe") || model.buds4Layout) f["swipe_on"] = bit(10, 6)
            }
            f["touchpad_option_l"] = (u8(11) and 0xF0) shr 4
            f["touchpad_option_r"] = u8(11) and 0x0F
            if (device == "BudsLive") f["noise_cancelling"] = u8(12) == 1 else f["noise_control_mode"] = u8(12)
            f["voice_wake_up"] = u8(13) == 1
            f["color_l"] = color(14)
            f["color_r"] = color(16)
            f["voice_wake_up_lang"] = u8(18)
            // Samsung's plugin reads 1 as on for the Buds4 family; GalaxyBudsClient 0 as on for the others.
            if (supports("seamless_connection")) f["seamless_connection_enabled"] = if (model.buds4Layout) u8(19) == 1 else u8(19) == 0
            if (supports("smart_things_find")) f["fmm_revision"] = u8(20)

            if (device == "BudsLive") {
                if (revision >= 5) f["relieve_ambient"] = u8(21) == 1
                if (revision >= 7) f["hearing_enhancements"] = u8(22)
                if (revision >= 8) f["call_path_control"] = u8(23) == 0
                if (revision >= 9) f["spatial_audio"] = u8(24) == 1
                return
            }

            // The Galaxy Buds Pro and later: which modes touch and hold switches between.
            f[NOISE_CYCLE] = u8(21)
            if (newCycleLayout(model)) {
                f["noise_control_touch_ambient"] = bit(21, 0)
                f["noise_control_touch_adaptive"] = bit(21, 1)
                f["noise_control_touch_off"] = bit(21, 2)
                f["noise_control_touch_anc"] = bit(21, 3)
                f["noise_control_touch_left_ambient"] = bit(21, 4)
                f["noise_control_touch_left_adaptive"] = bit(21, 5)
                f["noise_control_touch_left_off"] = bit(21, 6)
                f["noise_control_touch_left_anc"] = bit(21, 7)
            } else {
                f["noise_control_touch_off"] = bit(21, 0)
                f["noise_control_touch_ambient"] = bit(21, 1)
                f["noise_control_touch_anc"] = bit(21, 2)
                if (supports("noise_control_mode_dual_side")) {
                    f["noise_control_touch_left_off"] = bit(21, 4)
                    f["noise_control_touch_left_ambient"] = bit(21, 5)
                    f["noise_control_touch_left_anc"] = bit(21, 6)
                }
            }
            if (device == "BudsPro" && revision < 3) f["extra_high_ambient_enabled"] = u8(22) == 1 else f["speak_seamlessly"] = u8(22) == 1
            f["ambient_sound_volume"] = u8(23)
            f["noise_reduction_level"] = u8(24)

            when (device) {
                "BudsPro" -> {
                    f["auto_switch_audio_output"] = u8(25) == 1
                    f["detect_conversations"] = u8(26) == 1
                    f["detect_conversations_duration"] = u8(27).let { if (it > 2) 1 else it }
                    if (revision > 1) f["spatial_audio"] = u8(28) == 1
                    if (revision >= 5) f["hearing_enhancements"] = u8(29)
                    if (revision >= 6) f["extra_high_ambient_enabled"] = u8(30) == 1
                    if (revision >= 7) f["outside_double_tap"] = u8(31) == 1
                    if (revision >= 8) {
                        f["noise_controls_with_one_earbud"] = u8(32) == 1
                        f["ambient_custom_volume_on"] = u8(33) == 1
                        f["ambient_custom_volume_left"] = (u8(34) and 0xF0) shr 4
                        f["ambient_custom_volume_right"] = u8(34) and 0x0F
                        f["ambient_custom_sound_tone"] = u8(35)
                    }
                    if (revision >= 9) f["side_tone_enabled"] = u8(36) == 1
                    if (revision >= 10) f["call_path_control"] = u8(37) == 0
                }
                "Buds2" -> {
                    f["hearing_enhancements"] = u8(25)
                    f["extra_high_ambient_enabled"] = u8(26) == 1
                    if (revision >= 2) f["touch_type"] = u8(27)
                    if (revision >= 3) f["noise_controls_with_one_earbud"] = u8(28) == 1
                    if (revision >= 5) {
                        f["ambient_custom_volume_on"] = u8(29) == 1
                        f["ambient_custom_volume_left"] = (u8(30) and 0xF0) shr 4
                        f["ambient_custom_volume_right"] = u8(30) and 0x0F
                        f["ambient_custom_sound_tone"] = u8(31)
                        f["outside_double_tap"] = u8(32) == 1
                    }
                    if (revision >= 6) f["side_tone_enabled"] = u8(33) == 1
                    if (revision >= 7) f["call_path_control"] = u8(34) == 0
                    if (revision >= 8) f["spatial_audio"] = u8(35) == 1
                    if (revision >= 10) charging(u8(36))
                }
                else -> readLater(model, p, f, revision)
            }
        }

        /** The Galaxy Buds2 Pro and later share the bytes up to 33, then read on in order. */
        private fun readLater(model: SamsungModel, p: ByteArray, f: MutableMap<String, Any>, revision: Int) {
            fun u8(i: Int): Int = p[i].toInt() and 0xFF
            f["hearing_enhancements"] = u8(25)
            f["detect_conversations"] = u8(26) == 1
            f["detect_conversations_duration"] = u8(27).let { if (it > 2) 1 else it }
            f["noise_controls_with_one_earbud"] = u8(28) == 1
            f["ambient_custom_volume_on"] = u8(29) == 1
            f["ambient_custom_volume_left"] = (u8(30) and 0xF0) shr 4
            f["ambient_custom_volume_right"] = u8(30) and 0x0F
            f["ambient_custom_sound_tone"] = u8(31)
            f["outside_double_tap"] = u8(32) == 1
            f["side_tone_enabled"] = u8(33) == 1

            var at = 34
            fun next(): Int = u8(at++)
            fun more(): Boolean = at < p.size
            fun charging(b: Int) {
                f["is_left_charging"] = b and 0x10 != 0
                f["is_right_charging"] = b and 0x04 != 0
                f["is_case_charging"] = b and 0x01 != 0
            }

            if (model.buds4Layout) return readBuds4(model, p, f, revision)

            if (model.device == "Buds2Pro") {
                if (revision >= 1) {
                    f["call_path_control"] = next() == 0
                    f["spatial_audio"] = next() != 0
                    f["customize_conversation_boost"] = next() != 0
                    f["customize_noise_reduction_level"] = next()
                    f["neck_stretch_calibration"] = next() != 0
                    f["bixby_keyword"] = next()
                    f["hearing_test_value"] = next()
                }
                f["auto_adjust_sound"] = if (revision >= 3) next() != 0 else (f["hearing_test_value"] as? Int)?.let { it != 0 && it != 1 } ?: false
                if (revision >= 8) f["spatial_audio_head_tracking"] = next() != 0
                if (revision >= 11) charging(next())
                if (revision >= 13) {
                    f["extra_clear_call_sound"] = next() != 0
                    if (more()) f["extra_high_ambient_enabled"] = next() != 0
                }
                return
            }

            // The Galaxy Buds FE and later.
            f["call_path_control"] = next() == 0
            f["spatial_audio"] = next() != 0
            f["customize_conversation_boost"] = next() != 0
            f["customize_noise_reduction_level"] = next()
            f["neck_stretch_calibration"] = next() != 0
            f["bixby_keyword"] = next()
            val hearing = next()
            f["hearing_test_value"] = hearing
            f["auto_adjust_sound"] = hearing != 0 && hearing != 1
            if (model.device in setOf("BudsFe", "BudsCore", "Buds3Fe")) {
                // GalaxyBudsClient reads the Buds Core and Buds3 FE as the Buds FE, as the likeliest layout.
                next() // amplify ambient sound, unused
                f["spatial_audio_head_tracking"] = next() != 0
                charging(next())
                return
            }
            // The Galaxy Buds3 and later.
            f["spatial_audio_head_tracking"] = next() != 0
            charging(next())
            f["extra_clear_call_sound"] = next() != 0
            if (more()) f["extra_high_ambient_enabled"] = next() != 0
            if (more()) f["auto_pause_resume"] = next() != 0
            f["hot_command_enabled"] = next() != 0
            f["hot_command_language"] = next()
            f["peq_mod"] = next()
            f["adaptive_volume_enabled"] = next() != 0
            if (model.device == "Buds3") {
                f["adapt_sound_enabled"] = next() != 0
            } else {
                f["lighting_control"] = next()
                f["double_pinch_and_hold_advanced"] = next()
                f["siren_detect"] = next() != 0
                f["adapt_sound_enabled"] = next() != 0
            }
            if (model.supports("hot_command_language_update", revision)) f["hot_command_version"] = "${next()}.${next()}.${next()}"
        }

        /**
         * The rest of the state as Samsung's own plugin reads it for the Buds4 family and the models only it knows
         * (docs/samsung/PROTOCOL.md section 12), from byte 34. It differs from GalaxyBudsClient's from byte 40: an auto
         * adjust sound byte at 41 and no lighting byte, so head tracking, charging and the settings after them sit one
         * byte later. Each model then adds its own fields.
         */
        private fun readBuds4(model: SamsungModel, p: ByteArray, f: MutableMap<String, Any>, revision: Int) {
            fun u8(i: Int): Int = p[i].toInt() and 0xFF
            fun bool(i: Int): Boolean = u8(i) == 1
            f["call_path_control"] = u8(34) == 0
            f["spatial_audio"] = bool(35)
            f["customize_conversation_boost"] = bool(36)
            f["customize_noise_reduction_level"] = u8(37)
            f["neck_stretch_calibration"] = bool(38)
            f["bixby_keyword"] = u8(39)
            f["noise_reduction_amplify"] = bool(40)
            f["auto_adjust_sound"] = bool(41)
            f["spatial_audio_head_tracking"] = bool(42)
            val charging = u8(43)
            f["is_left_charging"] = charging and 0x10 != 0
            f["is_right_charging"] = charging and 0x04 != 0
            f["is_case_charging"] = charging and 0x01 != 0
            f["extra_clear_call_sound"] = bool(44)
            f["extra_high_ambient_enabled"] = bool(45)
            f["auto_pause_resume"] = bool(46)
            f["hot_command_enabled"] = bool(47)
            f["hot_command_language"] = u8(48)
            f["adjust_sound_fit"] = u8(49)
            f["adaptive_volume_enabled"] = bool(50)
            f["double_pinch_and_hold_advanced"] = u8(51)
            f["siren_detect"] = bool(52)
            f["adapt_sound_enabled"] = bool(53)
            f["hot_command_version"] = "${u8(54)}.${u8(55)}.${u8(56)}"
            f["voice_boost"] = bool(57)
            f["volume_normalization"] = bool(58)
            f["digital_assistant"] = u8(59)
            var at = 60
            fun more(): Boolean = at < p.size
            when (model.device) {
                "BudsOn" -> {
                    if (more()) f["gesture_on"] = u8(at++) == 3
                    if (more()) f["gesture_mode"] = u8(at++)
                    if (more()) f["auto_switch_left_right"] = u8(at++) == 1
                }
                "Buds4Pro" -> {
                    f["loud_noise_reduction"] = bool(at++)
                    f["adaptive_response_level"] = u8(at++)
                    if (revision >= 5 && more()) f["noise_monitor"] = u8(at++) == 1
                    if (revision >= 5 && more()) {
                        f["wdrc_call"] = u8(at++) == 1
                        f["wdrc_music"] = u8(at++) == 1
                    }
                }
                "H1", "T1" -> if (more()) f["auto_power_off_timer"] = u8(at++)
            }
        }

        /** The raw byte that says which modes touch and hold switches between (docs/samsung/PROTOCOL.md section 7). */
        public const val NOISE_CYCLE: String = "noise_cycle"

        /**
         * The Galaxy Buds3 and later write that byte in a newer layout, with adaptive among the
         * modes. GalaxyBudsClient writes it so for them, and its parser has the matching branch,
         * but gates the branch on a feature no model lists; PairFree reads it as it is written.
         * Samsung's own plugin reads and writes it so for the Buds4 family.
         */
        public fun newCycleLayout(model: SamsungModel): Boolean = model.atLeast("Buds3")

        private const val WEAR_RIGHT = 1
        private const val WEAR_LEFT = 16
        private const val WEAR_BOTH = 17
    }
}
