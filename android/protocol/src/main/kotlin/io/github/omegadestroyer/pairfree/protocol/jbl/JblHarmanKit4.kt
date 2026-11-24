package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab

/**
 * The settings of [JblHarmanKit] on the protocol 4 models (docs/jbl/PROTOCOL.md section 4.12): the same cards, sent as `DeviceInfoFeature5` records of JBL Headphones 5.37.10's
 * `General4Control`. A record is a feature id (two bytes, low first), its length and its value; the values are `StatusLevelSize1Feature` (one byte: bit 7 the status, bits 0 to 6
 * the level, 127 where the app has none), `Occupy1ByteEachIntFeature` (a byte for each number), `StatusLevelSize2Feature` (two bytes, low first: bit 15 the status) and
 * `CasePageConfigFeature` (one byte: bit 7 whether the page shows, bit 6 whether it can be set, bits 0 to 5 its place). No JBL earbuds have answered them.
 */
internal class JblHarmanKit4(private val model: JblModel, private val clock: () -> JblWallClock?) {
    private fun has(id: String) = id in model.extras

    private val raw = mutableMapOf<Int, ByteArray>()
    private var testingNoise = false
    private var testingSeal = false
    private var permission = 0

    private val displayFeatures: List<Int> get() = buildList {
        if (has(JblHarmanKit.CASE_BRIGHTNESS)) add(CASE_BRIGHTNESS)
        if (has(JblHarmanKit.CASE_NOTIFICATIONS)) { add(CASE_NOTIFICATION); add(CASE_PREVIEW) }
        if (has(JblHarmanKit.CASE_SCREEN_SAVER)) add(CASE_SCREEN_TIME)
        if (has(JblHarmanKit.CASE_LANGUAGE)) { add(CASE_LANGUAGE) }
        if (has(JblHarmanKit.CASE_WAKE)) add(CASE_WAKE)
        if (has(JblHarmanKit.CASE_INFO)) { add(CASE_VERSION); add(CASE_LATEST) }
        if (has(JblHarmanKit.CASE_CLOCK)) add(CASE_24H)
    }

    /** The features read with the model's own. */
    fun features(): List<Int> = buildList {
        if (has(JblHarmanKit.PSAP)) addAll(listOf(PSAP_STATUS, PSAP_BALANCE, PSAP_GAIN, PSAP_NOISE, PSAP_BEAMFORMING))
        if (has(JblHarmanKit.EAR_TIPS)) add(EAR_TIPS)
        addAll(displayFeatures)
        if (has(JblHarmanKit.CASE_PAGES)) addAll(PAGES.keys)
    }

    /** What is sent once the channel is open: the clock (`General4Control.setDeviceTime`: seconds first, then the format, 0 when the phone says none). */
    fun start(): List<JblFrame> = buildList {
        if (has(JblHarmanKit.CASE_CLOCK)) clock()?.let { c ->
            add(JblP4Packet.set(SYNC_TIME to byteArrayOf(c.second.toByte(), c.minute.toByte(), c.hour.toByte(), c.weekday.toByte(), c.day.toByte(), c.month.toByte(), c.year.toByte(), (c.year shr 8).toByte(), 0)))
        }
    }

    fun record(id: Int, v: ByteArray) {
        raw[id] = v
        when (id) {
            ENV_NOISE -> testingNoise = false
            SEALING -> testingSeal = false
        }
    }

