package io.github.omegadestroyer.pairfree.protocol.bose

import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Bose headphones and earbuds: one request at a time, each waiting for its answer (or 2 s, as bosectl waits), as bosectl
 * and Bose's app send them (docs/bose/PROTOCOL.md section 2). The device also sends changes made with its buttons as
 * they happen. Its settings are all rows; nothing of the shared commands goes to it.
 */
public class BoseTalk(public val session: BoseSession = BoseSession()) : SessionTalk<BosePacket>() {
    private val parser = BoseParser()
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val pacing: Pacing = Pacing(replyMs = 2_000)
    override fun start(): List<BosePacket> = session.start()
    override fun poll(): List<BosePacket> = session.batteryRequests()
    override fun allowed(message: BosePacket): Boolean = session.allowed(message)
    override fun bytes(message: BosePacket): ByteArray = message.bytes()
    override fun answers(sent: BosePacket, got: Any): Boolean = got is BosePacket && session.answers(sent, got)
    override fun takeOutbox(): List<BosePacket> = session.takeOutbox()
    override fun change(change: SettingChange): List<BosePacket>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val packets = parser.feed(bytes)
        for (packet in packets) guarded {
            log?.invoke("recv $packet")
            if (session.receive(packet)) changed = true
        }
        return Fed(changed, packets)
    }
}
