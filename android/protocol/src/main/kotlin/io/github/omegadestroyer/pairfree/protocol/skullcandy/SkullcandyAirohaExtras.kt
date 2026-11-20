package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceParser
import kotlin.math.roundToInt

/**
 * The settings of Skullcandy's Airoha models without an SDK configuration that its app writes with Skullcandy's own RACE commands (docs/skullcandy/PROTOCOL.md section 9.6):
 *
 * - **The custom pair `2C82` (write) and `2C83` (read)** that the Airoha SDK of the app carries (`libmmi1568` stages in `com.airoha.libmmi.b`): a two-byte setting number, then the value. Sidetone on or
 *   off is setting 7, its level (a signed short, low byte first) 6, the game and chat mix 0B and the microphone volume 0C. A read's answer is `05 5B`, the status, the setting number and the value.
 * - **Touch controls** (the Push 540 Open): the NV key `F514` read and the commands `0007` and `0008`, as `RaceCommandCapTouchS1Get/Set` and `S2Get/Set` build them.
 * - **Adaptive noise cancelling** (the Aviator 900 ANC): the module read `001F` and the ANC command `0E06` with `00 22`.
 * - **The Crusher's intensity** (the Crusher PLYR models): `2CD0` writes it and `2CD1` reads it.
 *
 * Every range the app does not state in its code is marked in the document as an inference.
 */
internal class SkullcandyAirohaExtras(private val product: SkullcandyProduct) {
    private val sdk = product.usesAirohaSdk
    val sidetone: Boolean = sdk && "adjustableSidetoneMode" in product.features
    val chatMix: Boolean = sdk && "gameChatMix" in product.features
    val micGain: Boolean = sdk && "adjustableMicGain" in product.features
    val touch: Boolean = sdk && "capTouchControl" in product.features
    val adaptiveAnc: Boolean = sdk && "adaptiveANCMode" in product.features
    val crusher: Boolean = sdk && "crusherTechnology" in product.features

    val has: Boolean get() = sidetone || chatMix || micGain || touch || adaptiveAnc || crusher

    /** Every command built here, which [SkullcandyRaceTalk.allowed] lets through. */
    val sent: MutableSet<RacePacket> = mutableSetOf()

    private fun pk(hex: String): RacePacket = RaceParser().feed(SkullcandyRace.bytes(hex)).single().also { sent += it }
    private fun hex(v: Int): String = (v and 0xFF).toString(16).padStart(2, '0').uppercase()

    private var sidetoneOn: Boolean? = null
    private var sidetoneLevel: Int? = null
    private var chat: Int? = null
    private var mic: Int? = null
    private var touchOn: Boolean? = null
    private var touchScenario1 = false
    private var adaptive: Boolean? = null
    private var intensity: Int? = null

    /** The NV read of the touch controls' key; its answer has no key, so the session matches it by the request. */
    val touchRead: RacePacket = RacePacket.nvRead(TOUCH_KEY, TOUCH_LENGTH).also { sent += it }

    /** The module read of adaptive noise cancelling. */
    val adaptiveRead: RacePacket = RacePacket.mmiGet(MODULE_ADAPTIVE_ANC).also { sent += it }

    /** What to ask for when the channel opens. */
    fun start(): List<RacePacket> = buildList {
        if (sidetone) { add(pk(readHex(SIDETONE_STATE))); add(pk(readHex(SIDETONE_LEVEL))) }
        if (chatMix) add(pk(readHex(CHAT_MIX)))
        if (micGain) add(pk(readHex(MIC_GAIN)))
        if (touch) add(touchRead)
        if (adaptiveAnc) add(adaptiveRead)
        if (crusher) add(pk(CRUSHER_GET))
    }

    private fun readHex(setting: Int): String = "05 5A 04 00 83 2C ${hex(setting)} 00"
    private fun writeHex(setting: Int, vararg value: Int): String = "05 5A ${hex(4 + value.size)} 00 82 2C ${hex(setting)} 00 " + value.joinToString(" ") { hex(it) }

