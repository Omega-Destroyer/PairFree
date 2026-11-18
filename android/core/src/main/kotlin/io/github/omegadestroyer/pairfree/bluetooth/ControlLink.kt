package io.github.omegadestroyer.pairfree.bluetooth

import kotlinx.coroutines.flow.Flow

/**
 * An open control channel to a pair of earbuds: the classic serial channel ([SppLink]) most brands
 * use, or Bluetooth LE GATT ([GattLink]) for JBL's. [close] can abort a [connect] still waiting.
 */
interface ControlLink {
    /** Opens the channel; throws an IOException when it cannot. */
    suspend fun connect()

    /** Sends one frame. */
    suspend fun send(frame: ByteArray)

    /** What the earbuds send, as it comes. Ends with an IOException when the channel closes. */
    fun reads(): Flow<ByteArray>

    /** Closes the channel; safe to call more than once, and while [connect] waits. */
    fun close()
}
