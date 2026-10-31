package io.github.omegadestroyer.pairfree.protocol.pixel

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model

/**
 * Which settings message a model takes (data/pixel/commands.json, settings): Allegro on the Pixel Buds Pro, Pro 2
 * and 2a, Presto on the Pixel Buds (2020), Stretto on the A-Series; COMMON holds the setup mode all share.
 */
public enum class PixelFamily { COMMON, PRESTO, STRETTO, ALLEGRO }

/** How the phone reaches the model: Maestro (Pigweed RPC in HDLC frames) or plain group/code frames (docs/pixel/PROTOCOL.md). */
public enum class PixelTransport { MAESTRO, TLV }

/** A settings field's wire type: varints (bool, enum, int, sint32 zigzagged) or a message. */
public enum class PixelFieldType { BOOL, ENUM, INT, SINT32, MESSAGE }

/**
 * One field of a family's settings message: its [field] number, [type] and Google's [name]; whether PairFree [read]s
 * it on connecting; [neverSend] for firmware update, setup and diagnostics; the row [key] it shows as, or the shared
 * [Command][io.github.omegadestroyer.pairfree.protocol.Command] name ([shared]) the screens know it by.
 */
public data class PixelField(
    val field: Int,
    val type: PixelFieldType,
    val name: String,
    val read: Boolean,
    val neverSend: Boolean,
    val key: String?,
    val shared: String?,
)

/** One of Google's EQ presets: the five bands' gains in dB, low bass to upper treble. */
public data class PixelEqPreset(val name: String, val gains: List<Float>)

/**
 * One Pixel Buds model from data/pixel/devices.json: the [deviceType] it reports, its name and Google's code name,
 * its settings [family] and [transport], whether the app offers [adaptive] noise control, the Bluetooth [names] it
 * uses, and the [features] (Google's feature id, minimum firmware) the app bundles for it.
 */
public data class PixelModel(
    val deviceType: Int,
    val name: String,
    val codename: String,
    val family: PixelFamily,
    val transport: PixelTransport,
    val adaptive: Boolean,
    val names: List<String>,
    val features: List<Pair<Int, Long?>>,
)

/** Every Pixel Buds model PairFree knows, and how a paired device is recognised as one. */
public object PixelModels {
    public val ALL: List<PixelModel> get() = PIXEL_MODELS

    /** The model that reports [deviceType], or null. */
    public fun of(deviceType: Int?): PixelModel? = PIXEL_MODELS.firstOrNull { it.deviceType == deviceType }

    /** The model a Bluetooth [name] belongs to, or null. */
    public fun fromName(name: String): PixelModel? {
        val n = name.trim()
        return PIXEL_MODELS.firstOrNull { m -> m.names.any { it.equals(n, ignoreCase = true) } }
    }

    /** The settings fields of [family]. */
    public fun fields(family: PixelFamily): List<PixelField> = PIXEL_SETTINGS[family].orEmpty()

    /** True when [uuids] hold either Pixel Buds control service, in either byte order. */
    public fun hasService(uuids: List<String>): Boolean = transportOf(uuids) != null

    /** The transport [uuids] offer: Maestro where its service is listed, else the plain frames' where theirs is. */
    public fun transportOf(uuids: List<String>): PixelTransport? {
        val list = uuids.map { it.lowercase() }
        return when {
            PixelWire.MAESTRO_UUID in list || PixelWire.MAESTRO_UUID_REVERSED in list -> PixelTransport.MAESTRO
            PixelWire.TLV_UUID in list || PixelWire.TLV_UUID_REVERSED in list -> PixelTransport.TLV
            else -> null
        }
    }

    /** The service UUID to open for [transport]. */
    public fun serviceFor(transport: PixelTransport): String = when (transport) {
        PixelTransport.MAESTRO -> PixelWire.MAESTRO_UUID
        PixelTransport.TLV -> PixelWire.TLV_UUID
    }

    /**
     * The screens' [Model] for a device type or model name as PairFree shows it. Every Pixel Buds model has Google
     * Fast Pair.
     */
    public fun model(codeOrName: String?): Model? {
        val n = codeOrName?.takeIf { it.isNotBlank() } ?: return null
        val known = n.toIntOrNull()?.let(::of) ?: PIXEL_MODELS.firstOrNull { it.name == n } ?: fromName(n)
        return Model(known?.deviceType?.toString() ?: n, listOfNotNull(known?.name ?: n), FAMILY, fitKeepsAnc = true, form = Form.EARBUDS, fastPair = true)
    }

    private const val FAMILY = "PIXEL"
}
