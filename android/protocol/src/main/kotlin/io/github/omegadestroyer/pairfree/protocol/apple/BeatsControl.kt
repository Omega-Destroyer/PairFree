package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup

/**
 * What the connection needs of a session with a Beats on Apple's chips, whichever of Apple's Beats protocols it speaks
 * (B2P, BCD; docs/apple/B2P.md). Everything is raw bytes on the serial channel; a session only ever returns bytes it allows.
 */
public interface BeatsControl {
    public val model: AppleModel?
    public val state: EarbudState
    public val groups: List<SettingGroup>

    /** The reads that open the conversation, in order (the connection sends them 200 ms apart). */
    public fun start(): List<ByteArray>

    /** The whole frames at the start of [buffer], and what is left of a frame not yet complete. */
    public fun split(buffer: ByteArray): Pair<List<ByteArray>, ByteArray>

    /** Applies one whole frame from the Beats; true when [state] or [groups] changed. [outgoing] then holds what to send back. */
    public fun receiveBytes(raw: ByteArray): Boolean

    /** Acknowledgements and re-reads that the last [receiveBytes] calls for; valid until the next call. */
    public val outgoing: List<ByteArray>

    public fun change(change: SettingChange): List<ByteArray>?

    public fun set(command: Command, value: Int): List<ByteArray>?

    /** What the Beats answered, for the device report. */
    public val answers: Map<String, Int?>

    /** Extra lines for the device report. */
    public val reportLines: List<String>
}
