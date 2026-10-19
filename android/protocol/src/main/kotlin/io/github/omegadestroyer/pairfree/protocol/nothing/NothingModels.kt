package io.github.omegadestroyer.pairfree.protocol.nothing

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * One Nothing or CMF model from data/nothing/devices.json: the product id it reports (read `C01C`), its
 * name and Nothing X's codename, the Bluetooth names it uses, whether its frames carry a CRC (all but the
 * Ear (1)), whether Nothing X reads the protocol version first ([activate]), and its features per firmware
 * range ([configs]).
 */
public data class NothingModel(
    val productId: String,
    val name: String,
    val codename: String,
    val names: List<String>,
    val form: Form,
    val crc: Boolean,
    val activate: Boolean,
    val headTracking: Boolean,
    val configs: List<NothingConfig>,
) {
    /** The features for [firmware] ("1.0.1.74"), or the first range's before the firmware is known. */
    public fun config(firmware: String?): NothingConfig =
        firmware?.let { fw -> configs.firstOrNull { it.covers(fw) } } ?: configs.first()
}

/**
 * What a model has on one firmware range, from the table Nothing X bundles (docs/nothing/PROTOCOL.md
 * section 6). [anc] lists the noise modes (high, mid, low, adaptive, transparency, off); [codecs] the
 * high-quality codecs (lhdc, ldac) beside the standard ones; [simpleEq] the custom EQ's three filters, where
 * the model has one.
 */
public data class NothingConfig(
    val minFirmware: String?,
    val maxFirmware: String?,
    val anc: List<String>,
    val eqPresets: Boolean,
    val bassBoost: Boolean,
    val spatialAudio: Boolean,
    val codecs: List<String>,
    val earDetection: Boolean,
    val lowLatency: Boolean,
    val gestures: List<NothingGesture>,
    val simpleEq: NothingSimpleEq? = null,
) {
    /**
     * True when [firmware] is in this range. Nothing X's ranges name the last two fields ("*.*.1.74"),
     * so only those are compared.
     */
    public fun covers(firmware: String): Boolean {
        val fw = tail(firmware) ?: return minFirmware == null && maxFirmware == null
        val min = minFirmware?.let(::tail)
        val max = maxFirmware?.let(::tail)
        return (min == null || compare(fw, min) >= 0) && (max == null || compare(fw, max) <= 0)
    }

    private fun tail(version: String): List<Int>? =
        version.trim().split('.').takeLast(2).map { it.toIntOrNull() ?: return null }.takeIf { it.size == 2 }

    private fun compare(a: List<Int>, b: List<Int>): Int = if (a[0] != b[0]) a[0].compareTo(b[0]) else a[1].compareTo(b[1])
}

/**
 * The custom EQ Nothing X calls simple (docs/nothing/PROTOCOL.md section 5): a low shelf, a peak and a high
 * shelf at these frequencies (Hz) and Qs, from the feature table's customEQ.
 */
public data class NothingSimpleEq(
    val lowHz: Double,
    val lowQ: Double,
    val peakHz: Double,
    val peakQ: Double,
    val highHz: Double,
    val highQ: Double,
)

/**
 * One gesture Nothing X lets the user set: on [device] (2 an earbud, which the app writes to the left, 2,
 * and the right, 3; 4 the case's dial; 6 the whole product), [button], [gesture] (1 single, 2 double,
 * 3 triple, 7 hold, 9 double and hold, 10 turn), and the [operations] it offers. [fixed] are actions it
 * shows but does not let the user change, such as answering a call; [custom] is false where nothing can
 * be picked.
 */
public data class NothingGesture(
    val device: Int,
    val button: Int,
    val gesture: Int,
    val custom: Boolean,
    val operations: List<Int>,
    val fixed: List<Int>,
)

/** Every Nothing and CMF model PairFree knows, and how a paired device is recognised as one. */
public object NothingModels {
    public val ALL: List<NothingModel> get() = NOTHING_MODELS

    /** The model that reports [productId] ("B184"), or null. */
    public fun of(productId: String?): NothingModel? = NOTHING_MODELS.firstOrNull { productId != null && it.productId.equals(productId, ignoreCase = true) }

    /** The model a Bluetooth [name] belongs to, or null. Several models share a name; the product id decides once read. */
    public fun fromName(name: String): NothingModel? {
        val n = name.trim()
        return NOTHING_MODELS.firstOrNull { m -> m.names.any { it.equals(n, ignoreCase = true) } }
    }

    /**
     * True for a paired device that is Nothing's or CMF's: it lists Nothing's control service
     * ([NothingWire.SERVICE_UUID]), else its name is one of the models' or starts with "Nothing " or "CMF ".
     */
    public fun identify(name: String, uuids: List<String>): Boolean {
        if (uuids.any { it.equals(NothingWire.SERVICE_UUID, ignoreCase = true) }) return true
        val n = name.trim()
        return fromName(n) != null || PREFIXES.any { n.startsWith(it, ignoreCase = true) && !n.contains(WATCH, ignoreCase = true) }
    }

    /**
     * The screens' [Model] for a product id or model name as PairFree shows it, with [form] for one PairFree
     * does not know. Every Nothing and CMF model has Google Fast Pair.
     */
    public fun model(codeOrName: String?, form: Form? = null): Model? {
        val n = codeOrName?.takeIf { it.isNotBlank() } ?: return null
        val known = of(n) ?: NOTHING_MODELS.firstOrNull { it.name == n } ?: fromName(n)
        return Model(known?.productId ?: n, listOfNotNull(known?.name ?: n), FAMILY, fitKeepsAnc = true, form = known?.form ?: form ?: Form.EARBUDS, fastPair = true)
    }

    private val PREFIXES = listOf("Nothing ", "CMF ")
    private const val WATCH = "Watch"
    private const val FAMILY = "NOTHING"
}
