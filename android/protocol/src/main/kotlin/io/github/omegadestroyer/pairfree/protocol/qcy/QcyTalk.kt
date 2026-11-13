package io.github.omegadestroyer.pairfree.protocol.qcy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * QCY earbuds and headphones: one GATT operation at a time, 50 ms apart, as QCY's app sends them (docs/qcy/PROTOCOL.md
 * section 3). The earbuds report each setting asked for, and every change made on them, as notifications: on QCY's
 * settings characteristic, or as JieLi RCSP frames, whose own commands get an answer. What the advert said shows at once.
 */
public class QcyTalk(public val session: QcyControl) : SessionTalk<ByteArray>() {
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eq
    override val pacing: Pacing = Pacing(gapMs = 50, showAtStart = true)
    override fun start(): List<ByteArray> = session.start()
    override fun poll(): List<ByteArray> = session.batteryRequests()
    override fun allowed(message: ByteArray): Boolean = session.allowed(message)
    override fun bytes(message: ByteArray): ByteArray = message
    override fun takeOutbox(): List<ByteArray> = session.takeOutbox()
    override fun takeReadBacks(): List<ByteArray> = session.takeReadBacks()
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        log?.invoke("recv ${bytes.hexLine()}")
        return Fed(session.receive(bytes))
    }
}
