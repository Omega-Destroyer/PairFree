package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to AirPods or Apple-chip Beats over Apple's accessory protocol (docs/apple/PROTOCOL.md),
 * shown on PairFree's screens. After the handshake the AirPods send their battery, their settings as
 * control commands, and an information packet with the model number and firmware; they send each
 * change too, and answer a write with the same packet. A setting shows once the AirPods have reported
 * it. Ported from LibrePods (github.com/kavishdevar/librepods, GPL-3.0-or-later); the setting values
 * are checked against MagicPodsCore (github.com/steam3d/MagicPodsCore).
 */
public class AppleSession {
    /** What the shared screens show. */
    public var state: EarbudState = EarbudState()
        private set

    /** The model number the AirPods reported ("A3048"), and the model it names. */
    public var modelNumber: String? = null
        private set

    public val model: AppleModel? get() = AppleModels.byModelNumber(modelNumber)

    /** The first version string of the information packet. */
    public var firmware: String? = null
        private set

    /** The name the AirPods advertise, kept for the screens only: the device report leaves it out. */
    private var advertisedName: String? = null

    /** Each control command's last value, by id. */
    public val controls: Map<Int, ByteArray> get() = _controls
    private val _controls = linkedMapOf<Int, ByteArray>()

    /** The last battery packet's data, for the device report. */
    public var batteryData: ByteArray? = null
        private set

    /** The mode before Adaptive was switched on, to go back to when it is switched off. */
    private var beforeAdaptive: Int = MODE_TRANSPARENCY

    /** The frames that open the conversation, in order (LibrePods sends them 200 ms apart, and again 5 s later). */
    public fun start(): List<ByteArray> = listOf(
        AppleWire.HANDSHAKE,
        AapPacket.of(AppleWire.FEATURE_FLAGS).bytes(),
        AapPacket.of(AppleWire.NOTIFICATIONS).bytes(),
    )

    /** False for what PairFree never sends: smart routing, keys, renaming, ownership and pairing settings. */
    public fun allowed(frame: ByteArray): Boolean {
        val packet = AapPacket.parse(frame) ?: return frame.contentEquals(AppleWire.HANDSHAKE)
        if (packet.opcode in AppleOpcode.NEVER_SEND) return false
        return packet.control?.first !in AppleControl.NEVER_SEND
    }

    /** Applies one read from the AirPods. Returns true when [state] or [groups] changed. */
    public fun receive(bytes: ByteArray): Boolean {
        val packet = AapPacket.parse(bytes) ?: return false
        val before = state to groups
        when (packet.opcode) {
            AppleOpcode.BATTERY -> battery(packet.data)
            AppleOpcode.CONTROL -> packet.control?.let { (id, value) -> _controls[id] = value }
            AppleOpcode.INFORMATION -> information(packet.data)
        }
        refresh()
        return (state to groups) != before
    }

    /**
     * The battery: a count, then per battery its component (1 single, 2 right, 4 left, 8 case), 01, the
     * level, the status (1 charging, 2 not, 4 disconnected) and 01. A disconnected battery is unknown.
     */
    private fun battery(d: ByteArray) {
        if (d.isEmpty()) return
        batteryData = d.copyOf()
        val values = mutableMapOf<Command, Value>()
        for (i in 0 until d.u8(0)) {
            val at = 1 + i * BATTERY_RECORD
            if (at + BATTERY_RECORD - 1 >= d.size) break
            val level = d.u8(at + 2).takeIf { it <= 100 && d.u8(at + 3) != DISCONNECTED }
            when (d.u8(at)) {
                SINGLE, LEFT -> values[Command.BATTERY_LEFT] = Value.Number(level)
                RIGHT -> values[Command.BATTERY_RIGHT] = Value.Number(level)
                CASE -> values[Command.BATTERY_CASE] = Value.Number(level)
            }
        }
        state = state.copy(values = state.values + values)
    }

