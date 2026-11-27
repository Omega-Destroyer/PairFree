package io.github.omegadestroyer.pairfree.protocol.earfun

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * EarFun earbuds and headphones over the standard serial port (docs/earfun/PROTOCOL.md section 2): one command at a time,
 * a read waiting for its answer (or 1 s), 50 ms between frames, as EarFun's app queues them. The Qualcomm models answer
 * in GAIA's frames, the others in EarFun's own; the session reads either.
 */
public class EarfunTalk(model: EarfunModel) : SessionTalk<ByteArray>() {
    public val session: EarfunSession = EarfunSession(model)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val pacing: Pacing = Pacing(replyMs = 1_000, gapMs = 50)
    override fun start(): List<ByteArray> = session.start()
    override fun poll(): List<ByteArray> = session.batteryRequests()
    override fun allowed(message: ByteArray): Boolean = session.allowed(message)
    override fun bytes(message: ByteArray): ByteArray = message
    override fun replyMs(message: ByteArray): Long = if (session.awaits(message) != null) pacing.replyMs else 0
    override fun answers(sent: ByteArray, got: Any): Boolean = got == session.awaits(sent)
    override fun takeOutbox(): List<ByteArray> = session.takeOutbox()
    override fun takeReadBacks(): List<ByteArray> = session.takeReadBacks()
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        log?.invoke("recv ${bytes.hexLine()}")
        val fed = session.feed(bytes)
        return Fed(fed.changed, fed.replies)
    }
}
