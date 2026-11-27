package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The VR P10's USB dongles, managed through the earbuds (docs/soundcore/PROTOCOL.md, The VR P10's dongle), from the Soundcore app 5.0.35; not tried.
 *
 * `0F 01` answers with a count and then 33 bytes each: the name (20, padded with zeros), 1 when connected, what it is plugged into (0 to 5), the
 * firmware (5 characters) and the address (6). `0F 02` answers with the low power mode. Renaming a dongle is left out.
 */
public class SoundcoreDongle {
    private class Dongle(val name: String, val connected: Boolean, val type: Int, val mac: ByteArray)

    private var dongles: List<Dongle> = emptyList()
    private var lowPower: Boolean? = null

    public fun receive(command: Int, body: ByteArray): Boolean {
        when (command) {
            SoundcoreCommand.DONGLE_INFO -> {
                if (body.isEmpty()) return false
                val count = body[0].toInt() and 0xFF
                if (body.size < 1 + count * RECORD) return false
                dongles = (0 until count).map { i ->
                    val at = 1 + i * RECORD
                    val raw = body.copyOfRange(at, at + NAME)
                    val name = raw.copyOfRange(0, raw.indexOf(0).let { if (it < 0) raw.size else it }).decodeToString().trim()
                    Dongle(name, body[at + NAME].toInt() == 1, body[at + NAME + 1].toInt() and 0xFF, body.copyOfRange(at + RECORD - MAC, at + RECORD))
                }
            }
            SoundcoreCommand.DONGLE_LOW_POWER_STATE -> {
                if (body.isEmpty()) return false
                lowPower = body[0].toInt() == 1
            }
            else -> return false
        }
        return true
    }

    private fun label(d: Dongle): String {
        val tail = d.mac.takeLast(2).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
        return if (d.name.isEmpty() || dongles.count { it.name == d.name } > 1) "${d.name.ifEmpty { "??" }} ($tail)" else d.name
    }

    public fun rows(): List<Setting> = buildList {
        val off = dongles.filter { !it.connected }.map(::label)
        val on = dongles.filter { it.connected }
        if (off.isNotEmpty()) add(Setting.Choice(CONNECT, null, off))
        if (on.isNotEmpty()) add(Setting.Action(DISCONNECT))
        if (dongles.isNotEmpty()) add(Setting.Choice(REMOVE, null, dongles.map(::label)))
        on.firstOrNull()?.let { add(Setting.Choice(TYPE, TYPES.getOrNull(it.type), TYPES)) }
        lowPower?.let { add(Setting.Toggle(LOW_POWER, it)) }
    }

    public fun change(change: SettingChange): List<SoundcorePacket>? = when {
        change is SettingChange.Press && change.key == DISCONNECT && dongles.any { it.connected } ->
            listOf(SoundcorePacket(SoundcoreCommand.DONGLE_DISCONNECT, ByteArray(0)))
        change is SettingChange.Toggle && change.key == LOW_POWER && lowPower != null -> {
            lowPower = change.on
            listOf(SoundcorePacket(SoundcoreCommand.DONGLE_LOW_POWER, byteArrayOf(if (change.on) 1 else 0)))
        }
        change is SettingChange.Choice && change.key == TYPE -> TYPES.indexOf(change.option)
            .takeIf { i -> i >= 0 && dongles.any { it.connected } }
            ?.let { listOf(SoundcorePacket(SoundcoreCommand.DONGLE_TYPE, byteArrayOf(it.toByte()))) }
        change is SettingChange.Choice && (change.key == CONNECT || change.key == REMOVE) -> {
            val target = dongles.firstOrNull { label(it) == change.option }
            if (target == null) null else {
                val command = if (change.key == CONNECT) SoundcoreCommand.DONGLE_CONNECT else SoundcoreCommand.DONGLE_REMOVE
                if (change.key == REMOVE) dongles = dongles - target
                listOf(SoundcorePacket(command, target.mac.copyOf()))
            }
        }
        else -> null
    }

    public companion object {
        public const val FEATURE: String = "dongle"
        public const val CONNECT: String = "sc_dongle_connect"
        public const val DISCONNECT: String = "sc_dongle_disconnect"
        public const val REMOVE: String = "sc_dongle_remove"
        public const val TYPE: String = "sc_dongle_type"
        public const val LOW_POWER: String = "sc_dongle_low_power"
        private val TYPES = listOf("oculus", "playstation", "pc", "switch", "phone", "other")
        private const val NAME = 20
        private const val MAC = 6
        private const val RECORD = 33
    }
}
