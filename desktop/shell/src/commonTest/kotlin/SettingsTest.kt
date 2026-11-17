package io.github.omegadestroyer.pairfree.shell

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsTest {
    private fun folder(name: String): Path {
        val dir = Path("settings-test-$name")
        SystemFileSystem.createDirectories(dir)
        for (f in SystemFileSystem.list(dir)) SystemFileSystem.delete(f)
        return dir
    }

    private fun write(path: Path, text: String) = SystemFileSystem.sink(path).buffered().use { it.writeString(text) }

    private fun read(path: Path) = SystemFileSystem.source(path).buffered().use { it.readString() }

    @Test
    fun valuesComeBackAsTheyWereWrittenAndTheFileReadsAsText() {
        val file = Path(folder("roundtrip"), "PairFree.toml")
        val s = DesktopSettings(file)
        s.look = "sketchbook"
        s.language = "pt-BR"
        s.closeAction = CloseAction.TRAY
        s.checkForUpdates = true
        s.windowBounds = listOf(10, 20, 840, 720)
        s.put("eq_profiles_H3", """[{"name":"It's \"warm\"","gains":[2,0,-1.5]}]""")
        val again = DesktopSettings(file)
        assertEquals("sketchbook", again.look)
        assertEquals("pt-BR", again.language)
        assertEquals(CloseAction.TRAY, again.closeAction)
        assertEquals(true, again.checkForUpdates)
        assertEquals(listOf(10, 20, 840, 720), again.windowBounds)
        assertEquals("""[{"name":"It's \"warm\"","gains":[2,0,-1.5]}]""", again.get("eq_profiles_H3"))
        val text = read(file)
        assertTrue(text.contains("[app]") && text.contains("look = 'sketchbook'") && text.contains("[store]"), text)
        assertNull(s.writeError)
    }

    @Test
    fun aNewFileHasTheDefaultsAndNoAnswerToTheUpdateQuestion() {
        val s = DesktopSettings(Path(folder("fresh"), "PairFree.toml"))
        assertEquals("studio", s.look)
        assertNull(s.language)
        assertEquals(CloseAction.ASK, s.closeAction)
        assertNull(s.checkForUpdates)
        assertNull(s.windowBounds)
        assertNull(s.get("anything"))
    }

    @Test
    fun aFileEditedByHandIsRead() {
        val file = Path(folder("hand"), "PairFree.toml")
        write(file, "# mine\n[app]\nlook = \"studio-light\"   # light\nclose = exit\nminimize_to_tray = true\nlanguage = \"system\"\nnonsense line\n[window]\nx = 5\ny = 6\nwidth = 700\nheight = 600\n")
        val s = DesktopSettings(file)
        assertEquals("studio-light", s.look)
        assertEquals(CloseAction.EXIT, s.closeAction)
        assertTrue(s.minimizeToTray)
        assertNull(s.language)
        assertEquals(listOf(5, 6, 700, 600), s.windowBounds)
        assertEquals(listOf("line 7: not read"), s.notes)
    }

    @Test
    fun aFileThatIsNothingLikeSettingsIsKeptAsideNotWrittenOver() {
        val dir = folder("bad")
        val file = Path(dir, "PairFree.toml")
        write(file, "this is not a settings file at all")
        val s = DesktopSettings(file)
        s.look = "studio"
        assertTrue(SystemFileSystem.exists(Path(dir, "PairFree.toml.bad")))
        assertEquals("this is not a settings file at all", read(Path(dir, "PairFree.toml.bad")))
        assertNotNull(DesktopSettings(file).get("none") ?: "x")
    }

    @Test
    fun aFolderThatCannotBeWrittenIsReportedAndTheSettingsGoOnInMemory() {
        val s = DesktopSettings(Path("no-such-folder-for-pairfree", "deeper", "PairFree.toml"))
        s.look = "sketchbook"
        assertNotNull(s.writeError)
        assertEquals("sketchbook", s.look)
    }

    @Test
    fun tomlStringsWithQuotesNewlinesAndKeysWithOddCharactersSurvive() {
        val d = TomlDocument()
        d.set("store", "a key with spaces", "line1\nline2\ttab \"q\" \\ back")
        d.set("store", "k2", "plain 'single'")
        val back = TomlDocument.parse(d.write())
        assertEquals("line1\nline2\ttab \"q\" \\ back", back["store", "a key with spaces"])
        assertEquals("plain 'single'", back["store", "k2"])
    }
}
