package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.jbl.JblRace
import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket

/**
 * The Crusher 1080 ANC's Auracast receiver over Airoha's RACE (docs/skullcandy/PROTOCOL.md section 18): the SDK's `AURACAST_RECEIVER` feature goes through Airoha's LE Audio
 * commands of the AB158x (`command_race.xml`, `libmmi158x`): `2200` scans (mode 3 starts, 0 stops; the broadcasts come as indications), `2201` picks a source (the address as it came,
 * its type and advertising set), `2202` plays a subgroup, `2203` stops the stream, `2209` sets the broadcast code and `2208` reads the state. The rows are the ones Sennheiser's Auracast uses.
 */
internal class SkullcandyAuracast {
    /** A broadcast the earbuds found: its name (the broadcast name of the public broadcast announcement), address as the earbuds gave it, address type, advertising set and whether it needs a code. */
    class Found(val name: String, val addrType: Int, val addr: ByteArray, val sid: Int, val encrypted: Boolean) {
        val key: String = addr.joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val label: String get() = name.ifEmpty { key }
    }

    val found = LinkedHashMap<String, Found>()
    var scanning = false
        private set
    var known = false
        private set

    /** The source picked, and its state: receiving, code_required, bad_code, failed; null while it is not synced. */
    var chosen: Found? = null
        private set
    var state: String? = null
        private set

    fun readState(): RacePacket = RacePacket(ID_STATE)

    fun scan(start: Boolean): RacePacket {
        if (start) found.clear()
        scanning = start
        return RacePacket(ID_SCAN, byteArrayOf(if (start) SCAN_START else 0))
    }

    /** Picks [label]'s source; null when it is not one the scan found. */
    fun select(label: String): RacePacket? {
        val f = found.values.firstOrNull { it.label == label } ?: return null
        chosen = f
        state = null
        return RacePacket(ID_SELECT, f.addr + byteArrayOf(f.addrType.toByte(), f.sid.toByte()))
    }

    fun stop(): RacePacket {
        state = null
        return RacePacket(ID_STOP)
    }

