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
import io.github.omegadestroyer.pairfree.protocol.soundcore.SleepAudioFile
import io.github.omegadestroyer.pairfree.protocol.soundcore.SleepLibrary
import io.github.omegadestroyer.pairfree.protocol.soundcore.SleepSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /** The sound is the one PairFree last sent to that slot, so nothing was sent. */
    data class AlreadyThere(val name: String, val slot: Int) : SleepUploadUi
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

    /** What the catalogue holds, for the screen. */
    data class Catalogue(val entries: List<SleepLibrary.Entry>, val slots: List<SleepLibrary.Slot>)

    private val folder = File(app.filesDir, "sleep")
    private val library: SleepLibrary = runCatching { SleepLibrary.parse(File(folder, INDEX).readText()) }.getOrDefault(SleepLibrary())
    private val _catalogue = MutableStateFlow(snapshot())

    /** The sounds sent so far and what each slot of each earbud was last given. */
    val catalogue: StateFlow<Catalogue> = _catalogue.asStateFlow()

    private fun snapshot() = Catalogue(library.entries, library.slots)

    private fun save() {
        _catalogue.value = snapshot()
        runCatching {
            folder.mkdirs()
            File(folder, INDEX).writeText(library.serialize())
        }
    }

    private fun containerFile(hash: String) = File(folder, "$hash.bin")

    /** Takes a sound out of the catalogue and the phone's storage. The earbuds are not touched. */
    fun forget(hash: String) {
        library.remove(hash)
        runCatching { containerFile(hash).delete() }
        save()
    }

    /** The file's own name without its extension, for the sound's name. */
    fun nameOf(uri: Uri): String {
        val raw = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "Sound"
        return raw.substringBeforeLast('.').ifBlank { "Sound" }
    }

    /** Starts the upload of [uri] to the earbuds at [address], into the sound slot [slot] (0 or 1). */
    @SuppressLint("MissingPermission") // the screen opens only with the earbuds connected, which needs the permission
    fun start(address: String, uri: Uri, slot: Int, highQuality: Boolean, force: Boolean = false) = begin(address, uri, null, slot, highQuality, force)

    /** Sends a sound from the catalogue (by its hash) to [slot], from the container kept on the phone. */
    @SuppressLint("MissingPermission")
    fun startFromCatalogue(address: String, hash: String, slot: Int, force: Boolean = false) = begin(address, null, hash, slot, false, force)

    @SuppressLint("MissingPermission")
    private fun begin(address: String, uri: Uri?, hash: String?, slot: Int, highQuality: Boolean, force: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            val failure = try {
                run(address, uri, hash, slot, highQuality, force)
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
    private suspend fun run(address: String, uri: Uri?, catalogued: String?, slot: Int, highQuality: Boolean, force: Boolean): String? {
        _state.value = SleepUploadUi.Preparing
        val file: ByteArray
        val hash: String
        val soundType: Int
        val seconds: Int
        val bitRate: Int
        var clipped = false
        val name: String
        if (catalogued != null) {
            val entry = library.find(catalogued) ?: return "that sound is no longer in the catalogue"
            file = withContext(Dispatchers.IO) { runCatching { containerFile(catalogued).readBytes() }.getOrNull() } ?: return "the phone no longer holds that sound's file"
            hash = catalogued
            soundType = entry.soundType
            seconds = entry.seconds
            bitRate = entry.bitRate
            name = entry.name
        } else {
            soundType = SleepSound.soundType(highQuality)
            val source = uri!!
            val pcm = withContext(Dispatchers.Default) { decode(source, SleepSound.sampleRate(soundType)) }
            if (pcm.isEmpty()) return "there is no sound in that file"
            val label = nameOf(source)
            // The same audio is the same sound whatever it is called, and keeps the media ID it was first given (docs/soundcore/PROTOCOL.md section 14).
            val prepared = withContext(Dispatchers.Default) { SleepSound.prepare(pcm, SecureRandom().nextLong() and Long.MAX_VALUE, label, soundType) }
            hash = SleepLibrary.hashOf(prepared.file, soundType = soundType)
            val known = library.find(hash)
            file = if (known == null) prepared.file else SleepAudioFile.build(prepared.file.copyOfRange(SleepAudioFile.HEADER_SIZE, prepared.file.size), known.mediaId, known.name, soundType, SleepSound.sampleRate(soundType))
            seconds = prepared.seconds
            bitRate = known?.bitRate ?: prepared.bitRate
            clipped = prepared.clipped
            name = known?.name ?: label
        }
        if (!force && library.holds(address, slot, hash)) {
            _state.value = SleepUploadUi.AlreadyThere(name, slot)
            return null
        }
        val remote = adapter?.getRemoteDevice(address) ?: return "Bluetooth is not available"
        _state.value = SleepUploadUi.Connecting
        val link = BesLink(remote)
        try {
            try {
                link.connect(scope)
            } catch (e: IOException) {
                return "the earbuds’ sound channel did not open (${e.message ?: "no reason given"})"
            }
            _state.value = SleepUploadUi.Sending(0, file.size)
            val result = BesUploadRunner(BesUpload(file, slot), link, file.size, { done, total -> _state.value = SleepUploadUi.Sending(done, total) }).run()
            if (result == null) {
                val mediaId = (0 until 8).fold(0L) { acc, i -> acc or ((file[8 + i].toLong() and 0xFF) shl (8 * i)) }
                withContext(Dispatchers.IO) { runCatching { folder.mkdirs(); containerFile(hash).writeBytes(file) } }
                library.add(SleepLibrary.Entry(hash, mediaId, name, seconds, bitRate, soundType, System.currentTimeMillis()))
                library.sent(address, slot, hash, System.currentTimeMillis())
                save()
                _state.value = SleepUploadUi.Done(seconds, clipped)
            }
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
    private companion object {
        const val INDEX = "index.txt"
    }

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
