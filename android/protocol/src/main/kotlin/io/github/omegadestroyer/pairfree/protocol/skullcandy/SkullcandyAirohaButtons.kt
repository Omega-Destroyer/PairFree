package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAirohaGesture
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RaceChip
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket

/**
 * The button settings of Skullcandy's Airoha models without an SDK configuration (docs/skullcandy/PROTOCOL.md section 9.5; data/skullcandy/buttons.json), two ways, as the app does them:
 *
 * - **Airoha's own gesture API** (`getGestureStatus` and `setGestureStatus`), for the earbuds and the Crusher 900 ANC: the key map flow of [JblAirohaGesture] (NV key `F2D6` or `F2E7` with the copy
 *   in `F2EF` and the reload, relayed to the other earbud; the AB1562's key map commands), with the tables of Airoha's own SDK, keyed by Airoha's action ids. It needs the chip.
 * - **The app's own key map code**, for the four products with a vendor button id (Icon ANC, Aviator 900 ANC, Hesh 540 ANC, Crusher 720): NV key `F2D6` read, and written as the command `F2D6` with
 *   the entries; only the changed press's entries of that button are replaced, every other entry is written back as it was read.
 *
 * Rows are one per press the manifest gives a default for, per earbud on earbuds; their options are the manifest's `mappable_buttons` that the route can write.
 */
internal class SkullcandyAirohaButtons(private val product: SkullcandyProduct) {
    /** True for a product whose buttons this class serves. */
    val has: Boolean = product.usesAirohaSdk && "customButtonActions" in product.features && product.defaultButtons.isNotEmpty()

    /** True when the app reads and writes the key map itself (a vendor button id). */
    val vendor: Boolean = has && product.enumName in SkullcandyButtonTables.VENDOR_MODELS

    /** True when Airoha's gesture API serves the buttons, which needs the chip. */
    val generic: Boolean = has && !vendor

    private val earbuds: Boolean = product.form != Form.HEADPHONES
    private val vendorKey: Int? = product.defaultButtons.values.flatten().firstNotNullOfOrNull { it.keyId }?.toInt(16)

    /** The presses with a row, in the app's order. */
    private val presses: List<String> = PRESSES.filter { it in product.defaultButtons }

    /** Every command these flows built, which [SkullcandyRaceTalk.allowed] lets through. */
    val sent: MutableSet<RacePacket> = mutableSetOf()

    // Airoha's gesture API: the flow under way, and what the last read found.
    private var flow: JblAirohaGesture? = null
    private var variant: String? = null
    private var keyId: Int? = null
    private var agentRight = false
    private val gestures = HashMap<Int, Int>()
    private var started = false

    // The vendor key map, as the last read found it, in eight-byte entries.
    private var map: List<ByteArray>? = null

    /** The vendor key map's read: NV read of `F2D6`, length 100, as the SDK reads that key. */
    val vendorRead: RacePacket = RacePacket.nvRead(SkullcandyButtonTables.VENDOR_NV_KEY, JblRace.GESTURE_READ_LENGTH).also { sent += it }

    /** True while Airoha's gesture flow is waiting on the earbuds. */
    val busy: Boolean get() = flow?.let { !it.done && !it.failed } == true

    /** The first command of the read, once: the vendor read at once, Airoha's flow once the chip is known. Null when there is nothing to start. */
    fun start(chip: RaceChip?): RacePacket? {
        if (started || !has) return null
        if (vendor) {
            started = true
            return vendorRead
        }
        val c = chip ?: return null
        started = true
        val f = JblAirohaGesture.read(c, null, earbuds, SKULLCANDY_AIROHA_GESTURE_VARIANTS)
        flow = f
        return f.start()?.also { sent += it }
    }

    /** Takes [p] when it answers the flow under way; true when the rows may have changed. */
    fun receive(p: RacePacket, next: (RacePacket) -> Unit): Boolean {
        val f = flow ?: return false
        if (f.done || f.failed) return false
        if (!f.receive(p) { sent += it; next(it) }) return false
        if (f.done) {
            variant = f.name
            agentRight = f.agentRight
            f.keyId?.let { keyId = it }
            gestures.clear()
            gestures += f.gestures
            flow = null
            return true
        }
        if (f.failed) flow = null
        return f.failed
    }

    /** The vendor key map read's answer. */
    fun vendorAnswer(data: ByteArray): Boolean {
        map = (0 until data.size / ENTRY).map { data.copyOfRange(it * ENTRY, it * ENTRY + ENTRY) }
        return true
    }

    private fun short(press: String): String = when (press) {
        "singlePress" -> "single"
        "doublePress" -> "double"
        "triplePress" -> "triple"
        else -> "hold"
    }

    private fun key(press: String, side: String?): String = if (side == null) "sk_button_${short(press)}" else "sk_rbutton_${side}_${short(press)}"

    /** The press and side ("left", "right", or null on headphones) of a row's key, or null for another key. */
    private fun rowOf(key: String): Pair<String, String?>? {
        for (press in presses) for (side in sides()) if (key(press, side) == key) return press to side
        return null
    }

    private fun sides(): List<String?> = if (earbuds && !vendor) listOf("left", "right") else listOf(null)

    /** The Airoha gesture id of [press] on [side]; on headphones the side the earbuds' parity gives (the one PairFree talks to). */
    private fun gestureId(press: String, side: String?): Int? {
        val ids = (if (product.enumName == "mod") SkullcandyButtonTables.GESTURES_MOD[press] else null) ?: SkullcandyButtonTables.GESTURES[press] ?: return null
        val right = if (side == null) agentRight else side == "right"
        return if (right) ids.second else ids.first
    }

