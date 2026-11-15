package io.github.omegadestroyer.pairfree.sleep

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import io.github.omegadestroyer.pairfree.bluetooth.BesLink
import io.github.omegadestroyer.pairfree.protocol.soundcore.BesUpload
import io.github.omegadestroyer.pairfree.protocol.soundcore.BesUploadRunner
import io.github.omegadestroyer.pairfree.protocol.soundcore.SleepSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder
import java.security.SecureRandom

/** What the upload screen shows. */
sealed interface SleepUploadUi {
    data object Idle : SleepUploadUi
    data object Preparing : SleepUploadUi
    data object Connecting : SleepUploadUi
    data class Sending(val done: Int, val total: Int) : SleepUploadUi
    data class Done(val seconds: Int, val clipped: Boolean) : SleepUploadUi
    data class Failed(val reason: String) : SleepUploadUi
}

/**
 * Puts a sound from the phone on a Soundcore Sleep A30 (issue #23, docs/soundcore/PROTOCOL.md section 14): decodes the chosen audio file with Android's own
 * decoders, prepares it as the Soundcore app does, and sends it over the earbuds' second serial channel with [BesUploadRunner]. UNTESTED ON AN A30. The
 * screen asks for an opt-in and a confirmation first, since this is the firmware updater's transfer with the music settings.
 */
class SleepUploader(private val app: Application, private val scope: CoroutineScope, private val adapter: BluetoothAdapter?) {
    private val _state = MutableStateFlow<SleepUploadUi>(SleepUploadUi.Idle)
    val state: StateFlow<SleepUploadUi> = _state.asStateFlow()
    private var job: Job? = null

    /** The file's own name without its extension, for the sound's name. */
    fun nameOf(uri: Uri): String {
        val raw = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "Sound"
        return raw.substringBeforeLast('.').ifBlank { "Sound" }
    }

    /** Starts the upload of [uri] to the earbuds at [address], into the sound slot [slot] (0 or 1). */
    @SuppressLint("MissingPermission") // the screen opens only with the earbuds connected, which needs the permission
    fun start(address: String, uri: Uri, slot: Int, highQuality: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            val failure = try {
                run(address, uri, slot, highQuality)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = SleepUploadUi.Failed("cancelled")
                throw e
            } catch (e: Exception) {
                e.message ?: e::class.simpleName.orEmpty()
            }
            if (failure != null) _state.value = SleepUploadUi.Failed(failure)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun run(address: String, uri: Uri, slot: Int, highQuality: Boolean): String? {
        val soundType = SleepSound.soundType(highQuality)
        _state.value = SleepUploadUi.Preparing
        val pcm = withContext(Dispatchers.Default) { decode(uri, SleepSound.sampleRate(soundType)) }
        if (pcm.isEmpty()) return "there is no sound in that file"
        // The earbuds take the media ID from the app's server in Soundcore's app; whether they accept one PairFree picks is not known.
        val mediaId = SecureRandom().nextLong() and Long.MAX_VALUE
        val prepared = withContext(Dispatchers.Default) { SleepSound.prepare(pcm, mediaId, nameOf(uri), soundType) }
        val remote = adapter?.getRemoteDevice(address) ?: return "Bluetooth is not available"
        _state.value = SleepUploadUi.Connecting
        val link = BesLink(remote)
        try {
            try {
                link.connect(scope)
            } catch (e: IOException) {
                return "the earbuds' sound channel did not open (${e.message ?: "no reason given"})"
            }
            _state.value = SleepUploadUi.Sending(0, prepared.file.size)
            val result = BesUploadRunner(BesUpload(prepared.file, slot), link, prepared.file.size, { done, total -> _state.value = SleepUploadUi.Sending(done, total) }).run()
            if (result == null) _state.value = SleepUploadUi.Done(prepared.seconds, prepared.clipped)
            return result
        } finally {
            link.close()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() {
        if (job?.isActive != true) _state.value = SleepUploadUi.Idle
    }

    /** Mono 16-bit samples at [rate] from the first audio track of [uri], cut at the sound's longest length. */
    private fun decode(uri: Uri, rate: Int): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(app, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw IOException("that file holds no audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            try {
                var sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var float = false
                var out = ShortArray(1 shl 20)
                var count = 0
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                // A little more than the cap, so a rate change does not leave the clip short.
                var wanted = Long.MAX_VALUE
                while (!outputDone) {
                    if (!inputDone) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val changed = codec.outputFormat
                            sourceRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            float = changed.containsKey(MediaFormat.KEY_PCM_ENCODING) && changed.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                            wanted = (SleepSound.MAX_SECONDS + 1L) * sourceRate
                        }
                        index >= 0 -> {
                            val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                            buffer.position(info.offset).limit(info.offset + info.size)
                            val frames = info.size / ((if (float) 4 else 2) * channels)
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += if (float) buffer.getFloat() * 32768f else buffer.getShort().toFloat()
                                if (count == out.size) out = out.copyOf(count * 2)
                                out[count++] = (sum / channels).coerceIn(-32768f, 32767f).toInt().toShort()
                            }
                            codec.releaseOutputBuffer(index, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || count >= wanted) outputDone = true
                        }
                    }
                }
                return SleepSound.resample(out.copyOf(count), sourceRate, rate)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }
}
