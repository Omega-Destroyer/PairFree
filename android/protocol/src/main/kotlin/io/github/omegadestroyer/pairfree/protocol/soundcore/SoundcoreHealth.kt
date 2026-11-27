package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The Liberty 4's heart rate measurement (docs/soundcore/PROTOCOL.md, Health), from the Soundcore app 5.0.35; not tried. While measuring, the earbuds
 * send `0D 01`: a sequence number, the heart rate, then intervals and variability, the blood oxygen at byte 24 and the steps (four bytes, low first)
 * from byte 26. PairFree shows the readings and keeps none of them: nothing is stored or sent anywhere.
 */
public class SoundcoreHealth {
    private var measuring = false
    private var rate: Int? = null
    private var oxygen: Int? = null
    private var steps: Long? = null

    public fun receive(body: ByteArray): Boolean {
        if (!measuring || body.size < FULL) return false
        fun u8(at: Int) = body[at].toInt() and 0xFF
        rate = u8(2).takeIf { it > 0 }
        oxygen = u8(OXYGEN).takeIf { it in 1..100 }
        steps = (0..3).sumOf { u8(STEPS + it).toLong() shl (8 * it) }
        return true
    }

    public fun rows(): List<Setting> = buildList {
        add(Setting.Toggle(MEASURE, measuring))
        rate?.let { add(Setting.Information(RATE, it.toString())) }
        oxygen?.let { add(Setting.Information(OXYGEN_KEY, "$it %")) }
        steps?.let { add(Setting.Information(STEPS_KEY, it.toString())) }
    }

    public fun change(change: SettingChange): List<SoundcorePacket>? {
        if (change !is SettingChange.Toggle || change.key != MEASURE) return null
        measuring = change.on
        if (!measuring) {
            rate = null
            oxygen = null
            steps = null
        }
        return listOf(SoundcorePacket(SoundcoreCommand.HEART_RATE_MEASURE, byteArrayOf(if (measuring) 1 else 0)))
    }

    public companion object {
        public const val FEATURE: String = "heart_rate"
        public const val MEASURE: String = "sc_heart_rate"
        public const val RATE: String = "sc_heart_rate_value"
        public const val OXYGEN_KEY: String = "sc_blood_oxygen"
        public const val STEPS_KEY: String = "sc_steps"
        private const val OXYGEN = 24
        private const val STEPS = 26
        private const val FULL = 43
    }
}
