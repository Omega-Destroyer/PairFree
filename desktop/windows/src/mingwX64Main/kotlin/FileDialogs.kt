@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import platform.windows.GetProcAddress
import platform.windows.HWND
import platform.windows.LoadLibraryW

/**
 * Windows' own Open and Save dialogs for the profile files, from comdlg32 (found by name, since the toolchain's headers do not declare it).
 * The files are text; the filters are the file's own pattern, which needs no words.
 */
internal object FileDialogs {
    private const val SIZE = 152
    private const val PATH_CHARS = 1024
    private const val OFN_OVERWRITEPROMPT = 0x2
    private const val OFN_NOCHANGEDIR = 0x8
    private const val OFN_PATHMUSTEXIST = 0x800
    private const val OFN_FILEMUSTEXIST = 0x1000

    private val library by lazy { LoadLibraryW("comdlg32.dll") }

    private fun wide(text: String): List<UShort> = text.map { it.code.toUShort() }

    /** Shows a dialog; returns the chosen path or null. */
    private fun ask(owner: HWND?, save: Boolean, suggested: String): String? = memScoped {
        val fn = library?.let { GetProcAddress(it, if (save) "GetSaveFileNameW" else "GetOpenFileNameW") } ?: return@memScoped null
        val call = fn.reinterpret<CFunction<(COpaquePointer?) -> Int>>()
        val ofn = allocArray<ByteVar>(SIZE)
        val file = allocArray<UShortVar>(PATH_CHARS)
        for ((i, c) in wide(suggested).take(PATH_CHARS - 1).withIndex()) file[i] = c
        // "*.json" and "*.*": the pattern is the label, each text ends with a zero and the list with another.
        val filterText = "*.json\u0000*.json\u0000*.*\u0000*.*\u0000\u0000"
        val filter = allocArray<UShortVar>(filterText.length + 1)
        for ((i, c) in wide(filterText).withIndex()) filter[i] = c
        val ext = allocArray<UShortVar>(5)
        for ((i, c) in wide("json").withIndex()) ext[i] = c
        ofn.reinterpret<UIntVar>()[0] = SIZE.toUInt()
        ofn.reinterpret<LongVar>()[1] = (owner?.toLong() ?: 0L)
        ofn.reinterpret<LongVar>()[3] = filter.toLong()
        ofn.reinterpret<UIntVar>()[11] = 1u
        ofn.reinterpret<LongVar>()[6] = file.toLong()
        ofn.reinterpret<UIntVar>()[14] = PATH_CHARS.toUInt()
        val flags = OFN_NOCHANGEDIR or OFN_PATHMUSTEXIST or if (save) OFN_OVERWRITEPROMPT else OFN_FILEMUSTEXIST
        ofn.reinterpret<UIntVar>()[24] = flags.toUInt()
        ofn.reinterpret<LongVar>()[13] = ext.toLong()
        if (call(ofn) == 0) return@memScoped null
        file.toKString()
    }

    fun openText(owner: HWND?): String? {
        val path = ask(owner, false, "") ?: return null
        return try {
            SystemFileSystem.source(Path(path)).buffered().use { it.readString() }
        } catch (_: Exception) {
            null
        }
    }

    fun saveText(owner: HWND?, name: String, text: String): Boolean {
        val path = ask(owner, true, name) ?: return false
        return try {
            SystemFileSystem.sink(Path(path)).buffered().use { it.writeString(text) }
            true
        } catch (_: Exception) {
            false
        }
    }
}
