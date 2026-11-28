package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab

/**
 * The charging cases that are Bluetooth devices of their own (docs/soundcore/PROTOCOL.md, Charging cases), from the
 * Soundcore app 5.0.35: the Liberty 5 Pro's and Pro Max's (`D1203-Box`, `D1204-Box`) and the Sleep Earbuds 4 Pro's
 * (`D1302-Box`). The earbuds report the case's address. [le] is true where the case is a Bluetooth LE device (its service
 * [uuid], with [NOTIFY] and [WRITE]), false where it is a classic serial service. Not tried on a case.
 */
public class SoundcoreCaseSpec private constructor(public val model: String, public val le: Boolean, public val uuid: String) {
    public companion object {
        public const val NOTIFY: String = "00008888-0000-1000-8000-00805f9b34fb"
        public const val WRITE: String = "00007777-0000-1000-8000-00805f9b34fb"
        public const val MTU: Int = 512

        public fun of(model: String?): SoundcoreCaseSpec? = when (model) {
            "D1203" -> SoundcoreCaseSpec(model, true, "0205f5da-0000-1000-8000-00805f9b34fb")
            "D1204" -> SoundcoreCaseSpec(model, false, "0cf12d31-fac3-4553-bd80-d6832e7b395d")
            "D1302" -> SoundcoreCaseSpec(model, true, "021cf5da-0000-1000-8000-00805f9b34fb")
            else -> null
        }

        /** The case's Bluetooth address from the earbuds' state (record 41), or null while they report none. */
        public fun address(state: SoundcoreState?): String? {
            val bytes = state?.field("case_address") ?: return null
            if (bytes.all { it == 0.toByte() } || bytes.all { it == 0xFF.toByte() }) return null
            return bytes.joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
        }
    }
}

/** What the case's clock is set from: the time in seconds since 1970, the local offset from UTC in seconds, and the 24-hour setting. */
public class SoundcoreCaseClock(public val epochSeconds: Long, public val offsetSeconds: Int, public val twentyFourHour: Boolean)

/**
 * The conversation with a case: on connecting its clock is set and its state asked for. The Sleep Earbuds 4 Pro's case
 * holds the notification and sleep switches (`41 88`, bits of record 27) and says what sound it plays (record 98, paused
 * and resumed with `13 08`); the Liberty 5 Pro's cases only report their firmware. [allowed] is a closed list.
 */
public class SoundcoreCaseTalk(private val model: String, private val clock: () -> SoundcoreCaseClock?) : SessionTalk<ByteArray>() {
    private val parser = SoundcoreParser()
    private var records: Map<Int, ByteArray> = emptyMap()
    private var raw: ByteArray? = null
    private var wrote = false
    private val sleepCase = model == "D1302"

    override val state: EarbudState = EarbudState()
    override val pacing: Pacing = Pacing()

    override fun start(): List<ByteArray> = listOfNotNull(clockFrame(), request())
    override fun poll(): List<ByteArray> = listOf(request())
    override fun bytes(message: ByteArray): ByteArray = message
    override fun takeReadBacks(): List<ByteArray> = if (wrote) listOf(request()).also { wrote = false } else emptyList()

    override fun allowed(message: ByteArray): Boolean = message.size >= 7 && command(message) in ALLOWED

    private fun command(frame: ByteArray): Int = ((frame[5].toInt() and 0xFF) shl 8) or (frame[6].toInt() and 0xFF)

    private fun frame(command: Int, body: ByteArray = ByteArray(0)): ByteArray = SoundcorePacket.build(command, body)

    private fun request(): ByteArray = frame(SoundcoreCommand.STATE)

