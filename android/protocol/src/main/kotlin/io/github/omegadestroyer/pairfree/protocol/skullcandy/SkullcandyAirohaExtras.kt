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
    val thx: Boolean = sdk && "thxSpatialAudio" in product.features
    val thxHead: Boolean = sdk && "thxHeadTracking" in product.features
    val led: Boolean = sdk && "ledCustomizer" in product.features

    val has: Boolean get() = sidetone || chatMix || micGain || touch || adaptiveAnc || crusher || thx || thxHead || led

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
    private var spatial: Boolean? = null
    private var head: Boolean? = null
    private var spatialLevel: Int? = null
    private var ledMode: Int? = null
    private var ledBrightness: Int? = null
    private var ledColor: Triple<Int, Int, Int>? = null

    /** The LED reads (attribute 0 colour, 1 brightness, 2 mode): the custom read `83 2C` of setting `23`; an answer does not name the attribute, so it is matched to the read asked. */
    private val ledReads: Map<RacePacket, Int> by lazy { (0..2).associate { pk("05 5A 05 00 83 2C 23 00 0$it") to it } }

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
        if (thx) { add(pk(thxHex(THX_READ_SPATIAL))); add(pk(thxHex(THX_READ_INTENSITY))) }
        if (thxHead) add(pk(thxHex(THX_READ_HEAD)))
        if (led) addAll(ledReads.keys)
    }

    /** An answer of one of the LED reads: `05 5B 08 00 83 2C 00 23 00` and three bytes; the colour is those three, the brightness the first and the mode the last (`GetLedStatusRaceCommand.decodeResponse`). */
    fun ledAnswer(asked: RacePacket?, p: RacePacket): Boolean {
        val attribute = ledReads[asked ?: return false] ?: return false
        val d = p.payload
        if (p.type != JblRace.TYPE_RESPONSE || d.size != 6 || d[0].toInt() != 0 || (d[1].toInt() and 0xFF) != LED_SETTING || d[2].toInt() != 0) return false
        fun u(i: Int) = d[3 + i].toInt() and 0xFF
        return when (attribute) {
            0 -> Triple(u(0), u(1), u(2)).let { c -> (ledColor != c).also { ledColor = c } }
            1 -> (ledBrightness != u(0)).also { ledBrightness = u(0) }
            else -> (ledMode != u(2)).also { ledMode = u(2) }
        }
    }

    private fun ledModeKey(code: Int?): String? = when (code) { 0 -> "off"; 1 -> "breathing"; 2 -> "solid"; 4 -> "random"; else -> null }

    /** The LED colour's nearest named colour, null when none is close. */
    private fun ledColorName(c: Triple<Int, Int, Int>): String? =
        LED_COLORS.entries.firstOrNull { (_, v) -> kotlin.math.abs(v.first - c.first) + kotlin.math.abs(v.second - c.second) + kotlin.math.abs(v.third - c.third) <= 60 }?.key

    /** THX's race id `01F4` (written in that order on the wire), a sub-command and its value bytes (section 9.8). */
    private fun thxHex(sub: Int, vararg value: Int): String = "05 5A ${hex(4 + value.size)} 00 01 F4 00 ${hex(sub)} " + value.joinToString(" ") { hex(it) }

    /**
     * An answer of the THX commands: the status byte, the sub-command, then what it carries. The app's `hasEnabledPayload` takes the bytes after the sub-command as a hex number and calls it on when that is 1
     * (read from the compiled code at 0x68400c), for a write's answer as for a read's; the intensity is the bytes after `11` as a big endian number over 65535.
     */
    private fun thxAnswer(d: ByteArray): Boolean {
        if (d.size < 3 || d[0].toInt() != 0) return false
        val sub = d[1].toInt() and 0xFF
        var rest = 0L
        for (i in 2 until d.size) rest = (rest shl 8) or (d[i].toLong() and 0xFF)
        val on = rest == 1L
        return when (sub) {
            THX_SPATIAL_ON, THX_SPATIAL_OFF, THX_READ_SPATIAL -> thx && (spatial != on).also { spatial = on }
            THX_HEAD_ON, THX_HEAD_OFF, THX_READ_HEAD -> thxHead && (head != on).also { head = on }
            THX_READ_INTENSITY -> thx && (rest * 10.0 / 65535.0).roundToInt().coerceIn(0, 10).let { l -> (spatialLevel != l).also { spatialLevel = l } }
            else -> false
        }
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
        if (p.id == THX_ID && (thx || thxHead) && p.type == JblRace.TYPE_RESPONSE) return thxAnswer(d)
        if (p.id != READ_ID) return false
        // the earbuds send no notice of these (the SDK's notices are type 5C on 2C82 and list only the touch state, the batteries and a few more), so a change is read back
        if (p.type == JblRace.TYPE_INDICATION) return false
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
        spatial?.let { rows += Setting.Toggle(THX_SPATIAL, it) }
        if (spatial == true) spatialLevel?.let { rows += Setting.Level(THX_INTENSITY, it, 0, 10) }
        head?.let { rows += Setting.Toggle(THX_HEAD, it) }
        if (led) {
            val ledRows = ArrayList<Setting>()
            ledMode?.let { m -> ledModeKey(m)?.let { ledRows += Setting.Choice(LED_MODE, it, listOf("off", "breathing", "solid", "random")) } }
            ledBrightness?.let { ledRows += Setting.Level(LED_BRIGHTNESS, (it * 10.0 / 255.0).roundToInt().coerceIn(0, 10), 0, 10) }
            ledColor?.let { ledRows += Setting.Choice(LED_COLOR, ledColorName(it), LED_COLORS.keys.toList()) }
            if (ledRows.isNotEmpty()) add(SettingGroup("sk_led", SettingTab.DEVICE, ledRows))
        }
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
            led && change is SettingChange.Choice && change.key == LED_MODE -> {
                // `85 2C`, then 03 and the pair the app builds for the mode (off 02 00, random 02 09, breathing 01 01, solid 02 01)
                val pair = when (change.option) { "off" -> "02 00"; "random" -> "02 09"; "breathing" -> "01 01"; "solid" -> "02 01"; else -> return null }
                readBacks(listOf(ledReads.entries.first { it.value == 2 }.key))
                return listOf(pk("05 5A 05 00 85 2C 03 $pair"))
            }
            led && change is SettingChange.Level && change.key == LED_BRIGHTNESS -> {
                // `86 2C` and the value three times (the template FF FF FF with each byte replaced)
                val v = hex((change.value.coerceIn(0, 10) * 255.0 / 10.0).roundToInt())
                readBacks(listOf(ledReads.entries.first { it.value == 1 }.key))
                return listOf(pk("05 5A 05 00 86 2C $v $v $v"))
            }
            led && change is SettingChange.Choice && change.key == LED_COLOR -> {
                // `88 2C`, the struct's first field (1 in breathing mode, else 2) and red, green, blue
                val c = LED_COLORS[change.option] ?: return null
                readBacks(listOf(ledReads.entries.first { it.value == 0 }.key))
                return listOf(pk("05 5A 06 00 88 2C ${if (ledMode == 1) "01" else "02"} ${hex(c.first)} ${hex(c.second)} ${hex(c.third)}"))
            }
            thx && change is SettingChange.Toggle && change.key == THX_SPATIAL -> {
                spatial = change.on
                readBacks(listOf(pk(thxHex(THX_READ_SPATIAL))))
                return listOf(pk(thxHex(if (change.on) THX_SPATIAL_ON else THX_SPATIAL_OFF)))
            }
            thx && change is SettingChange.Level && change.key == THX_INTENSITY -> {
                spatialLevel = change.value.coerceIn(0, 10)
                val v = (spatialLevel!! * 10 / 100.0 * 65535.0).toInt()
                readBacks(listOf(pk(thxHex(THX_READ_INTENSITY))))
                return listOf(pk(thxHex(THX_INTENSITY_SET, v shr 8, v and 0xFF)))
            }
            thxHead && change is SettingChange.Toggle && change.key == THX_HEAD -> {
                head = change.on
                readBacks(listOf(pk(thxHex(THX_READ_HEAD))))
                return listOf(pk(thxHex(if (change.on) THX_HEAD_ON else THX_HEAD_OFF)))
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
        const val LED_MODE = "sk_led_mode"
        const val LED_BRIGHTNESS = "sk_led_brightness"
        const val LED_COLOR = "sk_led_color"
        private const val LED_SETTING = 0x23

        /** The named colours the colour row offers (PairFree's own palette; the app has a colour picker). */
        val LED_COLORS: Map<String, Triple<Int, Int, Int>> = linkedMapOf(
            "red" to Triple(255, 0, 0), "orange" to Triple(255, 128, 0), "yellow" to Triple(255, 255, 0), "green" to Triple(0, 255, 0),
            "cyan" to Triple(0, 255, 255), "blue" to Triple(0, 0, 255), "purple" to Triple(128, 0, 255), "pink" to Triple(255, 0, 255), "white" to Triple(255, 255, 255),
        )
        const val THX_SPATIAL = "sk_thx_spatial"
        const val THX_HEAD = "sk_thx_head"
        const val THX_INTENSITY = "sk_thx_intensity"

        /** THX's race id and its sub-commands (`RaceCommand.thxRaceId` and the `Set`/`ReadThx*` classes). */
        private const val THX_ID = 0xF401
        private const val THX_SPATIAL_ON = 0x0A
        private const val THX_SPATIAL_OFF = 0x0B
        private const val THX_READ_SPATIAL = 0x0C
        private const val THX_HEAD_ON = 0x0D
        private const val THX_HEAD_OFF = 0x0E
        private const val THX_READ_HEAD = 0x0F
        private const val THX_INTENSITY_SET = 0x10
        private const val THX_READ_INTENSITY = 0x11

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
