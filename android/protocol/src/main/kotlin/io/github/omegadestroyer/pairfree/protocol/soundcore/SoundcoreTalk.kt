package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.FitTest
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * Soundcore earbuds: asks for the state on connecting and now and then after, and applies every packet. The earbuds also
 * send changes unasked. A setting goes out as the model's packets, and the whole state is read back a moment later, which
 * confirms it.
 */
public class SoundcoreTalk(model: String) : SessionTalk<ByteArray>() {
    public val session: SoundcoreSession = SoundcoreSession(model)
    private val parser = SoundcoreParser(checksum = session.layout?.checksum ?: true)
    private var wrote = false
    private var askedPeers = false
    private var outbox: List<ByteArray> = emptyList()
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val fitTest: FitTest get() = session
    override val pacing: Pacing = Pacing()
    override fun start(): List<ByteArray> = listOf(session.requestState())
    override fun poll(): List<ByteArray> = withPeers()
    override fun bytes(message: ByteArray): ByteArray = message
    override fun takeReadBacks(): List<ByteArray> = if (wrote) withPeers().also { wrote = false } else emptyList()
    override fun takeOutbox(): List<ByteArray> = outbox.also { outbox = emptyList() }

    /** The state request, and the paired devices' list where the earbuds keep one. */
    private fun withPeers(): List<ByteArray> = listOfNotNull(session.requestState(), if (session.listsPeers) session.requestPeers() else null)
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)?.let { written(listOf(it)) }
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)?.let { written(session.frames(it)) }

    /** [frames] are a write: the state is read back after them ([takeReadBacks]). */
    public fun written(frames: List<ByteArray>): List<ByteArray> = frames.also { wrote = true }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        for (packet in parser.feed(bytes)) guarded {
            log?.invoke("recv $packet")
            if (session.receive(packet)) changed = true
            // The first state that shows two devices switched on: ask for the list at once rather than at the next poll.
            if (!askedPeers && session.listsPeers) {
                askedPeers = true
                outbox = listOf(session.requestPeers())
            }
        }
        return Fed(changed)
    }
}
