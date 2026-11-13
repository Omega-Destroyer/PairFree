package io.github.omegadestroyer.pairfree.protocol

/**
 * One brand's side of the conversation with a pair of earbuds over an open channel, in the shape every brand shares:
 * what to send on connecting and now and then, what the earbuds' bytes mean, and how the shared settings and the brand's
 * own rows become messages. [M] is the brand's message as its session builds it (a packet, a frame, plain bytes).
 * [Conversation] runs any of these; a new brand implements this (its session can, directly) and needs no loop of its own.
 * One is made each time the channel opens, as the earbuds start over.
 */
public interface Talk<M> {
    /** What the shared screens show. */
    public val state: EarbudState

    /** The brand's settings beyond the shared ones, as rows. */
    public val groups: List<SettingGroup>

    /** What the earbuds last said about their EQ, whatever its form; the Sound tab opens again when it changes. */
    public val eq: Any? get() = null

    /** The session, when the earbuds run an ear tip fit test themselves. */
    public val fitTest: FitTest? get() = null

    /** How the brand's app spaces what it sends. */
    public val pacing: Pacing

    /** Gets a line for each message received, in debug builds; null otherwise. Set by [Conversation]. */
    public var log: ((String) -> Unit)?

    /** The messages that open the conversation, in order. */
    public fun start(): List<M>

    /** What to ask again every [Pacing.pollMs]: the battery, for earbuds that do not send it on their own. */
    public fun poll(): List<M> = emptyList()

    /** False for a message that must never go out (each brand's never-send list). */
    public fun allowed(message: M): Boolean = true

    /** The message as bytes for the channel. Called once per message, just before it first goes out. */
    public fun bytes(message: M): ByteArray

    /** How long to wait for the answer to [message] before the next goes; 0 when it has none. */
    public fun replyMs(message: M): Long = pacing.replyMs

    /** True when [got], one of [Fed.answers], is the answer [sent] waits for. */
    public fun answers(sent: M, got: Any): Boolean = true

    /** [sent] was never answered. True when that changed what the screens show. */
    public fun unanswered(sent: M): Boolean = false

    /** Takes what the earbuds sent, as each read of the channel gives it. */
    public fun feed(bytes: ByteArray): Fed

    /** Messages the session wants sent after what it just received. */
    public fun takeOutbox(): List<M> = emptyList()

    /** The reads that confirm the last change, to go out a moment after it. */
    public fun takeReadBacks(): List<M> = emptyList()

    /** One of the shared settings, or null when these earbuds do not have it. */
    public fun set(command: Command, value: Int): List<M>? = null

    /** One of the brand's own rows, or null when it is not one of them. */
    public fun change(change: SettingChange): List<M>? = null

    /** The message for the debug log, or null to leave it to the channel. */
    public fun describe(message: M): String? = null
}

/**
 * How a brand's app spaces its messages. [replyMs]: how long each waits for its answer (0: none is awaited). [gapMs]:
 * the pause after each. [startGapMs]: the pause after each of the opening messages. [againMs]: the opening messages go
 * once more this long after the first round (0: once). [pollMs]: how often [Talk.poll] is asked. [resends]: how often a
 * message with no answer goes again. [closeWhenUnanswered]: the channel is closed, to be opened afresh, when even that
 * brought no answer. [showAtStart]: the screens get the session's state before the earbuds have said anything.
 */
public class Pacing(
    public val replyMs: Long = 0,
    public val gapMs: Long = 0,
    public val startGapMs: Long = gapMs,
    public val againMs: Long = 0,
    public val pollMs: Long = POLL_MS,
    public val resends: Int = 0,
    public val closeWhenUnanswered: Boolean = false,
    public val showAtStart: Boolean = false,
) {
    public companion object {
        /** How often the brands that poll read the battery again. */
        public const val POLL_MS: Long = 30_000L
    }
}

/**
 * What one read of the channel came to. [changed]: the state or the rows changed. [answers]: what arrived that a waiting
 * message may be answered by ([Talk.answers]). [now]: bytes to send back at once, ahead of anything queued (an
 * acknowledgement).
 */
public class Fed(
    public val changed: Boolean,
    public val answers: List<Any> = emptyList(),
    public val now: List<ByteArray> = emptyList(),
)

/** A [Talk] over a session of the brand's own, with what the adapters share. */
public abstract class SessionTalk<M> : Talk<M> {
    override var log: ((String) -> Unit)? = null

    /**
     * Handles one frame, keeping a single bad one from ending the conversation: what it throws is logged and the frame
     * dropped. None of the device code has been tried on most models, so an unforeseen byte must not freeze it.
     */
    protected inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log?.invoke("dropped a frame: ${e.message}")
        }
    }

    protected fun ByteArray.hexLine(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