    /** True when [asked] is the touch key's read, whose [data] is then the key's bytes. */
    fun touchAnswer(data: ByteArray): Boolean {
        if (data.size < 3) return false
        val on = (data[0].toInt() and 0xFF) == 0
        val s1 = (data[2].toInt() and 0xFF) == 1
        val changed = touchOn != on || touchScenario1 != s1
        touchOn = on
        touchScenario1 = s1
        return changed
    }

    /** The module read of adaptive noise cancelling: the first data byte is 1 when it is on. */
    fun moduleAnswer(p: RacePacket): Boolean {
        if (!adaptiveAnc || p.module != MODULE_ADAPTIVE_ANC || p.status != 0 || p.data.isEmpty()) return false
        val on = (p.data[0].toInt() and 0xFF) == 1
        return (adaptive != on).also { adaptive = on }
    }

    /** Takes a reply or notification of the custom pair or of the Crusher's intensity; true when a row changed. */
    fun receive(p: RacePacket): Boolean {
        if (p.type != JblRace.TYPE_RESPONSE && p.type != JblRace.TYPE_INDICATION) return false
        val d = p.payload
        if (p.id == CRUSHER_READ_ID && crusher && p.type == JblRace.TYPE_RESPONSE && d.size >= 2 && d[0].toInt() == 0) {
            val v = d[1].toInt() and 0xFF
            return (intensity != v).also { intensity = v }
        }
        if (p.id != READ_ID) return false
        if (p.type == JblRace.TYPE_INDICATION) {
            // an unasked notice: the setting number, then the value as its last byte (the app's own reading of them)
            if (d.size >= 2 && sidetone && (d[0].toInt() and 0xFF) == SIDETONE_STATE) {
                val on = (d.last().toInt() and 0xFF) == 1
                return (sidetoneOn != on).also { sidetoneOn = on }
            }
            return false
        }
        if (d.size < 4 || d[0].toInt() != 0 || d[2].toInt() != 0) return false
        val value = d[3].toInt() and 0xFF
        return when (d[1].toInt() and 0xFF) {
            SIDETONE_STATE -> sidetone && (value == 1).let { on -> (sidetoneOn != on).also { sidetoneOn = on } }
            SIDETONE_LEVEL -> sidetone && d.size >= 5 && (levelOf(((d[4].toInt() shl 8) or value).toShort().toInt())).let { l -> (sidetoneLevel != l).also { sidetoneLevel = l } }
            CHAT_MIX -> chatMix && (percentLevel(value)).let { l -> (chat != l).also { chat = l } }
            MIC_GAIN -> micGain && (percentLevel(value)).let { l -> (mic != l).also { mic = l } }
            else -> false
        }
    }

    /** The app's slider of a sidetone level, 0 to 100, from Airoha's -40 to 17: `(level + 40) * 100 / 57`; shown in tenths. */
    private fun levelOf(airoha: Int): Int = (((airoha + 40) * 100.0 / 57.0) / 10.0).roundToInt().coerceIn(0, 10)

    /** The Airoha level of a slider in tenths, as `SKDYSidetoneSettings.fromUiLevel` does: `truncate(percent / 100 * 57) - 40`. */
    private fun airohaLevel(tenths: Int): Int = (tenths * 10 / 100.0 * 57.0).toInt() - 40

    private fun percentLevel(value: Int): Int = (value / 10.0).roundToInt().coerceIn(0, 10)

    fun groups(): List<SettingGroup> = buildList {
        val rows = ArrayList<Setting>()
        sidetoneOn?.let { rows += Setting.Toggle(SIDETONE, it) }
        sidetoneLevel?.let { rows += Setting.Level(SIDETONE_LEVEL_KEY, it, 0, 10) }
        chat?.let { rows += Setting.Level(CHAT_MIX_KEY, it, 0, 10) }
        mic?.let { rows += Setting.Level(MIC_GAIN_KEY, it, 0, 10) }
        intensity?.let { rows += Setting.Level(CRUSHER_INTENSITY, percentLevel(it), 0, 10) }
        adaptive?.let { rows += Setting.Toggle(ADAPTIVE_ANC, it) }
        if (rows.isNotEmpty()) add(SettingGroup("sk_extras", SettingTab.SOUND, rows))
        touchOn?.let { add(SettingGroup("sk_touch", SettingTab.CONTROLS, listOf(Setting.Toggle(TOUCH, it)))) }
    }

