package io.github.omegadestroyer.pairfree.protocol.soundcore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** The second serial channel of the Sleep A30 (the BES service), as the runner needs it. The app's implementation is a Bluetooth socket. */
public interface BesChannel {
    /** Writes [bytes] whole. Throws when the channel is closed. */
    public suspend fun write(bytes: ByteArray)

    /** The next bytes the earbuds sent, or null when none came within [timeoutMs]. Throws when the channel is closed. */
    public suspend fun read(timeoutMs: Long): ByteArray?
}

/**
 * Drives one [BesUpload] over a [BesChannel]: writes what the state machine gives (data chunks about 20 ms apart, as the app does), waits for the earbuds'
 * next answer, and gives up when they stay silent. UNTESTED ON AN A30: the tests run it against a virtual earbud that fragments, delays and drops
 * (docs/soundcore/PROTOCOL.md section 14).
 */
public class BesUploadRunner(
    private val upload: BesUpload,
    private val channel: BesChannel,
    private val fileSize: Int,
    /** Progress as bytes the earbuds have verified, out of the file's size. */
    private val progress: (done: Int, total: Int) -> Unit = { _, _ -> },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    /** Null when the transfer finished, else why it did not. */
    public suspend fun run(): String? {
        try {
            var out = upload.start()
            var silent = 0
            while (upload.phase != BesUpload.Phase.DONE && upload.phase != BesUpload.Phase.FAILED) {
                coroutineContext.ensureActive()
                for (message in out) {
                    channel.write(message)
                    if (message[0] == DATA) pause(CHUNK_GAP_MS)
                }
                out = emptyList()
                // The segment check after a segment's chunks is answered after the earbuds have written it to flash; everything else is quick.
                val wait = if (upload.phase == BesUpload.Phase.SEGMENT || upload.phase == BesUpload.Phase.END || upload.phase == BesUpload.Phase.ACTIVATE) SLOW_MS else ANSWER_MS
                val bytes = channel.read(wait)
                if (bytes == null) {
                    if (++silent >= SILENT_LIMIT) {
                        upload.abort("the earbuds stopped answering during ${upload.phase.name.lowercase()}")
                    }
                    continue
                }
                silent = 0
                out = upload.feed(bytes)
                progress(upload.verified, fileSize)
            }
        } catch (e: CancellationException) {
            upload.abort("cancelled")
            throw e
        } catch (e: Exception) {
            upload.abort("the channel failed: ${e.message ?: e::class.simpleName}")
        }
        return if (upload.phase == BesUpload.Phase.DONE) null else upload.failure ?: "the transfer stopped"
    }

    private companion object {
        const val DATA = 0x85.toByte()
        const val CHUNK_GAP_MS = 20L
        const val ANSWER_MS = 8_000L
        const val SLOW_MS = 20_000L
        const val SILENT_LIMIT = 2
    }
}
