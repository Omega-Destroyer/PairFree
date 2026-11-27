package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * An Avnera-platform JBL headphone that speaks LightX: the Everest Elite 100, 300, 700 and 750NC and the Reflect Aware (the 150NC's accessory manager wraps the same keys in another header, `accessory`) (`platform` Avnera in JBL Headphones 5.37.10's product list,
 * `EverestControl`). [smartButton], [autoOff] and [prompts] are the product list's `hasProgSmartButton`, `autoOffType` and `hasVoicePrompt`.
 */
public data class AvneraModel(val name: String, val pin: String, val smartButton: Boolean, val autoOff: Boolean, val prompts: Boolean = true, val accessory: Boolean = false)

public object AvneraModels {
    public val ALL: List<AvneraModel> = listOf(
        AvneraModel("JBL Everest Elite 100", "100A", smartButton = true, autoOff = true),
        AvneraModel("JBL Everest Elite 300", "300A", smartButton = true, autoOff = true),
        AvneraModel("JBL Everest Elite 700", "700A", smartButton = true, autoOff = true),
        AvneraModel("JBL Everest Elite 750NC", "750A", smartButton = true, autoOff = true),
        AvneraModel("JBL Reflect Aware", "AWCA", smartButton = false, autoOff = false),
        // The 150NC: the same keys behind the accessory manager's header (docs/jbl/PROTOCOL.md section 12.4).
        AvneraModel("JBL Everest Elite 150NC", "1003", smartButton = true, autoOff = true, accessory = true),
    )

    /** By the Bluetooth name: the model's name, with or without the "JBL" and in any case, and the Everest's "Elite" and the "NC" as the app's list has them. */
    public fun fromName(name: String): AvneraModel? {
        val n = name.trim().uppercase().replace("-", " ").replace(Regex("\\s+"), " ")
        if (!n.contains("EVEREST") && !n.contains("REFLECT AWARE")) return null
        return ALL.firstOrNull { m ->
            val key = m.name.uppercase().removePrefix("JBL ")
            n == m.name.uppercase() || n == key || n.contains(key)
        }
    }

    public fun model(name: String): Model? = fromName(name)?.let { Model(it.name, listOf(it.name), "JBL", form = Form.HEADPHONES) }
}

/**
 * The Everest Elite's and Reflect Aware's settings over Avnera's LightX protocol (docs/jbl/PROTOCOL.md section 12): the battery, noise cancelling on and off, the ambient level, the equalizer's
 * preset and the user's ten bands, auto power-off, voice prompts and the smart button. One request at a time, each answered by a packet with the same key. Only the keys of the
 * application range (128 to 255) the app uses go out; the boot, firmware and configuration keys never do.
 */
public class AvneraTalk(public val model: AvneraModel) : SessionTalk<AvneraPacket>() {
    private val reader = AvneraReader()
    private val known = mutableMapOf<Int, ByteArray>()
    private var readBacks = emptyList<AvneraPacket>()
    private var values = mapOf<Command, Value>()

    /** The last answer to each key, for the device report. */
    public val replies: Map<Int, ByteArray> get() = known

    override val state: EarbudState get() = EarbudState(values = values)
    override val pacing: Pacing = Pacing(replyMs = 1_000, gapMs = 50)

    override val groups: List<SettingGroup>
        get() = listOfNotNull(
            group("noise", SettingTab.HOME, noiseRows()),
            group("av_sound", SettingTab.SOUND, soundRows()),
            group("av_device", SettingTab.DEVICE, deviceRows()),
        )

    private fun group(key: String, tab: SettingTab, rows: List<Setting>): SettingGroup? = rows.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    private fun on(key: Int): Boolean? = number(key)?.let { it != 0 }

    // A one-byte answer (the smart button's `UInt8`) is padded to the word the others come in.
    private fun number(key: Int): Int? = known[key]?.takeIf { it.isNotEmpty() }?.let { AvneraPacket(0, key, it.copyOf(maxOf(4, it.size))).number() }

    private fun noiseRows(): List<Setting> = listOfNotNull(
        on(ANC)?.let { Setting.Toggle(ANC_KEY, it) },
        number(AWARENESS)?.takeIf { it in 1..3 }?.let { Setting.Choice(AMBIENT_KEY, AMBIENT[it - 1], AMBIENT) },
        // How much of the outside each ear lets in: a step from 0 to the headphones' step count less one (`gn.d.u`, `gn.v`'s case 5).
        number(RAW_LEFT)?.let { Setting.Level(RAW_LEFT_KEY, it.coerceIn(0, rawSteps()), 0, rawSteps()) },
        number(RAW_RIGHT)?.let { Setting.Level(RAW_RIGHT_KEY, it.coerceIn(0, rawSteps()), 0, rawSteps()) },
    )