    private fun u8(id: Int) = raw[id]?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() and 0xFF }
    private fun on(id: Int) = u8(id)?.let { it and 0x80 != 0 }
    private fun level(id: Int) = u8(id)?.let { it and 0x7F }

    fun groups(): List<SettingGroup> = listOfNotNull(
        psapRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("psap", SettingTab.SOUND, it) },
        caseRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("case", SettingTab.DEVICE, it) },
        pageRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("case_pages", SettingTab.DEVICE, it) },
        fitRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("fit", SettingTab.DEVICE, it) },
    )

    private fun psapRows(): List<Setting> {
        if (!has(JblHarmanKit.PSAP)) return emptyList()
        val enabled = on(PSAP_STATUS) ?: return emptyList()
        return buildList {
            add(Setting.Toggle(JblHarmanKit.PSAP_KEY, enabled))
            // The balance is sent 50 lower than the page's 95 to 105 (`setPSAPStatus`, `gn/l0`): 45 to 55.
            u8(PSAP_BALANCE)?.let { add(Setting.Level(JblHarmanKit.PSAP_BALANCE_KEY, (it - 45).coerceIn(0, 10), 0, 10, enabled)) }
            u8(PSAP_GAIN)?.let { add(Setting.Level(JblHarmanKit.PSAP_GAIN_KEY, it.coerceIn(0, 5), 0, 5, enabled)) }
            on(PSAP_NOISE)?.let { add(Setting.Toggle(PSAP_NOISE_KEY, it, enabled)) }
            u8(PSAP_BEAMFORMING)?.let { v -> add(Setting.Choice(PSAP_VOICE_KEY, VOICE[if (v and 0x80 != 0) (v and 0x7F).coerceIn(0, 2) else 0], VOICE)) }
        }
    }

    private fun caseRows(): List<Setting> = buildList {
        if (has(JblHarmanKit.CASE_BRIGHTNESS)) level(CASE_BRIGHTNESS)?.let { add(Setting.Level(JblHarmanKit.BRIGHTNESS_KEY, it.coerceIn(0, 3), 0, 3)) }
        if (has(JblHarmanKit.CASE_NOTIFICATIONS)) {
            val n = on(CASE_NOTIFICATION)
            n?.let { add(Setting.Toggle(JblHarmanKit.NOTIFICATIONS_KEY, it)) }
            on(CASE_PREVIEW)?.let { add(Setting.Toggle(JblHarmanKit.PREVIEW_KEY, it, enabled = n != false)) }
        }
        if (has(JblHarmanKit.CASE_SCREEN_SAVER)) screenTime()?.let { add(Setting.Level(JblHarmanKit.SCREEN_TIME_KEY, (it / 5).coerceIn(0, 30), 0, 30)) }
        if (has(JblHarmanKit.CASE_LANGUAGE)) u8(CASE_LANGUAGE)?.let { add(Setting.Choice(JblHarmanKit.LANGUAGE_KEY, JblHarmanKit.LANGUAGES.getOrNull(it - 1), JblHarmanKit.LANGUAGES)) }
        if (has(JblHarmanKit.CASE_WAKE)) on(CASE_WAKE)?.let { w ->
            add(Setting.Toggle(JblHarmanKit.WAKE_KEY, w))
            if (w) add(Setting.Choice(JblHarmanKit.WAKE_TAP_KEY, JblHarmanKit.WAKE_TAPS.getOrNull((level(CASE_WAKE) ?: 0) - 1), JblHarmanKit.WAKE_TAPS))
        }
        if (has(JblHarmanKit.CASE_CLOCK)) on(CASE_24H)?.let { add(Setting.Toggle(CASE_24H_KEY, it)) }
        if (has(JblHarmanKit.CASE_INFO)) {
            version(CASE_VERSION)?.let { add(Setting.Information(JblHarmanKit.CASE_VERSION_KEY, it)) }
            version(CASE_LATEST)?.let { add(Setting.Information(JblHarmanKit.CASE_LATEST_KEY, it)) }
        }
    }

    /** Fifteen bits of seconds (`StatusLevelSize2Feature`). */
    private fun screenTime(): Int? = raw[CASE_SCREEN_TIME]?.takeIf { it.size >= 2 }?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0x7F) shl 8) }

    /** `VersionStringFeature`: the bytes as numbers with dots; nothing when they are all zero. */
    private fun version(id: Int): String? = raw[id]?.takeIf { b -> b.any { it.toInt() != 0 } }?.joinToString(".") { (it.toInt() and 0xFF).toString() }

    private fun pageRows(): List<Setting> = if (!has(JblHarmanKit.CASE_PAGES)) emptyList() else PAGES.mapNotNull { (id, key) ->
        u8(id)?.let { v -> Setting.Toggle(key, v and 0x80 != 0, enabled = v and 0x40 != 0) }
    }

    private fun fitRows(): List<Setting> = buildList {
        if (has(JblHarmanKit.FIT_TEST)) {
            add(Setting.Action(JblHarmanKit.SEAL_CHECK_KEY, testingSeal))
            u8(SEALING)?.let { v ->
                add(Setting.Information(JblHarmanKit.SEAL_LEFT_KEY, if (v and 0x80 != 0) JblHarmanKit.SEALED else JblHarmanKit.NOT_SEALED))
                add(Setting.Information(JblHarmanKit.SEAL_RIGHT_KEY, if (v and 0x40 != 0) JblHarmanKit.SEALED else JblHarmanKit.NOT_SEALED))
            }
            add(Setting.Action(JblHarmanKit.NOISE_CHECK_KEY, testingNoise))
            // A set bit is quiet, a clear one noisy (`gn/l0`: the level is 0 or 100).
            u8(ENV_NOISE)?.let { v ->
                add(Setting.Information(NOISE_LEFT_KEY, if (v and 0x80 != 0) QUIET else NOISY))
                add(Setting.Information(NOISE_RIGHT_KEY, if (v and 0x40 != 0) QUIET else NOISY))
            }
        }
        if (has(JblHarmanKit.EAR_TIPS)) on(EAR_TIPS)?.let { add(Setting.Choice(JblHarmanKit.EAR_TIPS_KEY, JblHarmanKit.TIP_TYPES[if (it) 1 else 0], JblHarmanKit.TIP_TYPES)) }
    }

    // ---- writes ----

    private fun status(id: Int, on: Boolean, level: Int = 127) = id to JblP4Packet.statusLevel(on, level)

    private fun one(id: Int, v: Int) = id to byteArrayOf(v.toByte())

    fun change(change: SettingChange): List<JblFrame>? {
        val set: Pair<Int, ByteArray>? = when (change) {
            is SettingChange.Toggle -> when (change.key) {
                JblHarmanKit.PSAP_KEY -> if (has(JblHarmanKit.PSAP)) status(PSAP_STATUS, change.on) else null
                PSAP_NOISE_KEY -> status(PSAP_NOISE, change.on, 0)
                JblHarmanKit.NOTIFICATIONS_KEY -> if (u8(CASE_NOTIFICATION) != null) {
                    // With the permission the case reported, as `CaseMessageNotification` writes it; the app has it only after a read.
                    val p = (raw[CASE_NOTIFICATION]!![0].toInt() shr 6) and 1
                    CASE_NOTIFICATION to byteArrayOf((((if (change.on) 1 else 0) shl 7) or (p shl 6)).toByte())
                } else null
                JblHarmanKit.PREVIEW_KEY -> status(CASE_PREVIEW, change.on)
                JblHarmanKit.WAKE_KEY -> status(CASE_WAKE, change.on)
                CASE_24H_KEY -> status(CASE_24H, change.on)
                else -> PAGES.entries.firstOrNull { it.value == change.key }?.let { (id, _) ->
                    u8(id)?.let { v -> id to byteArrayOf((if (change.on) v or 0x80 else v and 0x7F).toByte()) }
                }
            }
            is SettingChange.Level -> when (change.key) {
                JblHarmanKit.PSAP_BALANCE_KEY -> one(PSAP_BALANCE, change.value + 45)
                JblHarmanKit.PSAP_GAIN_KEY -> one(PSAP_GAIN, change.value)
                JblHarmanKit.BRIGHTNESS_KEY -> status(CASE_BRIGHTNESS, true, change.value)
                JblHarmanKit.SCREEN_TIME_KEY -> {
                    // `StatusLevelSize2Feature`: the status is on for one to a hundred seconds (`setChargingCaseDisplaySettingsStatus`).
                    val s = change.value * 5
                    val word = ((if (s in 1..100) 1 else 0) shl 15) or (s and 0x7FFF)
                    CASE_SCREEN_TIME to byteArrayOf(word.toByte(), (word shr 8).toByte())
                }
                else -> null
            }
            is SettingChange.Choice -> when (change.key) {
                PSAP_VOICE_KEY -> VOICE.indexOf(change.option).takeIf { it >= 0 }?.let { status(PSAP_BEAMFORMING, it != 0, it) }
                JblHarmanKit.LANGUAGE_KEY -> JblHarmanKit.LANGUAGES.indexOf(change.option).takeIf { it >= 0 }?.let { one(CASE_LANGUAGE, it + 1) }
                JblHarmanKit.WAKE_TAP_KEY -> JblHarmanKit.WAKE_TAPS.indexOf(change.option).takeIf { it >= 0 }?.let { status(CASE_WAKE, true, it + 1) }
                JblHarmanKit.EAR_TIPS_KEY -> JblHarmanKit.TIP_TYPES.indexOf(change.option).takeIf { it >= 0 && has(JblHarmanKit.EAR_TIPS) }?.let { status(EAR_TIPS, it == 1) }
                else -> null
            }
            is SettingChange.Press -> when (change.key) {
                JblHarmanKit.NOISE_CHECK_KEY -> if (has(JblHarmanKit.FIT_TEST)) { testingNoise = true; one(ENV_NOISE, 0) } else null
                JblHarmanKit.SEAL_CHECK_KEY -> if (has(JblHarmanKit.FIT_TEST)) { testingSeal = true; one(SEALING, 0) } else null
                else -> null
            }
        }
        val (id, value) = set ?: return null
        raw[id] = value
        return listOf(JblP4Packet.set(id to value))
    }

    /** The read that confirms a change to [key]. */
    fun readBack(key: String): JblFrame? {
        val id = when (key) {
            JblHarmanKit.PSAP_KEY, JblHarmanKit.PSAP_BALANCE_KEY, JblHarmanKit.PSAP_GAIN_KEY -> PSAP_STATUS
            PSAP_NOISE_KEY -> PSAP_NOISE
            PSAP_VOICE_KEY -> PSAP_BEAMFORMING
            JblHarmanKit.BRIGHTNESS_KEY -> CASE_BRIGHTNESS
            JblHarmanKit.NOTIFICATIONS_KEY -> CASE_NOTIFICATION
            JblHarmanKit.PREVIEW_KEY -> CASE_PREVIEW
            JblHarmanKit.SCREEN_TIME_KEY -> CASE_SCREEN_TIME
            JblHarmanKit.LANGUAGE_KEY -> CASE_LANGUAGE
            JblHarmanKit.WAKE_KEY, JblHarmanKit.WAKE_TAP_KEY -> CASE_WAKE
            CASE_24H_KEY -> CASE_24H
            JblHarmanKit.EAR_TIPS_KEY -> EAR_TIPS
            else -> PAGES.entries.firstOrNull { it.value == key }?.key ?: return null
        }
        return JblP4Packet.get(listOf(id))
    }

    companion object {
        const val PSAP_NOISE_KEY = "jbl_psap_noise"
        const val PSAP_VOICE_KEY = "jbl_psap_voice"
        const val CASE_24H_KEY = "jbl_case_24h"
        const val NOISE_LEFT_KEY = "jbl_noise_left"
        const val NOISE_RIGHT_KEY = "jbl_noise_right"
        const val QUIET = "quiet"
        const val NOISY = "noisy"
        val VOICE = listOf("off", "medium", "high")

        const val SYNC_TIME = 49
        const val PSAP_STATUS = 3200
        const val PSAP_BALANCE = 3201
        const val PSAP_GAIN = 3202
        const val PSAP_NOISE = 3203
        const val PSAP_BEAMFORMING = 3204
        const val SEALING = 4099
        const val ENV_NOISE = 4100
        const val EAR_TIPS = 4101
        const val CASE_VERSION = 7937
        const val CASE_BRIGHTNESS = 7940
        const val CASE_NOTIFICATION = 7941
        const val CASE_SCREEN_TIME = 7943
        const val CASE_PREVIEW = 7944
        const val CASE_LANGUAGE = 7946
        const val CASE_WAKE = 7948
        const val CASE_LATEST = 7950
        const val CASE_24H = 7953

        /** The case's page configs (`DeviceInfoFeature5` 8064 to 8081) by their row. */
        val PAGES: Map<Int, String> = linkedMapOf(
            8064 to "jbl_case_page_ambient", 8065 to "jbl_case_page_equalizer", 8066 to "jbl_case_page_speak_to_chat", 8067 to "jbl_case_page_spatial",
            8068 to "jbl_case_page_low_eq", 8069 to "jbl_case_page_voice_aware", 8070 to "jbl_case_page_auto_pause", 8071 to "jbl_case_page_silent_now",
            8072 to "jbl_case_page_in_case", 8073 to "jbl_case_page_timer", 8074 to "jbl_case_page_wallpaper", 8075 to "jbl_case_page_notification",
            8076 to "jbl_case_page_find", 8077 to "jbl_case_page_flashlight", 8078 to "jbl_case_page_music", 8079 to "jbl_case_page_brightness",
            8080 to "jbl_case_page_language", 8081 to "jbl_case_page_time24",
        )

        val IDS: Set<Int> = setOf(
            PSAP_STATUS, PSAP_BALANCE, PSAP_GAIN, PSAP_NOISE, PSAP_BEAMFORMING, SEALING, ENV_NOISE, EAR_TIPS, CASE_VERSION, CASE_BRIGHTNESS, CASE_NOTIFICATION,
            CASE_SCREEN_TIME, CASE_PREVIEW, CASE_LANGUAGE, CASE_WAKE, CASE_LATEST, CASE_24H,
        ) + PAGES.keys
    }
}
