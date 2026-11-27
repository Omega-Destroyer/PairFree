package io.github.omegadestroyer.pairfree.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Right-to-left languages (Persian, Arabic, Hebrew, Urdu) mirror the screen: the first thing in a row goes to the
 * right. Most of the app should follow that, but some things describe the world and not reading order, and must
 * stay as they are in every language (Persian-speaking tester, issue #44):
 *
 *  - the left earbud is on the left and the right one on the right, on the battery card, the fit test and the
 *    controls' picker, since the drawings and the labels both stand for the physical earbuds;
 *  - a slider runs from low to high going right, so the minus button is on its left and the plus button on its
 *    right, as are the graph's bands (low to high pitch) and the buttons under it.
 *
 * [Physical] holds such a part in left-to-right order. Text inside it still reads by its own script;
 * wrap a part that should read normally again in [Reading].
 */
internal val LocalReadingDirection = compositionLocalOf { LayoutDirection.Ltr }

@Composable
internal fun Physical(content: @Composable () -> Unit) {
    val reading = LocalLayoutDirection.current
    CompositionLocalProvider(
        LocalReadingDirection provides reading,
        LocalLayoutDirection provides LayoutDirection.Ltr,
        content = content,
    )
}

/** Inside [Physical]: the screen's own reading direction again. */
@Composable
internal fun Reading(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LocalReadingDirection.current, content = content)
}

/** True when the screen reads right to left. */
@Composable
internal fun isRtl(): Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl

private const val LRM = "‎"

/**
 * A number with its sign (`+2`, `−3`) kept in that order on a right-to-left screen, where a bare sign would
 * otherwise land after its digits ("2+"). The invisible left-to-right mark changes nothing on left-to-right screens.
 */
internal fun signed(text: String): String = LRM + text
