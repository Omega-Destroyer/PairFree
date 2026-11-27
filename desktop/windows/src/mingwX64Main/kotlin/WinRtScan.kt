@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.omegadestroyer.pairfree.windows

import io.github.omegadestroyer.pairfree.brands.LeTarget
import io.github.omegadestroyer.pairfree.brands.QcyFound
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAdvert
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyAdvert
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** One advertisement a scan saw: who sent it and what it carries. */
internal class Advert(
    val address: String,
    val name: String,
    /** Manufacturer data by company id. */
    val manufacturer: Map<Int, ByteArray>,
    val serviceUuids: List<String>,
    /** The data of 16-bit service data sections by the service's short UUID (4 hex digits). */
    val serviceData: Map<String, ByteArray>,
)

/** Windows' Bluetooth LE advertisement watcher, used by the brands' finders (what an Android scan does). */
internal object LeScan {
    private const val WATCHER = "Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementWatcher"

    private fun addressText(n: ULong): String = (5 downTo 0).joinToString(":") { ((n shr (8 * it)) and 0xFFu).toString(16).uppercase().padStart(2, '0') }

    private fun parse(args: Com): Advert? {
        val address = addressText(WinRt.ulong(args, 7))
        val adv = WinRt.obj(args, 10) ?: return null
        val name = WinRt.string(adv, 8)
        val uuids = mutableListOf<String>()
        WinRt.obj(adv, 10)?.let { vector ->
            memScoped {
                val size = alloc<UIntVar>()
                comSlot(vector, 7).reinterpret<CFunction<(Com?, CPointer<UIntVar>?) -> Int>>()(vector, size.ptr)
                for (i in 0u until size.value) {
                    val g = allocArray<ByteVar>(16)
                    comSlot(vector, 6).reinterpret<CFunction<(Com?, UInt, CPointer<ByteVar>?) -> Int>>()(vector, i, g)
                    uuids += WinRt.guidText(ByteArray(16) { g[it] })
                }
            }
            comRelease(vector)
        }
        val manufacturer = mutableMapOf<Int, ByteArray>()
        WinRt.obj(adv, 11)?.let { vector ->
            for (m in WinRt.elements(vector)) {
                val buffer = WinRt.obj(m, 8)
                manufacturer[WinRt.ushort(m, 6)] = WinRt.bytes(buffer)
                comRelease(buffer)
                comRelease(m)
            }
            comRelease(vector)
        }
        val serviceData = mutableMapOf<String, ByteArray>()
        WinRt.obj(adv, 12)?.let { vector ->
            for (section in WinRt.elements(vector)) {
                // Type 0x16 is service data with a 16-bit UUID: the UUID first (little endian), then the data.
                val type = memScoped {
                    val b = alloc<ByteVar>()
                    comSlot(section, 6).reinterpret<CFunction<(Com?, CPointer<ByteVar>?) -> Int>>()(section, b.ptr)
                    b.value.toInt() and 0xFF
                }
                if (type == 0x16) {
                    val buffer = WinRt.obj(section, 8)
                    val bytes = WinRt.bytes(buffer)
                    comRelease(buffer)
                    if (bytes.size >= 2) serviceData[((bytes[1].toInt() and 0xFF).toString(16).padStart(2, '0') + (bytes[0].toInt() and 0xFF).toString(16).padStart(2, '0'))] = bytes.copyOfRange(2, bytes.size)
                }
                comRelease(section)
            }
            comRelease(vector)
        }
        comRelease(adv)
        return Advert(address, name, manufacturer, uuids, serviceData)
    }

    /**
     * Scans for up to [timeoutMs] and gives each advertisement to [onAdvert], which returns true to end the scan. Returns false when the
     * scan could not start (no Bluetooth LE radio, or the system refused).
     */
    suspend fun scan(timeoutMs: Long, log: (String) -> Unit, onAdvert: (Advert) -> Boolean): Boolean {
        val created = WinRt.create(WATCHER)
        // RoActivateInstance hands back an IInspectable; the watcher's own interface is asked for by its id.
        val watcher = created?.let { comQuery(it, "a6ac336f-f3d3-4297-8d6c-c81ea6623f40") }
        comRelease(created)
        if (watcher == null) {
            log("scan failed: no advertisement watcher")
            return false
        }
        // Active scanning also asks for the scan response that carries a device's name.
        comSlot(watcher, 12).reinterpret<CFunction<(Com?, Int) -> Int>>()(watcher, 1)
        val done = CompletableDeferred<Unit>()
        val handler = ComCallback("90eb4eca-d465-5ea0-a61c-033c8c5ecef2") { _, args ->
            if (args != null && !done.isCompleted) {
                val advert = parse(args)
                if (advert != null && onAdvert(advert)) done.complete(Unit)
            }
        }
        val token = memScoped {
            val t = alloc<LongVar>()
            val added = comSlot(watcher, 19).reinterpret<CFunction<(Com?, Com?, CPointer<LongVar>?) -> Int>>()(watcher, handler.pointer, t.ptr)
            log("add_Received: 0x${added.toUInt().toString(16)}")
            t.value
        }
        val hr = comSlot(watcher, 17).reinterpret<CFunction<(Com?) -> Int>>()(watcher)
        if (hr != 0) log("scan failed: 0x${hr.toUInt().toString(16)}")
        try {
            if (hr == 0) withTimeoutOrNull(timeoutMs) { done.await() }
        } finally {
            comSlot(watcher, 18).reinterpret<CFunction<(Com?) -> Int>>()(watcher)
            comSlot(watcher, 20).reinterpret<CFunction<(Com?, Long) -> Int>>()(watcher, token)
            comRelease(watcher)
        }
        return hr == 0
    }