    private fun rawSteps(): Int = (number(RAW_STEPS) ?: DEFAULT_RAW_STEPS + 1).minus(1).coerceIn(1, 32)

    private fun soundRows(): List<Setting> {
        val preset = number(EQ_PRESET)?.takeIf { it in PRESETS.indices } ?: return emptyList()
        return buildList {
            add(Setting.Choice(EQ_KEY, PRESETS[preset], PRESETS))
            // The ten bands of the user's own curve, as the app's graphic EQ shows them (-10 to 10 dB).
            if (preset == USER) bands()?.forEachIndexed { i, g -> add(Setting.Level("$BAND_KEY$i", g.coerceIn(-10, 10), -10, 10)) }
        }
    }

    private fun deviceRows(): List<Setting> = listOfNotNull(
        on(AUTO_OFF)?.takeIf { model.autoOff }?.let { Setting.Toggle(AUTO_OFF_KEY, it) },
        on(PROMPTS)?.takeIf { model.prompts }?.let { Setting.Toggle(PROMPTS_KEY, it) },
        on(SMART_BUTTON)?.takeIf { model.smartButton }?.let { Setting.Toggle(SMART_KEY, it) },
    )

    /** The band settings' answer: the preset and the count, then each band's gain in a word of its own (`gn.v`, case 9). */
    private fun bands(): List<Int>? = known[BANDS]?.takeIf { it.size >= 48 }?.let { b -> (0 until 10).map { b[8 + 4 * it].toInt() } }

    override fun start(): List<AvneraPacket> = buildList {
        add(read(FIRMWARE))
        add(read(BATTERY))
        add(read(ANC))
        add(read(AWARENESS))
        add(read(EQ_PRESET))
        add(read(RAW_STEPS))
        add(read(RAW_LEFT))
        add(read(RAW_RIGHT))
        if (model.autoOff) add(read(AUTO_OFF))
        if (model.prompts) add(read(PROMPTS))
        if (model.smartButton) add(read(SMART_BUTTON))
        add(bandRead(USER))
    }

    override fun poll(): List<AvneraPacket> = listOf(read(BATTERY))

    /** A read: the LightX app sends 256 zero bytes, the accessory manager as many as the answer holds (`z6.f0`: the larger of the two formats). */
    private fun read(key: Int): AvneraPacket = AvneraPacket.read(key, ByteArray(if (model.accessory) 4 else 256))

    override fun allowed(message: AvneraPacket): Boolean = message.key in ALLOWED && message.opcode in listOf(AvneraPacket.READ, AvneraPacket.WRITE)

    override fun bytes(message: AvneraPacket): ByteArray = (if (model.accessory) accessory(message) else message).bytes()

    /** The accessory manager's header: the module 0x43 as destination and the id's class byte (`F0` of `F085`) as the top of the 24-bit offset (`z6.s`, `z6.f0.a`, `z6.x0.z0`). */
    private fun accessory(m: AvneraPacket): AvneraPacket {
        val cls = CLASSES[m.key] ?: return m
        return AvneraPacket(m.opcode, m.key, m.buffer, m.status, cls shl 16, m.source, AvneraPacket.ACCESSORY)
    }

    override fun answers(sent: AvneraPacket, got: Any): Boolean = got is AvneraPacket && got.key == sent.key

    override fun takeReadBacks(): List<AvneraPacket> = readBacks.also { readBacks = emptyList() }