    /**
     * The information packet: null-terminated strings after a few bytes LibrePods skips (to the first
     * 00, and the short string after it): name, model number, manufacturer, serial number, two
     * versions and more. PairFree keeps the model number and the first version.
     */
    private fun information(d: ByteArray) {
        var i = 0
        while (i < d.size && d[i] != 0.toByte()) i++
        val strings = mutableListOf<String>()
        while (i < d.size) {
            while (i < d.size && d[i] == 0.toByte()) i++
            if (i >= d.size) break
            val start = i
            while (i < d.size && d[i] != 0.toByte()) i++
            strings += d.copyOfRange(start, i).decodeToString()
        }
        if (strings.isNotEmpty()) strings.removeAt(0)
        advertisedName = strings.getOrNull(INFO_NAME)
        modelNumber = strings.getOrNull(INFO_MODEL)?.takeIf { it.isNotBlank() }
        firmware = strings.getOrNull(INFO_VERSION)?.takeIf { it.isNotBlank() }
    }

    /** Puts the firmware and the listening mode where the shared screens read them. */
    private fun refresh() {
        val values = buildMap {
            firmware?.let { put(Command.FIRMWARE_VERSION, Value.Text("${displayName}_$it")) }
            listeningMode?.let { put(Command.ANC_MODE, Value.Number(ancNumberOf(it))) }
        }
        state = state.copy(values = state.values + values)
    }

    /** The model's name, else the name the AirPods advertise, else a plain one. */
    public val displayName: String get() = model?.name ?: advertisedName?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME

    private val listeningMode: Int? get() = _controls[AppleControl.LISTENING_MODE]?.firstOrNull()?.toInt()?.and(0xFF)

    /** Adaptive is offered on models that have it, and on any that report being in it. */
    private val hasAdaptive: Boolean get() = model?.adaptive == true || listeningMode == MODE_ADAPTIVE

    private fun switch(id: Int): Boolean? = _controls[id]?.firstOrNull()?.toInt()?.let { it == AppleWire.ON }

    private fun choice(id: Int): String? = _controls[id]?.firstOrNull()?.toInt()?.and(0xFF)?.let { AppleControl.VALUES[id]?.get(it) }

