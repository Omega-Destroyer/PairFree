package io.github.omegadestroyer.pairfree.capture

import io.github.omegadestroyer.pairfree.protocol.CurveMatch
import io.github.omegadestroyer.pairfree.protocol.EqCapture
import io.github.omegadestroyer.pairfree.protocol.FirmwareVersion
import io.github.omegadestroyer.pairfree.protocol.LoggedReads
import io.github.omegadestroyer.pairfree.protocol.VendorRecording

/**
 * The recording screen: what an official app sends and reads, so a model the maintainer does
 * not own can get its controls and equalizer (docs/DESIGN.md, Record from the official app).
 */
sealed interface Capture {
    data object Closed : Capture

    /** The steps, and a button to pick a bug report. */
    data object Guide : Capture

    data object Reading : Capture

    /**
     * What the file held, from [since] on (snoop log microseconds; null for the whole log).
     * From SOUNDPEATS earbuds: the EQ [writes] with what each [matches], the settings [reads],
     * and the user's [names] for the writes, null to leave one out. From other brands: what
     * their app and earbuds sent each other ([vendor]), with the model of each as PairFree
     * matched it to a paired device ([vendorNames]).
     */
    data class Done(
        val result: CaptureResult,
        val since: Long?,
        val wholeLog: Boolean,
        val writes: List<EqCapture.Write>,
        val matches: List<CurveMatch?>,
        val reads: List<LoggedReads.Earbuds>,
        val names: List<String?>,
        val vendor: List<VendorRecording.Earbuds> = emptyList(),
        val vendorNames: List<String> = emptyList(),
    ) : Capture {
        /** The writes the user named, in order, with their names. */
        val named: List<Pair<String, EqCapture.Write>> get() = writes.indices.mapNotNull { i -> names.getOrNull(i)?.let { it to writes[i] } }

        /** The earbuds the report is about: those of the named curves, else the first in the log. */
        val firmware: FirmwareVersion?
            get() = named.firstNotNullOfOrNull { it.second.firmware } ?: writes.firstNotNullOfOrNull { it.firmware }
                ?: reads.firstNotNullOfOrNull { it.firmware }

        /** Curves no known preset or slider setting explains, and not named yet. */
        val unnamedNew: Int get() = writes.indices.count { matches.getOrNull(it) == null && names.getOrNull(it) == null }

        /** True when the recording has SOUNDPEATS earbuds in it. */
        val hasSoundpeats: Boolean get() = writes.isNotEmpty() || reads.isNotEmpty()

        /** Something worth sending: a named curve, settings read from the earbuds, or another brand's messages. */
        val ready: Boolean get() = named.isNotEmpty() || reads.isNotEmpty() || vendor.isNotEmpty()
    }
}
