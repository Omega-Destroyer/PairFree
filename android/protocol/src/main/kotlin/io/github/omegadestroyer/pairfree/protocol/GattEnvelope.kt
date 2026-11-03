package io.github.omegadestroyer.pairfree.protocol

/**
 * Messages for a Bluetooth LE control channel that spreads over several characteristics of one service,
 * as QCY's does, carried as plain bytes through the app's control link. To the earbuds: an operation
 * (write or read), the characteristic's 16-bit short UUID and, for a write, the value. From them: the
 * characteristic's short UUID and the value, for a notification or the answer to a read alike.
 */
public object GattEnvelope {
    public const val WRITE: Int = 0
    public const val READ: Int = 1

    /** A write of [value] to the characteristic [short]. */
    public fun write(short: Int, value: ByteArray): ByteArray = byteArrayOf(WRITE.toByte(), (short shr 8).toByte(), short.toByte()) + value

    /** A read of the characteristic [short]. */
    public fun read(short: Int): ByteArray = byteArrayOf(READ.toByte(), (short shr 8).toByte(), short.toByte())

    /** What the link received: the characteristic [short] and its value. */
    public fun received(short: Int, value: ByteArray): ByteArray = byteArrayOf((short shr 8).toByte(), short.toByte()) + value

    /** One message to the earbuds, taken apart. */
    public data class Out(val operation: Int, val short: Int, val value: ByteArray) {
        override fun equals(other: Any?): Boolean = other is Out && operation == other.operation && short == other.short && value.contentEquals(other.value)
        override fun hashCode(): Int = (operation * 31 + short) * 31 + value.contentHashCode()
    }

    /** One message from the earbuds, taken apart. */
    public data class In(val short: Int, val value: ByteArray) {
        override fun equals(other: Any?): Boolean = other is In && short == other.short && value.contentEquals(other.value)
        override fun hashCode(): Int = short * 31 + value.contentHashCode()
        override fun toString(): String = "In(${short.toString(16).padStart(4, '0')}, ${value.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }})"
    }

    public fun out(bytes: ByteArray): Out? =
        if (bytes.size < 3) null else Out(bytes[0].toInt() and 0xFF, ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF), bytes.copyOfRange(3, bytes.size))

    public fun parse(bytes: ByteArray): In? =
        if (bytes.size < 2) null else In(((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF), bytes.copyOfRange(2, bytes.size))
}