    /** Every setting the AirPods reported, grouped by heading and tab. */
    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            group(
                "noise", SettingTab.HOME,
                listOfNotNull(
                    listeningMode?.takeIf { hasAdaptive }?.let { Setting.Toggle("adaptive", it == MODE_ADAPTIVE) },
                    _controls[AppleControl.ADAPTIVE_LEVEL]?.takeIf { hasAdaptive }?.let {
                        Setting.Level("adaptive_level", (it[0].toInt() and 0xFF).coerceIn(0, ADAPTIVE_MAX), 0, ADAPTIVE_MAX)
                    },
                    switch(AppleControl.CONVERSATION_AWARENESS)?.let { Setting.Toggle("conversation_awareness", it) },
                    switch(AppleControl.ONE_BUD_ANC)?.let { Setting.Toggle("noise_one_earbud", it) },
                    switch(AppleControl.PERSONALIZED_VOLUME)?.let { Setting.Toggle("personalized_volume", it) },
                ),
            ),
            group(
                "touch", SettingTab.CONTROLS,
                listOfNotNull(
                    choiceRow("press_speed", AppleControl.PRESS_SPEED),
                    choiceRow("hold_duration", AppleControl.HOLD_DURATION),
                    switch(AppleControl.VOLUME_SWIPE)?.let { Setting.Toggle("volume_swipe", it) },
                    choiceRow("volume_swipe_length", AppleControl.VOLUME_SWIPE_LENGTH),
                ),
            ),
            group(
                "other", SettingTab.DEVICE,
                listOfNotNull(
                    switch(AppleControl.EAR_DETECTION)?.let { Setting.Toggle("ear_detection", it) },
                    switch(AppleControl.ALLOW_OFF)?.let { Setting.Toggle("allow_off", it) },
                ),
            ),
        )

    private fun choiceRow(key: String, id: Int): Setting? {
        if (id !in _controls) return null
        return Setting.Choice(key, choice(id), AppleControl.VALUES.getValue(id).toSortedMap().values.toList())
    }

    private fun group(key: String, tab: SettingTab, settings: List<Setting>) = settings.takeIf { it.isNotEmpty() }?.let { SettingGroup(key, tab, it) }

    /**
     * The packets for a change from the screens, or null when the AirPods have not reported the setting.
     * It shows at once; the AirPods answer with the same packet.
     */
    public fun change(change: SettingChange): List<AapPacket>? {
        val setting = groups.flatMap { it.settings }.firstOrNull { it.key == change.key } ?: return null
        return when (change) {
            is SettingChange.Toggle -> {
                if (setting !is Setting.Toggle) return null
                if (change.key == "adaptive") {
                    val current = listeningMode ?: return null
                    if (change.on && current != MODE_ADAPTIVE) beforeAdaptive = current
                    return listOf(write(AppleControl.LISTENING_MODE, if (change.on) MODE_ADAPTIVE else beforeAdaptive))
                }
                val id = TOGGLES[change.key] ?: return null
                listOf(write(id, if (change.on) AppleWire.ON else AppleWire.OFF))
            }
            is SettingChange.Choice -> {
                if (setting !is Setting.Choice) return null
                val id = CHOICES[change.key] ?: return null
                val value = AppleControl.VALUES.getValue(id).entries.firstOrNull { it.value == change.option }?.key ?: return null
                listOf(write(id, value))
            }
            is SettingChange.Level -> {
                if (setting !is Setting.Level || change.key != "adaptive_level") return null
                listOf(write(AppleControl.ADAPTIVE_LEVEL, change.value.coerceIn(0, ADAPTIVE_MAX)))
            }
            is SettingChange.Press -> null
        }
    }

    /** The listening mode from the shared screens: off, noise cancelling or transparency. */
    public fun set(command: Command, value: Int): List<AapPacket>? {
        if (command != Command.ANC_MODE || listeningMode == null) return null
        val mode = when (value) {
            ancNumber("off") -> MODE_OFF
            ancNumber("anc") -> MODE_ANC
            ancNumber("transparency") -> MODE_TRANSPARENCY
            else -> return null
        }
        return listOf(write(AppleControl.LISTENING_MODE, mode))
    }

    /** A control command, applied to the state at once; the AirPods' answer confirms or replaces it. */
    private fun write(id: Int, value: Int): AapPacket {
        _controls[id] = byteArrayOf(value.toByte())
        refresh()
        return AapPacket.control(id, value)
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    private companion object {
        const val BATTERY_RECORD = 5
        const val SINGLE = 1
        const val RIGHT = 2
        const val LEFT = 4
        const val CASE = 8
        const val DISCONNECTED = 4

        // Information strings, after the one LibrePods drops.
        const val INFO_NAME = 0
        const val INFO_MODEL = 1
        const val INFO_VERSION = 4

        // Listening mode values (data/apple/commands.json): 1 off, 2 noise cancelling, 3 transparency, 4 adaptive.
        const val MODE_OFF = 1
        const val MODE_ANC = 2
        const val MODE_TRANSPARENCY = 3
        const val MODE_ADAPTIVE = 4

        const val ADAPTIVE_MAX = 100
        const val DEFAULT_NAME = "AirPods"

        val TOGGLES = mapOf(
            "conversation_awareness" to AppleControl.CONVERSATION_AWARENESS,
            "noise_one_earbud" to AppleControl.ONE_BUD_ANC,
            "personalized_volume" to AppleControl.PERSONALIZED_VOLUME,
            "volume_swipe" to AppleControl.VOLUME_SWIPE,
            "ear_detection" to AppleControl.EAR_DETECTION,
            "allow_off" to AppleControl.ALLOW_OFF,
        )
        val CHOICES = mapOf(
            "press_speed" to AppleControl.PRESS_SPEED,
            "hold_duration" to AppleControl.HOLD_DURATION,
            "volume_swipe_length" to AppleControl.VOLUME_SWIPE_LENGTH,
        )

        // The noise modes by their names in data/soundpeats/commands.json, so no number is retyped.
        fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

        fun ancNumberOf(mode: Int): Int? = when (mode) {
            MODE_OFF -> ancNumber("off")
            MODE_ANC -> ancNumber("anc")
            MODE_TRANSPARENCY -> ancNumber("transparency")
            MODE_ADAPTIVE -> ancNumber("adaptive")
            else -> null
        }
    }
}
