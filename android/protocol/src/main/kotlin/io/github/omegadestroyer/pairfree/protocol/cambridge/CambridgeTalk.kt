package io.github.omegadestroyer.pairfree.protocol.cambridge

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Legacy Melomania's GAIA v1 frames: one at a time, each waiting for its answer up to the app's 30-second deadline; a
 * frame never answered is marked failed. No raw state logging (addresses). All its settings are rows.
 */
public class CambridgeTalk(model: CambridgeModel) : SessionTalk<ByteArray>() {
    public val session: CambridgeSession = CambridgeSession(model)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val pacing: Pacing = Pacing(replyMs = CambridgeWire.REPLY_MS.toLong())
    override fun start(): List<ByteArray> = session.start()
    override fun poll(): List<ByteArray> = session.batteryRequests()
    override fun allowed(message: ByteArray): Boolean = session.allowed(message)
    override fun bytes(message: ByteArray): ByteArray = message
    override fun answers(sent: ByteArray, got: Any): Boolean = got == key(sent)
    override fun takeReadBacks(): List<ByteArray> = session.takeReadBacks()
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)

    override fun unanswered(sent: ByteArray): Boolean {
        session.failed(key(sent))
        return true
    }

    override fun feed(bytes: ByteArray): Fed = Fed(true, session.feed(bytes))

    private fun key(frame: ByteArray): Int = ((frame[6].toInt() and 0xff) shl 8) or (frame[7].toInt() and 0xff)
}
