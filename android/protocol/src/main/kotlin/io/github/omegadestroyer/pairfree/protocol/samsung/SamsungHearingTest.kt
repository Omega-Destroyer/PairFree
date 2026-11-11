package io.github.omegadestroyer.pairfree.protocol.samsung

/**
 * Galaxy Buds' Adapt Sound hearing test (docs/samsung/PROTOCOL.md section 14), a port of what Samsung's plugin (budsunitemgr 9.0.10, `HearingTestManager`)
 * does. The earbuds make the tones themselves: the phone sends message 204 with a 25-byte block that says which tone to play, in which earbud, at what
 * attenuation, and the user answers whether they hear it. Six frequencies (250, 500, 1000, 2000, 4000 and 8000 Hz) per earbud, each asked three times in
 * a shuffled order, each answer moving that frequency's level; the six levels per earbud are the result, kept in the earbuds as a profile (5 is the
 * quietest tone heard, 40 the loudest needed; Samsung's presets are flat 5 and three steps of loss).
 */
public class SamsungHearingTest(private val legacy: Boolean, random: kotlin.random.Random = kotlin.random.Random.Default) {
    /** One tone to play: [left] or right earbud, the frequency's index (0 to 5) and the attenuation code the earbuds take (bigger is quieter). */
    public data class Tone(val left: Boolean, val frequency: Int, val gain: Int)

    /** The levels so far, left and right, one per frequency, each starting at 20. */
    public val left: IntArray = IntArray(FREQUENCIES.size) { START_LEVEL }
    public val right: IntArray = IntArray(FREQUENCIES.size) { START_LEVEL }

    /** The 36 tones' order: each of the twelve (frequency, earbud) pairs three times, shuffled; [END] closes it. */
    private val order: IntArray = IntArray(ORDER_LENGTH).also { o ->
        for (i in 0 until PAIRS) {
            o[i] = i
            o[i + PAIRS] = i
            o[i + 2 * PAIRS] = i
        }
        o[ORDER_LENGTH - 1] = END
        // Samsung's own shuffle: each of the first 36 swaps with a random one of the first 36.
        for (i in 0 until ORDER_LENGTH - 1) {
            val j = random.nextInt(ORDER_LENGTH - 1)
            val t = o[i]
            o[i] = o[j]
            o[j] = t
        }
    }

    private var index = 0
    private val block = ByteArray(BLOCK)

    /** The tone to play now, or null once the test is over. */
    public val current: Tone?
        get() {
            val pair = order[index]
            if (pair == END) return null
            val side = pair >= FREQUENCIES.size
            val f = pair % FREQUENCIES.size
            val level = if (side) right[f] else left[f]
            return Tone(left = !side, frequency = f, gain = TONE_BASE - level / LEVEL_STEP)
        }

    /** How far the test is, from 0 to 1. */
    public val progress: Float get() = index.toFloat() / (ORDER_LENGTH - 1)

    public val finished: Boolean get() = current == null

    /** The user's answer to [current]: heard it, or not. Moves that frequency's level as Samsung's `setTestResult` does. */
    public fun answer(heard: Boolean) {
        val pair = order[index]
        if (pair == END) return
        val levels = if (pair >= FREQUENCIES.size) right else left
        val f = pair % FREQUENCIES.size
        levels[f] = next(levels[f], heard)
        index++
    }

    private fun set(value: Int, at: Int) {
        block[at] = value.toByte()
    }

    private fun frame(): ByteArray = SamsungPacket.build(MESSAGE, block.copyOf(), legacy)

    /** The first message: the block with 0 in the command byte. */
    public fun start(): List<ByteArray> {
        set(COMMAND_INIT, COMMAND)
        return listOf(frame())
    }

    /** The messages that play [tone]; the plugin sends the same block again every 800 ms while the tone plays ([again]). */
    public fun play(tone: Tone): List<ByteArray> {
        set(if (tone.left) 1 else 0, SIDE)
        set(SLOTS[tone.frequency], FREQUENCY)
        set(tone.gain, GAIN)
        set(COMMAND_PLAY, COMMAND)
        return listOf(frame())
    }

    /** The block as it was last sent, for the 800 ms repeat. */
    public fun again(): List<ByteArray> = listOf(frame())

    /** Stops the tone (after an answer). */
    public fun stop(): List<ByteArray> {
        set(COMMAND_STOP, COMMAND)
        return listOf(frame())
    }

    /** Leaves the test without a result. */
    public fun cancel(): List<ByteArray> {
        set(COMMAND_CANCEL, COMMAND)
        return listOf(frame())
    }

    /** The result: the levels into the profile slots, then the message that makes the earbuds take them and Adapt Sound on. */
    public fun apply(): List<ByteArray> = buildList {
        profile(left, right)
        add(frame())
        set(ADAPT_SOUND_ON, ADAPT_SOUND)
        set(COMMAND_ADAPT, COMMAND)
        add(frame())
    }

    private fun profile(l: IntArray, r: IntArray) {
        for (f in FREQUENCIES.indices) {
            set(l[f], SLOTS[f] + LEFT_PROFILE)
            set(r[f], SLOTS[f] + RIGHT_PROFILE)
        }
        set(COMMAND_PROFILE, COMMAND)
    }

    public companion object {
        /** The test's frequencies in Hz; the earbuds take them as the slots in [SLOTS]. */
        public val FREQUENCIES: List<Int> = listOf(250, 500, 1000, 2000, 4000, 8000)

        /** Each frequency's slot in the block's profile and its tone byte (Samsung's `f16285o`). */
        public val SLOTS: List<Int> = listOf(0, 1, 3, 5, 7, 9)

        public const val MESSAGE: Int = 204
        public const val START_LEVEL: Int = 20
        public const val BLOCK: Int = 25
        private const val ORDER_LENGTH = 37
        private const val PAIRS = 12
        private const val END = -10
        private const val LEVEL_STEP = 5
        private const val TONE_BASE = 13

        // The block: byte 0 Adapt Sound on or off, byte 1 the command, 2 the earbud (1 left, 0 right), 3 the frequency's slot, 4 the tone's attenuation;
        // from 5 the left profile and from 15 the right one, each by slot.
        private const val ADAPT_SOUND = 0
        private const val COMMAND = 1
        private const val SIDE = 2
        private const val FREQUENCY = 3
        private const val GAIN = 4
        private const val LEFT_PROFILE = 5
        private const val RIGHT_PROFILE = 15
        private const val COMMAND_INIT = 0
        private const val COMMAND_PLAY = 1
        private const val COMMAND_STOP = 2
        private const val COMMAND_CANCEL = 3
        private const val COMMAND_PROFILE = 4
        private const val COMMAND_ADAPT = 5
        private const val ADAPT_SOUND_ON = 1

        /** The level after an answer (Samsung's `setTestResult`): heard moves toward 5, not heard toward 40, in the plugin's own steps; an unknown level gives 0. */
        public fun next(level: Int, heard: Boolean): Int = when (level) {
            5 -> if (heard) 5 else 10
            10 -> if (heard) 5 else 15
            15 -> if (heard) 15 else 20
            20 -> if (heard) 10 else 30
            25 -> if (heard) 25 else 30
            30 -> if (heard) 25 else 35
            35 -> if (heard) 35 else 40
            // Samsung's code has no step past 40 and would write 0; the loudest stays the loudest.
            40 -> 40
            else -> 0
        }
    }
}
