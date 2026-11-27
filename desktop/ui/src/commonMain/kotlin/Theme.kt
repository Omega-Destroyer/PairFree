package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.shell.ArtRole
import io.github.omegadestroyer.pairfree.shell.DesktopLook
import io.github.omegadestroyer.pairfree.shell.SystemTones

/** A look as the toolkit draws by it: every colour of the palette in use as an ARGB number, and the shape and type tokens. */
class Theme(val look: DesktopLook, night: Boolean, tones: SystemTones) {
    private val p = look.palette(night)
    val background = tones.resolve(p.background)
    val panel = tones.resolve(p.panel)
    val raised = tones.resolve(p.raised)
    val hairline = tones.resolve(p.hairline)
    val text = tones.resolve(p.text)
    val secondaryText = tones.resolve(p.secondaryText)
    val label = tones.resolve(p.label)
    val accent = tones.resolve(p.accent)
    val onAccent = tones.resolve(p.onAccent)
    val destructive = tones.resolve(p.destructive)
    val outline = tones.resolve(p.outline)
    val shadow = tones.resolve(p.shadow)
    val highlight = tones.resolve(p.highlight)
    private val artFill = tones.resolve(p.artFill)
    private val artAccent = tones.resolve(p.artAccent)

    val corner: Float get() = look.corner
    val smallCorner: Float get() = look.smallCorner

    /** The look's colour for a role of the device drawings (docs/LOOKS.md). */
    fun art(role: ArtRole): Int = when (role) {
        ArtRole.BACKGROUND -> background
        ArtRole.PANEL -> artFill
        ArtRole.RAISED -> raised
        ArtRole.HAIRLINE -> hairline
        ArtRole.INK -> text
        ArtRole.GUIDE -> label
        ArtRole.ACCENT -> artAccent
    }

    val body = Font(14f, mono = false)
    val small = Font(12f)
    val title = Font(22f, bold = true)
    val heading = Font(16f, bold = true)
    val bigNumber = Font(22f, mono = true)
    val labelFont = Font(11f, mono = true)

    /** [alpha] (0..1) of an opaque colour. */
    fun withAlpha(color: Int, alpha: Float): Int = ((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
}
