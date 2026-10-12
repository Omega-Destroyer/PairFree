package io.github.omegadestroyer.soundpeter.protocol

/**
 * The settings an app read from earbuds, found in a Bluetooth log ([SnoopScanner]). PeatsAudio
 * reads only what SoundPEATS' server says a model has, so its reads show what the official app
 * offers on that model, and the replies show the earbuds' answers: the same lines as a device
 * report ([DeviceReport]), from a recording instead of a connection.
 */
public object LoggedReads {
    /** One pair of earbuds in the log, told apart by the firmware version they reported. */
    public class Earbuds(
        public val firmware: FirmwareVersion?,
        /** Every command read or reported, in the order of [Requests.ON_CONNECT] where it is there; null when asked and never answered. */
        public val replies: Map<Command, ByteArray?>,
        /** The GAIA v3 features, if the log has the reply to [Requests.gaiaFeatures]. */
        public val gaiaFeatures: Set<Int>?,
    ) {
        /** The report lines, as a device report prints them; [compact] as in [DeviceReport.line]. */
        public fun lines(compact: Boolean = false): List<String> =
            replies.map { (command, payload) -> DeviceReport.line(command, payload, compact) } +
                listOfNotNull(gaiaFeatures?.let { DeviceReport.gaiaLine(it, compact) })
    }

    /** Each pair of earbuds that answered in [frames], in the order they first appear. */
    public fun of(frames: List<SnoopScanner.Found>): List<Earbuds> {
        val sorted = frames.sortedBy { it.timeMicros }
        // Which firmware a frame belongs to: the last firmware reply on its connection, or for
        // frames before the first one (the firmware read itself), that first reply.
        val firmwareReplies = sorted.filter { it.fromEarbuds && firmwareOf(it.frame) != null }.groupBy { it.connection }
        fun firmwareFor(f: SnoopScanner.Found): String? {
            val replies = firmwareReplies[f.connection] ?: return null
            val reply = replies.lastOrNull { it.timeMicros <= f.timeMicros } ?: replies.first()
            return firmwareOf(reply.frame)
        }

        val asked = LinkedHashMap<String?, LinkedHashSet<Command>>()
        val answers = LinkedHashMap<String?, LinkedHashMap<Command, ByteArray>>()
        val gaia = HashMap<String?, Set<Int>>()
        for (f in sorted) {
            val key = firmwareFor(f)
            val frame = f.frame
            if (!f.fromEarbuds) {
                if (frame.vendor == Wire.VENDOR_MAIN && frame.payload.isEmpty() && !frame.isReply) {
                    BY_GET[frame.command]?.let { asked.getOrPut(key, ::LinkedHashSet) += it }
                }
                continue
            }
            Replies.commandFor(frame)?.let { answers.getOrPut(key, ::LinkedHashMap)[it] = frame.payload }
            gaiaFeaturesOf(frame)?.let { gaia[key] = it }
        }

        val keys = (answers.keys + gaia.keys).distinct()
        return keys.map { key ->
            val got = answers[key].orEmpty()
            val commands = (asked[key].orEmpty() + got.keys).distinct()
                .sortedBy { c -> Requests.ON_CONNECT.indexOf(c).let { if (it < 0) Int.MAX_VALUE else it } }
            Earbuds(key?.let(::FirmwareVersion), commands.associateWith { got[it] }, gaia[key])
        }
    }

    private fun firmwareOf(frame: Frame): String? =
        if (Replies.commandFor(frame) == Command.FIRMWARE_VERSION) (Replies.decode(Command.FIRMWARE_VERSION, frame.payload) as? Value.Text)?.text else null

    private val BY_GET: Map<Int, Command> = Command.entries.filter { it.get != null }.associateBy { it.get!! }
}
