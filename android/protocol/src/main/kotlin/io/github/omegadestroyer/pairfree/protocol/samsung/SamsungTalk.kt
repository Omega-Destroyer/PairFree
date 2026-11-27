package io.github.omegadestroyer.pairfree.protocol.samsung

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Galaxy Buds: they send their whole state on connecting, then each change as it happens, so nothing is asked for. Every
 * packet updates the state and the rows; what the session wants to say back (the manager info, the version request) goes
 * out at once.
 */
public class SamsungTalk(public val session: SamsungSession) : SessionTalk<ByteArray>() {
    private val parser = SamsungParser(session.model.legacy)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val fitTest: FitTest get() = session
    override val pacing: Pacing = Pacing(pollMs = 0)
    override fun start(): List<ByteArray> = emptyList()
    override fun bytes(message: ByteArray): ByteArray = message
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)?.let(session::frames)
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)?.let(session::frames)

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val now = ArrayList<ByteArray>()
        for (packet in parser.feed(bytes)) guarded {
            log?.invoke("recv $packet")
            if (session.receive(packet)) changed = true
            now += session.frames(session.takeOutbox())
        }
        return Fed(changed, now = now)
    }
}
