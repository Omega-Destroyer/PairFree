package io.github.omegadestroyer.pairfree.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
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

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            proxies[profile] = proxy
            waiting.remove(profile)
        }

        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
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
        if (proxies.isEmpty() || waiting.isNotEmpty()) return null
        return try {
            proxies.values.any { proxy -> proxy.connectedDevices.any { it.address == address } }
        } catch (_: SecurityException) {
            null
        }
    }

    private companion object {
        val PROFILES = buildList {
            add(BluetoothProfile.A2DP)
            add(BluetoothProfile.HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(BluetoothProfile.LE_AUDIO)
        }
    }
}
