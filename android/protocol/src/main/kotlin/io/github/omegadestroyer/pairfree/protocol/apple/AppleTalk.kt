package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * AirPods: the handshake, the feature flags and the notification request, 200 ms apart and once more 5 s later, as
 * LibrePods sends them (docs/apple/PROTOCOL.md section 3). The AirPods then send their battery and settings, and every
 * change, as it happens; nothing is polled. Each read of the channel is one packet.
 */
public class AppleTalk : SessionTalk<ByteArray>() {
    public val session: AppleSession = AppleSession()
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val pacing: Pacing = Pacing(startGapMs = 200, againMs = 5_000, pollMs = 0)
    override fun start(): List<ByteArray> = session.start()
    override fun allowed(message: ByteArray): Boolean = session.allowed(message)
    override fun bytes(message: ByteArray): ByteArray = message
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)?.map { it.bytes() }
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)?.map { it.bytes() }

    override fun feed(bytes: ByteArray): Fed {
        // The information packet carries serial numbers: keep them out of the log.
        log?.invoke(if (AapPacket.parse(bytes)?.opcode == AppleOpcode.INFORMATION) "recv information (${bytes.size} bytes)" else "recv ${bytes.hexLine()}")
        return Fed(session.receive(bytes))
    }
}

/**
 * Beats on Apple's chips that speak B2P or BCD: the reads, 200 ms apart, then every answer and announcement as it comes
 * (docs/apple/B2P.md). The bytes are split into frames by their length and checked by their CRC.
 */
public class BeatsTalk(public val session: BeatsControl) : SessionTalk<ByteArray>() {
    private var pending = ByteArray(0)
    override val state: EarbudState get() = session.state
    override val groups: List<SettingGroup> get() = session.groups
    override val pacing: Pacing = Pacing(startGapMs = 200, pollMs = 0)
    override fun start(): List<ByteArray> = session.start()
    override fun bytes(message: ByteArray): ByteArray = message
    override fun set(command: Command, value: Int): List<ByteArray>? = session.set(command, value)
    override fun change(change: SettingChange): List<ByteArray>? = session.change(change)

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val now = ArrayList<ByteArray>()
        val (frames, rest) = session.split(pending + bytes)
        pending = rest
        for (raw in frames) guarded {
            log?.invoke("recv ${raw.hexLine()}")
            if (session.receiveBytes(raw)) changed = true
            now += session.outgoing
        }
        return Fed(changed, now = now)
    }
}
