package io.github.omegadestroyer.pairfree.protocol.oneplus

/** HeyMelody's inner command/sequence/length packet and OPOv1 wrapper. No checksum or authentication invented. */
public data class OneplusPacket(val command: Int, val sequence: Int, val payload: ByteArray = byteArrayOf()) {
    public val response: Boolean get() = command and OneplusWire.RESPONSE_BIT != 0
    public val id: Int get() = command and OneplusWire.RESPONSE_BIT.inv()
    /** Both masked command and sequence must match; notifications alone do not satisfy a pending request. */
    public val key: Int get() = (id shl 8) or sequence

    public fun inner(): ByteArray {
        require(command in 0..0xffff && sequence in 0..255 && payload.size + 5 <= MAX_INNER)
        return byteArrayOf(command.toByte(), (command shr 8).toByte(), sequence.toByte(), payload.size.toByte(), (payload.size shr 8).toByte()) + payload
    }

    public fun bytes(): ByteArray = wrap(OneplusWire.SINGLE, null, inner())

    /** Payload bound per fragment, before the OPO header. Transport MTU accounting belongs to the future link. */
    public fun fragments(maxFragmentPayload: Int): List<ByteArray> {
        require(maxFragmentPayload in 1..MAX_INNER)
        val bytes = inner()
        if (bytes.size <= maxFragmentPayload) return listOf(bytes())
        val chunks = bytes.toList().chunked(maxFragmentPayload)
        require(chunks.size <= 256)
        return chunks.mapIndexed { index, chunk ->
            val kind = when (index) { 0 -> OneplusWire.FIRST; chunks.lastIndex -> OneplusWire.LAST; else -> OneplusWire.MIDDLE }
            wrap(kind, index, chunk.toByteArray())
        }
    }

    public companion object {
        /** Local bound for ordinary settings packets, not a claimed vendor MTU or firmware transport. */
        public const val MAX_INNER: Int = 4096

        private fun wrap(kind: Int, fragment: Int?, bytes: ByteArray): ByteArray {
            var length = bytes.size + if (fragment == null) 2 else 3
            var encoded = byteArrayOf()
            do {
                val low = length and 0x7f
                length = length ushr 7
                encoded += (low or if (length > 0) 0x80 else 0).toByte()
            } while (length > 0)
            return byteArrayOf(OneplusWire.START.toByte()) + encoded + byteArrayOf(kind.toByte(), 0) +
                (fragment?.let { byteArrayOf(it.toByte()) } ?: byteArrayOf()) + bytes
        }
    }
}

/** Reassembles both socket chunks and numbered logical fragments, with independent bounded buffers. */
public class OneplusParser {
    private var pending: ByteArray = byteArrayOf()
    private var fragments: ByteArray? = null
    private var nextFragment: Int = 0

    public fun reset() { pending = byteArrayOf(); clearFragments() }
    private fun clearFragments() { fragments = null; nextFragment = 0 }
    private fun discard() { pending = pending.drop(1).toByteArray(); clearFragments() }

    public fun feed(bytes: ByteArray): List<OneplusPacket> {
        val out = mutableListOf<OneplusPacket>()
        for (byte in bytes) {
            pending += byte
            while (pending.isNotEmpty()) {
                if (pending[0].u() != OneplusWire.START) { discard(); continue }
                if (pending.size < 2) break
                var length = pending[1].u() and 0x7f
                var count = 1
                if (pending[1].u() and 0x80 != 0) {
                    if (pending.size < 3) break
                    if (pending[2].u() and 0x80 != 0) { discard(); continue }
                    length = length or (pending[2].u() shl 7)
                    count = 2
                    if (length < 128) { discard(); continue } // reject overlong/noncanonical length
                }
                if (length !in 2..OneplusPacket.MAX_INNER + 3) { discard(); continue }
                val total = 1 + count + length
                if (pending.size < total) break
                val body = pending.copyOfRange(1 + count, total)
                pending = pending.drop(total).toByteArray()
                val kind = body[0].u() and OneplusWire.FRAGMENT_MASK
                if (kind == OneplusWire.SINGLE) {
                    clearFragments()
                    decode(body.copyOfRange(2, body.size))?.let(out::add)
                    continue
                }
                if (body.size < 4) { clearFragments(); continue }
                val index = body[2].u()
                val piece = body.copyOfRange(3, body.size)
                if (kind == OneplusWire.FIRST) {
                    clearFragments()
                    if (index != 0) continue
                    fragments = piece
                    nextFragment = 1
                } else {
                    val previous = fragments
                    if (previous == null || index != nextFragment || previous.size + piece.size > OneplusPacket.MAX_INNER) {
                        clearFragments(); continue
                    }
                    fragments = previous + piece
                    nextFragment++
                    if (kind == OneplusWire.LAST) {
                        decode(fragments!!)?.let(out::add)
                        clearFragments()
                    }
                }
                if ((fragments?.size ?: 0) > OneplusPacket.MAX_INNER) clearFragments()
            }
        }
        return out
    }

    private fun decode(inner: ByteArray): OneplusPacket? {
        if (inner.size !in 5..OneplusPacket.MAX_INNER) return null
        val length = inner[3].u() or (inner[4].u() shl 8)
        if (length + 5 != inner.size) return null
        return OneplusPacket(inner[0].u() or (inner[1].u() shl 8), inner[2].u(), inner.copyOfRange(5, inner.size))
    }
}

/** A narrow set of harmless reads, not the SDK's unconditional dispatch list (which also contains debug commands). */
public object OneplusReads {
    public fun capabilities(sequence: Int): OneplusPacket = OneplusPacket(OneplusCommand.CAPABILITIES, sequence)
    public fun product(sequence: Int): OneplusPacket = OneplusPacket(OneplusCommand.PRODUCT, sequence)
    public fun battery(sequence: Int): OneplusPacket = OneplusPacket(OneplusCommand.BATTERY, sequence)

    /** Status byte then LSB-first capability bits, capped at the source's 69 entries. */
    public fun capabilityBits(packet: OneplusPacket): Set<Int>? {
        if (!packet.response || packet.id != OneplusCommand.CAPABILITIES || packet.payload.firstOrNull()?.u() != 0) return null
        return (0 until OneplusWire.MAX_CAPABILITY_BITS).filter { bit ->
            packet.payload.getOrNull(1 + bit / 8)?.u()?.and(1 shl (bit % 8))?.let { it != 0 } == true
        }.toSet()
    }
}

/** Raw device type retained: it must be mapped using vendor constants before displaying left/right/case labels. */
public data class OneplusBattery(val type: Int, val percent: Int?, val charging: Boolean) {
    public companion object {
        public fun of(packet: OneplusPacket): List<OneplusBattery>? {
            val p = packet.payload
            if (!packet.response || packet.id != OneplusCommand.BATTERY || p.size < 2 || p[0].u() != 0 || p.size != 2 + p[1].u() * 2) return null
            return (0 until p[1].u()).map { index ->
                val raw = p[3 + index * 2].u()
                OneplusBattery(p[2 + index * 2].u(), (raw and 0x7f).takeIf { it <= 100 }, raw and 0x80 != 0)
            }
        }
    }
}

private fun Byte.u(): Int = toInt() and 0xff