    private const val SCAN_MS = 10_000L
    private const val HARMAN_SERVICE = "fddf"
    private const val SAME_ADDRESS = 3
    private const val ADDRESS_CRC = 2

    /** The LE side of paired JBL earbuds, when they do not answer over LE on their classic address (docs/jbl/PROTOCOL.md section 3). */
    suspend fun findJbl(model: JblModel, address: String, hostName: String?, log: (String) -> Unit): LeTarget? {
        var sure: String? = null
        val byName = mutableMapOf<String, String>()
        log("scanning for ${model.name}")
        scan(SCAN_MS, log) { a ->
            val data = a.manufacturer[JblAdvert.COMPANY] ?: a.serviceData[HARMAN_SERVICE] ?: return@scan false
            val score = if (a.address.equals(address, ignoreCase = true) && JblAdvert.pid(data)?.let { it in model.pids } == true) SAME_ADDRESS
            else JblAdvert.score(data, model, address, hostName)
            when {
                score >= ADDRESS_CRC -> { sure = a.address; true }
                score > 0 -> { byName[a.address] = a.address; false }
                else -> false
            }
        }
        // Only the computer's name matched: take it when exactly one device did, never a guess between several.
        val found = sure ?: byName.values.singleOrNull()
        log("scan found ${if (found == null) "nothing" else "the earbuds"}")
        return found?.let { WinLeTarget(it) }
    }

    /**
     * The LE side of paired QCY earbuds: the advert under QCY's company id that carries the paired address (docs/qcy/PROTOCOL.md section 2).
     * As in QCY's app, an advert with the batteries wins; one without ([QcyAdvert.hasBattery]) is taken only when no other came.
     */
    suspend fun findQcy(company: Int, address: String, log: (String) -> Unit): QcyFound? {
        var found: QcyFound? = null
        var withoutBattery: QcyFound? = null
        val senders = mutableSetOf<String>()
        log("scanning for QCY earbuds")
        scan(SCAN_MS, log) { a ->
            val advert = a.manufacturer[company]?.let(QcyAdvert::parse) ?: return@scan false
            if (!advert.matches(address)) return@scan false
            if (senders.add(a.address) && !advert.hasBattery) log("advert without batteries (earbuds in their case?), looking for another")
            when {
                advert.hasBattery -> { found = QcyFound(WinLeTarget(a.address), advert); true }
                else -> { if (withoutBattery == null) withoutBattery = QcyFound(WinLeTarget(a.address), advert); false }
            }
        }
        val result = found ?: withoutBattery
        log(
            if (result == null) "scan found nothing"
            else "scan found vendor id ${result.advert.vendorId}, ${if (result.advert.hasBattery) "with" else "without"} batteries (${senders.size} sender${if (senders.size == 1) "" else "s"} of its adverts)",
        )
        return result
    }

    private val SENNHEISER_SERVICES = setOf("0000fdce-0000-1000-8000-00805f9b34fb", "0000fcfe-0000-1000-8000-00805f9b34fb", "63331337-23c1-11e5-b696-feff819cdc9f")

    /** A Sennheiser product paired at [address]: the device with the classic address itself, else the one whose advertised name is one of the model's. */
    suspend fun findSennheiser(names: List<String>, address: String, log: (String) -> Unit): LeTarget? {
        var sure: String? = null
        val byName = mutableMapOf<String, String>()
        scan(SCAN_MS, log) { a ->
            if (a.serviceUuids.none { it.lowercase() in SENNHEISER_SERVICES }) return@scan false
            when {
                a.address.equals(address, ignoreCase = true) -> { sure = a.address; true }
                names.any { it.equals(a.name, ignoreCase = true) } -> { byName[a.address] = a.address; false }
                else -> false
            }
        }
        val found = sure ?: byName.values.singleOrNull()
        log("scan found ${if (found == null) "nothing" else "the product"}")
        return found?.let { WinLeTarget(it) }
    }

    /** A Skullcandy product paired at [address]: the device with the classic address itself, else the one device whose advertised name is one of the model's (docs/skullcandy/PROTOCOL.md section 17). */
    suspend fun findSkullcandy(names: List<String>, address: String, log: (String) -> Unit): LeTarget? {
        var sure: String? = null
        val byName = mutableMapOf<String, String>()
        scan(SCAN_MS, log) { a ->
            when {
                a.address.equals(address, ignoreCase = true) -> { sure = a.address; true }
                names.any { it.equals(a.name, ignoreCase = true) } -> { byName[a.address] = a.address; false }
                else -> false
            }
        }
        val found = sure ?: byName.values.singleOrNull()
        log("scan found ${if (found == null) "nothing" else "the product"}")
        return found?.let { WinLeTarget(it) }
    }
}
