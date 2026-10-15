package io.github.omegadestroyer.pairfree.status

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Model
import java.util.UUID
import java.util.concurrent.Executor

/**
 * Which battery notification the user wants for a pair of earbuds (docs/DESIGN.md,
 * Notification): PairFree's own, Google's Fast Pair notification (offered only on earbuds that
 * have Fast Pair), or none. PairFree cannot hide Google's notification; on earbuds with Fast
 * Pair it can only open the settings where the user turns it off.
 */
enum class StatusChoice { PAIRFREE, FAST_PAIR, NONE }

/**
 * The Device tab's view of the choice for the connected earbuds at [address]: the [options] to
 * offer, the [choice], whether they have Fast Pair, and what Android has granted.
 */
data class StatusUi(
    val address: String,
    val options: List<StatusChoice>,
    val choice: StatusChoice,
    val fastPair: Boolean,
    val canNotify: Boolean,
    val linked: Boolean,
)

/** What the notification needs from Android, asked once by itself the first time earbuds connect. */
enum class StatusPrompt { NOTIFICATIONS, LINK }

/** The choice per pair of earbuds, and what PairFree has learnt about their Fast Pair. */
class StatusSettings(private val prefs: SharedPreferences) {
    /**
     * Whether the earbuds have Google Fast Pair: from the model's data where an owner confirmed
     * it, else from [FastPairCheck] on these earbuds, else null while not known.
     */
    fun fastPair(device: Paired, model: Model?): Boolean? =
        model?.fastPair ?: if (prefs.contains(KEY_FAST_PAIR + device.address)) prefs.getBoolean(KEY_FAST_PAIR + device.address, false) else null

    fun detected(address: String, fastPair: Boolean) = prefs.edit { putBoolean(KEY_FAST_PAIR + address, fastPair) }

    /**
     * The user's choice, or the default: PairFree's notification, except on earbuds with Fast
     * Pair, which keep Google's until the user chooses (owner, 2026-10-14). Fast Pair chosen
     * for earbuds without it counts as none.
     */
    fun choice(device: Paired, fastPair: Boolean?): StatusChoice {
        val stored = prefs.getString(KEY_CHOICE + device.address, null)?.let { s -> StatusChoice.entries.firstOrNull { it.name == s } }
        val choice = stored ?: if (fastPair == true) StatusChoice.FAST_PAIR else StatusChoice.PAIRFREE
        return if (choice == StatusChoice.FAST_PAIR && fastPair != true) StatusChoice.NONE else choice
    }

    fun choose(address: String, choice: StatusChoice) = prefs.edit { putString(KEY_CHOICE + address, choice.name) }

    /** The choices to offer: Fast Pair only on earbuds that have it. */
    fun options(fastPair: Boolean?): List<StatusChoice> =
        if (fastPair == true) StatusChoice.entries else listOf(StatusChoice.PAIRFREE, StatusChoice.NONE)

    /** True once PairFree has asked for what the notification needs for these earbuds, so it asks only once by itself. */
    fun asked(address: String): Boolean = prefs.getBoolean(KEY_ASKED + address, false)

    fun markAsked(address: String) = prefs.edit { putBoolean(KEY_ASKED + address, true) }

    private companion object {
        const val KEY_CHOICE = "status_choice_"
        const val KEY_FAST_PAIR = "fast_pair_"
        const val KEY_ASKED = "status_asked_"
    }
}

/**
 * Looks for Google's Fast Pair service on earbuds: a short Bluetooth LE connection to their
 * address and a list of their services. Earbuds that use another address for LE are not
 * reached; the result is then null (not known) and nothing is stored.
 */
object FastPairCheck {
    private val SERVICE: UUID = UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")
    private const val TIMEOUT_MS = 15_000L

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun run(context: Context, device: BluetoothDevice, onResult: (Boolean?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        var done = false
        var gatt: BluetoothGatt? = null
        fun finish(result: Boolean?) {
            main.post {
                if (done) return@post
                done = true
                try {
                    gatt?.disconnect()
                    gatt?.close()
                } catch (_: SecurityException) {
                    // The permission was taken back meanwhile; nothing to close then.
                }
                onResult(result)
            }
        }
        val callback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission") // granted when the check started
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> if (!g.discoverServices()) finish(null)
                    newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS -> finish(null)
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                finish(if (status == BluetoothGatt.GATT_SUCCESS) g.getService(SERVICE) != null else null)
            }
        }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) finish(null) else main.postDelayed({ finish(null) }, TIMEOUT_MS)
    }
}

/**
 * The companion-device link Android asks for, once per pair of earbuds, before an app may start
 * its notification when they connect while the app is closed.
 */
object CompanionLink {
    fun isAssociated(context: Context, address: String): Boolean {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            cdm.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
        } else {
            @Suppress("DEPRECATION")
            cdm.associations.any { it.equals(address, ignoreCase = true) }
        }
    }

    /** Asks Android to link PairFree to the earbuds at [address]; [onPrompt] gets the system prompt to show. */
    fun associate(context: Context, address: String, onPrompt: (IntentSender) -> Unit, onFailure: () -> Unit) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return onFailure()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val main = Executor { Handler(Looper.getMainLooper()).post(it) }
            cdm.associate(
                request,
                main,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) = onPrompt(intentSender)
                    override fun onAssociationCreated(associationInfo: AssociationInfo) = Unit
                    override fun onFailure(error: CharSequence?) = onFailure()
                },
            )
        } else {
            @Suppress("DEPRECATION")
            cdm.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    @Deprecated("Deprecated in Java")
                    override fun onDeviceFound(chooserLauncher: IntentSender) = onPrompt(chooserLauncher)
                    override fun onFailure(error: CharSequence?) = onFailure()
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }
}

/** True when the app may post notifications (always before Android 13). */
fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
