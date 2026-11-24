package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab

/** The phone's local time, which JBL's app sends the earbuds' case on every connection (`SyncTimeToDeviceCmd`). [weekday] is 0 for Sunday. */
public class JblWallClock(
    public val year: Int,
    public val month: Int,
    public val day: Int,
    public val weekday: Int,
    public val hour: Int,
    public val minute: Int,
    public val second: Int,
)

/**
 * The Harman command classes of the BES models' `imp/cmd` package that the dashboard shows beyond [JblExtras] (docs/jbl/PROTOCOL.md section 4.11),
 * each a command with a sub-command first (00 write, 01 read, 02 reply) and read from JBL Headphones 5.37.10:
 * PSAP (`A0`), the smart case's screen (`A3`) and pages (`A4`), the clock (`A7`), the noise check (`99`) and the ear canal test (`9A`) and the ear tips (`9C`).
 * A model has the ones its dashboard cards and its control class both have ([JblModel.extras]).
 */
/** What an Airoha session knows of its earbuds that the fit checks need: the chip family and whether the earbud PairFree talks to is the right one. */
internal class RaceEnv(val chip: RaceChip?, val agentRight: Boolean)

internal class JblHarmanKit(private val model: JblModel, private val clock: () -> JblWallClock? = { null }, private val env: () -> RaceEnv = { RaceEnv(null, false) }) {
    private fun has(id: String) = id in model.extras

    val any: Boolean get() = model.extras.any { it in IDS }

    // The protocol 4 models send the same settings as features (section 4.12).
    private val k4 = if (model.family == JblModel.Family.P4) JblHarmanKit4(model, clock) else null

    private val kr = if (model.family == JblModel.Family.AIROHA) JblHarmanKitRace(model, clock, env) else null

    // The LE Audio switch (B1 key 1, protocol 4 feature 2976, the passthrough on Airoha).
    internal val leAudio = if (has(LE_AUDIO)) JblLeAudio(model.family == JblModel.Family.P4, model.family == JblModel.Family.AIROHA) else null

    // PersoniFi's switch and the messages around its hearing test (A1, or protocol 4's features 7680 to 7684).
    internal val personifi = if (has(PERSONIFI)) JblPersonifiMode(model.family == JblModel.Family.P4, model.family == JblModel.Family.AIROHA) else null

    fun raceReads(): List<JblFrame> = kr?.reads().orEmpty() + (if (kr != null) personifi?.reads().orEmpty() + leAudio?.reads().orEmpty() else emptyList())
    fun raceHandles(id: Int): Boolean = kr?.handles(id) == true || (personifi != null && id in PERSONIFI_RACE) || (leAudio != null && id == JblRace.HM_PASSTHROUGH)
    fun race(p: RacePacket) {
        if (personifi != null && p.id in PERSONIFI_RACE) { if (p.status == 0) personifi.raceReply(p.data) } else if (p.id == JblRace.HM_PASSTHROUGH) { if (p.status == 0) leAudio?.passthroughReply(p.data) } else kr?.receive(p)
    }

    fun p4Features(): List<Int> = k4?.features().orEmpty() + personifi?.p4Features().orEmpty() + leAudio?.p4Features().orEmpty()
    fun p4Start(): List<JblFrame> = k4?.start().orEmpty()
    fun p4(id: Int, v: ByteArray) {
        k4?.record(id, v)
        personifi?.record(id, v)
        leAudio?.record(id, v)
    }

    // The Auracast receiver (`B0`).
    private val auracast = JblAuracast()

    // PSAP: on, the balance (95 to 105) and the gain step (0 to 5).
    private var psapOn: Boolean? = null
    private var psapBalance = BALANCE_MIDDLE
    private var psapGain = 0

    // The case's screen (A3) by its ids, and its pages (A4).
    private val display = mutableMapOf<Int, Int>()
    private val pages = mutableMapOf<Int, Int>()
    private var caseSoftware: String? = null
    private var screenLatest: String? = null
    private var caseLatest: String? = null

