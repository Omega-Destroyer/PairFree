@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.shell.Sha256
import io.github.omegadestroyer.pairfree.shell.UpdateKey
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write
import platform.windows.CloseHandle
import platform.windows.CreateProcessW
import platform.windows.DeleteFileW
import platform.windows.GetModuleFileNameW
import platform.windows.GetProcAddress
import platform.windows.LoadLibraryW
import platform.windows.MoveFileExW
import platform.windows.PROCESS_INFORMATION
import platform.windows.STARTUPINFOW
import platform.windows.WCHARVar

/**
 * The update check and the swap of the program (docs/DESKTOP.md, section 6). The only network traffic of the desktop apps: a GET of GitHub's
 * release API and of the files of the release it names, through Windows' own HTTPS (WinHTTP), when the user allowed it. A download is
 * installed only when its signature checks against the release key the program carries ([UpdateKey]).
 */
internal object SelfUpdate {
    internal fun hex(hex: String) = hexBytes(hex)

    private val winhttp by lazy { LoadLibraryW("winhttp.dll") }
    private val bcrypt by lazy { LoadLibraryW("bcrypt.dll") }

    private inline fun <reified F : Function<*>> http(name: String): CPointer<CFunction<F>> = GetProcAddress(winhttp, name)!!.reinterpret()

    /** A GET of [url]; the body, or null when it could not be fetched (no network, a status other than 200). */
    fun get(url: String, accept: String? = null): ByteArray? {
        if (!url.startsWith("https://")) return null
        val rest = url.removePrefix("https://")
        val host = rest.substringBefore('/')
        val path = "/" + rest.substringAfter('/', "")
        val open = http<(COpaquePointer?, UInt, COpaquePointer?, COpaquePointer?, UInt) -> COpaquePointer?>("WinHttpOpen")
        val connect = http<(COpaquePointer?, COpaquePointer?, UShort, UInt) -> COpaquePointer?>("WinHttpConnect")
        val openRequest = http<(COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, UInt) -> COpaquePointer?>("WinHttpOpenRequest")
        val addHeaders = http<(COpaquePointer?, COpaquePointer?, UInt, UInt) -> Int>("WinHttpAddRequestHeaders")
        val send = http<(COpaquePointer?, COpaquePointer?, UInt, COpaquePointer?, UInt, UInt, ULong) -> Int>("WinHttpSendRequest")
        val receive = http<(COpaquePointer?, COpaquePointer?) -> Int>("WinHttpReceiveResponse")
        val query = http<(COpaquePointer?, UInt, COpaquePointer?, COpaquePointer?, CPointer<UIntVar>?, COpaquePointer?) -> Int>("WinHttpQueryHeaders")
        val read = http<(COpaquePointer?, COpaquePointer?, UInt, CPointer<UIntVar>?) -> Int>("WinHttpReadData")
        val close = http<(COpaquePointer?) -> Int>("WinHttpCloseHandle")
        return memScoped {
            // The program's name and version only: the release API asks for an agent, and nothing else about the user goes with it.
            val session = open("PairFree".wcstr.ptr, 4u /* automatic proxy */, null, null, 0u) ?: return@memScoped null
            val connection = connect(session, host.wcstr.ptr, 443u, 0u)
            val request = connection?.let { openRequest(it, "GET".wcstr.ptr, path.wcstr.ptr, null, null, null, 0x00800000u /* secure */) }
            try {
                if (request == null) return@memScoped null
                if (accept != null) addHeaders(request, "Accept: $accept".wcstr.ptr, UInt.MAX_VALUE, 0x20000000u /* add */)
                if (send(request, null, 0u, null, 0u, 0u, 0uL) == 0 || receive(request, null) == 0) return@memScoped null
                val status = alloc<UIntVar>()
                val size = alloc<UIntVar>()
                size.value = 4u
                query(request, (19u or 0x20000000u), null, status.ptr, size.ptr, null)
                if (status.value != 200u) return@memScoped null
                val out = ArrayList<ByteArray>()
                val buffer = allocArray<ByteVar>(65536)
                val got = alloc<UIntVar>()
                var total = 0L
                while (read(request, buffer, 65536u, got.ptr) != 0 && got.value > 0u) {
                    out += ByteArray(got.value.toInt()) { buffer[it] }
                    total += got.value.toLong()
                    if (total > MAX_BYTES) return@memScoped null
                }
                val all = ByteArray(total.toInt())
                var at = 0
                for (chunk in out) { chunk.copyInto(all, at); at += chunk.size }
                all
            } finally {
                if (request != null) close(request)
                if (connection != null) close(connection)
                close(session)
            }
        }
    }

    private const val MAX_BYTES = 200L * 1024 * 1024