    /** `01 A6`: plain for the Liberty 5 Pro's cases (`q1`), in two records for the Sleep Earbuds' (`D1302CmdService.E6`). */
    internal fun clockFrame(): ByteArray? {
        val c = clock() ?: return null
        // The zone as the app writes it: half hours, counted from 12 hours west.
        val zone = ((c.offsetSeconds + 12 * 3600) / 1800).toByte()
        fun le(v: Long) = ByteArray(4) { (v shr (8 * it)).toByte() }
        // No change of the clocks is announced: all ones and the same zone, as the app does where there is none.
        val time = le(c.epochSeconds) + zone + le(NO_CHANGE) + zone
        val body = if (!sleepCase) {
            time
        } else {
            val first = time + (if (c.twentyFourHour) 0 else 1).toByte()
            byteArrayOf(70, first.size.toByte()) + first + byteArrayOf(71, 2, 0, 0)
        }
        return frame(SoundcoreCommand.SLEEP_CLOCK, body)
    }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        for (packet in parser.feed(bytes)) guarded {
            if (packet.command != SoundcoreCommand.STATE) return@guarded
            val read = SoundcoreTlv.records(packet.body)
            if (read.isEmpty()) return@guarded
            records = read
            raw = packet.body.copyOf()
            changed = true
        }
        return Fed(changed)
    }

    private fun bit(id: Int): Boolean? {
        val bytes = records[SWITCHES_RECORD] ?: return null
        val at = id / 8
        return if (at < bytes.size) (bytes[at].toInt() shr (id % 8)) and 1 == 1 else null
    }

    /** The sound the case says it has: playing or not, its id as eight bytes and its kind. */
    private fun sound(): ByteArray? = records[SOUND_RECORD]?.takeIf { it.size >= 10 && (1..8).any { i -> it[i] != 0.toByte() } }

    override val groups: List<SettingGroup>
        get() {
            val rows = mutableListOf<Setting>()
            records[VERSION_RECORD]?.let { v ->
                val text = v.takeWhile { it >= 0x20 && it < 0x7F }.map { it.toInt().toChar() }.joinToString("")
                if (text.isNotEmpty()) rows += Setting.Information(FIRMWARE, text)
            }
            if (sleepCase) {
                for ((key, id) in SWITCHES) bit(id)?.let { rows += Setting.Toggle(key, it) }
                // The two that record readings are switched only once Anker's agreement was accepted in its app (bits 50 and 51).
                for ((key, id, agreed) in AGREED) if (bit(agreed) == true) bit(id)?.let { rows += Setting.Toggle(key, it) }
                sound()?.let { rows += Setting.Toggle(SOUND, it[0] == 1.toByte()) }
            }
            return if (rows.isEmpty()) emptyList() else listOf(SettingGroup(GROUP, SettingTab.DEVICE, rows))
        }

    override fun change(change: SettingChange): List<ByteArray>? {
        if (change !is SettingChange.Toggle || !sleepCase) return null
        if (change.key == SOUND) {
            val s = sound() ?: return null
            val body = s.copyOfRange(0, 10).also { it[0] = if (change.on) 1 else 0 }
            records = records + (SOUND_RECORD to body + s.copyOfRange(10, s.size))
            wrote = true
            return listOf(frame(SoundcoreCommand.CASE_SOUND, body))
        }
        val id = SWITCHES.firstOrNull { it.first == change.key }?.second
            ?: AGREED.firstOrNull { it.key == change.key && bit(it.agreed) == true }?.id
            ?: return null
        val bytes = records[SWITCHES_RECORD]?.copyOf() ?: return null
        if (id / 8 >= bytes.size) return null
        val mask = 1 shl (id % 8)
        bytes[id / 8] = (if (change.on) bytes[id / 8].toInt() or mask else bytes[id / 8].toInt() and mask.inv()).toByte()
        records = records + (SWITCHES_RECORD to bytes)
        wrote = true
        return listOf(frame(SoundcoreCommand.FUNCTION_SWITCH, byteArrayOf(id.toByte(), if (change.on) 1 else 0)))
    }

    /** The case's state reply for a device report: the serial number's, the addresses' and anything serial-like hidden. */
    public fun reportHex(): String? {
        val bytes = raw ?: return null
        val hide = BooleanArray(bytes.size)
        var i = 0
        while (i + 1 < bytes.size) {
            val end = i + 2 + (bytes[i + 1].toInt() and 0xFF)
            if ((bytes[i].toInt() and 0xFF) in PRIVATE_RECORDS) for (k in i + 2 until minOf(end, bytes.size)) hide[k] = true
            i = end
        }
        return bytes.indices.joinToString(" ") { k -> if (hide[k]) "XX" else (bytes[k].toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private class Agreed(val key: String, val id: Int, val agreed: Int) {
        operator fun component1() = key
        operator fun component2() = id
        operator fun component3() = agreed
    }

    public companion object {
        public const val GROUP: String = "sc_case"
        public const val PREFIX: String = "sc_case_"
        public const val FIRMWARE: String = "sc_case_firmware"
        public const val SOUND: String = "sc_case_sound"
        private const val VERSION_RECORD = 9
        private const val SWITCHES_RECORD = 27
        private const val SOUND_RECORD = 98
        private const val NO_CHANGE = 0xFFFFFFFFL

        /** The serial number (7), the addresses (41 and 28) in a case's records. */
        private val PRIVATE_RECORDS = setOf(7, 28, 41)
        private val SWITCHES = listOf(
            "sc_case_notifications" to 33,
            "sc_case_low_battery" to 34,
            "sc_case_charging_reminder" to 35,
            "sc_case_data_sync" to 36,
            "sc_case_sleep_report" to 37,
            "sc_case_noise_alert" to 38,
        )
        private val AGREED = listOf(Agreed("sc_case_biosync", 39, 50), Agreed("sc_case_sleep_check", 40, 51))
        private val ALLOWED get() = setOf(SoundcoreCommand.STATE, SoundcoreCommand.SLEEP_CLOCK, SoundcoreCommand.FUNCTION_SWITCH, SoundcoreCommand.CASE_SOUND)
    }
}
