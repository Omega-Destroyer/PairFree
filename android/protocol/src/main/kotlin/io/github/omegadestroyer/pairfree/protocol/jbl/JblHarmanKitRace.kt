package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab

/**
 * The settings of [JblHarmanKit] on the Airoha models (docs/jbl/PROTOCOL.md section 4.13), as RACE commands of JBL's Airoha adapter
 * (`AirohaDeviceStatusControl` and its `MmiStage_*Psap*`, `*ChargingCase*` and `SetDeviceTime` stages, JBL Headphones 5.37.10). Each is an id with a payload of fixed size:
 * reads are empty, the answer starts with a status byte and then the values; a write carries every field, 0xFF in those it leaves alone (the app builds each from an info
 * object whose fields start at -1). No JBL earbuds have answered them.
 */
internal class JblHarmanKitRace(private val model: JblModel, private val clock: () -> JblWallClock?) {
    private fun has(id: String) = id in model.extras

    private var psap: ByteArray? = null
    private var display: ByteArray? = null
    private var features: ByteArray? = null

    fun reads(): List<JblFrame> = buildList {
        addAll(start())
        if (has(JblHarmanKit.PSAP)) add(RacePacket(JblRace.PSAP_GET))
        if (usesDisplay) add(RacePacket(JblRace.CASE_DISPLAY_GET))
        if (has(JblHarmanKit.CASE_PAGES)) add(RacePacket(JblRace.CASE_FEATURE_GET))
    }

    /**
     * The clock is written once, as the app does on connecting: the Harman frame `A7` of section 4.11, passed to the earbuds through RACE `0093`
     * (`LiveBuds3Control.setDeviceTime` and `TourPro3LeControl.setDeviceTime` write `SyncTimeToDeviceCmd`'s bytes with `writeHmCommand`, `MmiStage_SendHmCmd`).
     */
    private fun start(): List<JblFrame> = buildList {
        if (has(JblHarmanKit.CASE_CLOCK)) clock()?.let { c ->
            val frame = (JblHarmanKit.timeFrame(c) as JblPacket).bytes()
            add(RacePacket(JblRace.HM_PASSTHROUGH, frame))
        }
    }

    private val usesDisplay: Boolean get() = listOf(
        JblHarmanKit.CASE_BRIGHTNESS, JblHarmanKit.CASE_NOTIFICATIONS, JblHarmanKit.CASE_SCREEN_SAVER, JblHarmanKit.CASE_LANGUAGE, JblHarmanKit.CASE_WAKE, JblHarmanKit.CASE_INFO,
    ).any { has(it) }

    fun handles(id: Int) = id in setOf(JblRace.PSAP_GET, JblRace.PSAP_SET, JblRace.CASE_DISPLAY_GET, JblRace.CASE_DISPLAY_SET, JblRace.CASE_FEATURE_GET, JblRace.CASE_FEATURE_SET)

    fun receive(p: RacePacket) {
        if (p.status != 0) return
        val d = p.data
        when (p.id) {
            // The on byte, the balance (95 to 105) and the gain step, as `notifyPsapStatus` takes bArr[7] to [9].
            JblRace.PSAP_GET, JblRace.PSAP_SET -> if (d.size >= 3) psap = d.copyOf(3)
            JblRace.CASE_DISPLAY_GET, JblRace.CASE_DISPLAY_SET -> if (d.size >= 12) display = d.copyOf()
            JblRace.CASE_FEATURE_GET -> if (d.size >= 27) features = d.copyOf()
        }
    }

    private fun u(b: ByteArray?, at: Int): Int? = b?.takeIf { at < it.size }?.let { it[at].toInt() and 0xFF }?.takeIf { it != 0xFF }

