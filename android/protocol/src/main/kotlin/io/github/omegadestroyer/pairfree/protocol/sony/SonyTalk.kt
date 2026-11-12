package io.github.omegadestroyer.pairfree.protocol.sony

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Sony earbuds and headphones: each message waits 750 ms for its acknowledgement and goes again up to ten times, as Sony's
 * app sends them (docs/sony/PROTOCOL.md section 2), and every message from them is acknowledged at once. The session asks
 * for the protocol, then for what the earbuds list; the battery is read again now and then, and changes arrive as
 * notifications. A message never acknowledged leaves the two sides' sequence numbers in doubt, so the channel closes and
 * opens again, as Sony's app does.
 */
public class SonyTalk(tableSet: Int, model: SonyModel?) : SessionTalk<SonyOut>() {
    public val session: SonySession = SonySession(tableSet, model)
    private val link = SonyLink()
    private val parser = SonyParser()
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eq
    override val pacing: Pacing = Pacing(replyMs = 750, resends = 10, closeWhenUnanswered = true)
    override fun start(): List<SonyOut> = session.start()
    override fun poll(): List<SonyOut> = session.batteryRequests()
    override fun allowed(message: SonyOut): Boolean = session.allowed(message)
    override fun bytes(message: SonyOut): ByteArray = link.frame(message)
    override fun takeOutbox(): List<SonyOut> = session.takeOutbox()
    override fun set(command: Command, value: Int): List<SonyOut>? = session.set(command, value)
    override fun change(change: SettingChange): List<SonyOut>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val acks = ArrayList<Any>()
        val now = ArrayList<ByteArray>()
        for (message in parser.feed(bytes)) guarded {
            log?.invoke("recv $message")
            if (message.dataType == SonyWire.ACK) {
                if (link.acked(message)) acks += Unit
            } else {
                val (ack, fresh) = link.received(message)
                now += ack
                if (fresh && session.receive(message)) changed = true
            }
        }
        return Fed(changed, acks, now)
    }
}
