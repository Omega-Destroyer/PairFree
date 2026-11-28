@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import platform.posix.memset
import platform.windows.CancelIoEx
import platform.windows.CloseHandle
import platform.windows.CreateEventW
import platform.windows.CreateFileW
import platform.windows.DUPLICATE_SAME_ACCESS
import platform.windows.DuplicateHandle
import platform.windows.ERROR_IO_PENDING
import platform.windows.FILE_FLAG_OVERLAPPED
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetCurrentProcess
import platform.windows.GetLastError
import platform.windows.GetOverlappedResult
import platform.windows.GetProcAddress
import platform.windows.HMODULE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LoadLibraryW
import platform.windows.OPEN_EXISTING
import platform.windows.OVERLAPPED
import platform.windows.ReadFile
import platform.windows.WAIT_OBJECT_0
import platform.windows.WAIT_TIMEOUT
import platform.windows.WaitForSingleObject
import platform.windows.WriteFile

/** The vendor HID collection on QC Ultra Headphones (2nd Gen). The other collections are media keys and audio. */
internal object BoseUsbHid {
    private const val INTERFACE_PREFIX = "\\\\?\\hid#vid_05a7&pid_4082&mi_00#"
    private const val BUFFER_SMALL = 0x1au
    private val cfg by lazy { LoadLibraryW("cfgmgr32.dll") }
    private val hid by lazy { LoadLibraryW("hid.dll") }

    private inline fun <reified F : Function<*>> entry(library: HMODULE?, name: String): CPointer<CFunction<F>>? =
        library?.let { GetProcAddress(it, name)?.reinterpret() }

    /** Never put the device path itself into settings or a report. */
    fun key(path: String): String = "usb:05a7:4082:" + path.lowercase().hashCode().toUInt().toString(16)

    fun interfaces(): List<String> {
        val size = entry<(COpaquePointer?, COpaquePointer?, COpaquePointer?, UInt) -> UInt>(cfg, "CM_Get_Device_Interface_List_SizeW")
            ?: return emptyList()
        val list = entry<(COpaquePointer?, COpaquePointer?, COpaquePointer?, UInt, UInt) -> UInt>(cfg, "CM_Get_Device_Interface_ListW")
            ?: return emptyList()
        val guid = comGuid("4d1e55b2-f16f-11cf-88cb-001111000030")
        return guid.usePinned { pinned ->
            val classId = pinned.addressOf(0)
            repeat(3) {
                val result = memScoped {
                    val length = alloc<UIntVar>()
                    if (size(length.ptr, classId, null, 0u) != 0u || length.value == 0u) return emptyList()
                    val buffer = allocArray<UShortVar>(length.value.toInt())
                    val status = list(classId, null, buffer, length.value, 0u)
                    if (status == BUFFER_SMALL) return@memScoped null
                    if (status != 0u) return emptyList()

                    val paths = mutableListOf<String>()
                    var offset = 0
                    while (offset < length.value.toInt() && buffer[offset] != 0.toUShort()) {
                        val path = (buffer + offset)!!.toKString()
                        if (path.startsWith(INTERFACE_PREFIX, ignoreCase = true)) paths += path
                        offset += path.length + 1
                    }
                    paths
                }
                if (result != null) return result
            }
            emptyList()
        }
    }

    /** HIDP_CAPS starts with usage, page, and maximum input and output sizes (including the report ID). */
    fun reportSizes(handle: COpaquePointer): Pair<Int, Int> = memScoped {
        val getData = entry<(COpaquePointer?, COpaquePointer?) -> UByte>(hid, "HidD_GetPreparsedData")
            ?: throw IOException("Windows' HID support is unavailable")
        val freeData = entry<(COpaquePointer?) -> UByte>(hid, "HidD_FreePreparsedData")
            ?: throw IOException("Windows' HID support is unavailable")
        val getCaps = entry<(COpaquePointer?, COpaquePointer?) -> Int>(hid, "HidP_GetCaps")
            ?: throw IOException("Windows' HID support is unavailable")
        val data = alloc<COpaquePointerVar>()
        if (getData(handle, data.ptr) == 0.toUByte()) throw IOException("could not read the Bose USB HID descriptor")
        try {
            val caps = allocArray<UShortVar>(32)
            if (getCaps(data.value, caps) != 0x00110000 || caps[0] != 1.toUShort() || caps[1] != 0xff00.toUShort() ||
                caps[2].toInt() < 5 || caps[3].toInt() < 7
            ) throw IOException("unsupported Bose USB HID report layout")
            caps[2].toInt() to caps[3].toInt()
        } finally {
            freeData(data.value)
        }
    }
}

/** The USB wrapper for BMAP; the driver keeps handling BMAP itself. See docs/bose/PROTOCOL.md. */
internal object BoseUsbReport {
    fun wrap(frame: ByteArray, reportSize: Int): ByteArray {
        require(frame.size in 4..259 && frame.size + 3 <= reportSize)
        val report = ByteArray(reportSize)
        report[0] = 0x0c
        report[1] = (frame.size shr 8).toByte()
        report[2] = frame.size.toByte()
        frame.copyInto(report, destinationOffset = 3)
        return report
    }

