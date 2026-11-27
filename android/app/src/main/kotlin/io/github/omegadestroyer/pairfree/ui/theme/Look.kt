package io.github.omegadestroyer.pairfree.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.look.LookSpec
import io.github.omegadestroyer.pairfree.look.Looks
import io.github.omegadestroyer.pairfree.look.Palette

// The app's look on the screens (docs/DESIGN.md, Looks; docs/LOOKS.md). A look is a file in data/looks; this turns the
// chosen one into colours, type and shapes. Screens take all three from [Look], never from a literal, so every look
// reaches every screen.

@Immutable
data class LookColors(
    val background: Color,
    val panel: Color,
    val raised: Color,
    val hairline: Color,
    val text: Color,
    val secondaryText: Color,
    val label: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val outline: Color,
    val shadow: Color,
    val highlight: Color,
    val artFill: Color,
    val artAccent: Color,
    /** Whether the look is a dark one as drawn now: the system bars' icons go light on it. */
    val dark: Boolean,
)

/** IBM Plex Sans, one variable font file for every weight. SIL Open Font License, bundled. */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** IBM Plex Mono, Studio's font for every number and for small capitalised labels. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/** Fredoka, Sketchbook's rounded font for titles and headings. SIL Open Font License, bundled. */
val Fredoka = FontFamily(
    Font(R.font.fredoka, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fredoka, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** Nunito, Sketchbook's text font, one variable font file for every weight. SIL Open Font License, bundled. */
val Nunito = FontFamily(
    Font(R.font.nunito, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.nunito, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

/**
 * The fonts a look can name (`type` in its file). A look with a font of its own adds the files to `res/font` (SIL Open
 * Font License or as free) and a line here; tools/test_looks.py checks each look's names against this list.
 */
fun fontFamily(name: String): FontFamily = when (name) {
    "plex_sans" -> PlexSans
    "plex_mono" -> PlexMono
    "fredoka" -> Fredoka
    "nunito" -> Nunito
    "system_mono" -> FontFamily.Monospace
    else -> FontFamily.Default // "system": the phone's own font
}

@Immutable
data class LookType(
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    /** Small labels. [io.github.omegadestroyer.pairfree.ui.Label] sets them in capitals where the look asks for it. */
    val label: TextStyle,
    val number: TextStyle,
    val bigNumber: TextStyle,
    val labelCaps: Boolean,
) {
    companion object {
        fun of(text: FontFamily, headings: FontFamily, numbers: FontFamily, labelCaps: Boolean) = LookType(
            title = TextStyle(fontFamily = headings, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
            heading = TextStyle(fontFamily = headings, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
            body = TextStyle(fontFamily = text, fontSize = 15.sp, lineHeight = 21.sp),
            small = TextStyle(fontFamily = text, fontSize = 13.sp, lineHeight = 18.sp),
            label = TextStyle(fontFamily = numbers, fontWeight = FontWeight.Medium, fontSize = if (labelCaps) 11.sp else 12.sp, letterSpacing = if (labelCaps) 1.sp else 0.2.sp),
            number = TextStyle(fontFamily = numbers, fontSize = 15.sp),
            bigNumber = TextStyle(fontFamily = numbers, fontWeight = FontWeight.Medium, fontSize = 22.sp),
            labelCaps = labelCaps,
        )
    }
}

/** Lines, corners and shadows. Touch targets are at least 44 px in every look (docs/DESIGN.md, Rules for every look). */
@Immutable
data class LookShape(
    val hairline: Dp,
    val corner: Dp,
    val smallCorner: Dp,
    /** Whether a panel is outlined, or told from the background by its colour alone. */
    val panelBorder: Boolean,
    /** How far a shape's four corners differ, as a part of the radius: a hand-drawn look's are never quite alike. */
    val uneven: Float = 0f,
    /** The offset of the hard shadow under panels and buttons; zero in a look without one. */
    val shadow: Dp = 0.dp,
    /** Whether a chosen secondary choice is filled with the highlight colour, or outlined in the accent. */
    val selectedFill: Boolean = false,
    val touch: Dp = 44.dp,
) {
    /** The shape of panels and dialogs. */
    val panel: Shape = rounded(corner)

    /** The shape of buttons, chips and other controls. */
    val control: Shape = rounded(smallCorner)

    private fun rounded(radius: Dp): Shape =
        if (uneven == 0f) RoundedCornerShape(radius)
        else RoundedCornerShape(radius * (1 + uneven), radius * (1 - uneven), radius * (1 + uneven / 2), radius * (1 - uneven / 2))
}

/**
 * How the device drawings are drawn: the style ("line": thin technical lines; "cartoon": heavier lines with a flat
 * fill set off from them), a factor on every stroke's width, and the cartoon fill's offset in the drawing's units.
 */
@Immutable
data class LookArt(val style: String, val stroke: Float, val offset: Float = 0f)

private val STUDIO: LookSpec = Looks.ALL.firstOrNull { it.id == Looks.DEFAULT } ?: Looks.ALL.first()

private fun colors(palette: Palette, dark: Boolean, resolve: (io.github.omegadestroyer.pairfree.look.Tone) -> Int) = LookColors(
    background = Color(resolve(palette.background)),
    panel = Color(resolve(palette.panel)),
    raised = Color(resolve(palette.raised)),
    hairline = Color(resolve(palette.hairline)),
    text = Color(resolve(palette.text)),
    secondaryText = Color(resolve(palette.secondaryText)),
    label = Color(resolve(palette.label)),
    accent = Color(resolve(palette.accent)),
    onAccent = Color(resolve(palette.onAccent)),
    destructive = Color(resolve(palette.destructive)),
    outline = Color(resolve(palette.outline)),
    shadow = Color(resolve(palette.shadow)),
    highlight = Color(resolve(palette.highlight)),
    artFill = Color(resolve(palette.artFill)),
    artAccent = Color(resolve(palette.artAccent)),
    dark = dark,
)

private fun type(spec: LookSpec) = LookType.of(fontFamily(spec.textFont), fontFamily(spec.headingFont), fontFamily(spec.numberFont), spec.labelCaps)
private fun shape(spec: LookSpec) = LookShape(spec.hairline.dp, spec.corner.dp, spec.smallCorner.dp, spec.panelBorder, spec.uneven, spec.shadow.dp, spec.selectedFill)
private fun art(spec: LookSpec) = LookArt(spec.artStyle, spec.artStroke, spec.artOffset)

private val LocalLookColors = staticCompositionLocalOf<LookColors> { error("No look: wrap the screen in LookTheme") }
private val LocalLookType = staticCompositionLocalOf { type(STUDIO) }
private val LocalLookShape = staticCompositionLocalOf { shape(STUDIO) }
private val LocalLookArt = staticCompositionLocalOf { art(STUDIO) }

/** The look the screen is drawn in. */
object Look {
    val colors: LookColors
        @Composable @ReadOnlyComposable get() = LocalLookColors.current
    val type: LookType
        @Composable @ReadOnlyComposable get() = LocalLookType.current
    val shape: LookShape
        @Composable @ReadOnlyComposable get() = LocalLookShape.current
    val art: LookArt
        @Composable @ReadOnlyComposable get() = LocalLookArt.current
}

/**
 * Draws [content] in the look the user chose, or in [look] (previews). A look with a dark and a light palette follows the
 * phone's dark mode, and colours taken from the wallpaper follow it too. Choosing another look draws the screens again
 * at once; nothing restarts.
 */
@Composable
fun LookTheme(look: LookSpec? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val chosen by Looks.flow(context).collectAsState()
    val spec = look ?: chosen
    val night = isSystemInDarkTheme()
    // The configuration is a key: a new wallpaper or dark mode changes the system colours without changing the look.
    val configuration = LocalConfiguration.current
    val colors = remember(spec, night, configuration) { colors(spec.palette(night), spec.isDark(night)) { it.resolve(context) } }
    CompositionLocalProvider(
        LocalLookColors provides colors,
        LocalLookType provides remember(spec) { type(spec) },
        LocalLookShape provides remember(spec) { shape(spec) },
        LocalLookArt provides remember(spec) { art(spec) },
        content = content,
    )
}

/** Gives the system bars light icons on a dark look and dark ones on a light look. Call inside [LookTheme]. */
@Composable
fun ComponentActivity.LookSystemBars() {
    val dark = Look.colors.dark
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
