package io.github.omegadestroyer.pairfree.protocol.soundcore

/**
 * The BES transfer of one prepared Sleep A30 file, as the Soundcore app 5.0.35 runs it for a music file (docs/soundcore/PROTOCOL.md section 14): the app's
 * `BesOtaService.e` handler, `BesOtaCMD.y` (what each answer means) and `k`, `j`, `f` (the next message, the next chunk, a segment's CRC), read from the restored
 * DEX. No transport and no clock: [start] gives the first message and [feed] takes what the earbuds send and gives what to send next, data chunks included
 * (the app writes them without waiting for answers, about 20 ms apart, and sends the segment check after the last chunk of a segment).
 *
 * The state machine runs against the virtual earbud in the tests only. No A30 has taken a file from it, and the first message's format (before the
 * earbuds' protocol answer the frames carry no length) is read from the app's code, not seen on the wire.
 */
public class BesUpload(
    private val file: ByteArray,
    /** The slot of the earbuds' two (0 or 1): the app selects the user `10 + index`. */
    private val indexInDevice: Int,
    /** The 32-byte code an earlier transfer's resume answer gave, or null. */
    private val savedCode: ByteArray? = null,
) {
    public enum class Phase { PROTOCOL, USER, BEGIN, RESUME, ANNOUNCE, CONFIGURE, DATA, SEGMENT, END, ACTIVATE, DONE, FAILED }

    public var phase: Phase = Phase.PROTOCOL
        private set

    /** Why it stopped, when [phase] is [Phase.FAILED]. */
    public var failure: String? = null
        private set

    /** The code the earbuds sent for a fresh transfer, to keep for the next one. */
    public var codeToKeep: ByteArray? = null
        private set

    /** Bytes of the file the earbuds have accepted (verified segments), for a progress bar. */
    public var verified: Int = 0
        private set

    private var framed = false
    private var buffer = ByteArray(0)
    private var offset = 0 // Y: how far the chunks have been sent
    private var segment = 0 // d0: the current segment
    private var chunk = 0
    private var segmentRetries = 0
    private var segmentEnd = false // e0

    /** The first message: the protocol query the app sends before anything else, with its skip-version flag set. */
    public fun start(): List<ByteArray> = listOf(byteArrayOf(0x99.toByte(), 0, 0, 0, 1))

    /** [bytes] as the earbuds sent them; returns the messages to send now, in order. */
    public fun feed(bytes: ByteArray): List<ByteArray> {
        if (phase == Phase.DONE || phase == Phase.FAILED) return emptyList()
        buffer += bytes
        val out = mutableListOf<ByteArray>()
        while (buffer.isNotEmpty()) {
            val opcode = buffer[0].toInt() and 0xFF
            val message: ByteArray
            if (!framed) {
                // The earbuds' protocol answer comes before the length field exists: the whole read is the message.
                message = buffer
                buffer = ByteArray(0)
            } else {
                if (buffer.size < 5) break
                val length = (0 until 4).sumOf { (buffer[1 + it].toInt() and 0xFF) shl (8 * it) }
                if (buffer.size < 5 + length) break
                message = buffer.copyOfRange(0, 5 + length)
                buffer = buffer.copyOfRange(5 + length, buffer.size)
            }
            out += handle(opcode, message)
            if (phase == Phase.DONE || phase == Phase.FAILED) break
        }
        return out
    }

    /** Stops the transfer from outside (a timeout, a closed channel, the user's cancel). */
    public fun abort(why: String) {
        if (phase != Phase.DONE && phase != Phase.FAILED) fail(why)
    }

    private fun status(message: ByteArray): Boolean = message.size > 5 && message[5].toInt() == 1

    private fun fail(why: String): List<ByteArray> {
        phase = Phase.FAILED
        failure = why
        return emptyList()
    }

    private fun handle(opcode: Int, message: ByteArray): List<ByteArray> = when {
        opcode == 0x9A && phase == Phase.PROTOCOL -> {
            framed = true
            phase = Phase.USER
            listOf(BesTransferMessages.user(indexInDevice))
        }
        opcode == 0x98 && phase == Phase.USER ->
            if (status(message)) { phase = Phase.BEGIN; listOf(BesTransferMessages.begin()) } else fail("the earbuds refused the sound slot")
        opcode == 0x91 && phase == Phase.BEGIN ->
            if (status(message)) { phase = Phase.RESUME; listOf(BesTransferMessages.resume(savedCode)) } else fail("the earbuds refused to begin")
        opcode == 0x8D && phase == Phase.RESUME -> {
            val start = if (message.size >= 9) (0 until 4).sumOf { (message[5 + it].toInt() and 0xFF) shl (8 * it) } else 0
            if (start > 0) {
                // An earlier transfer of this file: go on from the offset the earbuds hold.
                offset = start
                verified = start
                segment = start / SEGMENT
                configure()
            } else {
                if (message.size >= 41) codeToKeep = message.copyOfRange(9, 41)
                phase = Phase.ANNOUNCE
                listOf(BesTransferMessages.announce(file))
            }
        }
        opcode == 0x81 && phase == Phase.ANNOUNCE -> {
            // After BEST: two versions of two bytes each, then the earbuds' packet size as 16 bits.
            val mtu = if (message.size >= 15) (message[13].toInt() and 0xFF) or ((message[14].toInt() and 0xFF) shl 8) else DEFAULT_MTU
            chunk = payloadSize(if (mtu > 0) mtu else DEFAULT_MTU)
            configure()
        }
        opcode == 0x87 && phase == Phase.CONFIGURE ->
            if (status(message)) { phase = Phase.DATA; sendData() } else fail("the earbuds refused the configuration")
        opcode == 0x83 && phase == Phase.SEGMENT -> {
            if (status(message)) {
                segmentRetries = 0
                segment++
                verified = offset
                segmentEnd = false
                if (offset >= file.size) { phase = Phase.END; listOf(BesTransferMessages.end()) } else { phase = Phase.DATA; sendData() }
            } else if (++segmentRetries >= RETRIES) {
                fail("the earbuds refused a segment's check three times")
            } else {
                // The app goes back to the last verified offset and sends the segment again.
                offset = verified
                segmentEnd = false
                phase = Phase.DATA
                sendData()
            }
        }
        opcode == 0x84 && phase == Phase.END ->
            if (status(message)) { phase = Phase.ACTIVATE; listOf(BesTransferMessages.activate()) } else fail("the earbuds refused the finished file")
        opcode == 0x93 && phase == Phase.ACTIVATE ->
            if (status(message)) { phase = Phase.DONE; verified = file.size; emptyList() } else fail("the earbuds refused to activate the file")
        else -> emptyList() // an answer the app's parser ignores in this state
    }

    private fun configure(): List<ByteArray> {
        if (chunk == 0) chunk = payloadSize(DEFAULT_MTU)
        phase = Phase.CONFIGURE
        return listOf(BesTransferMessages.configuration(file))
    }

    /** `ProfileUtils.b` for a serial port with no response: the packet's size less its 5 bytes of header, and a stereo pair's `b(...)`. */
    private fun payloadSize(mtu: Int): Int = minOf(mtu - 1, 512) - 4

    /** The chunks of the current segment (`BesOtaCMD.j`), then its check (`f`) when the segment ends. */
    private fun sendData(): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        segmentEnd = false
        while (!segmentEnd) {
            val boundary = (segment + 1) * SEGMENT
            var size = chunk
            if (offset + chunk >= file.size) {
                size = file.size - offset
                segmentEnd = true
            } else if (offset + chunk > boundary) {
                size = boundary - offset
                segmentEnd = true
            }
            if (offset + size == boundary) segmentEnd = true
            if (size <= 0) break
            out += BesTransferMessages.data(file.copyOfRange(offset, offset + size))
            offset += size
        }
        phase = Phase.SEGMENT
        val start = segment * SEGMENT
        out += BesTransferMessages.segmentCrc(SleepAudioFile.crc32(file, start, offset))
        return out
    }

    private companion object {
        const val SEGMENT = 32768
        const val DEFAULT_MTU = 512
        const val RETRIES = 3
    }
}