    private fun bandRead(preset: Int): AvneraPacket {
        val request = ByteArray(48)
        request[0] = preset.toByte()
        request[4] = BAND_COUNT.toByte()
        return AvneraPacket.read(BANDS, request)
    }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val packets = reader.feed(bytes)
        for (p in packets) guarded {
            log?.invoke("recv $p")
            val b = p.buffer ?: return@guarded
            if (p.status != 0 || p.opcode != AvneraPacket.READ && p.opcode != AvneraPacket.WRITE) return@guarded
            known[p.key] = b
            changed = true
            when (p.key) {
                BATTERY -> values = values + (Command.BATTERY_LEFT to Value.Number(p.number().coerceIn(0, 100)))
                FIRMWARE -> if (b.size >= 3) values = values + (Command.FIRMWARE_VERSION to Value.Text("${model.name}_${b[0]}.${b[1]}.${b[2]}"))
            }
        }
        return Fed(changed, packets)
    }

    override fun set(command: Command, value: Int): List<AvneraPacket>? = null

    override fun change(change: SettingChange): List<AvneraPacket>? {
        val k = change.key
        return when (change) {
            is SettingChange.Toggle -> when (k) {
                ANC_KEY -> bool(ANC, change.on)
                AUTO_OFF_KEY -> if (model.autoOff) bool(AUTO_OFF, change.on) else null
                PROMPTS_KEY -> if (model.prompts) bool(PROMPTS, change.on) else null
                SMART_KEY -> if (model.smartButton) bool(SMART_BUTTON, change.on) else null
                else -> null
            }
            is SettingChange.Choice -> when (k) {
                AMBIENT_KEY -> AMBIENT.indexOf(change.option).takeIf { it >= 0 }?.let { number(AWARENESS, it + 1) }
                EQ_KEY -> PRESETS.indexOf(change.option).takeIf { it >= 0 }?.let { number(EQ_PRESET, it)?.also { _ -> readBacks += bandRead(USER) } }
                else -> null
            }
            is SettingChange.Level -> when {
                k == RAW_LEFT_KEY -> number(RAW_LEFT, change.value.coerceIn(0, rawSteps()))
                k == RAW_RIGHT_KEY -> number(RAW_RIGHT, change.value.coerceIn(0, rawSteps()))
                k.startsWith(BAND_KEY) -> band(k.removePrefix(BAND_KEY).toIntOrNull() ?: return null, change.value)
                else -> null
            }
            is SettingChange.Press -> null
        }
    }

    private fun bool(key: Int, on: Boolean): List<AvneraPacket> = number(key, if (on) 1 else 0)!!

    private fun number(key: Int, value: Int): List<AvneraPacket>? {
        known[key] = AvneraPacket.write(key, value).buffer!!
        readBacks += read(key)
        return listOf(AvneraPacket.write(key, value))
    }

    /** `o.E0`: the preset, the band and the gain in dB as signed words, the user's preset only. */
    private fun band(index: Int, gain: Int): List<AvneraPacket>? {
        if (index !in 0 until BAND_COUNT || gain !in -10..10) return null
        if (number(EQ_PRESET) != USER) return null
        val current = known[BANDS]?.copyOf()
        if (current != null && current.size >= 48) AvneraPacket.write(0, gain).buffer!!.copyInto(current, 8 + 4 * index)
        current?.let { known[BANDS] = it }
        readBacks += bandRead(USER)
        return listOf(AvneraPacket.write(BAND, USER, index, gain))
    }

    override fun describe(message: AvneraPacket): String = message.toString()

    public companion object {
        // The application keys the app reads and writes (a7.k, with the DataBinderMapperImpl constants resolved).
        public const val AUDIO_EQ_PRESET: Int = 129
        public const val ANC: Int = 133
        public const val AWARENESS: Int = 134
        public const val FIRMWARE: Int = 135
        public const val RAW_LEFT: Int = 137
        public const val RAW_RIGHT: Int = 138
        public const val RAW_STEPS: Int = 139
        public const val EQ_PRESET: Int = 146
        public const val BANDS: Int = 148
        public const val BAND: Int = 149
        public const val BATTERY: Int = 176
        public const val SMART_BUTTON: Int = 181
        public const val AUTO_OFF: Int = 182
        public const val PROMPTS: Int = 183
        /** The class byte of each key's id in the accessory manager's table (`F085` is key 0x85 of class 0xF0). */
        private val CLASSES: Map<Int, Int> = mapOf(
            ANC to 0xF0, AWARENESS to 0xAA, RAW_LEFT to 0xAA, RAW_RIGHT to 0xAA, RAW_STEPS to 0xAA, FIRMWARE to 0x01, BATTERY to 0x01, AUTO_OFF to 0x01, PROMPTS to 0x01,
            SMART_BUTTON to 0xB0, EQ_PRESET to 0xF1, BANDS to 0xF3, BAND to 0xF3,
        )
        private val ALLOWED = setOf(ANC, AWARENESS, RAW_LEFT, RAW_RIGHT, RAW_STEPS, FIRMWARE, EQ_PRESET, BANDS, BAND, BATTERY, SMART_BUTTON, AUTO_OFF, PROMPTS)

        public const val ANC_KEY: String = "jbl_av_anc"
        public const val AMBIENT_KEY: String = "jbl_av_ambient"
        public const val RAW_LEFT_KEY: String = "jbl_av_raw_left"
        public const val RAW_RIGHT_KEY: String = "jbl_av_raw_right"
        private const val DEFAULT_RAW_STEPS = 7
        public const val EQ_KEY: String = "jbl_av_eq"
        public const val BAND_KEY: String = "jbl_av_band_"
        public const val AUTO_OFF_KEY: String = "jbl_av_auto_off"
        public const val PROMPTS_KEY: String = "jbl_av_prompts"
        public const val SMART_KEY: String = "jbl_av_smart_button"

        public val AMBIENT: List<String> = listOf("low", "medium", "high")
        public val PRESETS: List<String> = listOf("off", "jazz", "vocal", "bass", "user")
        private const val USER = 4
        private const val BAND_COUNT = 10
    }
}