    /** The broadcast code as the SDK sends it: sixteen bytes, the text as ASCII padded with zeros, or its hexadecimal reading when longer. Null for no source picked, no text or text that is not hexadecimal and too long. */
    fun code(text: String): RacePacket? {
        if (text.isEmpty() || chosen == null) return null
        val out = ByteArray(CODE_LENGTH)
        if (text.length <= CODE_LENGTH) {
            text.encodeToByteArray().copyInto(out)
        } else {
            val hex = text.filter { it.isLetterOrDigit() }
            if (hex.length % 2 != 0 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            hex.chunked(2).take(CODE_LENGTH).forEachIndexed { i, b -> out[i] = b.toInt(16).toByte() }
        }
        state = null
        return RacePacket(ID_CODE, out)
    }

    /** Takes [p] when it is one of this feature's answers or events; [send] queues the next command. Returns true when the rows changed. */
    fun receive(p: RacePacket, send: (RacePacket) -> Unit): Boolean {
        val d = p.payload
        fun u8(at: Int) = if (at < d.size) d[at].toInt() and 0xFF else -1
        if (p.type == JblRace.TYPE_INDICATION) {
            return when (p.id) {
                ID_SCAN -> source(d)
                ID_SYNC -> { state = "receiving"; true }
                ID_TERMINATED -> {
                    state = when (u8(0)) { 0 -> null; 1 -> "bad_code"; else -> "failed" }
                    true
                }
                ID_INFO -> if (u8(0) > 0 && chosen != null && state == null) { state = "code_required"; true } else false
                else -> false
            }
        }
        if (p.type != JblRace.TYPE_RESPONSE) return false
        val ok = u8(0) == 0
        return when (p.id) {
            ID_STATE -> { known = ok; ok }
            ID_SCAN -> { if (!ok) scanning = false; !ok }
            ID_SELECT -> {
                val f = chosen
                if (!ok || f == null) {
                    state = "failed"
                    true
                } else if (f.encrypted) {
                    // a coded broadcast waits for its code
                    state = "code_required"
                    true
                } else {
                    // an open one is played at once, its first subgroup, as the app does
                    send(RacePacket(ID_PLAY, byteArrayOf(0)))
                    false
                }
            }
            ID_PLAY -> { state = if (ok) "receiving" else "failed"; true }
            ID_STOP -> { if (ok) { state = null; chosen = null }; ok }
            ID_CODE -> { if (!ok) state = "bad_code"; !ok }
            else -> false
        }
    }

    /** A broadcast the earbuds found, from an indication of `2200`: the scan status, the address type and address, the advertising set, the signal and the advertising data. */
    private fun source(d: ByteArray): Boolean {
        if (d.size < 11) return false
        if ((d[0].toInt() and 0xFF) != 0) {
            // a scan that ended
            val was = scanning
            scanning = false
            return was
        }
        val addr = d.copyOfRange(2, 8)
        val len = d[10].toInt() and 0xFF
        val info = if (len > 0 && d.size > 11) publicBroadcast(d.copyOfRange(11, d.size)) else Pair("", false)
        val f = Found(info.first, d[1].toInt() and 0xFF, addr, d[8].toInt() and 0xFF, info.second)
        if (f.key in found) return false
        found[f.key] = f
        return true
    }

    /**
     * The SDK's reading of the advertising data (`d1`): length-type-value entries; the 16-bit service data 1856 is the public broadcast announcement, whose first feature bit says the
     * stream is encrypted, and type 30 hex is the broadcast name.
     */
    fun publicBroadcast(data: ByteArray): Pair<String, Boolean> {
        var name = ""
        var encrypted = false
        var at = 0
        while (at + 1 < data.size) {
            val l = data[at].toInt() and 0xFF
            if (l == 0) break
            val t = data[at + 1].toInt() and 0xFF
            val end = (at + 1 + l).coerceAtMost(data.size)
            val v = data.copyOfRange(at + 2, end)
            if (t == TYPE_SERVICE_DATA && v.size >= 3 && (v[0].toInt() and 0xFF) == 0x56 && (v[1].toInt() and 0xFF) == 0x18) {
                encrypted = (v[2].toInt() and 1) != 0
            } else if (t == TYPE_NAME) {
                name = v.decodeToString()
            }
            at += 1 + l
        }
        return name to encrypted
    }

    fun group(): SettingGroup? {
        if (!known) return null
        val rows = ArrayList<Setting>()
        rows += Setting.Action(KEY_SCAN, scanning)
        val offered = found.values.filter { it !== chosen }
        if (offered.isNotEmpty()) rows += Setting.Choice(KEY_ADD, null, offered.map { it.label })
        val c = chosen
        if (c != null) {
            rows += Setting.Information(KEY_SOURCE, (state ?: "not_synced") + ":" + c.label)
            if (state == "code_required" || state == "bad_code") rows += Setting.Text(KEY_CODE, "", CODE_LENGTH)
            rows += Setting.Choice(KEY_REMOVE, null, listOf(c.label))
        }
        return SettingGroup("sh_auracast", SettingTab.DEVICE, rows)
    }

    companion object {
        const val ID_SCAN = 0x2200
        const val ID_SELECT = 0x2201
        const val ID_PLAY = 0x2202
        const val ID_STOP = 0x2203
        const val ID_STATE = 0x2208
        const val ID_CODE = 0x2209

        // events: the sync (`2202`'s indication), the end of a stream (`2203`'s) and the stream's information (`2209`'s)
        const val ID_SYNC = 0x2202
        const val ID_TERMINATED = 0x2203
        const val ID_INFO = 0x2209
        const val SCAN_START: Byte = 3
        const val CODE_LENGTH = 16
        private const val TYPE_SERVICE_DATA = 0x16
        private const val TYPE_NAME = 0x30

        /** The rows' keys, those of Sennheiser's Auracast so the screens' words are the same. */
        const val KEY_SCAN = "sh_auracast_scan"
        const val KEY_ADD = "sh_auracast_add"
        const val KEY_REMOVE = "sh_auracast_remove"
        const val KEY_SOURCE = "sh_auracast_source"
        const val KEY_CODE = "sh_auracast_code"
    }
}
