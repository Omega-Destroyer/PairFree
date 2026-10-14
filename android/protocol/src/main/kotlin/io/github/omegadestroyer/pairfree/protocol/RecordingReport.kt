package io.github.omegadestroyer.pairfree.protocol

/**
 * The body of a Record from PeatsAudio report (docs/DESIGN.md): what PeatsAudio read from
 * each pair of earbuds in the log, PairFree's own reads, and the EQ curves the user named.
 * The settings lines are device report lines, which tools/gen_models_table.py reads; the
 * equalizer part is what tools/import_eq_capture.py reads.
 */
public object RecordingReport {
    /** PairFree's own reads from the earbuds it is connected to. */
    public class OwnReads(
        public val firmware: FirmwareVersion?,
        public val replies: Map<Command, ByteArray>,
        public val gaiaFeatures: Set<Int>?,
    )

    /**
     * The sections, each after a blank line and a `----` heading. [compact] leaves out
     * padding and decoded values, which the raw bytes carry, so a report fits in a link.
     */
    public fun sections(
        reads: List<LoggedReads.Earbuds>,
        own: OwnReads?,
        named: List<Pair<String, EqCapture.Write>>,
        compact: Boolean = false,
    ): List<String> {
        val out = mutableListOf<String>()
        for (earbuds in reads) {
            out += ""
            out += "---- What PeatsAudio read from ${earbudsName(earbuds.firmware)} (from the Bluetooth log) ----"
            out += earbuds.lines(compact)
        }
        if (own != null) {
            out += ""
            out += "---- What PairFree reads on connect from ${earbudsName(own.firmware)} ----"
            out += DeviceReport.lines(own.replies, compact = compact)
            own.gaiaFeatures?.let { out += DeviceReport.gaiaLine(it, compact) }
        }
        if (named.isNotEmpty()) {
            out += ""
            out += "---- Equalizer: the curves PeatsAudio sent ----"
            out += EqCapture.reportLines(named, compact)
        }
        return out
    }

    /**
     * Where a recording starts in a log, in snoop log microseconds: [openedAtMicros] (when the
     * user opened Record from PeatsAudio) if anything follows it, else just before the
     * official app last connected (its last firmware read), else null for the whole log.
     * A phone keeps one Bluetooth log for hours, through Bluetooth being turned off and on.
     */
    public fun start(frames: List<SnoopScanner.Found>, openedAtMicros: Long?): Long? {
        if (openedAtMicros != null && frames.any { it.timeMicros >= openedAtMicros }) return openedAtMicros
        val firmwareRead = Command.FIRMWARE_VERSION.get
        val lastConnect = frames
            .filter { !it.fromEarbuds && it.frame.vendor == Wire.VENDOR_MAIN && it.frame.command == firmwareRead && it.frame.payload.isEmpty() }
            .maxOfOrNull { it.timeMicros } ?: return null
        // PeatsAudio reads the firmware two or three times in a row as it connects.
        return lastConnect - CONNECT_BURST_US
    }

    private const val CONNECT_BURST_US = 3_000_000L

    /** The model's name from its firmware code, and the firmware version. */
    public fun earbudsName(firmware: FirmwareVersion?): String {
        firmware ?: return "earbuds with no firmware reply in the log"
        val name = Models.find(firmware.code, null)?.names?.firstOrNull() ?: "a model not in PairFree's table yet"
        return "$name, firmware ${firmware.text}"
    }
}
