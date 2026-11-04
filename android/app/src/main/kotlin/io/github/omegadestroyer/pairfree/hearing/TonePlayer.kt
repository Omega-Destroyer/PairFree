package io.github.omegadestroyer.pairfree.hearing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Plays a hearing test's tones: a steady sine at a level below full scale, in both ears, as the files SOUNDPEATS' app
 * plays (docs/soundpeats/PROTOCOL.md section 13). Like that app, it turns media volume to the top for the test and puts it
 * back after ([begin], [end]), since the tones' levels are only meaningful at a known volume.
 */
class TonePlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var savedVolume: Int? = null

    /** True while another app plays audio, which would sound at full volume during the test. */
    val musicActive: Boolean get() = audio.isMusicActive

    fun begin() {
        if (savedVolume == null) savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
    }

    fun end() {
        stop()
        savedVolume?.let { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
        savedVolume = null
    }

    /** Plays [hz] at [levelDb] below full scale for [seconds]; [onEnd] runs on the main thread once it has played out. */
    fun play(hz: Float, levelDb: Float, seconds: Int, onEnd: () -> Unit) {
        stop()
        val frames = RATE * seconds
        val amplitude = 10.0.pow(levelDb / 20.0)
        val fade = min(FADE_FRAMES, frames / 2)
        val samples = FloatArray(frames * 2)
        for (i in 0 until frames) {
            // A short fade at each end keeps the start and stop from clicking.
            val edge = min(1.0, min(i, frames - 1 - i).toDouble() / fade)
            val v = (amplitude * edge * sin(2 * PI * hz * i / RATE)).toFloat()
            samples[2 * i] = v
            samples[2 * i + 1] = v
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
            .build()
        t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        t.notificationMarkerPosition = frames - 1
        t.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) {
                    if (track === this@TonePlayer.track) onEnd()
                }

                override fun onPeriodicNotification(track: AudioTrack) = Unit
            },
            main,
        )
        track = t
        t.play()
    }

    fun stop() {
        track?.let {
            track = null
            it.release()
        }
    }

    private companion object {
        const val RATE = 48000
        const val FADE_FRAMES = 480
    }
}
