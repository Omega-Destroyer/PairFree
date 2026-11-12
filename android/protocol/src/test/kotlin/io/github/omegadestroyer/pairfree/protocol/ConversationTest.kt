package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.apple.AppleTalk
import io.github.omegadestroyer.pairfree.protocol.bose.BoseTalk
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeModels
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeTalk
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunModels
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunTalk
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblTalk
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingTalk
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreModels
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreTalk
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusModels
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusTalk
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelTalk
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelTransport
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyControl
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyModels
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyTalk
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungSession
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungTalk
import io.github.omegadestroyer.pairfree.protocol.sony.SonyTalk
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTalk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The one conversation loop every brand shares, on a clock the test turns by hand: when each message goes out, what it
 * waits for, and what ends the conversation. Then every brand's talk over a channel that never answers: the opening
 * messages must go out as its session builds them, in order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationTest {
    /** A channel that notes what was sent and when, and gives the conversation what the test feeds it. */
    private class Wire(private val scope: TestScope) : TalkWire {
        val sent = mutableListOf<Pair<Long, List<Byte>>>()
        val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        var closed = false
        override suspend fun send(frame: ByteArray) {
            if (closed) throw IllegalStateException("closed")
            sent += scope.currentTime to frame.toList()
        }
        override fun reads(): Flow<ByteArray> = flow {
            for (bytes in incoming) emit(bytes)
            throw IllegalStateException("closed")
        }
        override fun close() {
            closed = true
            incoming.close()
        }
        val times: List<Long> get() = sent.map { it.first }
        val bytes: List<Int> get() = sent.map { it.second.single().toInt() }
    }

    /** A talk whose messages are single bytes; a byte fed back answers the message of the same number. */
    private class Plain(
        override val pacing: Pacing,
        private val opening: List<Int> = listOf(1, 2),
        private val never: Set<Int> = emptySet(),
        private val polled: List<Int> = emptyList(),
    ) : SessionTalk<Int>() {
        var failed = mutableListOf<Int>()
        override val state = EarbudState()
        override val groups = emptyList<SettingGroup>()
        override fun start() = opening
        override fun poll() = polled
        override fun allowed(message: Int) = message !in never
        override fun bytes(message: Int) = byteArrayOf(message.toByte())
        override fun answers(sent: Int, got: Any) = got == sent
        override fun unanswered(sent: Int): Boolean {
            failed += sent
            return true
        }
        // 0x7F asks for an acknowledgement at once; anything else is an answer.
        override fun feed(bytes: ByteArray) =
            if (bytes[0] == 0x7F.toByte()) Fed(false, now = listOf(byteArrayOf(0x55))) else Fed(true, bytes.map { it.toInt() })
    }

    private fun TestScope.talking(talk: Talk<Int>, wire: Wire, changed: () -> Unit = {}): Pair<Conversation<Int>, Job> {
        val conversation = Conversation(talk, wire, changed = changed)
        val job = backgroundScope.launch { conversation.run() }
        runCurrent()
        return conversation to job
    }

    @Test
    fun eachMessageWaitsForItsAnswerOrTheBrandsTime() = runTest {
        val wire = Wire(this)
        talking(Plain(Pacing(replyMs = 1_000, pollMs = 0)), wire)
        assertEquals(listOf(1), wire.bytes)
        advanceTimeBy(999)
        assertEquals(listOf(1), wire.bytes)
        advanceTimeBy(2)
        assertEquals(listOf(0L, 1_000L), wire.times)
        assertEquals(listOf(1, 2), wire.bytes)
    }

    @Test
    fun anAnswerLetsTheNextGoAtOnceAndAnotherMessagesAnswerDoesNot() = runTest {
        val wire = Wire(this)
        talking(Plain(Pacing(replyMs = 1_000, pollMs = 0)), wire)
        advanceTimeBy(100)
        wire.incoming.trySend(byteArrayOf(9))
        runCurrent()
        assertEquals(listOf(1), wire.bytes)
        wire.incoming.trySend(byteArrayOf(1))
        runCurrent()
        assertEquals(listOf(0L, 100L), wire.times)
    }

    @Test
    fun theGapFollowsEachMessageAndNothingIsAwaitedWithoutAReplyTime() = runTest {
        val wire = Wire(this)
        val (conversation, _) = talking(Plain(Pacing(gapMs = 40, pollMs = 0)), wire)
        advanceTimeBy(1_000)
        assertEquals(listOf(0L, 40L), wire.times)
        conversation.send(listOf(7, 8))
        advanceTimeBy(1_000)
        assertEquals(listOf(0L, 40L, 1_000L, 1_040L), wire.times)
        assertEquals(listOf(1, 2, 7, 8), wire.bytes)
    }

    @Test
    fun anUnacknowledgedMessageGoesAgainThenTheChannelCloses() = runTest {
        val wire = Wire(this)
        val (_, job) = talking(Plain(Pacing(replyMs = 750, resends = 10, closeWhenUnanswered = true, pollMs = 0)), wire)
        advanceTimeBy(20_000)
        assertEquals(List(11) { it * 750L }, wire.times)
        assertEquals(List(11) { 1 }, wire.bytes)
        assertTrue(wire.closed)
        assertTrue(job.isCompleted)
    }

    @Test
    fun aMessageNeverAnsweredIsReportedAndTheNextStillGoes() = runTest {
        val wire = Wire(this)
        val talk = Plain(Pacing(replyMs = 30_000, pollMs = 0))
        var changes = 0
        talking(talk, wire) { changes++ }
        advanceTimeBy(30_001)
        assertEquals(listOf(1), talk.failed)
        assertEquals(1, changes)
        assertEquals(listOf(1, 2), wire.bytes)
        assertFalse(wire.closed)
    }

    @Test
    fun theBatteryIsAskedAgainEveryPollTime() = runTest {
        val wire = Wire(this)
        talking(Plain(Pacing(), opening = listOf(1), polled = listOf(5)), wire)
        advanceTimeBy(Pacing.POLL_MS * 2 + 1)
        assertEquals(listOf(0L, Pacing.POLL_MS, Pacing.POLL_MS * 2), wire.times)
        assertEquals(listOf(1, 5, 5), wire.bytes)
    }

    @Test
    fun theOpeningMessagesAreSpacedAndGoOnceMoreWhenTheBrandSaysSo() = runTest {
        val wire = Wire(this)
        val (conversation, _) = talking(Plain(Pacing(startGapMs = 200, againMs = 5_000, pollMs = 0), opening = listOf(1, 2, 3)), wire)
        advanceTimeBy(1_000)
        conversation.send(listOf(9))
        advanceTimeBy(10_000)
        assertEquals(listOf(0L, 200L, 400L, 1_000L, 5_600L, 5_800L, 6_000L), wire.times)
        assertEquals(listOf(1, 2, 3, 9, 1, 2, 3), wire.bytes)
    }

    @Test
    fun aMessageOnTheNeverSendListDoesNotGoOut() = runTest {
        val wire = Wire(this)
        val (conversation, _) = talking(Plain(Pacing(pollMs = 0), opening = listOf(1, 66, 2), never = setOf(66)), wire)
        conversation.send(listOf(66, 3))
        advanceTimeBy(100)
        assertEquals(listOf(1, 2, 3), wire.bytes)
    }

    @Test
    fun anAcknowledgementGoesBackAtOnceAheadOfTheQueue() = runTest {
        val wire = Wire(this)
        talking(Plain(Pacing(replyMs = 1_000, pollMs = 0)), wire)
        advanceTimeBy(10)
        wire.incoming.trySend(byteArrayOf(0x7F))
        runCurrent()
        assertEquals(listOf(1, 0x55), wire.bytes)
        assertEquals(listOf(0L, 10L), wire.times)
    }

    @Test
    fun theConversationEndsWhenTheChannelCloses() = runTest {
        val wire = Wire(this)
        val (conversation, job) = talking(Plain(Pacing(replyMs = 1_000)), wire)
        wire.close()
        runCurrent()
        assertTrue(job.isCompleted)
        conversation.send(listOf(4))
        advanceTimeBy(60_000)
        assertEquals(listOf(1), wire.bytes)
    }

    /** What [make]'s talk sends over a channel that never answers, for [forMs]. */
    private fun <M> TestScope.silent(make: () -> Talk<M>, forMs: Long): Pair<List<List<Byte>>, List<List<Byte>>> {
        val expected = make().let { t -> t.start().filter(t::allowed).map { t.bytes(it).toList() } }
        val wire = Wire(this)
        val job = backgroundScope.launch { Conversation(make(), wire).run() }
        advanceTimeBy(forMs)
        job.cancel()
        return expected to wire.sent.map { it.second }
    }

    /** Every opening message goes out once, in the order the session gives them, before anything else. */
    private fun <M> TestScope.opensAsItsSessionSays(name: String, make: () -> Talk<M>) {
        // Long enough for every opening message to time out in turn, short of the first poll.
        val replyMs = make().let { t -> t.start().maxOfOrNull { t.replyMs(it) } ?: 0 }
        val (expected, sent) = silent(make, (expected(make) * (replyMs + 500)).coerceAtLeast(1_000))
        assertEquals(expected, sent.take(expected.size), name)
    }

    private fun <M> expected(make: () -> Talk<M>): Int = make().start().size

    @Test
    fun everyBrandOpensAsItsSessionSays() = runTest {
        opensAsItsSessionSays("Bose") { BoseTalk() }
        for (model in JblModels.ALL) for (gatt in listOf(false, true)) {
            opensAsItsSessionSays("JBL ${model.name}") { JblTalk(model, notifications = gatt, mtu = if (gatt) 247 else null) }
        }
        for (model in NothingModels.ALL + listOf(null)) opensAsItsSessionSays("Nothing ${model?.productId}") { NothingTalk(model) }
        for (model in OneMoreModels.ALL + listOf(null)) opensAsItsSessionSays("1MORE ${model?.name}") { OneMoreTalk(model) }
        for (transport in PixelTransport.entries) for (model in PixelModels.ALL + listOf(null)) {
            opensAsItsSessionSays("Pixel $transport ${model?.deviceType}") { PixelTalk(transport, model) }
        }
        for (model in EarfunModels.ALL.filter { it.covered }) opensAsItsSessionSays("EarFun ${model.name}") { EarfunTalk(model) }
        for (model in QcyModels.ALL + listOf(null)) opensAsItsSessionSays("QCY ${model?.code}") { QcyTalk(QcyControl.of(model, null)) }
        for (model in OneplusModels.ALL + listOf(null)) opensAsItsSessionSays("OnePlus ${model?.id}") { OneplusTalk(model) }
        for (model in listOf("A3936", "A3951", "A6611", "")) opensAsItsSessionSays("Soundcore $model") { SoundcoreTalk(model) }
        opensAsItsSessionSays("AirPods") { AppleTalk() }
    }

    @Test
    fun soundpeatsAsksEveryReadThenTheGaiaFeaturesOfQualcommModelsOnly() = runTest {
        val gets = Requests.ON_CONNECT.map { Requests.get(it).toList() }
        val gaia = Requests.gaiaFeatures().toList()
        for (model in Models.ALL) {
            val name = model.names.first()
            val (expected, sent) = silent({ SoundpeatsTalk(name) }, 5_000)
            assertEquals(if (Models.find(null, name)?.isQualcomm == true) gets + listOf(gaia) else gets, sent, name)
            assertEquals(expected, sent, name)
        }
    }

    @Test
    fun soundpeatsReadsTheBatteryAgainAndKeepsTheStateAcrossAReopenedChannel() = runTest {
        val name = Models.ALL.first().names.first()
        val (_, sent) = silent({ SoundpeatsTalk(name) }, Pacing.POLL_MS + 100)
        assertEquals(Requests.BATTERY.map { Requests.get(it).toList() }, sent.takeLast(Requests.BATTERY.size))
        // The next talk starts from the state and the replies it is given, not from nothing.
        val state = EarbudState()
        val replies = mapOf(Command.GAME_MODE to byteArrayOf(1))
        val second = SoundpeatsTalk(name, state, replies)
        assertTrue(second.state === state)
        assertEquals(replies, second.replies)
    }

    @Test
    fun aSoundpeatsSetIsReadBackOnce() = runTest {
        val talk = SoundpeatsTalk("x")
        val out = talk.write(Command.ANC_MODE, 1)
        assertEquals(listOf(Requests.set(Command.ANC_MODE, 1).toList()), out.map { it.toList() })
        val reads = talk.takeReadBacks()
        assertEquals(listOfNotNull(Requests.readBack(Command.ANC_MODE)?.let { Requests.get(it).toList() }), reads.map { it.toList() })
        assertEquals(emptyList<ByteArray>(), talk.takeReadBacks())
    }

    @Test
    fun cambridgeWaitsItsAppsThirtySecondsForEachFrame() = runTest {
        for (model in CambridgeModels.ALL) {
            val (expected, sent) = silent({ CambridgeTalk(model) }, 29_000)
            assertEquals(expected.take(1), sent, model.name)
        }
    }

    @Test
    fun sonySendsItsFirstMessageElevenTimesThenGivesUp() = runTest {
        for (set in 1..2) {
            val (expected, sent) = silent({ SonyTalk(set, null) }, 60_000)
            assertEquals(List(11) { expected.first() }, sent, "set $set")
        }
    }

    @Test
    fun galaxyBudsAreNotAskedAnything() = runTest {
        for (model in SamsungModels.ALL) {
            val (expected, sent) = silent({ SamsungTalk(SamsungSession(model)) }, 120_000)
            assertEquals(emptyList<List<Byte>>(), expected)
            assertEquals(emptyList<List<Byte>>(), sent, model.name)
        }
    }

    @Test
    fun airPodsOpeningGoesOnceMoreFiveSecondsLater() = runTest {
        val (expected, sent) = silent({ AppleTalk() }, 30_000)
        assertEquals(expected + expected, sent)
    }

    @Test
    fun soundcoreAsksForItsStateAgainEveryPollTime() = runTest {
        val (expected, sent) = silent({ SoundcoreTalk("A3936") }, Pacing.POLL_MS * 2 + 100)
        assertEquals(1, expected.size)
        assertEquals(List(3) { expected.single() }, sent)
    }
}