    private fun hexBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** True when [signature] (64 bytes, r then s) is the release key's ECDSA P-256 signature of the SHA-256 of [data]. */
    fun verify(data: ByteArray, signature: ByteArray, keyX: String = UpdateKey.X, keyY: String = UpdateKey.Y): Boolean {
        if (keyX.length != 64 || keyY.length != 64 || signature.size != 64) return false
        val open = GetProcAddress(bcrypt, "BCryptOpenAlgorithmProvider")!!.reinterpret<CFunction<(CPointer<COpaquePointerVar>?, COpaquePointer?, COpaquePointer?, UInt) -> Int>>()
        val import = GetProcAddress(bcrypt, "BCryptImportKeyPair")!!.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, CPointer<COpaquePointerVar>?, CPointer<ByteVar>?, UInt, UInt) -> Int>>()
        val verifySignature = GetProcAddress(bcrypt, "BCryptVerifySignature")!!.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?, CPointer<ByteVar>?, UInt, CPointer<ByteVar>?, UInt, UInt) -> Int>>()
        val destroyKey = GetProcAddress(bcrypt, "BCryptDestroyKey")!!.reinterpret<CFunction<(COpaquePointer?) -> Int>>()
        val closeProvider = GetProcAddress(bcrypt, "BCryptCloseAlgorithmProvider")!!.reinterpret<CFunction<(COpaquePointer?, UInt) -> Int>>()
        val hash = Sha256.digest(data)
        return memScoped {
            val provider = alloc<COpaquePointerVar>()
            if (open(provider.ptr, "ECDSA_P256".wcstr.ptr, null, 0u) != 0) return@memScoped false
            try {
                // BCRYPT_ECCKEY_BLOB: the magic 'ECS1' (a public key for ECDSA P-256) and the size of a coordinate, then X and Y.
                val blob = allocArray<ByteVar>(8 + 64)
                val header = byteArrayOf(0x45, 0x43, 0x53, 0x31, 32, 0, 0, 0)
                for (i in header.indices) blob[i] = header[i]
                val xy = hexBytes(keyX) + hexBytes(keyY)
                for (i in xy.indices) blob[8 + i] = xy[i]
                val key = alloc<COpaquePointerVar>()
                if (import(provider.value, null, "ECCPUBLICBLOB".wcstr.ptr, key.ptr, blob, 72u, 0u) != 0) return@memScoped false
                val h = allocArray<ByteVar>(32)
                for (i in 0 until 32) h[i] = hash[i]
                val s = allocArray<ByteVar>(64)
                for (i in 0 until 64) s[i] = signature[i]
                val ok = verifySignature(key.value, null, h, 32u, s, 64u, 0u) == 0
                destroyKey(key.value)
                ok
            } finally {
                closeProvider(provider.value, 0u)
            }
        }
    }

    /** The program's own file path. */
    fun programPath(): String = memScoped {
        val buffer = allocArray<WCHARVar>(1024)
        GetModuleFileNameW(null, buffer, 1024u)
        buffer.toKString()
    }

    private fun write(path: String, bytes: ByteArray) {
        SystemFileSystem.sink(Path(path)).buffered().use { it.write(bytes) }
    }

    /** Removes what an update left beside the program: the old copy of itself. Call when the program starts. */
    fun cleanUp() {
        val exe = programPath()
        DeleteFileW("$exe.old")
        DeleteFileW("$exe.new")
    }

    /**
     * Downloads [programUrl] and its signature, checks the signature, replaces the running program and starts the new one. Returns why it did not
     * (a text for the screen), or does not return. The settings file beside the program is never touched.
     */
    fun install(programUrl: String, signatureUrl: String?): String? {
        if (!UpdateKey.present) return "no key"
        if (signatureUrl == null) return "no signature"
        val program = get(programUrl) ?: return "download failed"
        val signature = get(signatureUrl) ?: return "signature download failed"
        if (!verify(program, signature)) return "signature does not match"
        val exe = programPath()
        val fresh = "$exe.new"
        val old = "$exe.old"
        write(fresh, program)
        DeleteFileW(old)
        // A running program's file can be renamed: the old copy moves aside, the new one takes its name.
        if (MoveFileExW(exe, old, 1u /* replace existing */) == 0) return "could not move the program aside"
        if (MoveFileExW(fresh, exe, 1u) == 0) {
            MoveFileExW(old, exe, 1u)
            return "could not put the new program in place"
        }
        memScoped {
            val startup = alloc<STARTUPINFOW>()
            startup.cb = sizeOf<STARTUPINFOW>().convert()
            val info = alloc<PROCESS_INFORMATION>()
            if (CreateProcessW(exe, "\"$exe\" --restarted".wcstr.ptr, null, null, 0, 0u, null, null, startup.ptr, info.ptr) != 0) {
                CloseHandle(info.hProcess)
                CloseHandle(info.hThread)
            }
        }
        return null
    }
}

/** Removes what an update left beside the program (its old copy). Called when the program starts; after an update the old copy needs a moment to end. */
fun afterUpdateCleanUp(restarted: Boolean) {
    if (restarted) platform.windows.Sleep(1500u)
    SelfUpdate.cleanUp()
}
