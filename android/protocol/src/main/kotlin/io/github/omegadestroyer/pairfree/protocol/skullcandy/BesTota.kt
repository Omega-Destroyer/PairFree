package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.soundcore.SleepAudioFile
import io.github.omegadestroyer.pairfree.protocol.soundcore.Sha256
import kotlin.random.Random

/**
 * BES's TOTA channel as Skullcandy's app speaks it with the Icon 180, Dime 540 ANC, Smokin' Buds 360 H and S and Push 180 Open (docs/skullcandy/PROTOCOL.md sections 12.2 and 12.3): the serial
 * service, the frame around every message, the handshake that agrees a key and the AES-128 layer that follows. Nothing here is a key of the vendor's: the key is derived afresh on each
 * connection from a buffer each side picks (section 12.2), so the layer is implementable from behaviour alone.
 */
public object BesTota {
    /** The serial service of the channel (the BES SDK's default). */
    public const val SERVICE: String = "66666666-6666-6666-6666-666666666666"

    private val HEAD = "HEAD".encodeToByteArray()
    private val TAIL = "TAIL".encodeToByteArray()

    /** The initialisation vector of every message: 00 01 .. 0F. */
    public val IV: ByteArray = ByteArray(16) { it.toByte() }

    /** The byte that starts a message on the wire, ahead of the frame's own length. */
    private const val LEAD: Int = 0x9F

    private fun le32(v: Int): ByteArray = ByteArray(4) { (v ushr (8 * it)).toByte() }

    private fun u32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    /** The frame around [payload]: `HEAD`, its length, the payload, the payload's CRC-32 (all little endian) and `TAIL`. */
    public fun frame(payload: ByteArray): ByteArray =
        HEAD + le32(payload.size) + payload + le32(SleepAudioFile.crc32(payload).toInt()) + TAIL

    /** The bytes of a message as they go out: the lead byte, the frame's length and the frame. */
    public fun wire(frame: ByteArray): ByteArray = byteArrayOf(LEAD.toByte()) + le32(frame.size) + frame

    /** A message before the key exists: the handshake's, framed and not encrypted. */
    public fun plainWire(payload: ByteArray): ByteArray = wire(frame(payload))

    /** A message once the key is agreed: the payload zero-padded to whole blocks, encrypted, framed. */
    public fun secureWire(key: ByteArray, payload: ByteArray): ByteArray = wire(frame(Aes128.cbcEncryptZeroPadded(key, IV, payload)))

    /** What the host's first handshake message holds: `01 10 <length u16> <m> <m bytes>`, with m from 16 to 127 (the app picks it at random) and the length m + 1. */
    public fun hello(buffer: ByteArray): ByteArray {
        val m = buffer.size
        require(m in 1..255) { "the host's buffer is a few bytes" }
        return byteArrayOf(0x01, 0x10, ((m + 1) and 0xFF).toByte(), (((m + 1) shr 8) and 0xFF).toByte(), m.toByte()) + buffer
    }

    /** A fresh buffer of [random]'s of the app's size (16 to 127 bytes). */
    public fun newBuffer(random: Random): ByteArray = random.nextBytes(16 + random.nextInt(112))

    /** The earbuds' buffer out of their `02 10 <length u16> <m> <buffer>` message, or null when [payload] is not one. */
    public fun theirBuffer(payload: ByteArray): ByteArray? {
        if (payload.size < 5 || payload[0].toInt() != 0x02 || payload[1].toInt() != 0x10) return null
        val length = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
        val n = length - 1
        if (n < 1 || payload.size < 5 + n) return null
        return payload.copyOfRange(5, 5 + n)
    }

    /**
     * The two buffers combined byte by byte (the shorter one padded with zeros): by the index modulo 6 the sum, the product, AND, OR, XOR and the sum of both squares, each as a byte; a sixth
     * case the app writes as 0 cannot be reached.
     */
    public fun mix(a: ByteArray, b: ByteArray): ByteArray {
        val n = maxOf(a.size, b.size)
        return ByteArray(n) { i ->
            val x = if (i < a.size) a[i].toInt() and 0xFF else 0
            val y = if (i < b.size) b[i].toInt() and 0xFF else 0
            when (i % 6) {
                0 -> x + y
                1 -> x * y
                2 -> x and y
                3 -> x or y
                4 -> x xor y
                else -> x * x + y * y
            }.toByte()
        }
    }

    /** The SHA-256 of the mixed buffers, which the host sends to confirm. */
    public fun hashOf(host: ByteArray, theirs: ByteArray): ByteArray = Sha256.digest(mix(theirs, host))

    /** The AES-128 key: the bytes of the hash at the even positions. */
    public fun keyOf(hash: ByteArray): ByteArray = ByteArray(16) { hash[it * 2] }

    /** The confirmation: `03 10 21 00 20` and the 32-byte hash. */
    public fun confirm(hash: ByteArray): ByteArray = byteArrayOf(0x03, 0x10, 0x21, 0x00, 0x20) + hash

    /** True when the earbuds' answer to the confirmation is `00 10 ..` with the text `suc` at offset 12. */
    public fun accepted(payload: ByteArray): Boolean =
        payload.size > 15 && payload[0].toInt() == 0x00 && payload[1].toInt() == 0x10 && payload[12].toInt() == 's'.code && payload[13].toInt() == 'u'.code && payload[14].toInt() == 'c'.code

    /** True for the handshake's own messages (second byte 0x10), whose payload is not encrypted. */
    public fun isHandshake(payload: ByteArray): Boolean = payload.size >= 2 && payload[1].toInt() == 0x10

    /** Reads the earbuds' bytes and gives each frame's payload whose CRC holds. A frame that fails it is dropped, as the app drops it. */
    public class Reader {
        private var buffer = ByteArray(0)

        public fun feed(bytes: ByteArray): List<ByteArray> {
            buffer += bytes
            val out = ArrayList<ByteArray>()
            while (buffer.isNotEmpty()) {
                if (buffer[0].toInt() and 0xFF == LEAD) {
                    if (buffer.size < 5) break
                    val length = u32(buffer, 1)
                    if (length < 16 || length > MAX) { drop(1); continue }
                    if (buffer.size < 5 + length) break
                    payloadOf(buffer.copyOfRange(5, 5 + length))?.let { out += it }
                    drop(5 + length)
                } else if (buffer.size >= 4 && buffer.copyOfRange(0, 4).contentEquals(HEAD)) {
                    if (buffer.size < 8) break
                    val length = u32(buffer, 4)
                    if (length < 0 || length > MAX) { drop(1); continue }
                    val total = 16 + length
                    if (buffer.size < total) break
                    payloadOf(buffer.copyOfRange(0, total))?.let { out += it }
                    drop(total)
                } else if (buffer.size < 4 && HEAD.copyOfRange(0, buffer.size).contentEquals(buffer)) {
                    break
                } else {
                    drop(1)
                }
            }
            return out
        }

        private fun drop(n: Int) {
            buffer = buffer.copyOfRange(n, buffer.size)
        }

        private fun payloadOf(frame: ByteArray): ByteArray? {
            if (frame.size < 16 || !frame.copyOfRange(0, 4).contentEquals(HEAD)) return null
            val length = u32(frame, 4)
            if (length < 0 || frame.size != 16 + length) return null
            val payload = frame.copyOfRange(8, 8 + length)
            if (u32(frame, 8 + length) != SleepAudioFile.crc32(payload).toInt()) return null
            if (!frame.copyOfRange(12 + length, 16 + length).contentEquals(TAIL)) return null
            return payload
        }

        private companion object {
            const val MAX = 1 shl 16
        }
    }
}
