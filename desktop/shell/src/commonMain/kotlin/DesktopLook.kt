package io.github.omegadestroyer.pairfree.shell

/**
 * A colour of a look: a fixed one, or a tone of the system's own colours (data/looks names them as Android's wallpaper colours,
 * `system_accent1_200`), which a computer fills from its accent colour and its dark or light mode ([SystemTones]).
 */
sealed interface Tone {
    /** An opaque colour as 0xAARRGGBB. */
    data class Fixed(val argb: Int) : Tone

    data class System(val name: String) : Tone
}

/** The colour roles of a palette, as docs/LOOKS.md names them; the last five are optional in a look file and filled from another role. */
data class DesktopPalette(
    val background: Tone, val panel: Tone, val raised: Tone, val hairline: Tone, val text: Tone,
    val secondaryText: Tone, val label: Tone, val accent: Tone, val onAccent: Tone, val destructive: Tone,
    val outline: Tone, val shadow: Tone, val highlight: Tone, val artFill: Tone, val artAccent: Tone,
)

/** One look: its palettes and the shape and type tokens the toolkit draws by (docs/LOOKS.md). Sizes are in device-independent pixels. */
data class DesktopLook(
    val id: String,
    /** The key of the look's name in the strings table. */
    val nameKey: String,
    val dark: DesktopPalette?,
    val light: DesktopPalette?,
    val textFont: String,
    val headingFont: String,
    val numberFont: String,
    val labelCaps: Boolean,
    val hairline: Float,
    val corner: Float,
    val smallCorner: Float,
    val panelBorder: Boolean,
    val uneven: Float,
    val shadow: Float,
    val selectedFill: Boolean,
    val artStyle: String,
    val artStroke: Float,
    val artOffset: Float,
) {
    /** True when the look is drawn dark while the system is in dark mode ([night]) or not: a look with both palettes follows the system. */
    fun isDark(night: Boolean): Boolean = if (dark != null && light != null) night else dark != null

    fun palette(night: Boolean): DesktopPalette = (if (isDark(night)) dark else light)!!

    val usesSystemTones: Boolean get() = listOfNotNull(dark, light).any { p -> p.tones().any { it is Tone.System } }

    companion object {
        /** The look with this id, or the first one (Studio) for an id no look has, as a settings file edited by hand may hold. */
        fun byId(id: String?): DesktopLook = BUILT_IN_LOOKS.firstOrNull { it.id == id } ?: BUILT_IN_LOOKS.first()
    }
}

private fun DesktopPalette.tones() = listOf(
    background, panel, raised, hairline, text, secondaryText, label, accent, onAccent, destructive, outline, shadow, highlight, artFill, artAccent,
)

/**
 * The system's colours as a computer gives them: its accent colour and whether it is in dark mode. The tones a look asks for
 * (`system_accent1_200`: the accent's tonal scale at 200 of 1000, `neutral` the grey scale tinted by it) are worked out from it.
 */
class SystemTones(private val accentArgb: Int) {
    /** [name] as an opaque colour. Tone 0 is white and 1000 black, as in Android's scales; accent2 and 3 are the accent less saturated. */
    fun resolve(name: String): Int {
        val m = Regex("system_(accent|neutral)([123])_(\\d+)").matchEntire(name) ?: return accentArgb
        val kind = m.groupValues[1]
        val n = m.groupValues[2].toInt()
        val tone = m.groupValues[3].toInt().coerceIn(0, 1000)
        val (h, s, _) = hsl(accentArgb)
        val saturation = when {
            kind == "neutral" -> if (n == 1) 0.06f else 0.12f
            n == 1 -> s.coerceAtLeast(0.45f)
            n == 2 -> s * 0.4f
            else -> s * 0.6f
        }
        val lightness = 1f - tone / 1000f
        return fromHsl(h, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }

    fun resolve(tone: Tone): Int = when (tone) {
        is Tone.Fixed -> tone.argb
        is Tone.System -> resolve(tone.name)
    }

    private fun hsl(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l)
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return Triple(h, s, l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