    fun unwrap(report: ByteArray, count: Int): ByteArray? {
        if (count !in 5..report.size || report[0] != 0x0d.toByte()) return null
        val end = 5 + (report[4].toInt() and 0xff)
        if (end > count) return null
        return report.copyOfRange(1, end)
    }
}

internal class BoseUsbLink(private val path: String) : ControlLink {
    private class Device(val handle: COpaquePointer, val inputSize: Int, val outputSize: Int)

    private val state = Mutex()
    private var device: Device? = null
    private var closed = false

    override suspend fun connect(): Unit = withContext(Dispatchers.IO) {
        val handle = CreateFileW(path, GENERIC_READ.toUInt() or GENERIC_WRITE.toUInt(), FILE_SHARE_READ.toUInt() or FILE_SHARE_WRITE.toUInt(),
            null, OPEN_EXISTING.toUInt(), FILE_FLAG_OVERLAPPED.toUInt(), null)
        if (handle == null || handle == INVALID_HANDLE_VALUE) throw IOException("could not open the Bose USB headset (Windows error ${GetLastError()})")
        var installed = false
        try {
            val (inputSize, outputSize) = BoseUsbHid.reportSizes(handle)
            state.withLock {
                if (closed || device != null) throw IOException("the Bose USB channel is already opened or closed")
                device = Device(handle, inputSize, outputSize)
                installed = true
            }
        } finally {
            if (!installed) CloseHandle(handle)
        }
    }

    override suspend fun send(frame: ByteArray): Unit = withContext(Dispatchers.IO) {
        val opened = duplicate()
        try {
            val output = BoseUsbReport.wrap(frame, opened.outputSize)
            val sent = withTimeoutOrNull(2_000) { transfer(opened.handle, output, write = true) }
            if (sent != output.size) throw IOException("could not send a complete Bose USB report")
        } finally {
            CloseHandle(opened.handle)
        }
    }

    override fun reads(): Flow<ByteArray> = flow {
        val opened = duplicate()
        try {
            val input = ByteArray(opened.inputSize)
            while (true) {
                val count = transfer(opened.handle, input, write = false)
                if (count == 0) throw IOException("the Bose USB channel closed")
                BoseUsbReport.unwrap(input, count)?.let { emit(it) }
            }
        } finally {
            CloseHandle(opened.handle)
        }
    }.flowOn(Dispatchers.IO)

    override fun close(): Unit = runBlocking {
        state.withLock {
            closed = true
            device?.let { CloseHandle(it.handle) }
            device = null
        }
    }

    /** Hold a handle for the duration of each reader or writer even if close() runs on another thread. */
    private suspend fun duplicate(): Device = state.withLock {
        val opened = device ?: throw IOException("the Bose USB channel is closed")
        memScoped {
            val out = alloc<COpaquePointerVar>()
            val process = GetCurrentProcess()
            if (DuplicateHandle(process, opened.handle, process, out.ptr, 0u, 0, DUPLICATE_SAME_ACCESS.toUInt()) == 0)
                throw IOException("could not keep the Bose USB channel open (Windows error ${GetLastError()})")
            Device(out.value!!, opened.inputSize, opened.outputSize)
        }
    }

    private suspend fun transfer(handle: COpaquePointer, bytes: ByteArray, write: Boolean): Int = memScoped {
        val event = CreateEventW(null, 1, 0, null) ?: throw IOException("could not create a USB I/O event")
        val overlapped = alloc<OVERLAPPED>()
        memset(overlapped.ptr, 0, sizeOf<OVERLAPPED>().toULong())
        overlapped.hEvent = event
        val count = alloc<UIntVar>()
        try {
            bytes.usePinned { buffer ->
                val data = buffer.addressOf(0)
                val started = if (write) WriteFile(handle, data, bytes.size.toUInt(), count.ptr, overlapped.ptr)
                    else ReadFile(handle, data, bytes.size.toUInt(), count.ptr, overlapped.ptr)
                if (started != 0) return@memScoped count.value.toInt()
                val error = GetLastError()
                if (error != ERROR_IO_PENDING.toUInt()) throw IOException("Bose USB I/O failed (Windows error $error)")
                var pending = true
                try {
                    var wait: UInt
                    do {
                        currentCoroutineContext().ensureActive()
                        if (state.withLock { closed }) throw IOException("the Bose USB channel is closed")
                        wait = WaitForSingleObject(event, 100u)
                    } while (wait == WAIT_TIMEOUT.toUInt())
                    if (wait != WAIT_OBJECT_0.toUInt()) throw IOException("could not wait for the Bose USB report (Windows error ${GetLastError()})")
                    pending = false
                    if (GetOverlappedResult(handle, overlapped.ptr, count.ptr, 0) == 0)
                        throw IOException("Bose USB I/O failed (Windows error ${GetLastError()})")
                    count.value.toInt()
                } finally {
                    if (pending) {
                        // Windows can still access both buffers until cancellation completes.
                        CancelIoEx(handle, overlapped.ptr)
                        GetOverlappedResult(handle, overlapped.ptr, count.ptr, 1)
                    }
                }
            }
        } finally {
            CloseHandle(event)
        }
    }
}
