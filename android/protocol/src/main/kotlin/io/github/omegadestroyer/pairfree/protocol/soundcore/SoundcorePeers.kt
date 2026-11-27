package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting

/**
 * The devices the earbuds have paired, for models with two devices at once (docs/soundcore/PROTOCOL.md, Paired devices).
 *
 * The list is the answer to `0B 01`, in one packet or several: the number of packets, this packet's number from 1, then each device as a length (counting
 * itself to the end of the name), 1 when connected, its address (six bytes) and its name padded with zeros. The layout and the writes are OpenSCQ30's
 * (github.com/Oppzippy/OpenSCQ30, GPL-3.0-or-later, `dual_connections`); the Soundcore app sends the same commands.
 */
public class SoundcorePeers {
    private class Peer(val mac: ByteArray, val name: String?, val connected: Boolean)

    private val pages = mutableMapOf<Int, List<Peer>>()
    private var peers: List<Peer> = emptyList()

    /** Takes one packet of the list. True when the list is complete and may have changed. */
    public fun receive(body: ByteArray): Boolean {
        if (body.size < 2) return false
        val total = body[0].toInt() and 0xFF
        val index = body[1].toInt() and 0xFF
        if (total == 0 || index !in 1..total) return false
        val page = mutableListOf<Peer>()
        var at = 2
        while (at < body.size) {
            val length = body[at].toInt() and 0xFF
            if (length < HEAD || at + length > body.size) return false
            val mac = body.copyOfRange(at + 2, at + HEAD)
            val raw = body.copyOfRange(at + HEAD, at + length)
            val name = raw.copyOfRange(0, raw.indexOf(0).let { if (it < 0) raw.size else it }).decodeToString().trim().ifEmpty { null }
            page += Peer(mac, name, body[at + 1].toInt() != 0)
            at += length
        }
        if (index == 1) pages.clear()
        pages[index] = page
        if (pages.size < total) return false
        peers = (1..total).flatMap { pages[it].orEmpty() }
        pages.clear()
        return true
    }

    /** What the screens name a device: its name, with its address's last two bytes when it has none or another carries the same name. */
    private fun label(p: Peer): String {
        val tail = p.mac.takeLast(2).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
        val name = p.name ?: return "?? $tail"
        return if (peers.count { it.name == name } > 1) "$name ($tail)" else name
    }

    /** Connect, disconnect and forget, once the earbuds have listed their devices. */
    public fun rows(): List<Setting> {
        if (peers.isEmpty()) return emptyList()
        val off = peers.filter { !it.connected }.map(::label)
        val on = peers.filter { it.connected }.map(::label)
        return buildList {
            if (off.isNotEmpty()) add(Setting.Choice(CONNECT, null, off))
            if (on.isNotEmpty()) add(Setting.Choice(DISCONNECT, null, on))
            add(Setting.Choice(DELETE, null, peers.map(::label)))
        }
    }

    /** The write for the device [name] names (the screens confirm forgetting first), or null when it is not in the list. */
    public fun change(key: String, name: String): SoundcorePacket? {
        val target = peers.firstOrNull { label(it) == name } ?: return null
        val command = when (key) {
            CONNECT -> SoundcoreCommand.PEER_CONNECT
            DISCONNECT -> SoundcoreCommand.PEER_DISCONNECT
            DELETE -> SoundcoreCommand.PEER_FORGET
            else -> return null
        }
        if (key == DELETE) peers = peers - target
        return SoundcorePacket(command, target.mac.copyOf())
    }

    public companion object {
        public const val CONNECT: String = "sc_peer_connect"
        public const val DISCONNECT: String = "sc_peer_disconnect"
        public const val DELETE: String = "sc_peer_delete"
        public const val PAIRING: String = "sc_pairing_mode"

        // The length byte, the connected byte and the address.
        private const val HEAD = 8
    }
}
