package io.github.omegadestroyer.pairfree.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The open channel, as a [Conversation] uses it. */
public interface TalkWire {
    /** Sends one frame. Throws when the channel has closed. */
    public suspend fun send(frame: ByteArray)

    /** What the earbuds send, as it comes. Ends by throwing when the channel closes. */
    public fun reads(): Flow<ByteArray>

    /** Closes the channel, which ends the conversation. */
    public fun close()
}

/**
 * Runs one [Talk] over one open channel, for every brand: the opening messages, then one message at a time from a queue,
 * each spaced and waited for as the brand's [Pacing] says, the battery asked again now and then, and everything the
 * earbuds send handed to the talk. [changed] is called when the state or the rows changed, [fed] after every read.
 *
 * It ends when the channel closes. Anything thrown while talking ends it the same way, so one brand's fault closes its
 * channel (which the connection then opens again) and never the app.
 */
public class Conversation<M>(
    private val talk: Talk<M>,
    private val wire: TalkWire,
    private val log: ((String) -> Unit)? = null,
    private val changed: () -> Unit = {},
    private val fed: () -> Unit = {},
) {
    private class Item<M>(val message: M, val opening: Boolean, val kind: Any? = null) {
        var dropped = false
    }

    private val queue = Channel<Item<M>>(Channel.UNLIMITED)

    /** What is still waiting of each kind of write ([send]'s `replaces`). */
    private val waiting = HashMap<Any, MutableList<Item<M>>>()

    /**
     * Queues [messages] behind what is waiting. With [replaces], they are a write of that kind (an EQ curve, say), and
     * take the place of whatever an older write of the same kind still has waiting: a write overtaken by a newer one is
     * not worth finishing. The message going out at that moment still goes. Nothing happens once the conversation has
     * ended. Like everything of a conversation, to be called from the thread it runs on.
     */
    public fun send(messages: List<M>, replaces: Any? = null) {
        if (replaces == null) {
            for (m in messages) queue.trySend(Item(m, opening = false))
            return
        }
        waiting.remove(replaces)?.forEach { it.dropped = true }
        val items = messages.map { Item(it, opening = false, kind = replaces) }
        waiting[replaces] = items.toMutableList()
        for (item in items) queue.trySend(item)
    }

    /** Talks until the channel closes. */
    public suspend fun run(): Unit = coroutineScope {
        val pacing = talk.pacing
        val answered = Channel<Any>(Channel.UNLIMITED)
        talk.log = log
        if (pacing.showAtStart) changed()
        val sender = launch {
            try {
                for (item in queue) {
                    if (item.dropped) continue
                    item.kind?.let { kind -> waiting[kind]?.let { left -> left.remove(item); if (left.isEmpty()) waiting.remove(kind) } }
                    val message = item.message
                    if (!talk.allowed(message)) continue
                    val frame = talk.bytes(message)
                    // Whatever came before this message is not its answer.
                    while (answered.tryReceive().isSuccess) Unit
                    if (log != null) talk.describe(message)?.let { log.invoke("send $it") }
                    val wait = talk.replyMs(message)
                    var answeredInTime = wait <= 0
                    var resent = 0
                    while (true) {
                        wire.send(frame)
                        if (wait <= 0) break
                        answeredInTime = withTimeoutOrNull(wait) {
                            while (!talk.answers(message, answered.receive())) Unit
                            true
                        } == true
                        if (answeredInTime || resent++ >= pacing.resends) break
                    }
                    if (!answeredInTime) {
                        if (talk.unanswered(message)) changed()
                        if (pacing.closeWhenUnanswered) {
                            // The two sides' sequence numbers are in doubt: the channel closes and opens again.
                            log?.invoke("no acknowledgement for ${talk.describe(message) ?: message}")
                            wire.close()
                            break
                        }
                    }
                    val gap = if (item.opening) pacing.startGapMs else pacing.gapMs
                    if (gap > 0) delay(gap)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // the channel closed; the reader below notices too
            }
        }
        val opening = talk.start()
        for (m in opening) queue.trySend(Item(m, opening = true))
        val again = if (pacing.againMs <= 0) null else launch {
            delay(opening.size * pacing.startGapMs + pacing.againMs)
            for (m in talk.start()) queue.trySend(Item(m, opening = true))
        }
        val poll = if (pacing.pollMs <= 0) null else launch {
            while (true) {
                delay(pacing.pollMs)
                send(talk.poll())
            }
        }
        try {
            wire.reads().collect { bytes ->
                val result = try {
                    talk.feed(bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log?.invoke("dropped a frame: ${e.message}")
                    null
                }
                if (result != null) {
                    for (frame in result.now) wire.send(frame)
                    if (result.changed) changed()
                    for (answer in result.answers) answered.trySend(answer)
                    send(talk.takeOutbox())
                }
                fed()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // the channel closed
        }
        poll?.cancel()
        again?.cancel()
        sender.cancel()
        queue.close()
    }
}
