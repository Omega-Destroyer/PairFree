package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * One connection to QCY earbuds, whichever protocol they speak: QCY's own ([QcySession]) or JieLi's RCSP
 * carrying QCY's items ([QcyJieliSession]). Messages in and out are [io.github.omegadestroyer.pairfree.protocol.GattEnvelope]s.
 */
public interface QcyControl {
    public val model: QcyModel?
    public val state: EarbudState
    public val groups: List<SettingGroup>
    public val firmware: String?

    /** What the device report shows: the last value of each item and characteristic read. */
    public val replies: Map<String, ByteArray>

    /** The service the channel opens. */
    public val service: String

    /** What is sent on opening the channel. */
    public fun start(): List<ByteArray>

    /** The battery read, to send again now and then. */
    public fun batteryRequests(): List<ByteArray>

    /** The reads that confirm the last changes, for the connection to send a little later. */
    public fun takeReadBacks(): List<ByteArray>

    /** What the session wants sent next, such as answers to the earbuds' own commands. */
    public fun takeOutbox(): List<ByteArray> = emptyList()

    /** False for what PairFree never sends. */
    public fun allowed(envelope: ByteArray): Boolean

    /** Applies one message from the earbuds. Returns true when [state] or [groups] changed. */
    public fun receive(envelope: ByteArray): Boolean

    /** The messages for a shared setting from the screens, or null when the earbuds do not have it. */
    public fun set(command: Command, value: Int): List<ByteArray>?

    /** The messages for a change to one of the [groups], or null when the earbuds do not have the setting. */
    public fun change(change: SettingChange): List<ByteArray>?

    /** The EQ as the earbuds last reported it, or null while they have not reported one PairFree can set. */
    public val eq: QcyEq? get() = null

    /** The messages that put a curve of [gains] (whole dB, one per band of [eq]) on the earbuds, or null when PairFree cannot. */
    public fun eqCustom(gains: List<Int>): List<ByteArray>? = null

    public companion object {
        /** The session for [model]'s protocol; QCY's own for a model PairFree does not know. */
        public fun of(model: QcyModel?, advert: QcyAdvert?): QcyControl = when (model?.protocol) {
            QcyProtocol.JIELI, QcyProtocol.JIELI_CUSTOM -> QcyJieliSession(model, advert)
            else -> QcySession(model, advert)
        }
    }
}
