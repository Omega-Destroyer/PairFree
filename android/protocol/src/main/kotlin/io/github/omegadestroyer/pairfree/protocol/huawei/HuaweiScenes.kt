package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/** The phone's time: seconds since 1970 and the local offset from UTC, which the sleep mode's messages carry. */
public class HuaweiClock(public val epochSeconds: Long, public val offsetSeconds: Int)

/**
 * The maker's "smart scenario": a game mode and a sleep mode, both key 48 of `2B B4`, told apart by tag 2. Each carries the
 * phone's own address, which the earbuds report on `2B 35`, so nothing is offered before that answer. A mode another phone
 * started is left alone (sleep) or shown as off here (game), as the maker's app does. The sleep mode runs only while the
 * phone says so every half minute, so PairFree has to stay connected. The app's sleep sounds play from the phone and are
 * not part of this: PairFree sends the music switch off.
 */
internal class HuaweiScenes(private val clock: () -> HuaweiClock?) {
    private var address: ByteArray? = null
    private var game: HuaweiPacket? = null
    private var sleep: HuaweiPacket? = null
    private var gameSound = DEFAULT_SOUND
    private var gameCurve = emptyList<HuaweiTlv>()
    private var minutes = DEFAULT_MINUTES
    private var mute = false

    /** The writes this object made last: nothing else of this key may go out. */
    private val made = ArrayDeque<List<Byte>>()

    /** Takes the phone's address and the two modes' reports; true when something shown changed. */
    fun receive(packet: HuaweiPacket): Boolean {
        if (packet.op == HuaweiCommand.LOCAL_ADDRESS) {
            val value = packet.field(4)?.takeIf { it.size == 6 && it.any { byte -> byte.toInt() != 0 } } ?: return false
            address = value.copyOf()
            return true
        }
        // A report without the switch (tag 3) is about something else, the app's adaptive game mode among them.
        val on = packet.scalar(3) ?: return false
        return when (packet.scalar(2)) {
            GAME -> {
                game = packet
                packet.scalar(7)?.let { gameSound = it }
                if (packet.scalar(7) != null) gameCurve = packet.fields.filter { it.tag in CURVE_TAGS }
                true
            }
            SLEEP -> {
                sleep = packet
                if (on == 1 && mine(packet)) {
                    packet.scalar(21)?.let { mute = it == 1 }
                    val start = time(packet, 13)
                    val end = time(packet, 15)
                    if (start != null && end != null && end > start) minutes = TIMERS.minBy { kotlin.math.abs(it - (end - start) / 60) }
                }
                true
            }
            else -> false
        }
    }

    private fun mine(report: HuaweiPacket): Boolean = address?.let { report.field(4)?.contentEquals(it) } == true

    private val gameOn: Boolean get() = game?.let { it.scalar(3) == 1 && mine(it) } == true
    private val sleepOn: Boolean get() = sleep?.let { it.scalar(3) == 1 && mine(it) } == true
    private val sleepElsewhere: Boolean get() = sleep?.let { it.scalar(3) == 1 && !mine(it) } == true

    fun rows(): List<Setting> = buildList {
        if (address == null) return@buildList
        game?.let { report ->
            add(Setting.Toggle(GAME_ROW, gameOn))
            if (gameOn) {
                add(Setting.Toggle(LOCK_ROW, report.scalar(5) == 1))
                add(Setting.Toggle(LATENCY_ROW, report.scalar(6) == 1))
                add(Setting.Toggle(SOUND_ROW, report.scalar(7) != null))
            }
        }
        if (sleep != null && clock() != null) {
            if (sleepElsewhere) {
                add(Setting.Information(STATE_ROW, "hw_sleep_elsewhere"))
            } else {
                add(Setting.Toggle(SLEEP_ROW, sleepOn))
                add(Setting.Choice(TIMER_ROW, timerOption(minutes), TIMERS.map(::timerOption)))
                add(Setting.Toggle(MUTE_ROW, mute))
            }
        }
    }

    /** The messages for a changed row, or null when it is not one of these rows or cannot change now. */
    fun change(change: SettingChange): List<HuaweiPacket>? {
        if (rows().none { it.key == change.key }) return null
        if (change is SettingChange.Choice && change.key == TIMER_ROW) {
            minutes = TIMERS.firstOrNull { timerOption(it) == change.option } ?: return null
            // A running sleep mode starts over with the new time, as in the app.
            return if (sleepOn) listOf(startSleep() ?: return null) else emptyList()
        }
        if (change !is SettingChange.Toggle) return null
        return when (change.key) {
            GAME_ROW -> listOf(gameWrite(if (change.on) 1 else 0) ?: return null)
            LOCK_ROW -> listOf(gameWrite(PREFERENCE, lock = change.on) ?: return null)
            LATENCY_ROW -> listOf(gameWrite(PREFERENCE, latency = change.on) ?: return null)
            SOUND_ROW -> listOf(gameWrite(PREFERENCE, sound = change.on) ?: return null)
            SLEEP_ROW -> listOf((if (change.on) startSleep() else sleepWrite(HuaweiTlv(3, byteArrayOf(0)))) ?: return null)
            MUTE_ROW -> {
                mute = change.on
                if (sleepOn) listOf(sleepWrite(HuaweiTlv(21, byteArrayOf(if (change.on) 1 else 0)))) else emptyList()
            }
            else -> null
        }
    }

    /** True when [change] starts the sleep mode, before which the app also sends the noise mode. */
    fun startsSleep(change: SettingChange): Boolean = change is SettingChange.Toggle && change.key == SLEEP_ROW && change.on