    // The environment noise levels, the ear canal test's result, and the ear tips.
    private var noiseLeft: Int? = null
    private var noiseRight: Int? = null
    private var canalResult: Int? = null
    private var testingCanal = false
    private var checkingNoise = false
    private var tips: Int? = null
    private var sealLeft: Int? = null
    private var sealRight: Int? = null
    private var checkingSeal = false

    fun reads(): List<JblFrame> = buildList {
        if (has(PSAP)) add(JblPacket.of(JblCommand.PSAP, READ))
        if (usesDisplay) add(JblPacket.of(JblCommand.CHARGING_CASE_DISPLAY, READ))
        if (has(CASE_PAGES)) add(JblPacket.of(JblCommand.CHARGING_CASE_FEATURE, READ))
        if (has(EAR_TIPS)) add(JblPacket.of(JblCommand.EAR_TIPS, READ))
        if (has(CASE_CLOCK)) clock()?.let { add(time(it)) }
        if (has(AURACAST)) addAll(auracast.reads())
        if (kr == null) personifi?.let { addAll(it.reads()) }
        if (kr == null) leAudio?.let { addAll(it.reads()) }
    }

    private val usesDisplay: Boolean get() = DISPLAY_IDS.any { has(it) }

    fun reportsAa(command: Int): Boolean = command in AA_COMMANDS

    /** A reply (`02` first). */
    fun aa(command: Int, p: ByteArray) {
        if (p.isEmpty()) return
        when (command) {
            JblCommand.PSAP -> psapReply(p)
            JblCommand.CHARGING_CASE_DISPLAY -> if (p.u8(0) == RET) displayReply(p)
            JblCommand.CHARGING_CASE_FEATURE -> if (p.u8(0) == RET) pagesReply(p)
            JblCommand.EAR_CANAL_TEST -> if (p.u8(0) == RET && p.size >= 2) {
                canalResult = p.u8(1)
                testingCanal = false
            }
            JblCommand.ENVIRONMENT_NOISE -> if (p.u8(0) == RET && p.size >= 5) {
                noiseLeft = p.u8(1) or (p.u8(2) shl 8)
                noiseRight = p.u8(3) or (p.u8(4) shl 8)
                checkingNoise = false
            }
            JblCommand.EAR_TIPS -> if (p.u8(0) == RET && p.size >= 2) tips = p.u8(1)
            JblCommand.LE_AUDIO -> auracast.reply(p)
            JblCommand.PERSONIFI -> personifi?.reply(p)
            JblCommand.GET_SET_FEATURE -> leAudio?.reply(p)
        }
    }

    /** The seal check's status reply (type `42`, then the left and the right earbud: 1 sealed, 0 not; `HandleParse.parseDevStatus`). */
    fun sealReply(p: ByteArray) {
        if (p.size < 3) return
        sealLeft = p.u8(1)
        sealRight = p.u8(2)
        checkingSeal = false
    }

    /**
     * PSAPCmd.parse: the long reply holds the feature ids with their values (1 on, 2 balance, 3 gain), the short one the on byte and the balance.
     */
    private fun psapReply(p: ByteArray) {
        if (p.u8(0) != RET) return
        if (p.size >= 7) {
            psapOn = p.u8(2) == 1
            psapBalance = p.u8(4)
            psapGain = p.u8(6)
        } else if (p.size >= 3) {
            psapOn = p.u8(1) == 1
            psapBalance = p.u8(2)
        }
    }

    /**
     * ChargingCaseDisplayStatusCmd.parse: pairs of id and value; id 4 holds two bytes (low first), 7 four, 11 and 12 three (a version, one byte each),
     * 14 four more that the app skips; every other id one byte.
     */
    private fun displayReply(p: ByteArray) {
        var at = 1
        while (at < p.size) {
            val id = p.u8(at)
            when {
                id == ID_SCREEN_TIME && at + 2 < p.size -> { display[id] = p.u8(at + 1) or (p.u8(at + 2) shl 8); at += 3 }
                id == ID_DFU_CRC && at + 4 < p.size -> { at += 5 }
                id == 14 -> at += 5
                (id == ID_EARBUDS_VERSION || id == ID_CASE_VERSION) && at + 3 < p.size -> {
                    val v = "${p.u8(at + 1)}.${p.u8(at + 2)}.${p.u8(at + 3)}"
                    if (id == ID_EARBUDS_VERSION) screenLatest = v else caseLatest = v
                    at += 4
                }
                at + 1 < p.size -> { display[id] = p.u8(at + 1); at += 2 }
                else -> return
            }
        }
    }

