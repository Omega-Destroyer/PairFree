package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModel
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyAdvert

/**
 * A Bluetooth LE device a channel can be opened to: the earbuds' classic address where their LE side answers on it
 * ([Transport.at]), or the device a scan found.
 */
interface LeTarget {
    val address: String
}

/** A command service over GATT: its UUID, the characteristic the device notifies on, the one PairFree writes to, and the MTU asked for. */
class GattUuids(val service: String, val notify: String, val write: String, val mtu: Int) {
    companion object {
        /** Harman's command service ("excelpoint.com" in ASCII) and its characteristics, from JBL's app (Constants), with the MTU it asks for. */
        val HARMAN = GattUuids(
            "65786365-6c70-6f69-6e74-2e636f6d0000",
            "65786365-6c70-6f69-6e74-2e636f6d0001",
            "65786365-6c70-6f69-6e74-2e636f6d0002",
            512,
        )

        /** Airoha's service on JBL's Airoha models (data/jbl/commands.json), with the MTU Airoha's SDK asks for (GattLinkParam). */
        val AIROHA = GattUuids(JblRace.GATT_SERVICE, JblRace.GATT_NOTIFY, JblRace.GATT_WRITE, 517)
    }
}

/** Sennheiser's Bluetooth LE bonding: the characteristic its pairing steps are written to, and the encrypted one whose read provokes the bond. */
class GattPairing(val characteristic: String, val encrypted: String)

/**
 * How a GATT command channel opens. [rawNotify]: JBL's protocol 4 security modes 1 and 3 also subscribe the raw
 * characteristic (docs/jbl/PROTOCOL.md section 7). [rawPreamble]: mode 3 writes this frame (the host name's checksum) to
 * the raw characteristic before the main subscription. [pairing]: Sennheiser's bonding steps, for a device that is not
 * bonded yet (docs/sennheiser/PROTOCOL.md section 6); null for every other brand.
 */
class GattSpec(
    val uuids: GattUuids = GattUuids.HARMAN,
    val rawNotify: Boolean = false,
    val rawPreamble: ByteArray? = null,
    val pairing: GattPairing? = null,
)

/** A GATT command channel, where each notification is one frame. */
interface GattChannel : ControlLink {
    /** The MTU both sides agreed on; 23 (the least) until they do. */
    val mtu: Int
}

/** QCY earbuds as a scan found them: their LE side, and the advert that names the model. */
class QcyFound(val target: LeTarget, val advert: QcyAdvert)

/**
 * The Bluetooth of the system PairFree runs on, as the brands open their channels through it ([BrandSpec.open]): Android's
 * in the app, Windows' and macOS' in the desktop apps (docs/DESKTOP.md). A brand's file decides which channel its earbuds
 * need and in what order to try; this makes the channel. A channel is returned unopened: the caller holds it
 * ([OpenEnv.hold]) and then connects, so a disconnect can abort it. Opening fails with an IOException.
 */
interface Transport {
    /** True when Bluetooth LE channels can be opened at all. */
    val hasLe: Boolean

    /** True when the user has allowed scanning for LE devices. */
    val mayScan: Boolean

    /** True when the system's Bluetooth stack opens the AirPods' channel (docs/apple/PROTOCOL.md section 7). */
    val opensAppleChannel: Boolean

    /** True when the device paired at [address] is an LE-only device (JBL's Airoha models paired over LE Audio). */
    fun isLeOnly(address: String): Boolean

    /** This phone's or computer's own Bluetooth name, or null where it may not be read. */
    fun hostName(): String?

    /** The LE side of the device at its classic [address]. */
    fun at(address: String): LeTarget

    /** The classic serial channel (RFCOMM) to the service [uuid] of the device at [address]. */
    fun serial(address: String, uuid: String, log: (String) -> Unit): ControlLink

    fun gatt(target: LeTarget, spec: GattSpec, log: (String) -> Unit): GattChannel

    /** QCY's kind of channel: a service whose characteristics are each read, written and notified on their own ([io.github.omegadestroyer.pairfree.protocol.GattEnvelope]). */
    fun envelopeGatt(target: LeTarget, service: String, log: (String) -> Unit): ControlLink

    /** The AirPods' channel (classic L2CAP), where [opensAppleChannel]. */
    fun appleChannel(address: String, log: (String) -> Unit): ControlLink

    /** Scans for the LE side of a JBL [model] paired at [address] (docs/jbl/PROTOCOL.md section 3); null when it is not found. */
    suspend fun findJbl(model: JblModel, address: String, log: (String) -> Unit): LeTarget?

    /** Scans for the advert of QCY earbuds paired at [address], sent under [company]'s id (docs/qcy/PROTOCOL.md section 2). */
    suspend fun findQcy(company: Int, address: String, log: (String) -> Unit): QcyFound?

    /** Scans for a Sennheiser product paired at [address] that advertises one of [names] (docs/sennheiser/PROTOCOL.md section 6). */
    suspend fun findSennheiser(names: List<String>, address: String, log: (String) -> Unit): LeTarget?
}
