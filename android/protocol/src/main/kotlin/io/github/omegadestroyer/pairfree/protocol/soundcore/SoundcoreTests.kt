package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import kotlin.time.Clock

/**
 * The tests the earbuds run themselves when asked (docs/soundcore/PROTOCOL.md, Tests): the wear detection calibration of the Space headphones
 * (`20 84`, in two steps: on a table, then worn) and the personal noise cancelling of the Liberty 3 Pro and Liberty 4 NC (`03 88`, `03 89`).
 * Read from the Soundcore app 5.0.35; neither tried on earbuds.
 */
public class SoundcoreTests(private val features: Set<String>, private val now: () -> Long = { Clock.System.now().epochSeconds }) {
    private var calibrating: String? = null
    private var calibration: Pair<String, Boolean>? = null
    private var tuning = false
    private var tuned: Boolean? = null

    public fun rows(): List<Setting> = buildList {
        if (CALIBRATION in features) {
            add(Setting.Action(ON_TABLE, calibrating == ON_TABLE))
            add(Setting.Action(WORN, calibrating == WORN))
            calibration?.let { (step, passed) -> add(Setting.Information(if (step == ON_TABLE) ON_TABLE_RESULT else WORN_RESULT, if (passed) PASSED else FAILED)) }
        }
        if (PERSONAL_ANC in features) {
            add(Setting.Action(TUNE, tuning))
            tuned?.let { add(Setting.Information(TUNE_RESULT, if (it) PASSED else FAILED)) }
            add(Setting.Action(RESET))
        }
    }

    /** The write for a press of one of [rows], or null when the key is not one of them. */
    public fun change(change: SettingChange): List<SoundcorePacket>? {
        if (change !is SettingChange.Press) return null
        return when (change.key) {
            ON_TABLE, WORN -> {
                if (CALIBRATION !in features) return null
                calibrating = change.key
                calibration = null
                listOf(SoundcorePacket(SoundcoreCommand.WEAR_CALIBRATION, byteArrayOf(if (change.key == WORN) 1 else 0)))
            }
            TUNE -> {
                if (PERSONAL_ANC !in features) return null
                tuning = !tuning
                tuned = null
                listOf(SoundcorePacket(SoundcoreCommand.PERSONAL_ANC_TEST, testBody(tuning)))
            }
            RESET -> {
                if (PERSONAL_ANC !in features) return null
                tuning = false
                tuned = null
                listOf(SoundcorePacket(SoundcoreCommand.PERSONAL_ANC_RESET, ByteArray(0)))
            }
            else -> null
        }
    }

    // Start (1) or cancel (0), the time in seconds (high byte first), and the room's noise in dB: the app measures it with the phone's microphone,
    // which PairFree does not use, so it sends the app's own value for none (255).
    private fun testBody(start: Boolean): ByteArray {
        val t = now().toInt()
        return byteArrayOf(if (start) 1 else 0, (t ushr 24).toByte(), (t ushr 16).toByte(), (t ushr 8).toByte(), t.toByte(), NO_NOISE_READING.toByte())
    }

    /** Takes the earbuds' answer to a test. True when a row changed. */
    public fun receive(command: Int, body: ByteArray): Boolean {
        when (command) {
            SoundcoreCommand.WEAR_CALIBRATION -> {
                val step = calibrating ?: return false
                // The step passed when the two bytes after the first read 1 (the app's `getWearCheckTestCallBack`).
                if (body.size < 3) return false
                calibration = step to (((body[1].toInt() and 0xFF) or ((body[2].toInt() and 0xFF) shl 8)) == 1)
                calibrating = null
            }
            SoundcoreCommand.PERSONAL_ANC_TEST -> {
                if (!tuning || body.isEmpty()) return false
                tuned = (body[0].toInt() and 0xFF) != NO_NOISE_READING
                tuning = false
            }
            else -> return false
        }
        return true
    }

    public companion object {
        public const val CALIBRATION: String = "wear_calibration"
        public const val PERSONAL_ANC: String = "personal_anc"
        public const val ON_TABLE: String = "sc_calibrate_table"
        public const val WORN: String = "sc_calibrate_worn"
        public const val ON_TABLE_RESULT: String = "sc_calibrate_table_result"
        public const val WORN_RESULT: String = "sc_calibrate_worn_result"
        public const val TUNE: String = "sc_personal_anc"
        public const val TUNE_RESULT: String = "sc_personal_anc_result"
        public const val RESET: String = "sc_personal_anc_reset"
        public const val PASSED: String = "passed"
        public const val FAILED: String = "failed"
        private const val NO_NOISE_READING = 255
    }
}