    /** Every half minute while the sleep mode runs from this phone: the seconds left, then the state again; at none left, off. */
    fun heartbeat(): List<HuaweiPacket> {
        if (!sleepOn) return emptyList()
        val report = sleep ?: return emptyList()
        val now = clock()?.epochSeconds ?: return emptyList()
        val start = time(report, 13) ?: return listOf(SLEEP_QUERY)
        val end = time(report, 15) ?: return listOf(SLEEP_QUERY)
        val left = if (end - start == ALWAYS * 60L) ALWAYS.toLong() else (end - now).coerceIn(0, 0xffff)
        if (left == 0L) return listOf(sleepWrite(HuaweiTlv(3, byteArrayOf(0))), SLEEP_QUERY)
        return listOf(sleepWrite(HuaweiTlv(20, bytes(left, 2))), SLEEP_QUERY)
    }

    fun allows(packet: HuaweiPacket): Boolean = packet.bytes().asList() in made

    private fun gameWrite(
        switch: Int,
        lock: Boolean = game?.scalar(5) == 1,
        latency: Boolean = game?.scalar(6) == 1,
        sound: Boolean = game?.scalar(7) != null,
    ): HuaweiPacket? {
        val own = address ?: return null
        return write(GAME, buildList {
            add(HuaweiTlv(3, byteArrayOf(switch.toByte())))
            add(HuaweiTlv(4, own))
            add(HuaweiTlv(5, byteArrayOf(if (lock) 1 else 0)))
            add(HuaweiTlv(6, byteArrayOf(if (latency) 1 else 0)))
            if (sound) {
                add(HuaweiTlv(7, byteArrayOf(gameSound.toByte())))
                // A curve of the user's own, saved in the maker's app, goes back as the earbuds reported it.
                addAll(gameCurve)
            }
        })
    }

    private fun startSleep(): HuaweiPacket? {
        val own = address ?: return null
        val now = clock() ?: return null
        val start = now.epochSeconds
        val zone = bytes(now.offsetSeconds.toLong(), 4)
        return write(SLEEP, listOf(
            HuaweiTlv(3, byteArrayOf(1)),
            HuaweiTlv(4, own),
            HuaweiTlv(13, bytes(start, 4)),
            HuaweiTlv(14, zone),
            HuaweiTlv(15, bytes(start + minutes * 60L, 4)),
            HuaweiTlv(16, byteArrayOf(0)),
            HuaweiTlv(17, bytes(start, 4)),
            HuaweiTlv(18, zone),
            HuaweiTlv(19, bytes(start + MUSIC_MINUTES * 60L, 4)),
            HuaweiTlv(20, bytes(minutes * 60L, 2)),
            HuaweiTlv(21, byteArrayOf(if (mute) 1 else 0)),
        ))
    }

    private fun sleepWrite(field: HuaweiTlv): HuaweiPacket = write(SLEEP, listOf(field))

    private fun write(scene: Int, fields: List<HuaweiTlv>): HuaweiPacket {
        val packet = huaweiPacket(HuaweiFeature.OP,
            listOf(HuaweiTlv(1, byteArrayOf(KEY.toByte())), HuaweiTlv(2, byteArrayOf(scene.toByte()))) + fields)
        made.addLast(packet.bytes().asList())
        while (made.size > KEPT) made.removeFirst()
        return packet
    }

    private fun time(packet: HuaweiPacket, tag: Int): Long? =
        packet.field(tag)?.takeIf { it.size == 4 }?.fold(0L) { total, byte -> total shl 8 or (byte.toLong() and 0xff) }

    private fun bytes(value: Long, count: Int): ByteArray = ByteArray(count) { (value shr (8 * (count - 1 - it))).toByte() }

    companion object {
        /** The key of `2B B4` both modes use. */
        const val KEY = 48
        private const val GAME = 1
        private const val SLEEP = 2
        private const val PREFERENCE = 2
        private const val DEFAULT_SOUND = 25
        private const val DEFAULT_MINUTES = 30
        private const val MUSIC_MINUTES = 15
        /** The app's "always on": a timer of 100 minutes, and 100 seconds left in every heartbeat. */
        private const val ALWAYS = 100
        private const val KEPT = 12
        private val CURVE_TAGS = 8..11
        private val TIMERS = listOf(15, 30, 60, 90, ALWAYS)
        private const val GAME_ROW = "hw_game"
        private const val LOCK_ROW = "hw_game_lock"
        private const val LATENCY_ROW = "hw_game_latency"
        private const val SOUND_ROW = "hw_game_sound"
        private const val SLEEP_ROW = "hw_sleep"
        private const val TIMER_ROW = "hw_sleep_timer"
        private const val MUTE_ROW = "hw_sleep_mute"
        private const val STATE_ROW = "hw_sleep_state"

        private fun timerOption(minutes: Int): String = if (minutes == ALWAYS) "hw_sleep_always" else "hw_sleep_$minutes"

        private fun query(scene: Int, vararg more: HuaweiTlv): HuaweiPacket = huaweiPacket(HuaweiFeature.OP,
            listOf(HuaweiTlv(1, byteArrayOf(KEY.toByte())), HuaweiTlv(2, byteArrayOf(scene.toByte()))) + more)

        val GAME_QUERY: HuaweiPacket = query(GAME, HuaweiTlv(3))
        val SLEEP_QUERY: HuaweiPacket = query(SLEEP)
    }
}
