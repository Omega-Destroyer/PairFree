package io.github.omegadestroyer.soundpeter.protocol

/**
 * The body of a Record from PeatsAudio report (docs/DESIGN.md): what PeatsAudio read from
 * each pair of earbuds in the log, SoundPeter's own reads, and the EQ curves the user named.
 * The settings lines are device report lines, which tools/gen_models_table.py reads; the
 * equalizer part is what tools/import_eq_capture.py reads.
 */
public object RecordingReport {
    /** SoundPeter's own reads from the earbuds it is connected to. */
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
            out += "---- What SoundPeter reads on connect from ${earbudsName(own.firmware)} ----"
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

    /** The model's name from its firmware code, and the firmware version. */
    public fun earbudsName(firmware: FirmwareVersion?): String {
        firmware ?: return "earbuds with no firmware reply in the log"
        val name = Models.find(firmware.code, null)?.names?.firstOrNull() ?: "a model not in SoundPeter's table yet"
        return "$name, firmware ${firmware.text}"
    }
}