    fun groups(): List<SettingGroup> = listOfNotNull(
        psapRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("psap", SettingTab.SOUND, it) },
        caseRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("case", SettingTab.DEVICE, it) },
        pageRows().takeIf { it.isNotEmpty() }?.let { SettingGroup("case_pages", SettingTab.DEVICE, it) },
    )

    private fun psapRows(): List<Setting> {
        if (!has(JblHarmanKit.PSAP)) return emptyList()
        val p = psap ?: return emptyList()
        val on = (p[0].toInt() and 0xFF) == 1
        return listOf(
            Setting.Toggle(JblHarmanKit.PSAP_KEY, on),
            Setting.Level(JblHarmanKit.PSAP_BALANCE_KEY, ((p[1].toInt() and 0xFF) - 95).coerceIn(0, 10), 0, 10, on),
            Setting.Level(JblHarmanKit.PSAP_GAIN_KEY, (p[2].toInt() and 0xFF).coerceIn(0, 5), 0, 5, on),
        )
    }

    private fun caseRows(): List<Setting> = buildList {
        if (has(JblHarmanKit.CASE_BRIGHTNESS)) u(display, BRIGHTNESS)?.let { add(Setting.Level(JblHarmanKit.BRIGHTNESS_KEY, it.coerceIn(0, 3), 0, 3)) }
        if (has(JblHarmanKit.CASE_NOTIFICATIONS)) {
            val n = u(display, NOTIFICATION)?.let { it == 1 }
            n?.let { add(Setting.Toggle(JblHarmanKit.NOTIFICATIONS_KEY, it)) }
            u(display, PREVIEW)?.let { add(Setting.Toggle(JblHarmanKit.PREVIEW_KEY, it == 1, enabled = n != false)) }
        }
        if (has(JblHarmanKit.CASE_SCREEN_SAVER)) u(display, SCREEN_TIME)?.let { add(Setting.Level(JblHarmanKit.SCREEN_TIME_KEY, (it / 5).coerceIn(0, 30), 0, 30)) }
        if (has(JblHarmanKit.CASE_LANGUAGE)) u(display, LANGUAGE)?.let { add(Setting.Choice(JblHarmanKit.LANGUAGE_KEY, JblHarmanKit.LANGUAGES.getOrNull(it - 1), JblHarmanKit.LANGUAGES)) }
        if (has(JblHarmanKit.CASE_WAKE)) u(display, WAKE_STATUS)?.let { s ->
            add(Setting.Toggle(JblHarmanKit.WAKE_KEY, s == 1))
            if (s == 1) add(Setting.Choice(JblHarmanKit.WAKE_TAP_KEY, JblHarmanKit.WAKE_TAPS.getOrNull((u(display, WAKE_ACTION) ?: 0) - 1), JblHarmanKit.WAKE_TAPS))
        }
        if (has(JblHarmanKit.CASE_INFO)) display?.takeIf { it.size >= 19 }?.let { d ->
            add(Setting.Information(JblHarmanKit.CASE_LATEST_KEY, "${d[16].toInt() and 0xFF}.${d[17].toInt() and 0xFF}.${d[18].toInt() and 0xFF}"))
        }
    }

    private fun pageRows(): List<Setting> = if (!has(JblHarmanKit.CASE_PAGES)) emptyList() else PAGE_AT.mapNotNull { (at, key) ->
        u(features, at)?.let { Setting.Toggle(key, it == 1) }
    }

    fun change(change: SettingChange): List<JblFrame>? = when (change) {
        is SettingChange.Toggle -> when (change.key) {
            JblHarmanKit.PSAP_KEY -> psapWrite(on = change.on)
            JblHarmanKit.NOTIFICATIONS_KEY -> displayWrite(NOTIFICATION to if (change.on) 1 else 0)
            JblHarmanKit.PREVIEW_KEY -> displayWrite(PREVIEW to if (change.on) 1 else 0)
            // The switch sends the status alone (`QuickWakeUpInfo(1, -1)`), a tap its number with the status on.
            JblHarmanKit.WAKE_KEY -> displayWrite(WAKE_STATUS to if (change.on) 1 else 0)
            else -> PAGE_AT.entries.firstOrNull { it.value == change.key }?.let { featureWrite(it.key to if (change.on) 1 else 0) }
        }
        is SettingChange.Level -> when (change.key) {
            JblHarmanKit.PSAP_BALANCE_KEY -> psapWrite(balance = change.value + 95)
            JblHarmanKit.PSAP_GAIN_KEY -> psapWrite(gain = change.value)
            JblHarmanKit.BRIGHTNESS_KEY -> displayWrite(BRIGHTNESS to change.value)
            JblHarmanKit.SCREEN_TIME_KEY -> displayWrite(SCREEN_TIME to change.value * 5)
            else -> null
        }
        is SettingChange.Choice -> when (change.key) {
            JblHarmanKit.LANGUAGE_KEY -> JblHarmanKit.LANGUAGES.indexOf(change.option).takeIf { it >= 0 }?.let { displayWrite(LANGUAGE to it + 1) }
            JblHarmanKit.WAKE_TAP_KEY -> JblHarmanKit.WAKE_TAPS.indexOf(change.option).takeIf { it >= 0 }?.let { displayWrite(WAKE_STATUS to 1, WAKE_ACTION to it + 1) }
            else -> null
        }
        is SettingChange.Press -> null
    }

    /** `MmiStage_SetPsapStatus`: the on byte, the balance and the gain, with what the earbuds last said for the two not changed. */
    private fun psapWrite(on: Boolean? = null, balance: Int? = null, gain: Int? = null): List<JblFrame>? {
        val p = psap ?: return null
        val out = byteArrayOf(
            (if (on != null) (if (on) 1 else 0) else p[0].toInt()).toByte(),
            (balance ?: (p[1].toInt() and 0xFF)).toByte(),
            (gain ?: (p[2].toInt() and 0xFF)).toByte(),
        )
        psap = out
        return listOf(RacePacket(JblRace.PSAP_SET, out))
    }

    /** `MmiStage_SetChargingCaseDisplaySetting`: 26 bytes, 0xFF in each field left alone. */
    private fun displayWrite(vararg fields: Pair<Int, Int>): List<JblFrame>? {
        if (!usesDisplay) return null
        val out = ByteArray(26) { 0xFF.toByte() }
        for ((at, v) in fields) out[at] = v.toByte()
        display?.let { d -> for ((at, v) in fields) if (at < d.size) d[at] = v.toByte() }
        return listOf(RacePacket(JblRace.CASE_DISPLAY_SET, out))
    }

    /** `MmiStage_SetChargingCaseFeatureSetting`: 27 bytes; the case's version and address go back as the case reported them. */
    private fun featureWrite(vararg fields: Pair<Int, Int>): List<JblFrame>? {
        val read = features ?: return null
        val out = ByteArray(27) { 0xFF.toByte() }
        for (i in 9..17) out[i] = read[i]
        for ((at, v) in fields) out[at] = v.toByte()
        for ((at, v) in fields) read[at] = v.toByte()
        return listOf(RacePacket(JblRace.CASE_FEATURE_SET, out))
    }

    fun readBack(key: String): JblFrame? = when (key) {
        JblHarmanKit.PSAP_KEY, JblHarmanKit.PSAP_BALANCE_KEY, JblHarmanKit.PSAP_GAIN_KEY -> RacePacket(JblRace.PSAP_GET)
        JblHarmanKit.NOTIFICATIONS_KEY, JblHarmanKit.PREVIEW_KEY, JblHarmanKit.BRIGHTNESS_KEY, JblHarmanKit.SCREEN_TIME_KEY, JblHarmanKit.LANGUAGE_KEY,
        JblHarmanKit.WAKE_KEY, JblHarmanKit.WAKE_TAP_KEY,
        -> RacePacket(JblRace.CASE_DISPLAY_GET)
        else -> if (key in PAGE_AT.values) RacePacket(JblRace.CASE_FEATURE_GET) else null
    }

    companion object {
        // The display struct (`MmiStage_SetChargingCaseDisplaySetting`, `notifyChargingCaseDisplaySetting`).
        private const val BRIGHTNESS = 0
        private const val NOTIFICATION = 1
        private const val PREVIEW = 2
        private const val SCREEN_TIME = 4
        private const val LANGUAGE = 10
        private const val WAKE_STATUS = 24
        private const val WAKE_ACTION = 25

        /** The page's byte in the 27-byte feature struct, by the row it makes. */
        val PAGE_AT: Map<Int, String> = JblHarmanKit.PAGES.values.toList().let { keys ->
            val at = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 19, 20, 21, 22, 23, 24, 25, 26)
            at.indices.associate { at[it] to keys[it] }
        }
    }
}
