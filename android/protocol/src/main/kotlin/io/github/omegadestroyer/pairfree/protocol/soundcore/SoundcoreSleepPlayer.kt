package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The Sleep A10's own player (docs/soundcore/PROTOCOL.md, The Sleep A10's player), from the Soundcore app 5.0.35; not tried. The state holds the
 * play time, whether a sound is on the earbuds, its volume and the countdown. Alarms and putting a sound on the earbuds are not built.
 */
public object SoundcoreSleepPlayer {
    private const val PLAY_TIME_FIELD = "play_time"
    private const val HAS_SOUND_FIELD = "has_sleep_music"
    private const val VOLUME_FIELD = "music_volume"
    private const val COUNTDOWN_FIELD = "play_countdown"

    public const val PLAY: String = "sc_a10_play"
    public const val PAUSE: String = "sc_a10_pause"
    public const val VOLUME: String = "sc_a10_volume"
    public const val PLAY_TIME: String = "sc_a10_play_time"
    public const val COUNTDOWN: String = "sc_a10_countdown"
    private const val NO_LIMIT = "off"
    private const val KEEP = "keep"
    private const val MAX_VOLUME = 65
    private const val PLAY_TIME_OFF = 255
    private const val KEEP_PLAYING = 0xFFFF
    private val PLAY_TIMES = listOf(NO_LIMIT) + listOf(5, 10, 15, 20, 30, 45, 60).map(Int::toString)
    private val COUNTDOWNS = listOf(NO_LIMIT, KEEP) + (30..480 step 30).map(Int::toString)

    private fun SoundcoreState.u8(name: String, at: Int = 0): Int? = field(name)?.getOrNull(at)?.let { it.toInt() and 0xFF }

    public fun rows(s: SoundcoreState): List<Setting> = buildList {
        if (s.u8(HAS_SOUND_FIELD) == 1) {
            add(Setting.Action(PLAY))
            add(Setting.Action(PAUSE))
            s.u8(VOLUME_FIELD)?.takeIf { it <= MAX_VOLUME }?.let { add(Setting.Level(VOLUME, it, 0, MAX_VOLUME)) }
        }
        s.u8(PLAY_TIME_FIELD)?.let { v -> add(Setting.Choice(PLAY_TIME, if (v == PLAY_TIME_OFF) NO_LIMIT else v.toString().takeIf { it in PLAY_TIMES }, PLAY_TIMES)) }
        val high = s.u8(COUNTDOWN_FIELD, 0)
        val low = s.u8(COUNTDOWN_FIELD, 1)
        if (high != null && low != null) {
            val v = (high shl 8) or low
            add(Setting.Choice(COUNTDOWN, when (v) { 0 -> NO_LIMIT; KEEP_PLAYING -> KEEP; else -> v.toString().takeIf { it in COUNTDOWNS } }, COUNTDOWNS))
        }
    }

    /** The packet for a change to one of [rows] and the state as it should then read, or null. */
    public fun change(s: SoundcoreState, change: SettingChange): Pair<SoundcorePacket, SoundcoreState>? {
        val shown = rows(s).map { it.key }
        if (change.key !in shown) return null
        fun packet(command: Int, vararg body: Int) = SoundcorePacket(command, ByteArray(body.size) { body[it].toByte() })
        return when {
            change is SettingChange.Press -> packet(SoundcoreCommand.SLEEP_PLAY_PAUSE, if (change.key == PLAY) 1 else 0) to s
            change is SettingChange.Level && change.key == VOLUME -> {
                val v = change.value.coerceIn(0, MAX_VOLUME)
                packet(SoundcoreCommand.SLEEP_PLAY_VOLUME, 0, v) to s.with(VOLUME_FIELD, byteArrayOf(v.toByte()))
            }
            change is SettingChange.Choice && change.key == PLAY_TIME && change.option in PLAY_TIMES -> {
                val v = change.option.toIntOrNull() ?: PLAY_TIME_OFF
                packet(SoundcoreCommand.SLEEP_PLAY_TIME, v) to s.with(PLAY_TIME_FIELD, byteArrayOf(v.toByte()))
            }
            change is SettingChange.Choice && change.key == COUNTDOWN && change.option in COUNTDOWNS -> {
                val v = when (change.option) { NO_LIMIT -> 0; KEEP -> KEEP_PLAYING; else -> change.option.toInt() }
                packet(SoundcoreCommand.SLEEP_PLAY_COUNTDOWN, v shr 8, v and 0xFF) to s.with(COUNTDOWN_FIELD, byteArrayOf((v shr 8).toByte(), v.toByte()))
            }
            else -> null
        }
    }
}
