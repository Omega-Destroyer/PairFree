package io.github.omegadestroyer.pairfree.protocol.soundcore

/**
 * A sound for the Sleep A30's local mode, prepared as the Soundcore app 5.0.35 prepares one (docs/soundcore/PROTOCOL.md section 14): mono 16-bit samples at
 * 16 kHz (32 kHz for sound type 3), cut at 60 seconds, SBC at 128 kb/s and, when the container passes the app's 1,024,000-byte limit, at 72 kb/s, in the
 * container of [SleepAudioFile]. UNTESTED ON AN A30: whether the earbuds take a media ID PairFree picks is not known.
 */
public object SleepSound {
    public const val MAX_SECONDS: Int = 60
    public const val SIZE_LIMIT: Int = 1_024_000
    public const val NAME_LIMIT: Int = 24

    /** The sample rate for a sound type: 32 kHz for type 3, else 16 kHz. */
    public fun sampleRate(soundType: Int): Int = if (soundType == 3) 32000 else 16000

    /** The sound type PairFree writes: 2 for an ordinary sound, 3 for the better-sounding 32 kHz one. */
    public fun soundType(highQuality: Boolean): Int = if (highQuality) 3 else 2

    public class Prepared(public val file: ByteArray, public val seconds: Int, public val bitRate: Int, public val clipped: Boolean)

    /**
     * [pcm] is mono 16-bit at [sampleRate(soundType)]; longer than [MAX_SECONDS] it is cut. [mediaId] is the identifier written in the header.
     */
    public fun prepare(pcm: ShortArray, mediaId: Long, name: String, soundType: Int): Prepared {
        val rate = sampleRate(soundType)
        val limit = rate * MAX_SECONDS
        val clipped = pcm.size > limit
        val samples = if (clipped) pcm.copyOf(limit) else pcm
        require(samples.isNotEmpty()) { "there is no sound to send" }
        var bitRate = 128000
        var file = SleepAudioFile.build(SbcEncoder(rate, bitRate).encode(samples), mediaId, name.take(NAME_LIMIT), soundType, rate)
        if (file.size > SIZE_LIMIT) {
            bitRate = 72000
            file = SleepAudioFile.build(SbcEncoder(rate, bitRate).encode(samples), mediaId, name.take(NAME_LIMIT), soundType, rate)
        }
        return Prepared(file, (samples.size + rate - 1) / rate, bitRate, clipped)
    }

    /**
     * Mono samples at [to] Hz from [from] Hz, by averaging the input over each output sample's span (a box filter, which also keeps higher frequencies from
     * folding back when the rate drops) and interpolating when it rises.
     */
    public fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to) return input
        val size = (input.size.toLong() * to / from).toInt()
        val out = ShortArray(size)
        val step = from.toDouble() / to
        for (i in 0 until size) {
            val a = i * step
            val b = a + step
            val first = a.toInt()
            val last = minOf(input.size - 1, if (step <= 1.0) first + 1 else kotlin.math.ceil(b).toInt() - 1)
            if (step <= 1.0) {
                val x = input[first].toInt()
                val y = input[last].toInt()
                out[i] = (x + (y - x) * (a - first)).toInt().toShort()
            } else {
                var sum = 0.0
                var weight = 0.0
                for (k in first..last) {
                    val w = minOf(b, k + 1.0) - maxOf(a, k.toDouble())
                    if (w > 0) {
                        sum += input[k] * w
                        weight += w
                    }
                }
                out[i] = if (weight > 0) (sum / weight).toInt().toShort() else 0
            }
        }
        return out
    }
}
