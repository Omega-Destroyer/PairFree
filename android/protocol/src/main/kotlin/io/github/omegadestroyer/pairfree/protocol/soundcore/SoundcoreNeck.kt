package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The Liberty 4's neck posture and in-ear calibration (docs/soundcore/PROTOCOL.md, Health), from the Soundcore app 5.0.35; not tried.
 *
 * `0D 88` calibrates (1, then 2 for in-ear detection or 3 for the neck); the reply's second byte is the kind and its third 0 on success. `0D 04`
 * answers with 72 counts of two bytes (low first): 36 five degree bands from 90 down to -90 front to back, then the same side to side (which half is
 * which is read from the field order of the app's upload, not confirmed). The app sends them to Anker's server; PairFree only shows them. `0D 89`
 * clears them, `0D 87` switches the posture report, and `0D 03` is the live angle, front to back and sideways, when the earbuds send it.
 */
public class SoundcoreNeck {
    private var calibrating: Int? = null
    private val calibrated = mutableMapOf<Int, Boolean>()
    private var report: String? = null
    private var counts: List<Int>? = null
    private var angle: Pair<Int, Int>? = null

    public fun receive(command: Int, body: ByteArray): Boolean {
        when (command) {
            SoundcoreCommand.POSTURE_CALIBRATE -> {
                if (body.size < 3) return false
                val kind = body[1].toInt() and 0xFF
                if (kind != IN_EAR && kind != NECK) return false
                calibrated[kind] = body[2].toInt() == 0
                calibrating = null
            }
            SoundcoreCommand.POSTURE_HISTORY -> {
                if (body.size < BANDS * 4) return false
                counts = List(BANDS * 2) { (body[it * 2].toInt() and 0xFF) or ((body[it * 2 + 1].toInt() and 0xFF) shl 8) }
            }
            SoundcoreCommand.POSTURE_ANGLE -> {
                if (body.size < 2) return false
                angle = (body[0].toInt() and 0xFF) to (body[1].toInt() and 0xFF)
            }
            else -> return false
        }
        return true
    }

    /** The share of the counted time, in percent, that [half] (0 front to back, 1 sideways) spends [TILT] degrees or more from upright. */
    private fun tilted(all: List<Int>, half: Int): Int? {
        val bands = all.subList(half * BANDS, (half + 1) * BANDS)
        val total = bands.sum()
        if (total == 0) return null
        val over = bands.withIndex().filter { (i, _) ->
            val high = 90 - i * 5
            val low = high - 5
            low >= TILT || high <= -TILT
        }.sumOf { it.value }
        return over * 100 / total
    }

    public fun rows(): List<Setting> = buildList {
        add(Setting.Action(CALIBRATE_IN_EAR, calibrating == IN_EAR))
        calibrated[IN_EAR]?.let { add(Setting.Information(IN_EAR_RESULT, if (it) SoundcoreTests.PASSED else SoundcoreTests.FAILED)) }
        add(Setting.Action(CALIBRATE_NECK, calibrating == NECK))
        calibrated[NECK]?.let { add(Setting.Information(NECK_RESULT, if (it) SoundcoreTests.PASSED else SoundcoreTests.FAILED)) }
        add(Setting.Choice(REPORT, report, listOf(ON, OFF)))
        add(Setting.Action(READ_HISTORY))
        counts?.let { all ->
            tilted(all, 0)?.let { add(Setting.Information(TILT_FRONT, "$it %")) }
            tilted(all, 1)?.let { add(Setting.Information(TILT_SIDE, "$it %")) }
            add(Setting.Action(CLEAR_HISTORY))
        }
        angle?.let { add(Setting.Information(ANGLE, "${it.first}° / ${it.second}°")) }
    }

    public fun change(change: SettingChange): List<SoundcorePacket>? {
        fun packet(command: Int, vararg body: Int) = listOf(SoundcorePacket(command, ByteArray(body.size) { body[it].toByte() }))
        if (change is SettingChange.Choice && change.key == REPORT) {
            if (change.option != ON && change.option != OFF) return null
            report = change.option
            return packet(SoundcoreCommand.POSTURE_REPORT, if (change.option == ON) 1 else 0)
        }
        if (change !is SettingChange.Press) return null
        return when (change.key) {
            CALIBRATE_IN_EAR, CALIBRATE_NECK -> {
                val kind = if (change.key == CALIBRATE_IN_EAR) IN_EAR else NECK
                calibrating = kind
                calibrated.remove(kind)
                packet(SoundcoreCommand.POSTURE_CALIBRATE, 1, kind)
            }
            READ_HISTORY -> packet(SoundcoreCommand.POSTURE_HISTORY)
            CLEAR_HISTORY -> {
                if (counts == null) return null
                counts = null
                packet(SoundcoreCommand.POSTURE_HISTORY_CLEAR)
            }
            else -> null
        }
    }

    public companion object {
        public const val CALIBRATE_IN_EAR: String = "sc_calibrate_in_ear"
        public const val IN_EAR_RESULT: String = "sc_calibrate_in_ear_result"
        public const val CALIBRATE_NECK: String = "sc_calibrate_neck"
        public const val NECK_RESULT: String = "sc_calibrate_neck_result"
        public const val REPORT: String = "sc_neck_report"
        public const val READ_HISTORY: String = "sc_neck_history"
        public const val CLEAR_HISTORY: String = "sc_neck_clear"
        public const val TILT_FRONT: String = "sc_neck_tilt_front"
        public const val TILT_SIDE: String = "sc_neck_tilt_side"
        public const val ANGLE: String = "sc_neck_angle"
        public const val ON: String = "on"
        public const val OFF: String = "off"
        private const val IN_EAR = 2
        private const val NECK = 3
        private const val BANDS = 36
        private const val TILT = 20
    }
}
