package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyAdvert
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyWire
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds the Bluetooth LE side of paired QCY earbuds (docs/qcy/PROTOCOL.md section 2): QCY's app scans for
 * adverts under QCY's company id, reads each one's classic addresses, and connects over LE to the device
 * whose advert carries the paired address. The advert also names the model (its vendor id). The scan lasts
 * at most [SCAN_MS] and nothing is kept. It needs BLUETOOTH_SCAN, which PairFree asks for only when the user
 * opens QCY earbuds, and declares it is never used for location.
 */
class QcyFinder(private val adapter: BluetoothAdapter, private val log: ((String) -> Unit)? = null) {
    /** The earbuds' LE side and their advert. */
    data class Found(val device: BluetoothDevice, val advert: QcyAdvert)

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    suspend fun find(address: String): Found? {
        val scanner = adapter.bluetoothLeScanner ?: return null
        val result = CompletableDeferred<Found?>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, scan: ScanResult) {
                val data = scan.scanRecord?.getManufacturerSpecificData(QcyWire.COMPANY) ?: return
                val advert = QcyAdvert.parse(data) ?: return
                if (advert.matches(address)) result.complete(Found(scan.device, advert))
            }

            override fun onScanFailed(errorCode: Int) {
                log?.invoke("scan failed: $errorCode")
                result.complete(null)
            }
        }
        val filters = listOf(ScanFilter.Builder().setManufacturerData(QcyWire.COMPANY, ByteArray(0), ByteArray(0)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        log?.invoke("scanning for QCY earbuds")
        scanner.startScan(filters, settings, callback)
        return try {
            withTimeoutOrNull(SCAN_MS) { result.await() }
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: IllegalStateException) {
                // Bluetooth went off meanwhile.
            }
        }.also { log?.invoke(if (it == null) "scan found nothing" else "scan found vendor id ${it.advert.vendorId}") }
    }

    companion object {
        const val SCAN_MS = 10_000L
    }
}
