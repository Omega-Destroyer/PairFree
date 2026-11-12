package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * JBL headphones and earbuds: one command at a time, each waiting for anything from the earbuds or 1 s, as JBL's app
 * waits (HandleSend; docs/jbl/PROTOCOL.md section 2). The earbuds also send changes made with their buttons as they
 * happen. Over GATT each notification is one frame ([notifications]); [mtu] is what the LE link agreed on.
 */
public class JblTalk(model: JblModel, private val notifications: Boolean, mtu: Int? = null) : SessionTalk<JblFrame>() {
    public val session: JblSession = JblSession(model).also { s -> mtu?.let { s.mtu = it } }
    private val parser = JblReader(race = model.family == JblModel.Family.AIROHA, p4 = model.family == JblModel.Family.P4)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eq
    override val pacing: Pacing = Pacing(replyMs = 1_000)
    override fun start(): List<JblFrame> = session.start()
    override fun poll(): List<JblFrame> = session.batteryRequests()
    override fun allowed(message: JblFrame): Boolean = session.allowed(message)
    override fun bytes(message: JblFrame): ByteArray = message.bytes()
    override fun takeOutbox(): List<JblFrame> = session.takeOutbox()
    override fun takeReadBacks(): List<JblFrame> = session.takeReadBacks()
    override fun set(command: Command, value: Int): List<JblFrame>? = session.set(command, value)
    override fun change(change: SettingChange): List<JblFrame>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val frames = parser.feed(bytes, newMessage = notifications)
        for (frame in frames) guarded {
            log?.invoke("recv $frame")
            if (session.receive(frame)) changed = true
        }
        return Fed(changed, frames)
    }
}
