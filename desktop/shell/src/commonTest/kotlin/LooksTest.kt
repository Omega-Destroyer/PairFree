package io.github.omegadestroyer.pairfree.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LooksTest {
    @Test
    fun everyLookFileIsALookAndStudioIsFirst() {
        assertEquals(listOf("studio", "studio-light", "material-you", "sketchbook"), BUILT_IN_LOOKS.map { it.id })
        assertEquals("look_material_you", BUILT_IN_LOOKS[2].nameKey)
        assertEquals("System", DesktopStrings("en").text(BUILT_IN_LOOKS[2].nameKey))
        assertEquals("Studio", DesktopStrings("en").text(BUILT_IN_LOOKS[0].nameKey))
    }

    @Test
    fun studioIsDarkWhateverTheSystemIsAndTheLightLookIsLight() {
        val studio = DesktopLook.byId("studio")
        assertTrue(studio.isDark(night = false) && studio.isDark(night = true))
        assertEquals(0xFF101214.toInt(), (studio.palette(false).background as Tone.Fixed).argb)
        assertTrue(!DesktopLook.byId("studio-light").isDark(night = true))
    }

    @Test
    fun anUnknownIdIsStudio() {
        assertEquals("studio", DesktopLook.byId("nonsense").id)
        assertEquals("studio", DesktopLook.byId(null).id)
    }

    @Test
    fun theSystemLookFollowsTheSystemsModeAndUsesItsTones() {
        val sys = DesktopLook.byId("material-you")
        assertTrue(sys.usesSystemTones)
        assertTrue(sys.isDark(true) != sys.isDark(false) || sys.dark == null || sys.light == null)
    }

    @Test
    fun systemTonesGoFromWhiteToBlackAndKeepTheAccentsHue() {
        val tones = SystemTones(0xFF0078D4.toInt())
        assertEquals(0xFFFFFFFF.toInt(), tones.resolve("system_accent1_0"))
        assertEquals(0xFF000000.toInt(), tones.resolve("system_accent1_1000"))
        val mid = tones.resolve("system_accent1_500")
        val blue = mid and 0xFF
        val red = (mid shr 16) and 0xFF
        assertTrue(blue > red, "a blue accent stays blue")
        assertNotEquals(tones.resolve("system_accent1_200"), tones.resolve("system_accent1_700"))
        assertEquals(0xFF123456.toInt(), tones.resolve(Tone.Fixed(0xFF123456.toInt())))
    }
}