    /** The commands of a change of one of these rows, or null for another key; [readBacks] gets what to read again. */
    fun change(change: SettingChange, readBacks: (List<RacePacket>) -> Unit): List<RacePacket>? {
        when {
            sidetone && change is SettingChange.Toggle && change.key == SIDETONE -> {
                sidetoneOn = change.on
                readBacks(listOf(pk(readHex(SIDETONE_STATE))))
                return buildList {
                    add(pk(writeHex(SIDETONE_STATE, if (change.on) 1 else 0)))
                    sidetoneLevel?.let { add(levelWrite(it)) }
                }
            }
            sidetone && change is SettingChange.Level && change.key == SIDETONE_LEVEL_KEY -> {
                sidetoneLevel = change.value.coerceIn(0, 10)
                readBacks(listOf(pk(readHex(SIDETONE_LEVEL))))
                return buildList {
                    sidetoneOn?.let { add(pk(writeHex(SIDETONE_STATE, if (it) 1 else 0))) }
                    add(levelWrite(sidetoneLevel!!))
                }
            }
            chatMix && change is SettingChange.Level && change.key == CHAT_MIX_KEY -> {
                chat = change.value.coerceIn(0, 10)
                readBacks(listOf(pk(readHex(CHAT_MIX))))
                return listOf(pk(writeHex(CHAT_MIX, chat!! * 10)))
            }
            micGain && change is SettingChange.Level && change.key == MIC_GAIN_KEY -> {
                mic = change.value.coerceIn(0, 10)
                readBacks(listOf(pk(readHex(MIC_GAIN))))
                return listOf(pk(writeHex(MIC_GAIN, mic!! * 10)))
            }
            crusher && change is SettingChange.Level && change.key == CRUSHER_INTENSITY -> {
                val v = change.value.coerceIn(0, 10) * 10
                intensity = v
                readBacks(listOf(pk(CRUSHER_GET)))
                return listOf(pk("05 5A 03 00 D0 2C ${hex(v)}"))
            }
            adaptiveAnc && change is SettingChange.Toggle && change.key == ADAPTIVE_ANC -> {
                adaptive = change.on
                readBacks(listOf(adaptiveRead))
                return listOf(pk("05 5A 05 00 06 0E 00 22 ${if (change.on) "01" else "00"}"))
            }
            touch && change is SettingChange.Toggle && change.key == TOUCH -> {
                // the app's order: make sure scenario 1 is on, then write scenario 2 (00 is on, 01 off)
                touchOn = change.on
                readBacks(listOf(touchRead))
                return buildList {
                    if (!touchScenario1) add(pk("05 5A 03 00 07 00 01"))
                    add(pk("05 5A 03 00 08 00 ${if (change.on) "00" else "01"}"))
                }
            }
        }
        return null
    }

    private fun levelWrite(tenths: Int): RacePacket {
        val v = airohaLevel(tenths)
        return pk(writeHex(SIDETONE_LEVEL, v and 0xFF, (v shr 8) and 0xFF))
    }

    companion object {
        const val SIDETONE = "sk_sidetone"
        const val SIDETONE_LEVEL_KEY = "sk_sidetone_level"
        const val TOUCH = "sk_touch_control"
        const val CHAT_MIX_KEY = "sk_chat_mix"
        const val MIC_GAIN_KEY = "sk_mic_gain"
        const val ADAPTIVE_ANC = "sk_adaptive_anc"
        const val CRUSHER_INTENSITY = "sk_crusher_intensity"

        /** The custom pair's ids: `2C82` writes, `2C83` reads. */
        const val READ_ID = 0x2C83

        private const val SIDETONE_STATE = 7
        private const val SIDETONE_LEVEL = 6
        private const val CHAT_MIX = 0x0B
        private const val MIC_GAIN = 0x0C

        private const val TOUCH_KEY = 0xF514
        private const val TOUCH_LENGTH = 0x23
        const val MODULE_ADAPTIVE_ANC = 0x1F
        private const val CRUSHER_GET = "05 5A 02 00 D1 2C"
        private const val CRUSHER_READ_ID = 0x2CD1
    }
}
