package io.github.omegadestroyer.pairfree.protocol

/**
 * The body of a Record from the official app report (docs/DESIGN.md): what PeatsAudio read
 * from each pair of SOUNDPEATS earbuds in the log, PairFree's own reads, the EQ curves the user
 * named, and what another brand's app and its earbuds sent each other ([VendorRecording]).
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
        vendor: List<Pair<String, VendorRecording.Earbuds>> = emptyList(),
    ): List<String> {
        val out = mutableListOf<String>()
        for ((name, earbuds) in vendor) {
            out += ""
            out += "---- What the official app and $name sent each other (from the Bluetooth log) ----"
            out += VendorRecording.lines(earbuds, compact)
        }
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
     * user opened Record from the official app) if anything follows it. Else just before an
     * official app last connected: PeatsAudio's last firmware read, or when other earbuds last
     * connected ([VendorRecording.start]), whichever is later. Else null for the whole log.
     * A phone keeps one Bluetooth log for hours, through Bluetooth being turned off and on.
     */
    public fun start(frames: List<SnoopScanner.Found>, openedAtMicros: Long?, packets: List<SnoopScanner.Packet> = emptyList()): Long? {
        if (openedAtMicros != null && (frames.any { it.timeMicros >= openedAtMicros } || packets.any { it.timeMicros >= openedAtMicros })) {
            return openedAtMicros
        }
        val firmwareRead = Command.FIRMWARE_VERSION.get
        val lastConnect = frames
            .filter { !it.fromEarbuds && it.frame.vendor == Wire.VENDOR_MAIN && it.frame.command == firmwareRead && it.frame.payload.isEmpty() }
            .maxOfOrNull { it.timeMicros }
        // PeatsAudio reads the firmware two or three times in a row as it connects.
        return listOfNotNull(lastConnect?.minus(CONNECT_BURST_US), VendorRecording.start(packets)).maxOrNull()
    }

    private const val CONNECT_BURST_US = 3_000_000L

    /** The model's name from its firmware code, and the firmware version. */
    public fun earbudsName(firmware: FirmwareVersion?): String {
        firmware ?: return "earbuds with no firmware reply in the log"
        val name = Models.find(firmware.code, null)?.names?.firstOrNull() ?: "a model not in PairFree's table yet"
        return "$name, firmware ${firmware.text}"
    }
}
