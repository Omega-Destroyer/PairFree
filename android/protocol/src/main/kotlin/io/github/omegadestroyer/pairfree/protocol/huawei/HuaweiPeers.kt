package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.Setting

/**
 * The hosts a model with two devices at once has paired (docs/huawei/PROTOCOL.md, Paired devices). The read answers once per host:
 * how many there are, this one's index, its address (least significant byte first), its name, whether it is connected, preferred
 * and connected automatically. The layout and the actions are LibreBuds' (c7b0833, GPL-3.0-or-later, `Multipoint`), from its
 * captures. Unpairing (action 3) is not offered. Addresses and names stay out of the device report.
 */
internal class HuaweiPeers {
    private class Host(val mac: ByteArray, val name: String?, val connected: Boolean, val preferred: Boolean, val automatic: Boolean?)

    private val pending = mutableMapOf<Int, Host>()
    private var expected = 0
    private var hosts: List<Host> = emptyList()

    /** Takes one reply. True when a whole list has arrived. */
    fun receive(packet: HuaweiPacket): Boolean {
        val count = number(packet.field(2)) ?: return false
        val index = number(packet.field(3)) ?: return false
        val mac = packet.field(4)?.takeIf { it.size == 6 } ?: return false
        if (count !in 1..MAX_HOSTS || index !in 0 until count) return false
        if (count != expected) {
            pending.clear()
            expected = count
        }
        pending[index] = Host(
            mac.copyOf(),
            packet.field(9)?.decodeToString()?.substringBefore('\u0000')?.filterNot(Char::isISOControl)?.trim()?.take(48)?.ifEmpty { null },
            (number(packet.field(5)) ?: 0) > 0,
            number(packet.field(7)) == 1,
            number(packet.field(8))?.let { it == 1 },
        )
        if (pending.size < expected) return false
        hosts = pending.keys.sorted().map { pending.getValue(it) }
        pending.clear()
        expected = 0
        return true
    }

    private fun number(value: ByteArray?): Int? = when (value?.size) {
        1 -> value[0].toInt() and 0xff
        2 -> (value[0].toInt() and 0xff shl 8) or (value[1].toInt() and 0xff)
        else -> null
    }

    /** A device's name, with the end of its address when it has none or another carries the same one. */
    private fun label(host: Host): String {
        val tail = host.mac.copyOf(2).reversedArray().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0').uppercase() }
        val name = host.name ?: return "?? $tail"
        return if (hosts.count { it.name == name } > 1) "$name ($tail)" else name
    }

    fun rows(): List<Setting> = buildList {
        fun add(key: String, value: String?, list: List<Host>) {
            if (list.isNotEmpty()) add(Setting.Choice(key, value, list.map(::label)))
        }
        add(CONNECT, null, hosts.filterNot { it.connected })
        add(DISCONNECT, null, hosts.filter { it.connected })
        add(PREFERRED, hosts.firstOrNull { it.preferred }?.let(::label), if (hosts.size > 1) hosts else emptyList())
        add(AUTO_ON, null, hosts.filter { it.automatic == false })
        add(AUTO_OFF, null, hosts.filter { it.automatic == true })
    }

    /** The write for the device [name] names under [key], or null when that device is not offered there. */
    fun change(key: String, name: String): HuaweiPacket? {
        if (rows().none { it is Setting.Choice && it.key == key && name in it.options }) return null
        val host = hosts.firstOrNull { label(it) == name } ?: return null
        return when (key) {
            PREFERRED -> huaweiPacket(HuaweiCommand.PEER_PREFERRED, listOf(HuaweiTlv(1, host.mac.copyOf())))
            else -> ACTIONS[key]?.let { huaweiPacket(HuaweiCommand.PEER_EXECUTE, listOf(HuaweiTlv(it, host.mac.copyOf()))) }
        }
    }

    /** Only the writes above, to a listed device. */
    fun allows(packet: HuaweiPacket): Boolean {
        val field = packet.fields.singleOrNull() ?: return false
        if (hosts.none { it.mac.contentEquals(field.value) }) return false
        return when (packet.op) {
            HuaweiCommand.PEER_PREFERRED -> field.tag == 1
            HuaweiCommand.PEER_EXECUTE -> field.tag in ACTIONS.values
            else -> false
        }
    }

    companion object {
        const val CONNECT = "hw_peer_connect"
        const val DISCONNECT = "hw_peer_disconnect"
        const val PREFERRED = "hw_peer_preferred"
        const val AUTO_ON = "hw_peer_auto_on"
        const val AUTO_OFF = "hw_peer_auto_off"
        private const val MAX_HOSTS = 16
        private val ACTIONS = mapOf(CONNECT to 1, DISCONNECT to 2, AUTO_ON to 4, AUTO_OFF to 5)
        val KEYS = ACTIONS.keys + PREFERRED
    }
}
