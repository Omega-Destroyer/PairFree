package io.github.omegadestroyer.pairfree.protocol.nothing

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Nothing and CMF earbuds and headphones: one command at a time, each waiting for its answer (or 1 s, as Nothing X waits
 * for the version), 40 ms between frames (HeadsetSppConnector.SEND_DELAY; docs/nothing/PROTOCOL.md section 3). The
 * earbuds also send changes made on them as events. A read they do not have gets an error, an empty answer, or none.
 */
public class NothingTalk(hint: NothingModel?) : SessionTalk<NothingFrame>() {
    public val session: NothingSession = NothingSession(hint)
    private val parser = NothingParser()
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eq
    override val fitTest: FitTest get() = session
    override val pacing: Pacing = Pacing(replyMs = 1_000, gapMs = 40)
    override fun start(): List<NothingFrame> = session.start()
    override fun poll(): List<NothingFrame> = session.batteryRequests()
    override fun allowed(message: NothingFrame): Boolean = session.allowed(message)
    override fun bytes(message: NothingFrame): ByteArray = message.bytes()
    override fun answers(sent: NothingFrame, got: Any): Boolean = got is NothingFrame && session.answers(sent, got)
    override fun takeOutbox(): List<NothingFrame> = session.takeOutbox()
    override fun takeReadBacks(): List<NothingFrame> = session.takeReadBacks()
    override fun set(command: Command, value: Int): List<NothingFrame>? = session.set(command, value)
    override fun change(change: SettingChange): List<NothingFrame>? = session.change(change)

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
