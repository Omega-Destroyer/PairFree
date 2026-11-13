package io.github.omegadestroyer.pairfree.protocol

/**
 * SOUNDPEATS earbuds (docs/soundpeats/PROTOCOL.md): on connect the read-only GETs in [Requests.ON_CONNECT], then on
 * Qualcomm models the GAIA feature list, then the battery now and then. Every reply, asked for or not, updates the
 * state; a GET the firmware does not have gets no reply, so nothing is waited for. The official app's 50 ms between
 * writes is the serial channel's own.
 *
 * The earbuds restart their link after some changes (LDAC, two devices), and the screens keep the last state meanwhile:
 * so each talk starts from [initial], what the one before it held, and from its [replies].
 */
public class SoundpeatsTalk(
    private val name: String,
    initial: EarbudState = EarbudState(),
    replies: Map<Command, ByteArray> = emptyMap(),
) : SessionTalk<ByteArray>() {
    private val parser = FrameParser()
    private val gaiaFeatures = Requests.gaiaFeatures()
    private var readBack: ByteArray? = null

    override var state: EarbudState = initial
        private set

    /** SOUNDPEATS' rows follow from the state ([SoundpeatsSettings.groups]), so the session keeps none. */
    override val groups: List<SettingGroup> get() = emptyList()

    /** The last raw reply to each GET, for the device report. */
    public var replies: Map<Command, ByteArray> = replies
        private set

    override val pacing: Pacing = Pacing()

    /** Every read the app knows, then the GAIA feature list, which [allowed] lets through to Qualcomm models alone. */
    override fun start(): List<ByteArray> = Requests.ON_CONNECT.map { Requests.get(it) } + listOf(gaiaFeatures)

    override fun poll(): List<ByteArray> = Requests.BATTERY.map { Requests.get(it) }

    /**
     * Qualcomm models also have GAIA v3 features, such as the fit test. The question is put when the read's turn comes:
     * by then the firmware reply has named the model. Never send GAIA frames to other families.
     */
    override fun allowed(message: ByteArray): Boolean =
        message !== gaiaFeatures || Models.find(state.firmware?.code, name)?.isQualcomm == true

    override fun bytes(message: ByteArray): ByteArray = message

    override fun feed(bytes: ByteArray): Fed {
        val frames = parser.feed(bytes)
        for (frame in frames) guarded {
            log?.invoke("recv ${frame.summary()}")
            Replies.commandFor(frame)?.let { replies = replies + (it to frame.payload) }
            state = state.update(frame)
        }
        return Fed(frames.isNotEmpty())
    }

    override fun takeReadBacks(): List<ByteArray> = listOfNotNull(readBack).also { readBack = null }

    /**
     * A SET. The change shows at once ([EarbudState.expect]), and the value is read back a moment later, so the earbuds'
     * answer replaces the guess (docs/DESIGN.md).
     */
    public fun write(command: Command, vararg payload: Int): List<ByteArray> {
        readBack = Requests.readBack(command)?.let { Requests.get(it) }
        state = state.expect(command, *payload)
        return listOf(Requests.set(command, *payload))
    }

    override fun set(command: Command, value: Int): List<ByteArray> = write(command, value)

    /** SOUNDPEATS earbuds' extra settings are commands of their own table, sent as any SET is. */
    override fun change(change: SettingChange): List<ByteArray>? {
        // Restart comes only from its row, after the user confirmed it on screen.
        if (change is SettingChange.Press && change.key == PowerActions.RESTART) return listOf(Requests.rebootConfirmedByUser())
        return SoundpeatsSettings.change(change)?.let { (command, value) -> write(command, value) }
    }

    /** Changes the state from outside a reply: the fit test clears its last result and puts noise control back. */
    public fun update(change: (EarbudState) -> EarbudState) {
        state = change(state)
    }
}
