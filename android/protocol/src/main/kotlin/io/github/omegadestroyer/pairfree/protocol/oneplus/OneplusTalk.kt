package io.github.omegadestroyer.pairfree.protocol.oneplus

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * OnePlus and OPPO earbuds over HeyMelody's protocol (docs/oneplus/PROTOCOL.md): the reads the model lists, 200 ms apart,
 * then every answer as it comes. Source analysis and a virtual run only.
 */
public class OneplusTalk(model: OneplusModel?) : SessionTalk<ByteArray>() {
    public val session: OneplusSession = OneplusSession(model)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val eq: Any? get() = session.eqReport
    override val fitTest: FitTest get() = session
    override val pacing: Pacing = Pacing(startGapMs = 200, pollMs = 0)
    override fun start(): List<ByteArray> = session.start()
    override fun allowed(message: ByteArray): Boolean = session.allowed(message)
    override fun bytes(message: ByteArray): ByteArray = message
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)
    override fun feed(bytes: ByteArray): Fed = Fed(session.receive(bytes))
}
