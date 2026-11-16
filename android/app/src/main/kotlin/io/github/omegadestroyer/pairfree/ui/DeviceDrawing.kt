package io.github.omegadestroyer.pairfree.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import io.github.omegadestroyer.pairfree.ui.theme.Look
import io.github.omegadestroyer.pairfree.ui.theme.LookArt
import io.github.omegadestroyer.pairfree.ui.theme.LookColors

/**
 * The colours tools/gen_device_art.py writes into every device drawing. They are Studio's, and here they are names: each
 * stands for a role, and a drawing is shown with the look's colour for that role (docs/LOOKS.md). tools/test_looks.py
 * fails when a drawing holds a colour that is not one of these.
 */
private val BACKGROUND = Color(0xFF101214)
private val PANEL = Color(0xFF181B1F)
private val RAISED = Color(0xFF1F2328)
private val HAIRLINE = Color(0xFF2B3037)
private val INK = Color(0xFFE8E6E1)
private val GUIDE = Color(0xFF8A9099)
private val ACCENT = Color(0xFFE6A23C)

private fun roles(c: LookColors): Map<Color, Color> = mapOf(
    BACKGROUND to c.background,
    PANEL to c.panel,
    RAISED to c.raised,
    HAIRLINE to c.hairline,
    INK to c.text,
    GUIDE to c.label,
    ACCENT to c.accent,
)

/**
 * A device drawing in the app's look: the same shapes for every look, with the look's colours and its stroke width. Use
 * this, not `painterResource`, for every drawing from [deviceArt].
 */
@Composable
fun deviceArtPainter(@DrawableRes drawing: Int): Painter {
    val colors = Look.colors
    val art = Look.art
    val source = ImageVector.vectorResource(drawing)
    val image = remember(source, colors, art) { source.inLook(roles(colors), art) }
    return rememberVectorPainter(image)
}

private fun ImageVector.inLook(roles: Map<Color, Color>, art: LookArt): ImageVector {
    if (art.stroke == 1f && roles.all { (from, to) -> from == to }) return this
    fun Brush?.mapped(): Brush? = if (this is SolidColor) SolidColor(roles[value] ?: value) else this
    val out = ImageVector.Builder(name, defaultWidth, defaultHeight, viewportWidth, viewportHeight, tintColor, tintBlendMode, autoMirror)
    fun add(group: VectorGroup) {
        for (node in group) when (node) {
            is VectorPath -> out.addPath(
                pathData = node.pathData,
                pathFillType = node.pathFillType,
                name = node.name,
                fill = node.fill.mapped(),
                fillAlpha = node.fillAlpha,
                stroke = node.stroke.mapped(),
                strokeAlpha = node.strokeAlpha,
                strokeLineWidth = node.strokeLineWidth * art.stroke,
                strokeLineCap = node.strokeLineCap,
                strokeLineJoin = node.strokeLineJoin,
                strokeLineMiter = node.strokeLineMiter,
                trimPathStart = node.trimPathStart,
                trimPathEnd = node.trimPathEnd,
                trimPathOffset = node.trimPathOffset,
            )
            is VectorGroup -> {
                out.addGroup(node.name, node.rotation, node.pivotX, node.pivotY, node.scaleX, node.scaleY, node.translationX, node.translationY, node.clipPathData)
                add(node)
                out.clearGroup()
            }
        }
    }
    add(root)
    return out.build()
}