    /** The Airoha action id the app sends for [action] on this product. */
    private fun actionId(action: String): Int? =
        if (action == "stayAwareOnOff" && product.enumName in SkullcandyButtonTables.STAY_AWARE_SWITCH_MODELS) SkullcandyButtonTables.STAY_AWARE_SWITCH else SkullcandyButtonTables.ACTIONS[action]

    /** The manifest's action for an Airoha action id read back (5 and 10 are both the stay-aware switch), or the press's default for the firmware's default. */
    private fun actionOf(id: Int, press: String, side: String?): String? = when (id) {
        SkullcandyButtonTables.DEFAULT -> defaultOf(press, side)
        SkullcandyButtonTables.STAY_AWARE_SWITCH -> "stayAwareOnOff"
        else -> SkullcandyButtonTables.ACTIONS.entries.firstOrNull { it.value == id }?.key
    }

    private fun defaultOf(press: String, side: String?): String? =
        product.defaultButtons[press]?.let { list -> list.firstOrNull { it.side == side } ?: list.firstOrNull() }?.action

    /** The options of a row: the manifest's actions this route can write on these earbuds. */
    private fun options(): List<String> = product.mappableButtons.filter { a ->
        if (vendor) a in SkullcandyButtonTables.VENDOR_EVENTS
        else {
            val id = actionId(a)
            id != null && (id == 0 || SKULLCANDY_AIROHA_GESTURE_VARIANTS[variant]?.write?.containsKey(id) == true)
        }
    }.map(::optionOf)

    /** The row's settings group, once the key map was read. */
    fun group(): SettingGroup? {
        if (!has) return null
        val rows = ArrayList<Setting>()
        val opts = options()
        if (vendor) {
            val m = map ?: return null
            for (press in presses) rows += Setting.Choice(key(press, null), vendorAction(m, press)?.let(::optionOf), opts)
        } else {
            if (variant == null) return null
            for (side in sides()) for (press in presses) {
                val id = gestureId(press, side)?.let { gestures[it] } ?: continue
                rows += Setting.Choice(key(press, side), actionOf(id, press, side)?.let(::optionOf), opts)
            }
        }
        return rows.takeIf { it.isNotEmpty() }?.let { SettingGroup("sk_buttons", SettingTab.CONTROLS, it) }
    }

    /** The action the vendor key map holds for [press]: the one whose events are exactly the entries of that press on the button. */
    private fun vendorAction(m: List<ByteArray>, press: String): String? {
        val type = SkullcandyButtonTables.VENDOR_KEY_TYPES[press] ?: return null
        val events = m.filter { u8(it, 0) == type && u8(it, 1) == vendorKey }.map { u8(it, 2) or (u8(it, 3) shl 8) }.toSet()
        if (events.isEmpty()) return null
        return SkullcandyButtonTables.VENDOR_EVENTS.entries.firstOrNull { it.value.toSet() == events }?.key
    }

    /** The commands for a row's change, or null when [key] is not one of these rows or the option cannot be written. */
    fun change(key: String, option: String): List<RacePacket>? {
        val (press, side) = rowOf(key) ?: return null
        val action = ACTION_OF_OPTION[option] ?: return null
        if (vendor) {
            val m = map ?: return null
            val type = SkullcandyButtonTables.VENDOR_KEY_TYPES[press] ?: return null
            val events = SkullcandyButtonTables.VENDOR_EVENTS[action] ?: return null
            val k = vendorKey ?: return null
            val kept = m.filter { !(u8(it, 0) == type && u8(it, 1) == k) }
            val next = kept + events.map { e -> byteArrayOf(type.toByte(), k.toByte(), e.toByte(), (e shr 8).toByte(), 0, 0, 0, 0) }
            map = next
            val write = RacePacket(SkullcandyButtonTables.VENDOR_NV_KEY, next.fold(ByteArray(0)) { a, b -> a + b })
            sent += write
            return listOf(write)
        }
        val v = variant ?: return null
        val gid = gestureId(press, side) ?: return null
        val aid = actionId(action) ?: return null
        if (flow != null) return null
        val f = JblAirohaGesture.write(v, earbuds, agentRight, gestures, keyId, listOf(gid to aid), SKULLCANDY_AIROHA_GESTURE_VARIANTS)
        gestures[gid] = aid
        flow = f
        return listOfNotNull(f.start()?.also { sent += it })
    }

    /** The read after a vendor write. */
    fun readBack(): List<RacePacket> = if (vendor) listOf(vendorRead) else emptyList()

    private fun u8(b: ByteArray, at: Int): Int = b[at].toInt() and 0xFF

    companion object {
        private const val ENTRY = 8
        private val PRESSES = listOf("singlePress", "doublePress", "triplePress", "shortPressHold")

        /** The row options' names (the words the screens show) for the manifest's actions. */
        private val OPTION_OF_ACTION = mapOf(
            "oneTouchSpotify" to "spotify", "stayAwareOnOff" to "stay_aware", "playPause" to "play_pause", "trackBack" to "track_back", "trackForward" to "track_forward",
            "volumeDown" to "volume_down", "volumeUp" to "volume_up", "takePhoto" to "take_photo", "voiceAssistant" to "voice_assistant", "none" to "none",
            "cycleEQMode" to "cycle_eq", "headTracking" to "head_tracking", "spatialAudio" to "spatial",
        )
        private val ACTION_OF_OPTION = OPTION_OF_ACTION.entries.associate { it.value to it.key }

        fun optionOf(action: String): String = OPTION_OF_ACTION[action] ?: action
    }
}
