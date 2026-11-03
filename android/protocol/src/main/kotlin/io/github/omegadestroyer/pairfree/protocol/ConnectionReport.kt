package io.github.omegadestroyer.pairfree.protocol

/**
 * The report a user can copy when PairFree could not open the controls of their earbuds, for any brand
 * (GitHub issue #3: AirPods that failed with nothing to show why). Release builds log nothing, so this
 * is the only record of what PairFree tried, in order, and how each try ended. It goes into a public
 * issue: the name goes through [ReportPrivacy.name], every other line through [ReportPrivacy.addresses],
 * and it holds no serial number.
 */
public object ConnectionReport {
    /**
     * @property facts the report's header lines, label to value, in order: the device as PairFree found it
     *   and the phone.
     * @property steps what PairFree tried, each with the milliseconds since the attempt began.
     */
    public class Attempt(
        public val name: String,
        public val facts: List<Pair<String, String>>,
        public val steps: List<String>,
    )

    public fun text(attempt: Attempt, issues: String): String = buildString {
        appendLine("PairFree connection report")
        appendLine("Paste this whole report into a new issue at")
        appendLine("$issues to help find out why")
        appendLine("PairFree could not reach these earbuds. It holds no serial number or full address.")
        appendLine()
        val rows = listOf("Bluetooth name" to ReportPrivacy.name(attempt.name)) + attempt.facts
        // As a device report lines them up: the colon, then at least two spaces.
        val width = rows.maxOf { it.first.length } + 3
        for ((label, value) in rows) appendLine("${"$label:".padEnd(width)}${ReportPrivacy.addresses(value)}")
        appendLine()
        appendLine("What PairFree tried, in order (milliseconds since it began):")
        if (attempt.steps.isEmpty()) appendLine("  nothing yet")
        attempt.steps.takeLast(MAX_STEPS).forEach { appendLine("  ${ReportPrivacy.addresses(it)}") }
    }

    /** How an exception reads in a report: its class and message, then each cause's, which hold the stack's own reason. */
    public fun describe(e: Throwable): String {
        val parts = generateSequence(e) { it.cause.takeIf { c -> c !== it } }.take(4)
            .map { t -> listOfNotNull(t::class.simpleName ?: "Exception", t.message?.takeIf { it.isNotBlank() }).joinToString(": ") }
        return parts.joinToString(", caused by ")
    }

    /** A connection that keeps retrying could make the list long; the last steps say most. */
    public const val MAX_STEPS: Int = 40
}
