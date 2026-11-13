package io.github.omegadestroyer.pairfree.protocol.onemore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * 1MORE earbuds and headphones over the standard serial port (docs/1more/PROTOCOL.md section 3): one command at a time,
 * each waiting for its answer (or 1 s; 1MORE's app does not wait, PairFree waits as for Nothing's), 50 ms between frames.
 * The earbuds answer a read with the same command and a write with success or failure.
 */
public class OneMoreTalk(model: OneMoreModel?) : SessionTalk<OneMoreFrame>() {
    public val session: OneMoreSession = OneMoreSession(model)
    private val parser = OneMoreParser()
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eq
    override val pacing: Pacing = Pacing(replyMs = 1_000, gapMs = 50)
    override fun start(): List<OneMoreFrame> = session.start()
    override fun poll(): List<OneMoreFrame> = session.batteryRequests()
    override fun allowed(message: OneMoreFrame): Boolean = session.allowed(message)
    override fun bytes(message: OneMoreFrame): ByteArray = message.bytes()
    override fun answers(sent: OneMoreFrame, got: Any): Boolean = got is OneMoreFrame && session.answers(sent, got)
    override fun takeOutbox(): List<OneMoreFrame> = session.takeOutbox()
    override fun takeReadBacks(): List<OneMoreFrame> = session.takeReadBacks()
    override fun set(command: Command, value: Int): List<OneMoreFrame>? = session.set(command, value)
    override fun change(change: SettingChange): List<OneMoreFrame>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val frames = parser.feed(bytes)
        for (frame in frames) guarded {
            log?.invoke("recv $frame")
            if (session.receive(frame)) changed = true
        }
        return Fed(changed, frames)
    }
}
