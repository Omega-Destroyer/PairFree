package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One connection to a Beats on Apple's chips over the B2P protocol (docs/apple/B2P.md), which Apple's Beats app for Android
 * speaks over an ordinary serial channel. Source analysis only: the layouts come from the app's native library, and no Beats
 * has answered PairFree yet. A setting shows once its read has been answered.
 *
 * Each read is a command (an even opcode) and its answer the next odd opcode. The listening mode is read twice, with a
 * payload of 1 and then 2; the app compares the two answers, which are taken here as "noise cancelling is on" and
 * "transparency is on" (the decoding is inferred; docs/apple/B2P.md).
 */
public class BeatsSession(public val model: AppleModel?) {
    public var state: EarbudState = EarbudState()
        private set

    private var sequence = 0
    private var listeningFirst: Int? = null
    private var listeningSecond: Int? = null
    private var listeningReplies = 0
    private var inEar: Int? = null
    private var lastMode: Int? = null

    /** Product id from the Beats (a little-endian number), for the device report. */
    public var productId: Int? = null
        private set

    /** The battery reply's raw payload, for the device report. */
    public var batteryData: ByteArray? = null
        private set

    /** What the Beats answered, for the device report: the two listening mode reads and the in-ear detection read. */
    public val answers: Map<String, Int?> get() = mapOf("listening_mode_1" to listeningFirst, "listening_mode_2" to listeningSecond, "in_ear_detection" to inEar)

    private fun frame(opcode: Int, vararg payload: Int): B2pFrame {
        sequence = sequence % MAX_SEQUENCE + 1
        return B2pFrame(sequence, opcode, ByteArray(payload.size) { payload[it].toByte() })
    }

    /** Reads sent on connecting: the product id, the battery, the listening mode (twice) and in-ear detection. */
    public fun start(): List<B2pFrame> = listOf(
        frame(PRODUCT_ID_GET), frame(BATTERY_GET), frame(LISTENING_MODE_GET, 1), frame(LISTENING_MODE_GET, 2), frame(IN_EAR_GET),
    ).also { listeningReplies = 0 }

    /** Reads again the values the Beats announce changes of. */
    private fun rereadListening(): List<B2pFrame> = listOf(frame(LISTENING_MODE_GET, 1), frame(LISTENING_MODE_GET, 2)).also { listeningReplies = 0 }

    /** Reads PairFree asks for after [receive], for example when the Beats announce a change; take it once. */
    public var followUps: List<B2pFrame> = emptyList()
        private set

    /** False for what PairFree never sends: firmware, the genuine-product exchange, logs and the like. */
    public fun allowed(frame: B2pFrame): Boolean = frame.opcode in ALLOWED

    /** Applies one frame from the Beats. True when [state] or [groups] changed. */
    public fun receive(frame: B2pFrame): Boolean {
        followUps = emptyList()
        val before = state to groups
        val p = frame.payload
        when (frame.opcode) {
            BATTERY_GET + 1 -> battery(p)
            PRODUCT_ID_GET + 1 -> if (p.size == 2) productId = u8(p, 0) or (u8(p, 1) shl 8)
            LISTENING_MODE_GET + 1 -> if (p.size == 1) {
                if (listeningReplies == 0) listeningFirst = u8(p, 0) else listeningSecond = u8(p, 0)
                listeningReplies++
            }
            IN_EAR_GET + 1 -> if (p.size == 1) inEar = u8(p, 0)
            LISTENING_MODE_NOTIFY, LISTENING_MODE_NOTIFY + 1 -> followUps = rereadListening()
            LISTENING_MODE_SET + 1 -> followUps = rereadListening()
        }
        refresh()
        return (state to groups) != before
    }

    private fun battery(p: ByteArray) {
        if (p.size != BATTERY_SIZE) return
        batteryData = p.copyOf()
        val level = u8(p, BATTERY_LEVEL).takeIf { it <= 100 }
        state = state.copy(values = state.values + (Command.BATTERY_LEFT to Value.Number(level)))
    }

    /**
     * Noise cancelling when the first answer is on and the second off, transparency the other way round, else off. "On" is
     * the nonzero value (the app compares with 1, 2 or 100 depending on the model's capability list, which is not read here).
     */
    private fun listening(): Int? {
        val a = listeningFirst ?: return null
        val b = listeningSecond ?: return null
        return when {
            a != 0 && b == 0 -> MODE_ANC
            a == 0 && b != 0 -> MODE_TRANSPARENCY
            else -> MODE_OFF
        }
    }

    private fun refresh() {
        val mode = lastMode ?: listening()
        state = state.copy(values = if (mode != null) state.values + (Command.ANC_MODE to Value.Number(ancNumber(mode))) else state.values)
    }

    public val groups: List<SettingGroup>
        get() = listOfNotNull(
            inEar?.takeIf { it == IN_EAR_ON || it == IN_EAR_OFF || it == 0 }?.let { SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle("ear_detection", it == IN_EAR_ON))) },
        )

    /** A toggle from the screens, or null for one the Beats did not report. */
    public fun change(change: SettingChange): List<B2pFrame>? {
        if (change is SettingChange.Toggle && change.key == "ear_detection" && inEar != null) {
            inEar = if (change.on) IN_EAR_ON else IN_EAR_OFF
            return listOf(frame(IN_EAR_SET, inEar!!))
        }
        return null
    }

    /** The listening mode from the shared screens: off, noise cancelling or transparency, with the level 100 as the app writes. */
    public fun set(command: Command, value: Int): List<B2pFrame>? {
        if (command != Command.ANC_MODE || listening() == null && lastMode == null) return null
        val mode = when (value) {
            ancNumber(MODE_OFF) -> MODE_OFF
            ancNumber(MODE_ANC) -> MODE_ANC
            ancNumber(MODE_TRANSPARENCY) -> MODE_TRANSPARENCY
            else -> return null
        }
        lastMode = mode
        refresh()
        return listOf(frame(LISTENING_MODE_SET, mode, LEVEL_FULL))
    }

    private fun u8(b: ByteArray, at: Int) = b[at].toInt() and 0xFF

    public companion object {
        public const val BATTERY_GET: Int = 0x0152
        public const val PRODUCT_ID_GET: Int = 0x013A
        public const val LISTENING_MODE_GET: Int = 0x0158
        public const val LISTENING_MODE_SET: Int = 0x015A
        public const val LISTENING_MODE_NOTIFY: Int = 0x015E
        public const val IN_EAR_GET: Int = 0x014C
        public const val IN_EAR_SET: Int = 0x014A

        /** The only commands PairFree sends. */
        public val ALLOWED: Set<Int> = setOf(BATTERY_GET, PRODUCT_ID_GET, LISTENING_MODE_GET, LISTENING_MODE_SET, IN_EAR_GET, IN_EAR_SET)

        private const val MAX_SEQUENCE = 255
        private const val BATTERY_SIZE = 5
        private const val BATTERY_LEVEL = 2
        private const val MODE_OFF = 0
        private const val MODE_ANC = 1
        private const val MODE_TRANSPARENCY = 2
        private const val LEVEL_FULL = 0x64
        private const val IN_EAR_OFF = 1
        private const val IN_EAR_ON = 2

        private fun ancNumber(mode: Int): Int = Command.ANC_MODE.labels.entries.first { it.value == when (mode) { MODE_ANC -> "anc"; MODE_TRANSPARENCY -> "transparency"; else -> "off" } }.key
    }
}
