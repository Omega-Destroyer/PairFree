package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.protocol.jbl.JblAdvert
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds the Bluetooth LE side of paired JBL earbuds when they do not answer over LE on their classic
 * address (docs/jbl/PROTOCOL.md section 3). It scans for Harman's adverts, as JBL's app does, for at most
 * [SCAN_MS], and takes the one with the model's product id that carries the classic address itself,
 * its CRC, or else the CRC of this phone's name, which the earbuds advertise while connected to it.
 * Nothing is kept: the scan ends with the search. It needs BLUETOOTH_SCAN, which PairFree asks for
 * only when the user opens a JBL model, and declares it is never used for location.
 */
class JblFinder(private val adapter: BluetoothAdapter, private val log: ((String) -> Unit)? = null) {
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    suspend fun find(model: JblModel, address: String): BluetoothDevice? {
        val scanner = adapter.bluetoothLeScanner ?: return null
        val phoneName = try {
            adapter.name
        } catch (_: SecurityException) {
            null
        }
        // The earbuds for certain, or null once the scan failed.
        val sure = CompletableDeferred<BluetoothDevice?>()
        val byName = mutableMapOf<String, BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord ?: return
                val data = record.getManufacturerSpecificData(JblAdvert.COMPANY) ?: record.getServiceData(HARMAN_SERVICE) ?: return
                val found = result.device
                val score = if (found.address.equals(address, ignoreCase = true) && JblAdvert.pid(data)?.let { it in model.pids } == true) {
                    SAME_ADDRESS
                } else {
                    JblAdvert.score(data, model, address, phoneName)
                }
                when {
                    score >= ADDRESS_CRC -> sure.complete(found)
                    score > 0 -> synchronized(byName) { byName[found.address] = found }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log?.invoke("scan failed: $errorCode")
                sure.complete(null)
            }
        }
        val filters = listOf(
            ScanFilter.Builder().setManufacturerData(JblAdvert.COMPANY, ByteArray(0), ByteArray(0)).build(),
            ScanFilter.Builder().setServiceData(HARMAN_SERVICE, ByteArray(0), ByteArray(0)).build(),
        )
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        log?.invoke("scanning for ${model.name}")
        scanner.startScan(filters, settings, callback)
        return try {
            withTimeoutOrNull(SCAN_MS) { sure.await() }
                // Only the phone's name matched: take it when exactly one device did, never a guess between several.
                ?: synchronized(byName) { byName.values.singleOrNull() }
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: IllegalStateException) {
                // Bluetooth went off meanwhile.
            }
        }.also { log?.invoke("scan found ${if (it == null) "nothing" else "the earbuds"}") }
    }

    companion object {
        /** The 16-bit service whose data some Harman devices advertise instead (FDDF), as JBL's app scans for it. */
        private val HARMAN_SERVICE: ParcelUuid = ParcelUuid.fromString("0000fddf-0000-1000-8000-00805f9b34fb")

        const val SCAN_MS = 10_000L
        private const val SAME_ADDRESS = 3
        private const val ADDRESS_CRC = 2
    }
}
