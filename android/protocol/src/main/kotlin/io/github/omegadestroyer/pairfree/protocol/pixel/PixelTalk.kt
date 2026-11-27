package io.github.omegadestroyer.pairfree.protocol.pixel

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Pixel Buds: one message at a time, each that has a reply waiting for it (or 1 s), 40 ms between messages; Maestro
 * streams the battery and every change, the plain frames send them as they happen (docs/pixel/PROTOCOL.md section 3).
 */
public class PixelTalk(transport: PixelTransport, hint: PixelModel?) : SessionTalk<PixelMessage>() {
    public val session: PixelSession = PixelSession(transport, hint)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eq
    override val fitTest: FitTest get() = session
    override val pacing: Pacing = Pacing(replyMs = 1_000, gapMs = 40)
    override fun start(): List<PixelMessage> = session.start()
    override fun poll(): List<PixelMessage> = session.batteryRequests()
    override fun allowed(message: PixelMessage): Boolean = session.willSend(message)
    override fun bytes(message: PixelMessage): ByteArray = message.bytes
    override fun replyMs(message: PixelMessage): Long = if (message.reply != null) pacing.replyMs else 0
    override fun answers(sent: PixelMessage, got: Any): Boolean = got is PixelReply && session.answers(sent, got)
    override fun takeOutbox(): List<PixelMessage> = session.takeOutbox()
    override fun takeReadBacks(): List<PixelMessage> = session.takeReadBacks()
    override fun set(command: Command, value: Int): List<PixelMessage>? = session.set(command, value)
    override fun change(change: SettingChange): List<PixelMessage>? = session.change(change)
    override fun describe(message: PixelMessage): String = message.toString()

    override fun feed(bytes: ByteArray): Fed {
        log?.let { l -> session.describe(bytes).forEach { l("recv $it") } }
        val fed = session.feed(bytes)
        return Fed(fed.changed, fed.replies)
    }
}
