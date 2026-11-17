package io.github.omegadestroyer.pairfree.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.github.omegadestroyer.pairfree.brands.GattChannel
import io.github.omegadestroyer.pairfree.brands.GattSpec
import io.github.omegadestroyer.pairfree.brands.LeTarget
import io.github.omegadestroyer.pairfree.brands.QcyFound
import io.github.omegadestroyer.pairfree.brands.Transport
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import java.io.IOException
import java.util.UUID

/**
 * Android's Bluetooth as the brands open their channels through it: each call makes the link class that did the same
 * before the brands' files stopped naming Android's types ([SppLink], [GattLink], [EnvelopeGattLink], [AppleLink]) or
 * runs the same finder. [context] opens LE connections; without one there is no LE.
 */
@SuppressLint("MissingPermission") // only used while connecting, which needs the permission; the scan's is asked through mayScan
class AndroidTransport(private val context: Context?, private val adapter: BluetoothAdapter?) : Transport {
    private class Target(val device: BluetoothDevice) : LeTarget {
        override val address: String get() = device.address
    }

    private fun remote(address: String): BluetoothDevice = adapter?.getRemoteDevice(address) ?: throw IOException("no Bluetooth adapter")

    private fun deviceOf(target: LeTarget): BluetoothDevice = (target as? Target)?.device ?: remote(target.address)

    private fun contextForLe(): Context = context ?: throw IOException("no context for Bluetooth LE")

    override val isOn: Boolean get() = adapter?.isEnabled == true

    override fun notPermitted(failure: Throwable): Boolean = failure is SecurityException

    override fun hiddenBySystem(failure: Throwable): Boolean = failure is ConstructorsHidden

    override fun deviceFacts(address: String): List<Pair<String, String>> {
        val remote = adapter?.getRemoteDevice(address)
        fun known(read: () -> String): String = try { read() } catch (e: SecurityException) { "not allowed to read" }
        val type = remote?.let { r ->
            known {
                when (r.type) {
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
                    BluetoothDevice.DEVICE_TYPE_LE -> "LE only"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "classic and LE"
                    else -> "unknown"
                }
            }
        } ?: "unknown"
        val bond = remote?.let { r ->
            known {
                when (r.bondState) {
                    BluetoothDevice.BOND_BONDED -> "paired"
                    BluetoothDevice.BOND_BONDING -> "pairing"
                    else -> "not paired"
                }
            }
        } ?: "unknown"
        val services = remote?.let { r -> known { r.uuids?.joinToString(", ") { it.toString() } ?: "none cached" } } ?: "unknown"
        return listOf("Device" to "$type, $bond", "Its services" to services)
    }

    override val hasLe: Boolean get() = context != null

    override val mayScan: Boolean
        get() = context != null && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    override val opensAppleChannel: Boolean get() = AppleSupport.opensChannel()

    override fun isLeOnly(address: String): Boolean = remote(address).type == BluetoothDevice.DEVICE_TYPE_LE

    override fun hostName(): String? = try {
        adapter?.name
    } catch (_: SecurityException) {
        null
    }

    override fun at(address: String): LeTarget = Target(remote(address))

    override fun serial(address: String, uuid: String, log: (String) -> Unit): ControlLink = SppLink(remote(address), log, uuid)

    override fun gatt(target: LeTarget, spec: GattSpec, log: (String) -> Unit): GattChannel = GattLink(
        contextForLe(),
        deviceOf(target),
        log,
        rawNotify = spec.rawNotify,
        rawPreamble = spec.rawPreamble,
        uuids = GattLink.Uuids(UUID.fromString(spec.uuids.service), UUID.fromString(spec.uuids.notify), UUID.fromString(spec.uuids.write), spec.uuids.mtu),
        pairing = spec.pairing?.let { GattLink.Pairing(UUID.fromString(it.characteristic), UUID.fromString(it.encrypted)) },
    )

    override fun envelopeGatt(target: LeTarget, service: String, log: (String) -> Unit): ControlLink =
        EnvelopeGattLink(contextForLe(), deviceOf(target), UUID.fromString(service), log)

    override fun appleChannel(address: String, log: (String) -> Unit): ControlLink =
        AppleLink(adapter ?: throw IOException("no Bluetooth adapter"), remote(address), log)

    override suspend fun findJbl(model: JblModel, address: String, log: (String) -> Unit): LeTarget? =
        adapter?.let { JblFinder(it, log).find(model, address) }?.let(::Target)

    override suspend fun findQcy(company: Int, address: String, log: (String) -> Unit): QcyFound? =
        adapter?.let { QcyFinder(it, log, company).find(address) }?.let { QcyFound(Target(it.device), it.advert) }

    override suspend fun findSennheiser(names: List<String>, address: String, log: (String) -> Unit): LeTarget? =
        adapter?.let { SennheiserFinder(it, log).find(names, address) }?.let(::Target)
}