    /** ChargingCaseFeatureCmd.parseInfo: 192 is the case's version (three bytes), 193 its address (six); every other id has one byte. */
    private fun pagesReply(p: ByteArray) {
        var at = 1
        while (at < p.size) {
            val id = p.u8(at)
            when {
                id == ID_CASE_SOFTWARE && at + 3 < p.size -> { caseSoftware = "${p.u8(at + 1)}.${p.u8(at + 2)}.${p.u8(at + 3)}"; at += 4 }
                id == ID_CASE_ADDRESS -> at += 7
                at + 1 < p.size -> { pages[id] = p.u8(at + 1); at += 2 }
                else -> return
            }
        }
    }

    // ---- rows ----

    fun groups(): List<SettingGroup> = (k4?.groups() ?: kr?.groups() ?: groupsOwn()) +
        listOfNotNull(
            personifi?.rows()?.takeIf { it.isNotEmpty() }?.let { SettingGroup("personifi", SettingTab.SOUND, it) },
            leAudio?.rows()?.takeIf { it.isNotEmpty() }?.let { SettingGroup("le_audio", SettingTab.DEVICE, it) },
        )

    private fun groupsOwn(): List<SettingGroup> = listOfNotNull(
        psapRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("psap", SettingTab.SOUND, it) },
        caseRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("case", SettingTab.DEVICE, it) },
        pageRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("case_pages", SettingTab.DEVICE, it) },
        fitRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("fit", SettingTab.DEVICE, it) },
        (if (has(AURACAST)) auracast.rows() else emptyList()).takeIf { it.isNotEmpty() }?.let { SettingGroup("auracast", SettingTab.DEVICE, it) },
    )

    private fun psapRows(): List<Setting> {
        if (!has(PSAP)) return emptyList()
        val on = psapOn ?: return emptyList()
        return listOf(
            Setting.Toggle(PSAP_KEY, on),
            Setting.Level(PSAP_BALANCE_KEY, psapBalance.coerceIn(BALANCE_MIN, BALANCE_MAX) - BALANCE_MIN, 0, BALANCE_MAX - BALANCE_MIN, enabled = on),
            Setting.Level(PSAP_GAIN_KEY, psapGain.coerceIn(0, GAIN_MAX), 0, GAIN_MAX, enabled = on),
        )
    }

    private fun caseRows(): List<Setting> = buildList {
        if (has(CASE_BRIGHTNESS)) display[ID_BRIGHTNESS]?.let { add(Setting.Level(BRIGHTNESS_KEY, it.coerceIn(0, BRIGHTNESS_MAX), 0, BRIGHTNESS_MAX)) }
        if (has(CASE_NOTIFICATIONS)) {
            val on = display[ID_NOTIFICATION]?.let { it == 1 }
            on?.let { add(Setting.Toggle(NOTIFICATIONS_KEY, it)) }
            display[ID_PREVIEW]?.let { add(Setting.Toggle(PREVIEW_KEY, it == 1, enabled = on != false)) }
        }
        if (has(CASE_SCREEN_SAVER)) display[ID_SCREEN_TIME]?.let { add(Setting.Level(SCREEN_TIME_KEY, (it / SCREEN_TIME_STEP).coerceIn(0, SCREEN_TIME_MAX), 0, SCREEN_TIME_MAX)) }
        // The built-in screen savers by number (a custom picture is 193, which the app shows apart); the custom picture can be removed.
        if (has(CASE_SCREEN_SAVER)) display[ID_SAVER]?.takeIf { it < SAVER_COUNT }?.let { add(Setting.Level(SAVER_KEY, it + 1, 1, SAVER_COUNT)) }
        if (has(CASE_SCREEN_SAVER) && display[ID_CUSTOM] == 1) add(Setting.Action(SAVER_REMOVE_KEY))
        if (has(CASE_UNLOCK)) display[ID_UNLOCK]?.let { add(Setting.Toggle(UNLOCK_KEY, it == 1)) }
        if (has(CASE_FIND)) display[ID_FIND]?.let { add(Setting.Toggle(FIND_KEY, it == 1)) }
        if (has(CASE_LANGUAGE)) display[ID_LANGUAGE]?.let { code ->
            add(Setting.Choice(LANGUAGE_KEY, LANGUAGES.getOrNull(code - 1), LANGUAGES))
        }
        if (has(CASE_WAKE)) display[ID_WAKE]?.let { v ->
            add(Setting.Toggle(WAKE_KEY, (v shr 7) == 1))
            if ((v shr 7) == 1) add(Setting.Choice(WAKE_TAP_KEY, WAKE_TAPS.getOrNull((v and 127) - 1), WAKE_TAPS))
        }
        if (has(CASE_INFO)) {
            caseSoftware?.let { add(Setting.Information(CASE_VERSION_KEY, it)) }
            caseLatest?.let { add(Setting.Information(CASE_LATEST_KEY, it)) }
        }
    }

    private fun pageRows(): List<Setting> = if (!has(CASE_PAGES)) emptyList() else PAGES.mapNotNull { (id, key) ->
        pages[id]?.let { Setting.Toggle(key, it == 1) }
    }

    private fun fitRows(): List<Setting> = buildList {
        if (has(FIT_TEST)) {
            add(Setting.Action(SEAL_CHECK_KEY, active = checkingSeal))
            sealLeft?.let { add(Setting.Information(SEAL_LEFT_KEY, if (it == 1) SEALED else NOT_SEALED)) }
            sealRight?.let { add(Setting.Information(SEAL_RIGHT_KEY, if (it == 1) SEALED else NOT_SEALED)) }
            add(Setting.Action(NOISE_CHECK_KEY, active = checkingNoise))
            if (noiseLeft != null && noiseRight != null) add(Setting.Information(FIT_NOISE_KEY, "${noiseLeft} / ${noiseRight}"))
            add(Setting.Action(CANAL_TEST_KEY, active = testingCanal))
            canalResult?.let { add(Setting.Information(CANAL_RESULT_KEY, if (it == CANAL_DONE) DONE else FAILED)) }
        }
        if (has(EAR_TIPS)) tips?.let { add(Setting.Choice(EAR_TIPS_KEY, TIP_TYPES.getOrNull(it), TIP_TYPES)) }
    }

    // ---- writes ----

    /** The frames for a change to one of these rows, or null when it is not one of them. */
    fun change(change: SettingChange): List<JblFrame>? = personifi?.change(change) ?: leAudio?.change(change) ?: if (k4 != null) k4.change(change) else if (kr != null) kr.change(change) else if (has(AURACAST) && change.key in JblAuracast.KEYS) auracast.change(change) else own(change)

    private fun own(change: SettingChange): List<JblFrame>? = when (change) {
        is SettingChange.Toggle -> when (change.key) {
            PSAP_KEY -> if (has(PSAP)) listOf(psapWrite(on = change.on)).also { psapOn = change.on } else null
            NOTIFICATIONS_KEY -> displayWrite(ID_NOTIFICATION, if (change.on) 1 else 0)
            PREVIEW_KEY -> displayWrite(ID_PREVIEW, if (change.on) 1 else 0)
            UNLOCK_KEY -> displayWrite(ID_UNLOCK, if (change.on) 1 else 0)
            // Find my case: 1 plays the case's sound, 2 stops it (`FindMyCaseViewModel`).
            FIND_KEY -> displayWrite(ID_FIND, if (change.on) 1 else 2)
            WAKE_KEY -> displayWrite(ID_WAKE, wakeValue(change.on, 0))
            else -> PAGES.entries.firstOrNull { it.value == change.key }?.let { pageWrite(it.key, if (change.on) 1 else 0) }
        }
        is SettingChange.Level -> when (change.key) {
            PSAP_BALANCE_KEY -> if (has(PSAP)) listOf(psapWrite(balance = change.value + BALANCE_MIN)).also { psapBalance = change.value + BALANCE_MIN } else null
            PSAP_GAIN_KEY -> if (has(PSAP)) listOf(psapWrite(gain = change.value)).also { psapGain = change.value } else null
            BRIGHTNESS_KEY -> displayWrite(ID_BRIGHTNESS, change.value)
            SAVER_KEY -> displayWrite(ID_SAVER, change.value - 1)
            SCREEN_TIME_KEY -> displayWrite(ID_SCREEN_TIME, change.value * SCREEN_TIME_STEP)
            else -> null
        }
        is SettingChange.Choice -> when (change.key) {
            LANGUAGE_KEY -> LANGUAGES.indexOf(change.option).takeIf { it >= 0 }?.let { displayWrite(ID_LANGUAGE, it + 1) }
            WAKE_TAP_KEY -> WAKE_TAPS.indexOf(change.option).takeIf { it >= 0 }?.let { displayWrite(ID_WAKE, wakeValue(true, it + 1)) }
            EAR_TIPS_KEY -> TIP_TYPES.indexOf(change.option).takeIf { it >= 0 && has(EAR_TIPS) }?.let { t ->
                tips = t
                listOf(JblPacket.of(JblCommand.EAR_TIPS, WRITE, t))
            }
            else -> null
        }
        is SettingChange.Press -> when (change.key) {
            SEAL_CHECK_KEY -> if (has(FIT_TEST)) listOf(JblPacket.of(JblCommand.STATUS, JblStatus.SEAL_CHECK)).also { checkingSeal = true } else null
            // `ChargingCaseDisplayStatusCmd.delCustomizeScreenSaver`: the delete sub-command, the custom picture's id 6 and 193.
            SAVER_REMOVE_KEY -> if (has(CASE_SCREEN_SAVER)) listOf(JblPacket.of(JblCommand.CHARGING_CASE_DISPLAY, 3, ID_CUSTOM, CUSTOM_SAVER)).also { display[ID_CUSTOM] = 0 } else null
            NOISE_CHECK_KEY -> if (has(FIT_TEST)) listOf(JblPacket.of(JblCommand.ENVIRONMENT_NOISE, READ)).also { checkingNoise = true } else null
            CANAL_TEST_KEY -> if (has(FIT_TEST)) listOf(JblPacket.of(JblCommand.EAR_CANAL_TEST, READ)).also { testingCanal = true } else null
            else -> null
        }
    }

    /** The read that confirms a change to [key]. */
    fun readBack(key: String): JblFrame? = personifi?.readBack(key) ?: leAudio?.readBack(key) ?: if (k4 != null) k4.readBack(key) else if (kr != null) kr.readBack(key) else if (has(AURACAST)) auracast.readBack(key) ?: ownReadBack(key) else ownReadBack(key)

    private fun ownReadBack(key: String): JblFrame? = when (key) {
        PSAP_KEY, PSAP_BALANCE_KEY, PSAP_GAIN_KEY -> JblPacket.of(JblCommand.PSAP, READ)
        NOTIFICATIONS_KEY, PREVIEW_KEY, BRIGHTNESS_KEY, SCREEN_TIME_KEY, LANGUAGE_KEY, WAKE_KEY, WAKE_TAP_KEY, UNLOCK_KEY, FIND_KEY, SAVER_KEY, SAVER_REMOVE_KEY -> JblPacket.of(JblCommand.CHARGING_CASE_DISPLAY, READ)
        EAR_TIPS_KEY -> JblPacket.of(JblCommand.EAR_TIPS, READ)
        else -> if (PAGES.containsValue(key)) JblPacket.of(JblCommand.CHARGING_CASE_FEATURE, READ) else null
    }

    /** PSAPCmd.toBytesSet: the three features with the values the earbuds last reported, one of them changed. */
    private fun psapWrite(on: Boolean = psapOn == true, balance: Int = psapBalance, gain: Int = psapGain): JblFrame =
        JblPacket.of(JblCommand.PSAP, WRITE, 1, if (on) 1 else 0, 2, balance, 3, gain)

    /** ChargingCaseDisplayStatusCmd.toBytesSet: id and value pairs, the screen saver time in two bytes. */
    private fun displayWrite(id: Int, value: Int): List<JblFrame>? {
        if (!usesDisplay) return null
        display[id] = value
        val body = if (id == ID_SCREEN_TIME) intArrayOf(id, value and 0xFF, (value shr 8) and 0xFF) else intArrayOf(id, value)
        return listOf(JblPacket.of(JblCommand.CHARGING_CASE_DISPLAY, WRITE, *body))
    }

    /**
     * The quick wake-up's value: the action in the low seven bits, the status above (ChargingCaseDisplayStatusCmd's id 10). The switch sends action 0, a
     * tap its number with the status on, as TapToWakeCard does (QuickWakeUpInfo(1, -1) and QuickWakeUpInfo(1, n)).
     */
    private fun wakeValue(on: Boolean, action: Int): Int = action or ((if (on) 1 else 0) shl 7)

    private fun pageWrite(id: Int, value: Int): List<JblFrame>? {
        if (!has(CASE_PAGES) || id !in pages) return null
        pages[id] = value
        return listOf(JblPacket.of(JblCommand.CHARGING_CASE_FEATURE, WRITE, id, value))
    }

    /** SyncTimeToDeviceCmd.toBytesSet(long): the time the app has, hour first, the year in two bytes. */
    fun time(c: JblWallClock): JblFrame = timeFrame(c)

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    companion object {
        fun timeFrame(c: JblWallClock): JblPacket =
            JblPacket.of(JblCommand.SYNC_TIME, WRITE, 1, c.hour, c.minute, c.second, c.weekday, c.day, c.month, c.year and 0xFF, (c.year shr 8) and 0xFF)

        const val PSAP = "psap"
        const val CASE_PAGES = "case_pages"
        const val CASE_BRIGHTNESS = "case_brightness"
        const val CASE_NOTIFICATIONS = "case_notifications"
        const val CASE_SCREEN_SAVER = "case_screen_saver"
        const val CASE_LANGUAGE = "case_language"
        const val CASE_CLOCK = "case_clock"
        const val CASE_WAKE = "case_wake"
        const val CASE_INFO = "case_info"
        const val FIT_TEST = "fit_test"
        const val EAR_TIPS = "ear_tips"
        const val AURACAST = "auracast"
        const val PERSONIFI = "personifi"
        const val LE_AUDIO = "le_audio"
        const val CASE_FIND = "case_find"
        const val CASE_UNLOCK = "case_unlock"
        const val FIND_KEY = "jbl_case_find"
        const val UNLOCK_KEY = "jbl_case_unlock"
        const val SAVER_KEY = "jbl_case_saver"
        const val SAVER_REMOVE_KEY = "jbl_case_saver_remove"
        /** The built-in screen savers every case with the card lists (`defaultScreenSaver` in the dashboards: five), and the number a custom picture has. */
        const val SAVER_COUNT = 5
        const val CUSTOM_SAVER = 193
        val PERSONIFI_RACE = setOf(JblRace.PERSONIFI_STATUS_GET, JblRace.PERSONIFI_STATUS_SET)
        const val PERSONIFI_OPEN = "personifi_open"

        private val DISPLAY_IDS = listOf(CASE_BRIGHTNESS, CASE_NOTIFICATIONS, CASE_SCREEN_SAVER, CASE_LANGUAGE, CASE_WAKE, CASE_INFO, CASE_FIND, CASE_UNLOCK)
        val IDS = setOf(PSAP, CASE_PAGES, CASE_CLOCK, FIT_TEST, EAR_TIPS, AURACAST, PERSONIFI, LE_AUDIO) + DISPLAY_IDS

        val AA_COMMANDS = setOf(
            JblCommand.PSAP, JblCommand.CHARGING_CASE_DISPLAY, JblCommand.CHARGING_CASE_FEATURE, JblCommand.EAR_CANAL_TEST,
            JblCommand.ENVIRONMENT_NOISE, JblCommand.EAR_TIPS, JblCommand.LE_AUDIO, JblCommand.PERSONIFI, JblCommand.GET_SET_FEATURE,
        )

        const val PSAP_KEY = "jbl_psap"
        const val PSAP_BALANCE_KEY = "jbl_psap_balance"
        const val PSAP_GAIN_KEY = "jbl_psap_gain"
        const val BRIGHTNESS_KEY = "jbl_case_brightness"
        const val NOTIFICATIONS_KEY = "jbl_case_notifications"
        const val PREVIEW_KEY = "jbl_case_preview"
        const val SCREEN_TIME_KEY = "jbl_case_screen_time"
        const val LANGUAGE_KEY = "jbl_case_language"
        const val WAKE_KEY = "jbl_case_wake"
        const val WAKE_TAP_KEY = "jbl_case_wake_tap"
        const val CASE_VERSION_KEY = "jbl_case_version"
        const val CASE_LATEST_KEY = "jbl_case_latest"
        const val NOISE_CHECK_KEY = "jbl_noise_check"
        const val SEAL_CHECK_KEY = "jbl_seal_check"
        const val SEAL_LEFT_KEY = "jbl_seal_left"
        const val SEAL_RIGHT_KEY = "jbl_seal_right"
        const val SEALED = "sealed"
        const val NOT_SEALED = "not_sealed"
        const val CANAL_TEST_KEY = "jbl_canal_test"
        const val FIT_NOISE_KEY = "jbl_fit_noise"
        const val CANAL_RESULT_KEY = "jbl_canal_result"
        const val EAR_TIPS_KEY = "jbl_ear_tips"
        const val DONE = "done"
        const val FAILED = "failed"

        /** The smart case's pages by their id in `A4` (ChargingCaseFeatureCmd.toBytesSet), each shown on the case or not. */
        val PAGES: Map<Int, String> = linkedMapOf(
            1 to "jbl_case_page_ambient", 2 to "jbl_case_page_equalizer", 3 to "jbl_case_page_speak_to_chat", 4 to "jbl_case_page_spatial",
            5 to "jbl_case_page_low_eq", 6 to "jbl_case_page_voice_aware", 7 to "jbl_case_page_auto_pause", 8 to "jbl_case_page_silent_now",
            9 to "jbl_case_page_in_case", 10 to "jbl_case_page_timer", 11 to "jbl_case_page_wallpaper", 12 to "jbl_case_page_notification",
            13 to "jbl_case_page_find", 14 to "jbl_case_page_flashlight", 15 to "jbl_case_page_music", 16 to "jbl_case_page_volume",
            17 to "jbl_case_page_brightness",
        )

        /** The case's languages (`CaseLangType`, 1 to 13; `A3` id 8). */
        val LANGUAGES = listOf(
            "english", "dutch", "french", "german", "italian", "japanese", "korean", "portuguese", "russian",
            "chinese_simplified", "spanish", "thai", "vietnamese",
        )

        /** Quick wake-up's taps, 1 to 3 (`TapToWakeCard`). */
        val WAKE_TAPS = listOf("single_tap", "double_tap", "triple_tap")

        /** The ear tips: 0 open, 1 sealing (`SoundFitCard`'s TIPS_OPEN and TIPS_SEALING). */
        val TIP_TYPES = listOf("open", "sealing")

        private const val WRITE = 0
        private const val READ = 1
        private const val RET = 2
        private const val CANAL_DONE = 3

        private const val BALANCE_MIN = 95
        private const val BALANCE_MAX = 105
        private const val BALANCE_MIDDLE = 100
        private const val GAIN_MAX = 5
        private const val BRIGHTNESS_MAX = 3
        private const val SCREEN_TIME_STEP = 5
        private const val SCREEN_TIME_MAX = 30

        private const val ID_BRIGHTNESS = 1
        private const val ID_NOTIFICATION = 2
        private const val ID_SAVER = 3
        private const val ID_SCREEN_TIME = 4
        private const val ID_CUSTOM = 6
        private const val ID_UNLOCK = 9
        private const val ID_FIND = 13
        private const val ID_PREVIEW = 5
        private const val ID_DFU_CRC = 7
        private const val ID_LANGUAGE = 8
        private const val ID_WAKE = 10
        private const val ID_EARBUDS_VERSION = 11
        private const val ID_CASE_VERSION = 12
        private const val ID_CASE_SOFTWARE = 192
        private const val ID_CASE_ADDRESS = 193
    }
}
