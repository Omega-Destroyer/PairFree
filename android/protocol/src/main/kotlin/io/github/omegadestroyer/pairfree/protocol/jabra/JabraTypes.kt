package io.github.omegadestroyer.pairfree.protocol.jabra

/** How the vendor's form draws a setting (docs/jabra/PROTOCOL.md, "Other GNP controls"). */
public enum class JabraWidget { LIST, BOOL, STATIC, TEXT }

/** One option of a setting: the vendor's identifier for it and its code on the wire (one or two bytes, high first). */
public class JabraOption(public val name: String, public val code: Int)

/**
 * One setting as the Sound+ forms list it for a model and firmware (data/jabra/settings.json): the GNP command that carries it,
 * the selector bytes its read sends, and where the value sits in the payload. [pos] counts from 1 after the subcommand byte.
 * A [shared] payload holds more than one setting's value (sidetone: the on/off and the level), so a write copies the last
 * read payload and changes only its own bytes.
 */
public class JabraSetting(
    public val id: String,
    public val name: String,
    public val group: String,
    public val destination: Int,
    public val command: String,
    public val widget: JabraWidget,
    public val main: Int?,
    public val sub: Int?,
    public val selectors: List<Pair<Int, Int>>,
    public val pos: Int,
    public val width: Int,
    public val shared: Boolean,
    public val options: List<JabraOption>,
    /** "read" and/or "write" when the earbuds want the configuration mode entered first (DEVICE `0D/11`, left with `0D/12`). */
    public val configMode: Set<String>,
    /** The vendor's app handles it itself (a language list, a tone, the ambience commands). */
    public val special: Boolean,
    /** The SDK's virtual command name for the ambience and loop settings, else null. */
    public val virtual: String?,
    public val depends: List<String>,
) {
    /** The selector bytes of a read, in position order, or none. */
    public val selectorBytes: ByteArray get() = ByteArray(selectors.size) { selectors[it].second.toByte() }

    public fun option(code: Int): JabraOption? = options.firstOrNull { it.code == code }
}

/** A production model's variant by product id (Evolve2 65 has four) with what the app's definition says about it. */
public class JabraVariant(
    public val pid: Int,
    public val definition: String,
    /** "gnp" (the SDK's own protocol) or "gaia" (Qualcomm's, through Jabra's vendor plugins). */
    public val protocol: String,
    public val flags: Set<String>,
    /** How the app draws the battery: dual, dualWithCradle, mono, ... */
    public val battery: String,
    public val wear: String,
    public val snapshots: List<String>,
    /** The ambience loop the app starts with (hearThrough, anc), or empty. */
    public val noiseLoop: List<String>,
    public val myControls: Boolean,
    public val mySound: Boolean,
    public val spotify: Boolean,
    public val assistants: List<String>,
    public val spatial: Set<String>,
    public val leAudioFirmware: String?,
) {
    public fun has(flag: String): Boolean = flag in flags
}

/** A model by its display name: the earbuds, headphones or neckband the app lists, with its variants. */
public class JabraModel(public val name: String, public val kind: String, public val variants: List<JabraVariant>) {
    /** The variant with this product id, else the only one there is. */
    public fun variant(pid: Int?): JabraVariant? = variants.firstOrNull { it.pid == pid } ?: variants.singleOrNull()
}

/** One firmware snapshot the app bundles: the settings its form lists for a product id and firmware. */
public class JabraProfile(
    public val snapshot: String,
    public val pid: Int,
    public val variant: String,
    public val firmware: String,
    public val caps: Set<String>,
    public val settings: List<String>,
)

/** The six equalizer presets of the Sound+ app: slider percentages for the five bands, low to high. */
public class JabraPreset(public val name: String, public val percent: List<Int>)
