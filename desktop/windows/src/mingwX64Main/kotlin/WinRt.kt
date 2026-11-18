@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.wcstr
import kotlinx.coroutines.delay
import platform.windows.GetProcAddress
import platform.windows.LoadLibraryW

/**
 * The Windows Runtime's Bluetooth LE API as plain COM, with no binding library (docs/DESKTOP.md, phase 4): activation, asynchronous
 * operations, strings, buffers and the small callback objects the events need. The method slots of each interface were read from Windows'
 * own metadata (Windows.Devices.Bluetooth.winmd).
 */
internal object WinRt {
    /** Called at the stages of opening a device, for a developer chasing a crash (`--le-probe`). */
    var trace: (String) -> Unit = {}

    private val combase by lazy { LoadLibraryW("combase.dll") }

    private fun proc(name: String) = GetProcAddress(combase, name)!!

    private val createString by lazy { proc("WindowsCreateString").reinterpret<CFunction<(CPointer<UShortVar>?, UInt, CPointer<COpaquePointerVar>?) -> Int>>() }
    private val deleteString by lazy { proc("WindowsDeleteString").reinterpret<CFunction<(COpaquePointer?) -> Int>>() }
    private val rawBuffer by lazy { proc("WindowsGetStringRawBuffer").reinterpret<CFunction<(COpaquePointer?, CPointer<UIntVar>?) -> CPointer<UShortVar>?>>() }
    private val getFactory by lazy { proc("RoGetActivationFactory").reinterpret<CFunction<(COpaquePointer?, CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> Int>>() }
    private val activateInstance by lazy { proc("RoActivateInstance").reinterpret<CFunction<(COpaquePointer?, CPointer<COpaquePointerVar>?) -> Int>>() }
    private val initialize by lazy { proc("RoInitialize").reinterpret<CFunction<(Int) -> Int>>() }

    private val started: Boolean by lazy {
        // Multithreaded apartment: calls and callbacks may come from any thread. 1 is RO_INIT_MULTITHREADED; a thread already in another apartment is fine.
        initRoResult = initialize(1)
        true
    }

    /** What RoInitialize answered, for a developer (`--le-probe`). */
    var initRoResult = 0

    fun ensure() {
        started
    }

    /** Makes an HSTRING; the caller deletes it with [free]. */
    fun hstring(text: String): COpaquePointer? = memScoped {
        ensure()
        val out = alloc<COpaquePointerVar>()
        createString(text.wcstr.ptr, text.length.toUInt(), out.ptr)
        out.value
    }

    fun free(h: COpaquePointer?) {
        deleteString(h)
    }

    /** An HSTRING's text. */
    fun text(h: COpaquePointer?): String = if (h == null) "" else rawBuffer(h, null)?.toKStringFromUtf16() ?: ""

    /** The activation factory of [className] as [iid]. */
    fun factory(className: String, iid: String): Com? = memScoped {
        ensure()
        val name = hstring(className)
        val out = alloc<COpaquePointerVar>()
        comGuid(iid).usePinned { getFactory(name, it.addressOf(0), out.ptr) }
        free(name)
        out.value
    }

    /** A new instance of [className] through its default constructor. */
    fun create(className: String): Com? = memScoped {
        ensure()
        val name = hstring(className)
        val out = alloc<COpaquePointerVar>()
        activateInstance(name, out.ptr)
        free(name)
        out.value
    }

    // ---- calls by slot ----

    fun obj(o: Com, slot: Int): Com? = memScoped {
        val out = alloc<COpaquePointerVar>()
        comSlot(o, slot).reinterpret<CFunction<(Com?, CPointer<COpaquePointerVar>?) -> Int>>()(o, out.ptr)
        out.value
    }

    fun int(o: Com, slot: Int): Int = memScoped {
        val out = alloc<IntVar>()
        comSlot(o, slot).reinterpret<CFunction<(Com?, CPointer<IntVar>?) -> Int>>()(o, out.ptr)
        out.value
    }

    fun uint(o: Com, slot: Int): UInt = int(o, slot).toUInt()

    fun ulong(o: Com, slot: Int): ULong = memScoped {
        val out = alloc<LongVar>()
        comSlot(o, slot).reinterpret<CFunction<(Com?, CPointer<LongVar>?) -> Int>>()(o, out.ptr)
        out.value.toULong()
    }

    fun ushort(o: Com, slot: Int): Int = memScoped {
        val out = alloc<UShortVar>()
        comSlot(o, slot).reinterpret<CFunction<(Com?, CPointer<UShortVar>?) -> Int>>()(o, out.ptr)
        out.value.toInt()
    }

    /** A property that is an HSTRING. */
    fun string(o: Com, slot: Int): String {
        val h = obj(o, slot)
        val s = text(h)
        free(h)
        return s
    }

    /** A property that is a GUID, as lower-case text. */
    fun guid(o: Com, slot: Int): String = memScoped {
        val out = allocArray<ByteVar>(16)
        comSlot(o, slot).reinterpret<CFunction<(Com?, CPointer<ByteVar>?) -> Int>>()(o, out)
        guidText(ByteArray(16) { out[it] })
    }

    fun guidText(b: ByteArray): String {
        val order = intArrayOf(3, 2, 1, 0, 5, 4, 7, 6, 8, 9, 10, 11, 12, 13, 14, 15)
        val hex = order.joinToString("") { (b[it].toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    /** An IVectorView's elements, as objects (each to be released by the caller). */
    fun elements(vector: Com): List<Com> = memScoped {
        val size = alloc<UIntVar>()
        comSlot(vector, 7).reinterpret<CFunction<(Com?, CPointer<UIntVar>?) -> Int>>()(vector, size.ptr)
        (0u until size.value).mapNotNull { i ->
            val out = alloc<COpaquePointerVar>()
            comSlot(vector, 6).reinterpret<CFunction<(Com?, UInt, CPointer<COpaquePointerVar>?) -> Int>>()(vector, i, out.ptr)
            out.value
        }
    }

    // ---- asynchronous operations ----

    /** Waits for an IAsyncOperation (polling its IAsyncInfo) for up to [timeoutMs] and returns its status: 1 completed, 2 canceled, 3 failed, 0 still going. */
    suspend fun finish(operation: Com, timeoutMs: Long): Int {
        val info = comQuery(operation, "00000036-0000-0000-C000-000000000046") ?: return 3
        var waited = 0L
        var status = int(info, 7)
        while (status == 0 && waited < timeoutMs) {
            delay(30)
            waited += 30
            status = int(info, 7)
        }
        comRelease(info)
        return status
    }

    /** The object an operation produced, or null when it failed or timed out. The operation is released. */
    suspend fun await(operation: Com?, timeoutMs: Long = 15_000): Com? {
        if (operation == null) return null
        val status = finish(operation, timeoutMs)
        val result = if (status == 1) obj(operation, 8) else null
        comRelease(operation)
        return result
    }

    /** The number an operation produced (an enum such as a communication status), or [failed] when it failed or timed out. */
    suspend fun awaitInt(operation: Com?, failed: Int = -1, timeoutMs: Long = 15_000): Int {
        if (operation == null) return failed
        val status = finish(operation, timeoutMs)
        val result = if (status == 1) int(operation, 8) else failed
        comRelease(operation)
        return result
    }

    // ---- buffers ----

    private val cryptography by lazy { factory("Windows.Security.Cryptography.CryptographicBuffer", "320b7e22-3cb0-4cdf-8663-1d28910065eb") }
    private val taskFree by lazy { GetProcAddress(LoadLibraryW("ole32.dll"), "CoTaskMemFree")!!.reinterpret<CFunction<(COpaquePointer?) -> Unit>>() }

    /** An IBuffer holding [bytes]; the caller releases it. */
    fun buffer(bytes: ByteArray): Com? = memScoped {
        val statics = cryptography ?: return@memScoped null
        val out = alloc<COpaquePointerVar>()
        val data = allocArray<ByteVar>(maxOf(1, bytes.size))
        for (i in bytes.indices) data[i] = bytes[i]
        comSlot(statics, 9).reinterpret<CFunction<(Com?, UInt, CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> Int>>()(statics, bytes.size.toUInt(), data, out.ptr)
        out.value
    }

    /** The bytes of an IBuffer; the buffer is not released. */
    fun bytes(buffer: Com?): ByteArray {
        val statics = cryptography
        if (buffer == null || statics == null) return ByteArray(0)
        return memScoped {
            val count = alloc<UIntVar>()
            val data = alloc<COpaquePointerVar>()
            comSlot(statics, 10).reinterpret<CFunction<(Com?, Com?, CPointer<UIntVar>?, CPointer<COpaquePointerVar>?) -> Int>>()(statics, buffer, count.ptr, data.ptr)
            val p = data.value?.reinterpret<ByteVar>()
            val out = if (p == null) ByteArray(0) else ByteArray(count.value.toInt()) { p[it] }
            taskFree(data.value)
            out
        }
    }
}

/**
 * A COM object made in Kotlin to receive a WinRT event: a delegate with one method, Invoke(sender, args). It answers every interface
 * but IMarshal with itself, which is all the delegate's callers ask of it. [onInvoke] may run on any thread.
 */
internal class ComCallback(delegateIid: String, onInvoke: (Com?, Com?) -> Unit) {
    private val ref = StableRef.create(onInvoke)

    /** The object to hand to an `add_…` method. */
    val pointer: COpaquePointer

    init {
        // [vtable, the callback, a reference count]; the table is shared by every callback.
        val self = nativeHeap.allocArray<COpaquePointerVar>(5)
        val iid = nativeHeap.allocArray<ByteVar>(16)
        comGuid(delegateIid).forEachIndexed { i, b -> iid[i] = b }
        self[4] = iid
        self[0] = table
        self[1] = ref.asCPointer()
        self.reinterpret<LongVar>()[2] = 1L
        // Windows keeps a handler for the thread that raises the event, so it must be agile: the free-threaded marshaler, aggregated into this object, says so.
        self[3] = memScoped {
            val unknown = alloc<COpaquePointerVar>()
            createFreeThreadedMarshaler(self, unknown.ptr)
            unknown.value
        }
        pointer = self
    }

    companion object {
        /** IAgileObject's id, {94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90}. */
        val AGILE: ByteArray by lazy { comGuid("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90") }

        val table: CPointer<COpaquePointerVar> by lazy {
            val t = nativeHeap.allocArray<COpaquePointerVar>(4)
            t[0] = staticCFunction { self: COpaquePointer?, riid: CPointer<ByteVar>?, out: CPointer<COpaquePointerVar>? ->
                // IMarshal's id is {00000003-0000-0000-C000-000000000046}, INoMarshal's {ecc8691b-…}.
                val first = riid?.reinterpret<UIntVar>()?.get(0) ?: 0u
                val mine = self?.reinterpret<COpaquePointerVar>()?.get(4)?.reinterpret<ByteVar>()
                fun same(other: ByteArray): Boolean = riid != null && (0 until 16).all { riid[it] == other[it] }
                when {
                    first == 3u -> {
                        // IMarshal comes from the free-threaded marshaler.
                        val ftm = self?.reinterpret<COpaquePointerVar>()?.get(3)
                        if (ftm == null) {
                            out?.pointed_set(null)
                            -2147467262
                        } else {
                            comSlot(ftm, 0).reinterpret<CFunction<(Com?, CPointer<ByteVar>?, CPointer<COpaquePointerVar>?) -> Int>>()(ftm, riid, out)
                        }
                    }
                    // Only what a delegate is: IUnknown, IAgileObject and its own interface. Anything else is refused, since a caller
                    // that is told yes calls the methods it expects there.
                    first == 0u || same(AGILE) || (mine != null && riid != null && (0 until 16).all { riid[it] == mine[it] }) -> {
                        out?.pointed_set(self)
                        self?.reinterpret<LongVar>()?.let { it[2] = it[2] + 1 }
                        0
                    }
                    else -> {
                        out?.pointed_set(null)
                        -2147467262 // E_NOINTERFACE
                    }
                }
            }
            t[1] = staticCFunction { self: COpaquePointer? -> self?.reinterpret<LongVar>()?.let { it[2] = it[2] + 1; it[2].toInt() } ?: 0 }
            t[2] = staticCFunction { self: COpaquePointer? -> self?.reinterpret<LongVar>()?.let { it[2] = it[2] - 1; it[2].toInt() } ?: 0 }
            t[3] = staticCFunction { self: COpaquePointer?, sender: COpaquePointer?, args: COpaquePointer? ->
                val callback = self?.reinterpret<COpaquePointerVar>()?.get(1)?.asStableRef<(Com?, Com?) -> Unit>()?.get()
                callback?.invoke(sender, args)
                0
            }
            t
        }
    }
}

private fun qlog(text: String) {
    val f = platform.posix.fopen("qi.txt", "a")
    if (f != null) { platform.posix.fputs(text + "\n", f); platform.posix.fclose(f) }
}

private val createFreeThreadedMarshaler by lazy {
    GetProcAddress(LoadLibraryW("ole32.dll"), "CoCreateFreeThreadedMarshaler")!!.reinterpret<CFunction<(COpaquePointer?, CPointer<COpaquePointerVar>?) -> Int>>()
}

private fun CPointer<COpaquePointerVar>.pointed_set(value: COpaquePointer?) {
    this[0] = value
}
