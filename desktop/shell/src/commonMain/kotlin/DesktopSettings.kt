package io.github.omegadestroyer.pairfree.shell

import io.github.omegadestroyer.pairfree.eq.TextStore
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

/** What the window does when its close button is pressed. */
enum class CloseAction { ASK, TRAY, EXIT }

/**
 * The desktop apps' settings: one readable file (`PairFree.toml`, beside the program). `[app]` holds the choices the settings
 * screen offers, `[window]` where the window was, and `[store]` the texts the shared code keeps (the equalizer's saved profiles
 * and last settings, as the TextStore says), so a setting can be read and changed with a text editor. The file is rewritten as a
 * whole, beside itself and then renamed over the old one, so a crash cannot leave half of it. A file that cannot be read starts
 * empty and keeps the unreadable one as `.bad`, so a hand edit is never lost; a file that cannot be written is reported by
 * [writeError] and the settings go on in memory.
 */
class DesktopSettings(private val path: Path, private val header: String = DEFAULT_HEADER) : TextStore {
    private val readNotes = mutableListOf<String>()
    private var doc: TomlDocument = load()

    /** Why the last write failed, or null. The shell shows it once and offers the user's own folder. */
    var writeError: String? = null
        private set

    /** Lines of the file that were not understood when it was read. */
    val notes: List<String> get() = readNotes

    private fun load(): TomlDocument {
        if (!SystemFileSystem.exists(path)) return TomlDocument()
        val text = try {
            SystemFileSystem.source(path).buffered().use { it.readString() }
        } catch (e: Exception) {
            return TomlDocument()
        }
        val document = TomlDocument.parse(text.removePrefix("﻿"), readNotes)
        // Nothing at all was understood in a file that holds text: keep it aside rather than write over it.
        if (text.isNotBlank() && document.keys("app").isEmpty() && document.keys("window").isEmpty() && document.keys("store").isEmpty()) {
            try {
                SystemFileSystem.atomicMove(path, Path(path.toString() + ".bad"))
            } catch (_: Exception) {
            }
        }
        return document
    }

    private fun save() {
        val temp = Path(path.toString() + ".tmp")
        try {
            SystemFileSystem.sink(temp).buffered().use { it.writeString(doc.write(header)) }
            SystemFileSystem.atomicMove(temp, path)
            writeError = null
        } catch (e: Exception) {
            writeError = e.message ?: "could not write the settings file"
        }
    }

    override fun get(key: String): String? = doc["store", key]

    override fun put(key: String, value: String) {
        doc.set("store", key, value)
        save()
    }

    /** The language tag ("en", "pt-BR"), or null for the computer's own. */
    var language: String?
        get() = doc["app", "language"]?.takeIf { it.isNotBlank() && it != "system" }
        set(value) = change { it.set("app", "language", value ?: "system") }

    /** The look's id ("studio", "studio-light", "sketchbook", "system"). */
    var look: String
        get() = doc["app", "look"] ?: "studio"
        set(value) = change { it.set("app", "look", value) }

    var closeAction: CloseAction
        get() = doc["app", "close"]?.let { v -> CloseAction.entries.firstOrNull { it.name.equals(v, ignoreCase = true) } } ?: CloseAction.ASK
        set(value) = change { it.set("app", "close", value.name.lowercase()) }

    /** Whether the window goes to the tray when minimised, instead of the taskbar. */
    var minimizeToTray: Boolean
        get() = doc["app", "minimize_to_tray"] == "true"
        set(value) = change { it.setBoolean("app", "minimize_to_tray", value) }

    var startMinimized: Boolean
        get() = doc["app", "start_minimized"] == "true"
        set(value) = change { it.setBoolean("app", "start_minimized", value) }

    /** Whether to look for a newer release on GitHub; null until the user was asked (first run). */
    var checkForUpdates: Boolean?
        get() = doc["app", "check_for_updates"]?.let { it == "true" }
        set(value) = change { if (value == null) it.remove("app", "check_for_updates") else it.setBoolean("app", "check_for_updates", value) }

    /** The window's place and size in pixels: x, y, width, height; null before it was ever moved. */
    var windowBounds: List<Int>?
        get() = listOf("x", "y", "width", "height").map { doc["window", it]?.toIntOrNull() ?: return null }
        set(value) = change { d ->
            listOf("x", "y", "width", "height").forEachIndexed { i, k ->
                if (value == null) d.remove("window", k) else d.setNumber("window", k, value[i].toLong())
            }
        }

    private fun change(edit: (TomlDocument) -> Unit) {
        edit(doc)
        save()
    }

    companion object {
        const val FILE_NAME = "PairFree.toml"

        const val DEFAULT_HEADER = "PairFree's settings. You may edit this file while PairFree is closed; PairFree writes it whole, so comments are not kept.\n" +
            "[app]: language (a tag such as en or pt-BR, or system), look (studio, studio-light, sketchbook, system),\n" +
            "close (ask, tray, exit), minimize_to_tray, start_minimized, check_for_updates.\n" +
            "[store]: texts PairFree keeps for its equalizer; delete a line to forget it."
    }
}
