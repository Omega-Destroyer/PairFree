package io.github.omegadestroyer.pairfree.shell

import io.github.omegadestroyer.pairfree.protocol.Form
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceArtTest {
    @Test
    fun pathDataIsReadAsCommands() {
        val c = PathData.parse("M1.5 -2.0L3 4C5 6 7 8 9 10Z")
        assertEquals(listOf(PathCommand.MoveTo(1.5f, -2f), PathCommand.LineTo(3f, 4f), PathCommand.CubicTo(5f, 6f, 7f, 8f, 9f, 10f), PathCommand.Close), c)
        assertFailsWith<IllegalArgumentException> { PathData.parse("M1 2A3 4") }
    }

    @Test
    fun theH3HasItsOwnDrawingsAndAnUnknownModelTheGenericOnes() {
        val h3 = deviceArt(null, "SOUNDPEATS H3")
        assertEquals("device_h3_bud", h3.bud)
        assertNotNull(h3.case)
        assertEquals("device_generic_bud", deviceArt(null, "Nothing Known", Form.EARBUDS).bud)
        assertEquals("device_generic_headphones_bud", deviceArt(null, "Nothing Known", Form.HEADPHONES).bud)
        assertNull(deviceArt(null, "Nothing Known", Form.SPEAKER).case)
    }

    @Test
    fun everyDrawingTheTablesNameIsThereAndReadsAsPaths() {
        val names = (DeviceArtTables.byName.values + DeviceArtTables.byCode.values +
            listOf(DeviceArtTables.genericEarbuds, DeviceArtTables.genericHeadphones, DeviceArtTables.genericSpeaker))
            .flatMap { listOfNotNull(it.bud, it.case) }.toSet()
        assertTrue(names.size > 200, "${names.size} drawings")
        for (n in names) {
            val d = assertNotNull(ArtDrawing.of(n), n)
            assertTrue(d.width > 0 && d.height > 0 && d.paths.isNotEmpty(), n)
            assertTrue(d.paths.all { it.commands.first() is PathCommand.MoveTo }, n)
        }
    }

    @Test
    fun aDrawingKeepsTheRolesOfItsColours() {
        val d = assertNotNull(ArtDrawing.of("device_generic_headphones_bud"))
        assertTrue(d.paths.any { it.fill == ArtRole.PANEL && it.stroke == ArtRole.INK })
        assertTrue(d.paths.any { it.fill == ArtRole.RAISED && it.stroke == ArtRole.ACCENT })
    }
}
