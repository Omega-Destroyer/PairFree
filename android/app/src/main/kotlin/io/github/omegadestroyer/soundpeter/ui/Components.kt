package io.github.omegadestroyer.soundpeter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.omegadestroyer.soundpeter.ui.theme.Studio
import io.github.omegadestroyer.soundpeter.ui.theme.StudioShape

/** Text in the Studio type. Defaults to body text in the text colour. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Studio.type.body,
    color: Color = Studio.colors.text,
    textAlign: TextAlign? = null,
) {
    BasicText(text, modifier, style.copy(color = color, textAlign = textAlign ?: style.textAlign))
}

/** A small capitalised label in Plex Mono. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Studio.colors.label) {
    Text(text.uppercase(), modifier, Studio.type.label, color)
}

/** A panel: hairline border, 6 px corners, no shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(StudioShape.corner)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Studio.colors.panel)
            .border(StudioShape.hairline, Studio.colors.hairline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/**
 * A button. Primary: amber fill with dark text. Secondary: amber outline and amber text.
 * The two differ in fill as well as colour, so the choice never rests on colour alone.
 */
@Composable
fun StudioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Studio.colors
    val shape = RoundedCornerShape(StudioShape.smallCorner)
    val fill = if (primary && enabled) c.accent else Color.Transparent
    val edge = if (enabled) c.accent else c.hairline
    val ink = when {
        !enabled -> c.label
        primary -> c.onAccent
        else -> c.accent
    }
    Box(
        modifier
            .heightIn(min = StudioShape.touch)
            .clip(shape)
            .background(fill)
            .border(StudioShape.hairline, edge, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Studio.type.heading, color = ink)
    }
}

/** A level bar: hairline track, amber fill. Empty when the level is unknown. */
@Composable
fun LevelBar(percent: Int?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(shape)
            .background(Studio.colors.hairline),
    ) {
        if (percent != null && percent > 0) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .clip(shape)
                    .background(Studio.colors.accent),
            )
        }
    }
}
