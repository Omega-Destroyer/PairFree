package io.github.omegadestroyer.pairfree.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringsTest {
    @Test
    fun englishHasTheMainTextsAndTheDesktopWordingWins() {
        val en = DesktopStrings("en")
        assertEquals("PairFree", en.text("app_name"))
        assertEquals("Language", en.text("app_language_title"))
        assertEquals("Same as the computer", en.text("app_language_system"))
    }

    @Test
    fun aMissingKeyShowsItsName() {
        assertEquals("no_such_text", DesktopStrings("en").text("no_such_text"))
    }

    @Test
    fun placesAndPluralsAreFilled() {
        val en = DesktopStrings("en")
        assertEquals("1 of 5 bar", en.plural("battery_bars", 1, 1, 5))
        assertEquals("3 of 5 bars", en.plural("battery_bars", 3, 3, 5))
        assertEquals("a b 100%", DesktopStrings.format("%s %2\$s 100%%", arrayOf("a", "b")))
        assertEquals("x 7", DesktopStrings.format("%s %d", arrayOf("x", 7)))
    }

    @Test
    fun tagsResolveToTheBuiltInLanguages() {
        assertEquals("fa", DesktopStrings.resolve("fa_IR"))
        assertEquals("en", DesktopStrings.resolve("xx"))
        assertEquals("en", DesktopStrings.resolve(null))
        assertTrue("fa" in DesktopStrings.languages && DesktopStrings.languages.first() == "en")
    }

    @Test
    fun aTranslationFallsBackToEnglishForWhatItLacks() {
        val fa = DesktopStrings("fa")
        assertEquals("PairFree", fa.text("app_name"))
        assertTrue(fa.text("app_language_title") != "app_language_title")
    }
}
