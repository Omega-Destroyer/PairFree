package io.github.omegadestroyer.pairfree.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether paired earbuds are connected to the phone for sound (A2DP, hands-free or LE Audio), through the
 * profiles' public proxies. The status notification ends when they are not, even when Android's disconnect
 * broadcast did not reach PairFree, and PairFree does not keep trying to open a channel to earbuds that
 * are in their case.
 */
class AudioLinks(private val context: Context, private val adapter: BluetoothAdapter?) {
    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
    private val waiting = ConcurrentHashMap.newKeySet<Int>()
    private var requested = false

    /** Profiles whose proxy Android took back (its service stopped); asked for again by [connected]. */
    private val lost = ConcurrentHashMap.newKeySet<Int>()

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            waiting.remove(profile)
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            lost += profile
        }
    }

    /** Asks Android for the profiles' proxies, once; they arrive a moment later. */
    fun start() {
        if (requested || adapter == null) return
        requested = true
        for (profile in PROFILES) {
            if (adapter.getProfileProxy(context, listener, profile)) waiting += profile
        }
    }

    /**
     * True when one of the profiles has the earbuds at [address] connected, false when none has, and null
     * while that is not known: before every proxy Android promised has arrived, or without the permission.
     */
    @SuppressLint("MissingPermission") // a missing permission is a SecurityException, answered as not known
    fun connected(address: String): Boolean? {
        start()
        // A proxy Android took back is never given again unless asked for: without it the answer would stay unknown for good.
        for (profile in lost.toList()) {
            if (lost.remove(profile) && adapter?.getProfileProxy(context, listener, profile) == true) waiting += profile
        }
        if (proxies.isEmpty() || waiting.isNotEmpty()) return null
        return try {
            proxies.values.any { proxy -> proxy.connectedDevices.any { it.address == address } }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Where the phone's sound stands with the earbuds at [address]. Android can hold earbuds "connected" without playing
     * through them, or not connect them for sound at all while PairFree's own channel is open (earbuds another device is
     * using; issue #48). Anything this cannot tell for sure is [SoundLink.UNKNOWN], so the screen only speaks up when it knows.
     */
    @SuppressLint("MissingPermission")
    fun sound(address: String): SoundLink {
        val connected = connected(address) ?: return SoundLink.UNKNOWN
        val outputs = try {
            context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in BLUETOOTH_OUTPUTS }
        } catch (_: RuntimeException) {
            return SoundLink.UNKNOWN
        }
        if (outputs.any { it.address.equals(address, ignoreCase = true) }) return SoundLink.ACTIVE
        // Another Bluetooth output is there. LE Audio earbuds play under another address than the paired one, and Android may
        // hide an output's address: neither says these earbuds are not the ones playing.
        if (outputs.isNotEmpty()) {
            val leAudio = try {
                proxies.entries.any { (profile, proxy) -> profile != BluetoothProfile.A2DP && profile != BluetoothProfile.HEADSET && proxy.connectedDevices.any { it.address == address } }
            } catch (_: SecurityException) {
                true
            }
            if (leAudio || outputs.any { !BluetoothAdapter.checkBluetoothAddress(it.address.uppercase()) }) return SoundLink.UNKNOWN
        }
        return if (connected) SoundLink.CONNECTED else SoundLink.NOT_CONNECTED
    }

    /**
     * Asks Android to play through the earbuds at [address]: connect them for sound where they are not, then make them the
     * active device, for music (A2DP) and calls (hands-free). These are the calls Android's own Bluetooth settings make, and
     * what LibrePods does for AirPods (`connectAudio`); Android's Bluetooth service takes them from an app that holds the
     * Bluetooth permission (AOSP's A2dpService checks BLUETOOTH_CONNECT for both), but they are not in the public SDK, so
     * they go by name, and a phone whose maker changed them answers false. True when Android took at least one request; the
     * caller offers Bluetooth settings otherwise. Nothing is sent to the earbuds themselves.
     */
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi", "SoonBlockedPrivateApi", "PrivateApi")
    fun useForSound(address: String): Boolean {
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return false
        start()
        var taken = false
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            val proxy = proxies[profile] ?: continue
            fun ask(method: String): Boolean = try {
                proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) == true
            } catch (_: ReflectiveOperationException) {
                false
            } catch (_: RuntimeException) {
                false
            }
            val linked = try {
                proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
            } catch (_: SecurityException) {
                false
            }
            if (!linked) taken = ask("connect") || taken
            taken = ask("setActiveDevice") || taken
        }
        return taken
    }

    private companion object {
        val BLUETOOTH_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID,
        )
        val PROFILES = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
        }
    }
}

/** Whether the phone plays through a pair of earbuds ([AudioLinks.sound]). */
enum class SoundLink {
    /** Not known, or not sure enough to say. */
    UNKNOWN,

    /** Android has not connected the earbuds for sound. */
    NOT_CONNECTED,

    /** Connected for sound, but Android plays elsewhere. */
    CONNECTED,

    /** The phone plays through them. */
    ACTIVE,
}
