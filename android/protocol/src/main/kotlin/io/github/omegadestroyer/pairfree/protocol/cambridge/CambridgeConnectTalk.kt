package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/** Melomania Connect's GAIA v3 frames: one at a time with the app's 100 ms gap, each waiting up to 3 s for its answer. */
public class CambridgeConnectTalk(model: CambridgeModel) : SessionTalk<ByteArray>() {
    public val session: CambridgeConnectSession = CambridgeConnectSession(model)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val pacing: Pacing = Pacing(replyMs = REPLY_MS, gapMs = CambridgeConnectWire.TX_DELAY_MS.toLong())
    override fun start(): List<ByteArray> = session.start()
    override fun poll(): List<ByteArray> = session.batteryRequests()
    override fun allowed(message: ByteArray): Boolean = session.allowed(message)
    override fun bytes(message: ByteArray): ByteArray = message
    override fun answers(sent: ByteArray, got: Any): Boolean = got == key(sent)
    override fun takeReadBacks(): List<ByteArray> = session.takeReadBacks()
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)
    override fun unanswered(sent: ByteArray): Boolean = true
    override fun feed(bytes: ByteArray): Fed = Fed(true, session.feed(bytes))
    override val eq: Any? get() = session.eqBands

    /** The frame's vendor, feature and command, as [CambridgeConnectSession.feed] names what answered. */
    private fun key(frame: ByteArray): Int = ConnectParser().feed(frame).singleOrNull()?.key ?: -1

    private companion object {
        const val REPLY_MS = 3_000L
    }
}
