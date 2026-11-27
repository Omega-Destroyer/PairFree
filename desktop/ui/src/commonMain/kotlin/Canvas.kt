package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.shell.PathCommand

/** A font by role and size in logical pixels; the system maps the role to a typeface (the look's own fonts come with the toolkit's fonts). */
data class Font(val size: Float, val bold: Boolean = false, val mono: Boolean = false)

/**
 * What the toolkit draws with. Windows backs it with GDI+ (and Direct2D later), macOS with CoreGraphics; everything above it is common
 * Kotlin. Coordinates are logical pixels: the system's canvas applies the display's scale.
 */
interface Canvas {
    fun fillRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int)

    fun strokeRoundRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int, width: Float)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float)

    fun fillCircle(cx: Float, cy: Float, r: Float, color: Int)

    /** Draws [text] with its top left at ([x], [y]). */
    fun text(text: String, x: Float, y: Float, font: Font, color: Int)

    fun textWidth(text: String, font: Font): Float

    fun lineHeight(font: Font): Float

    /**
     * Draws a path given in its own units: [scale] and the offset ([x], [y]) place it, [mirror] flips it left to right inside a box [boxWidth] wide.
     * A null colour leaves out the fill or the stroke.
     */
    fun path(commands: List<PathCommand>, x: Float, y: Float, scale: Float, mirror: Boolean, boxWidth: Float, fill: Int?, stroke: Int?, strokeWidth: Float)

    /** Keeps what follows inside the rectangle until [popClip]. */
    fun pushClip(x: Float, y: Float, w: Float, h: Float)

    fun popClip()
}
