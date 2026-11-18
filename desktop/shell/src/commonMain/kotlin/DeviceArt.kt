package io.github.omegadestroyer.pairfree.shell

import io.github.omegadestroyer.pairfree.protocol.Form

/** The drawings of one model: the left earbud (the right is its mirror), or the whole product for headphones, and the closed case when there is one. */
data class DeviceArt(val bud: String, val case: String?)

/** Which drawings a model has: by Bluetooth name, then by firmware or model code, else a generic one of its [form] (tools/gen_device_art.py's lookup). */
fun deviceArt(firmwareCode: String?, name: String, form: Form = Form.EARBUDS): DeviceArt =
    DeviceArtTables.byName[name.lowercase()] ?: firmwareCode?.let { DeviceArtTables.byCode[it] } ?: when (form) {
        Form.EARBUDS -> DeviceArtTables.genericEarbuds
        Form.HEADPHONES -> DeviceArtTables.genericHeadphones
        Form.SPEAKER -> DeviceArtTables.genericSpeaker
    }

/**
 * The seven colours the drawings are written in, each standing for a role of the look: the toolkit paints a drawing with the look's
 * colour for the role (docs/LOOKS.md), as Android's `deviceArtPainter` does.
 */
enum class ArtRole { BACKGROUND, PANEL, RAISED, HAIRLINE, INK, GUIDE, ACCENT }

/** One path of a drawing: its stroke and fill roles (null: none), the stroke's width and the shape. */
class ArtPath(val stroke: ArtRole?, val fill: ArtRole?, val strokeWidth: Float, val commands: List<PathCommand>)

/** A drawing in its own units: the viewport it was drawn in and its paths, back to front. */
class ArtDrawing(val width: Float, val height: Float, val paths: List<ArtPath>) {
    companion object {
        private val ROLES = mapOf('B' to ArtRole.BACKGROUND, 'P' to ArtRole.PANEL, 'R' to ArtRole.RAISED, 'H' to ArtRole.HAIRLINE, 'I' to ArtRole.INK, 'G' to ArtRole.GUIDE, 'A' to ArtRole.ACCENT)

        /** The drawing called [name] (`device_h3_bud`), or null for a name no drawing has. */
        fun of(name: String): ArtDrawing? = DeviceArtTables.drawings[name]?.let(::parse)

        internal fun parse(text: String): ArtDrawing {
            val lines = text.lines()
            val (w, h) = lines[0].split(' ').map { it.toFloat() }
            val paths = lines.drop(1).map { line ->
                val (roles, width, data) = line.split(' ', limit = 3)
                ArtPath(ROLES[roles[0]], ROLES[roles[1]], width.toFloat(), PathData.parse(data))
            }
            return ArtDrawing(w, h, paths)
        }
    }
}

/** A step of a path, in absolute coordinates: the commands the drawings use (move, line, cubic curve, close). */
sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand

    data class LineTo(val x: Float, val y: Float) : PathCommand

    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : PathCommand

    data object Close : PathCommand
}

object PathData {
    /** Reads SVG path data of absolute `M`, `L`, `C` and `Z`; any other command is an error, so a drawing with one is caught at once. */
    fun parse(data: String): List<PathCommand> {
        val out = mutableListOf<PathCommand>()
        var i = 0
        fun number(): Float {
            while (i < data.length && (data[i] == ' ' || data[i] == ',')) i++
            val start = i
            while (i < data.length && (data[i].isDigit() || data[i] == '.' || data[i] == '-' || data[i] == '+' || data[i] == 'e' || data[i] == 'E')) i++
            return data.substring(start, i).toFloatOrNull() ?: throw IllegalArgumentException("no number at $start in $data")
        }
        while (i < data.length) {
            val c = data[i]
            if (c == ' ' || c == ',') { i++; continue }
            i++
            when (c) {
                'M' -> out += PathCommand.MoveTo(number(), number())
                'L' -> out += PathCommand.LineTo(number(), number())
                'C' -> out += PathCommand.CubicTo(number(), number(), number(), number(), number(), number())
                'Z' -> out += PathCommand.Close
                else -> throw IllegalArgumentException("path command $c is not one the drawings use")
            }
        }
        return out
    }
}
