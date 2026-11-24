package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The earbuds as an Auracast receiver, over the BES models' `LeaAudioCmd` (`B0`, docs/jbl/PROTOCOL.md section 4.11): the earbuds scan for broadcasts themselves, and the
 * phone tells them which one to listen to. Read from JBL Headphones 5.37.10 (`LeaAudioCmd`, `HandleParse`, `AuracastP2Ui2ViewModel`); no earbuds have answered it.
 *
 * Sub-commands (the first payload byte): 00 asks what the earbuds receive, 01 starts a scan (then 01 and the seconds, 30 as the app asks), 02 stops it, 20 receives a
 * source (01, its six address bytes in the reverse of the order a reply gives them, then the password's characters), 21 stops receiving, 22 picks a sub-group. The replies are
 * 10 (receiving), 11 (a scan result, one source per message), 12 (the scan stopped), 30, 31 and 32 (the answers to 20, 21 and 22), each with a return status and, for the first
 * three, the source as records of a type, a length and a value.
 */
internal class JblAuracast {
    class Source(
        val address: String,
        var name: String,
        var needsPassword: Boolean,
        var receiving: Boolean,
        var high: Boolean,
        val subGroups: MutableList<Int> = mutableListOf(),
        val bytes: ByteArray,
    ) {
        val label: String get() = name.ifEmpty { address }
    }

    var scanning = false
        private set
    private val found = linkedMapOf<String, Source>()
    private var code = ""
    private var known = false

    /** What the last receive, stop or sub-group request answered: 0 done, 1 wrong password, 2 timed out (`CONNECT_STATUS_*`); null before an answer. */
    var lastAnswer: Int? = null
        private set

    fun reads(): List<JblFrame> = listOf(JblPacket.of(JblCommand.LE_AUDIO, QUERY))

    fun reply(p: ByteArray) {
        if (p.size < 2) return
        known = true
        val type = p[0].toInt() and 0xFF
        val status = p[1].toInt() and 0xFF
        when (type) {
            RET_QUERY, RET_SCAN -> {
                if (type == RET_SCAN && status == SCAN_TIMEOUT) scanning = false
                val source = parse(p, from = 2, replaceReceiving = type == RET_QUERY)
                if (source != null) found[source.address] = source else if (type == RET_QUERY) for (s in found.values) s.receiving = false
            }
            RET_STOP_SCAN -> scanning = false
            RET_RECEIVE, RET_CANCEL, RET_SUBGROUP -> lastAnswer = status
        }
    }

    /** `LeaAudioCmd.parse`: records of a type, a length and a value; type 0 is the source itself, any other a sub-group of it. */
    private fun parse(p: ByteArray, from: Int, replaceReceiving: Boolean): Source? {
        var at = from
        var main: Source? = null
        val subs = mutableListOf<Fields>()
        while (at + 1 < p.size) {
            val type = p[at].toInt() and 0xFF
            val length = p[at + 1].toInt() and 0xFF
            val end = at + 2 + length
            if (end > p.size) break
            val value = p.copyOfRange(at + 2, end)
            val fields = fields(value)
            if (type == 0) main = source(fields) else subs += fields
            at = end
        }
        val source = main ?: return null
        source.subGroups.clear()
        for (f in subs) f.index?.let { source.subGroups += it }
        if (subs.isNotEmpty()) {
            source.receiving = source.receiving || subs.any { it.active == 1 }
            source.high = subs.any { it.quality == 2 }
        }
        if (replaceReceiving && source.address.isNotEmpty()) {
            for (other in found.values) if (other.address != source.address) other.receiving = false
        }
        return source
    }

    private class Fields(
        val bytes: ByteArray, val address: String, val name: String, val receiveStatus: Int?, val password: Boolean,
        val index: Int?, val active: Int?, val quality: Int?,
    )

    /** `parseGroupInfo` and `parseSubGroupInfo`: 1 the address, 2 the name, 3 the receive status, 4 whether a password is needed, 32 the sub-group's index, 33 whether it is on, 35 its quality. */
    private fun fields(v: ByteArray): Fields {
        var at = 0
        var bytes = ByteArray(0)
        var address = ""
        var name = ""
        var receive: Int? = null
        var password = false
        var index: Int? = null
        var active: Int? = null
        var quality: Int? = null
        while (at + 1 < v.size) {
            val id = v[at].toInt() and 0xFF
            val length = v[at + 1].toInt() and 0xFF
            val end = at + 2 + length
            if (end > v.size) break
            val value = v.copyOfRange(at + 2, end)
            when (id) {
                1 -> { bytes = value; address = value.joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() } }
                2 -> name = value.decodeToString()
                3 -> receive = value.firstOrNull()?.toInt()?.and(0xFF)
                4 -> password = value.firstOrNull()?.toInt() == 1
                32 -> index = value.firstOrNull()?.toInt()?.and(0xFF)
                33 -> active = value.firstOrNull()?.toInt()?.and(0xFF)
                35 -> quality = value.firstOrNull()?.toInt()?.and(0xFF)
            }
            at = end
        }
        return Fields(bytes, address, name, receive, password, index, active, quality)
    }

    private fun source(f: Fields) = found[f.address]?.also {
        if (f.name.isNotEmpty()) it.name = f.name
        it.needsPassword = f.password
        it.receiving = f.active == 1 || f.receiveStatus == 1
    } ?: Source(f.address, f.name, f.password, f.active == 1 || f.receiveStatus == 1, false, bytes = f.bytes)

    fun rows(): List<Setting> {
        if (!known) return emptyList()
        return buildList {
            add(Setting.Action(SCAN_KEY, scanning))
            val receiving = found.values.filter { it.receiving }
            for (r in receiving) add(Setting.Information(SOURCE_KEY, r.label))
            val offered = found.values.filter { !it.receiving && it.address.isNotEmpty() }
            if (offered.isNotEmpty()) add(Setting.Choice(ADD_KEY, null, offered.map { it.label }))
            if (offered.any { it.needsPassword }) add(Setting.Text(CODE_KEY, code, CODE_LENGTH))
            if (receiving.isNotEmpty()) add(Setting.Action(STOP_KEY))
            val groups = receiving.firstOrNull()?.subGroups.orEmpty()
            if (groups.size > 1) add(Setting.Choice(SUBGROUP_KEY, null, groups.map { it.toString() }))
        }
    }

    fun change(change: SettingChange): List<JblFrame>? = when (change) {
        is SettingChange.Press -> when (change.key) {
            SCAN_KEY -> if (scanning) {
                scanning = false
                listOf(JblPacket.of(JblCommand.LE_AUDIO, STOP_SCAN, 0))
            } else {
                scanning = true
                found.values.removeAll { !it.receiving }
                listOf(JblPacket.of(JblCommand.LE_AUDIO, START_SCAN, 1, SCAN_SECONDS))
            }
            STOP_KEY -> listOf(JblPacket.of(JblCommand.LE_AUDIO, CANCEL, 0))
            else -> null
        }
        is SettingChange.Choice -> when (change.key) {
            ADD_KEY -> found.values.firstOrNull { it.label == change.option && it.bytes.size == 6 }?.let { s ->
                // The six bytes go back in the reverse of the order they came in (`receiveLeAudioSource`).
                listOf(JblPacket(JblCommand.LE_AUDIO, byteArrayOf(RECEIVE.toByte(), 1) + s.bytes.reversedArray() + code.encodeToByteArray()))
            }
            SUBGROUP_KEY -> change.option.toIntOrNull()?.let { listOf(JblPacket.of(JblCommand.LE_AUDIO, SWITCH_SUBGROUP, it)) }
            CODE_KEY -> {
                code = change.option.take(CODE_LENGTH)
                emptyList()
            }
            else -> null
        }
        else -> null
    }

    fun readBack(key: String): JblFrame? = if (key in setOf(ADD_KEY, STOP_KEY, SUBGROUP_KEY)) JblPacket.of(JblCommand.LE_AUDIO, QUERY) else null

    companion object {
        const val SCAN_KEY = "jbl_auracast_scan"
        const val ADD_KEY = "jbl_auracast_add"
        const val CODE_KEY = "jbl_auracast_code"
        const val SOURCE_KEY = "jbl_auracast_source"
        const val STOP_KEY = "jbl_auracast_stop"
        const val SUBGROUP_KEY = "jbl_auracast_subgroup"
        val KEYS = setOf(SCAN_KEY, ADD_KEY, CODE_KEY, SOURCE_KEY, STOP_KEY, SUBGROUP_KEY)

        private const val QUERY = 0x00
        private const val START_SCAN = 0x01
        private const val STOP_SCAN = 0x02
        private const val RECEIVE = 0x20
        private const val CANCEL = 0x21
        private const val SWITCH_SUBGROUP = 0x22
        private const val RET_QUERY = 0x10
        private const val RET_SCAN = 0x11
        private const val RET_STOP_SCAN = 0x12
        private const val RET_RECEIVE = 0x30
        private const val RET_CANCEL = 0x31
        private const val RET_SUBGROUP = 0x32
        private const val SCAN_TIMEOUT = 2
        private const val SCAN_SECONDS = 30
        private const val CODE_LENGTH = 16
    }
}
