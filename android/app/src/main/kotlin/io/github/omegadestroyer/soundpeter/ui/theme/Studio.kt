package io.github.omegadestroyer.soundpeter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.omegadestroyer.soundpeter.R

// The Studio look (docs/DESIGN.md), the app's only look. Colours, type and shapes live
// here, not in the screens.

@Immutable
data class StudioColors(
    val background: Color = Color(0xFF101214),
    val panel: Color = Color(0xFF181B1F),
    val raised: Color = Color(0xFF1F2328),
    val hairline: Color = Color(0xFF2B3037),
    val text: Color = Color(0xFFE8E6E1),
    val secondaryText: Color = Color(0xFFA0A5AD),
    val label: Color = Color(0xFF8A9099),
    val accent: Color = Color(0xFFE6A23C),
    val onAccent: Color = Color(0xFF101214),
    val destructive: Color = Color(0xFFE88868),
)

/** IBM Plex Sans, one variable font file for every weight. SIL Open Font License, bundled. */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** IBM Plex Mono, for every number and for small capitalised labels. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

@Immutable
data class StudioType(
    val title: TextStyle = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    val heading: TextStyle = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, lineHeight = 21.sp),
    val small: TextStyle = TextStyle(fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 18.sp),
    /** Small capitalised labels. Set the text in capitals; [Label] does it for you. */
    val label: TextStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp),
    val number: TextStyle = TextStyle(fontFamily = PlexMono, fontSize = 15.sp),
    val bigNumber: TextStyle = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 22.sp),
)

/** 1 px hairlines, 4 to 6 px corners, no shadows. Touch targets at least 44 px. */
object StudioShape {
    val hairline = 1.dp
    val corner = 6.dp
    val smallCorner = 4.dp
    val touch = 44.dp
}

private val LocalStudioColors = staticCompositionLocalOf { StudioColors() }
private val LocalStudioType = staticCompositionLocalOf { StudioType() }

object Studio {
    val colors: StudioColors
        @Composable @ReadOnlyComposable get() = LocalStudioColors.current
    val type: StudioType
        @Composable @ReadOnlyComposable get() = LocalStudioType.current
}

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStudioColors provides StudioColors(),
        LocalStudioType provides StudioType(),
        content = content,
    )
}
