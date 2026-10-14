package io.github.omegadestroyer.soundpeter.protocol

/**
 * The EQ curves an app sent to the earbuds, found in a Bluetooth log ([SnoopScanner]).
 * The earbuds cannot report their EQ (docs/soundpeats/PROTOCOL.md section 4), so a capture of the
 * official app picking each preset is how a model's base curve and presets are learnt.
 */
public object EqCapture {
    /** What PeatsAudio calls a model's base curve, the stock tuning. */
    public const val CLASSIC: String = "SOUNDPEATS Classic"

    /** One decoded band of a write. */
    public data class Band(val hz: Double, val gainDb: Double, val q: Double)

    /**
     * One complete curve: a write of every band, one frame per band, sent together.
     * [firmware] is the last firmware version the same connection reported before it.
     */
    public class Write(
        public val timeMicros: Long,
        public val command: Int,
        public val payloads: List<ByteArray>,
        public val firmware: FirmwareVersion?,
    ) {
        public val masterGainDb: Double get() = values(payloads.first()).first
        public val bands: List<Band> get() = payloads.map { values(it).second }

        /** True when [other] sends the same bytes. */
        public fun sameAs(other: Write): Boolean =
            command == other.command && payloads.size == other.payloads.size &&
                payloads.zip(other.payloads).all { (a, b) -> a.contentEquals(b) }

        private fun values(p: ByteArray): Pair<Double, Band> {
            val at = if (command == Wire.EQ_COMMAND_OTHER) 2 else 1
            val master = p.u16(at).toShort() / 60.0
            return master to Band(p.u16(at + 2) / 3.0, p.u16(at + 4).toShort() / 60.0, p.u16(at + 6) / 4096.0)
        }
    }

    /**
     * The complete EQ writes in [frames], oldest first, from [since] on when it is given (snoop
     * log microseconds). Writes cut short in the log are left out. Earlier frames still name
     * the firmware a write came with.
     */
    public fun writes(frames: List<SnoopScanner.Found>, since: Long? = null): List<Write> {
        val firmware = HashMap<Int, FirmwareVersion>()
        val open = HashMap<Int, Building>()
        val out = mutableListOf<Write>()
        fun close(connection: Int) {
            open.remove(connection)?.complete()?.let(out::add)
        }
        for (f in frames.sortedBy { it.timeMicros }) {
            val frame = f.frame
            if (f.fromEarbuds) {
                if (frame.vendor == Wire.VENDOR_MAIN && frame.command == (Command.FIRMWARE_VERSION.get!! or Wire.REPLY_MASK)) {
                    (Replies.decode(Command.FIRMWARE_VERSION, frame.payload) as? Value.Text)?.let { firmware[f.connection] = FirmwareVersion(it.text) }
                }
                continue
            }
            if (frame.vendor != Wire.VENDOR_EQ || (frame.command != Wire.EQ_COMMAND_QUALCOMM && frame.command != Wire.EQ_COMMAND_OTHER)) continue
            val band = Building.position(frame) ?: continue
            val current = open[f.connection]
            val continues = current != null && current.command == frame.command && current.count == band.first &&
                band.second == current.payloads.size + 1 && f.timeMicros - current.last <= GAP_US
            if (!continues) {
                close(f.connection)
                if (band.second != 1) continue
                open[f.connection] = Building(f.timeMicros, frame.command, band.first, firmware[f.connection])
            }
            open.getValue(f.connection).add(frame.payload, f.timeMicros)
            if (open.getValue(f.connection).payloads.size == band.first) close(f.connection)
        }
        open.keys.toList().forEach(::close)
        return out.filter { since == null || it.timeMicros >= since }.sortedBy { it.timeMicros }
    }

    private class Building(val start: Long, val command: Int, val count: Int, val firmware: FirmwareVersion?) {
        val payloads = mutableListOf<ByteArray>()
        var last = start

        fun add(payload: ByteArray, time: Long) {
            payloads += payload
            last = time
        }

        fun complete(): Write? = if (payloads.size == count) Write(start, command, payloads.toList(), firmware) else null

        companion object {
            /** (band count, band index) of an EQ frame, or null if it is not a band write. */
            fun position(frame: Frame): Pair<Int, Int>? {
                val p = frame.payload
                val position = if (frame.command == Wire.EQ_COMMAND_QUALCOMM) {
                    if (p.size != 9) return null else ((p[0].toInt() and 0xF0) shr 4) to (p[0].toInt() and 0x0F)
                } else {
                    if (p.size != 10) return null else (p[0].toInt() and 0xFF) to (p[1].toInt() and 0xFF)
                }
                return position.takeIf { (count, index) -> count > 0 && index in 1..count }
            }
        }
    }

    /** Bands of one write arrive about 50 ms apart; a longer pause starts a new write. */
    private const val GAP_US = 1_000_000L

    /**
     * The equalizer part of a report: what the maintainer needs to add the model's EQ, as
     * text a person can paste. Each named write lists its exact payloads, one band per line,
     * under its decoded bands; [compact] leaves out the decoded rows and puts a write's
     * payloads on one line, so a report fits in a link. tools/import_eq_capture.py reads both.
     */
    public fun reportLines(named: List<Pair<String, Write>>, compact: Boolean = false): List<String> {
        val out = mutableListOf<String>()
        val firmware = named.mapNotNull { it.second.firmware?.text }.distinct()
        out += "Firmware:  ${firmware.joinToString(", ").ifEmpty { "not in the log" }}"
        named.firstOrNull()?.second?.let { w ->
            out += "EQ:        command ${w.command.hex4()}, ${w.payloads.size} bands, master gain ${db(w.masterGainDb)} dB"
        }
        named.forEachIndexed { i, (name, w) ->
            out += ""
            out += "${i + 1}. $name"
            if (compact) {
                out += w.payloads.joinToString(" ") { it.hex() }
                return@forEachIndexed
            }
            out += "   Hz   " + w.bands.joinToString(" ") { trim(it.hz) }
            out += "   dB   " + w.bands.joinToString(" ") { db(it.gainDb) }
            out += "   Q    " + w.bands.joinToString(" ") { trim(it.q) }
            for (p in w.payloads) out += "   ${p.hex()}"
        }
        return out
    }

    private fun db(v: Double): String = (if (v >= 0) "+" else "") + trim(v)

    private fun trim(v: Double): String {
        val r = kotlin.math.round(v * 100) / 100
        return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()
    }
}
