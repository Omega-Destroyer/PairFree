package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Finds the Bluetooth LE side of the six older Qualcomm Skullcandy models (docs/skullcandy/PROTOCOL.md section 17) when it does not answer on the classic address: a scan, taking the device with that address
 * itself or, failing that, the one device whose advertised name is one of the model's. It needs BLUETOOTH_SCAN, which PairFree asks for only when the user opens such a model, and is never used for location.
 */
class SkullcandyFinder(private val adapter: BluetoothAdapter, private val log: ((String) -> Unit)? = null) {
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    suspend fun find(names: List<String>, address: String): BluetoothDevice? {
        val scanner = adapter.bluetoothLeScanner ?: return null
        val sure = CompletableDeferred<BluetoothDevice?>()
        val byName = mutableMapOf<String, BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val found = result.device
                if (found.address.equals(address, ignoreCase = true)) {
                    sure.complete(found)
                    return
                }
                val name = result.scanRecord?.deviceName ?: return
                if (names.any { it.equals(name, ignoreCase = true) }) synchronized(byName) { byName[found.address] = found }
            }

            override fun onScanFailed(errorCode: Int) {
                log?.invoke("scan failed: $errorCode")
                sure.complete(null)
            }
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        return try {
            // The same address for certain; else the one device of the model's name, never a guess between several.
            withTimeoutOrNull(SCAN_MS) { sure.await() } ?: synchronized(byName) { byName.values.singleOrNull() }
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: IllegalStateException) {
                // Bluetooth went off meanwhile.
            }
        }.also { log?.invoke("scan found ${if (it == null) "nothing" else "the product"}") }
    }

    companion object {
        const val SCAN_MS = 10_000L
    }
}
