package io.github.omegadestroyer.pairfree.protocol.apple

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * One frame of the BCD protocol of the older Beats (Powerbeats3, Solo3, BeatsX, Flex, Studio3; docs/apple/B2P.md), read from
 * libBcdAdapter.so: `AA`, a length (the command's two bytes plus the payload), a sequence number (1 to 255), the command (high
 * byte first), the payload and a CRC-8 (SAE J1850 table, polynomial 1D, start 0) over every byte before it. A frame with length 0 is
 * an acknowledgement: `AA 00 sequence crc`.
 */
public class BcdFrame(public val sequence: Int, public val command: Int, public val payload: ByteArray = ByteArray(0), public val ack: Boolean = false) {
    public fun bytes(): ByteArray {
        val body = if (ack) byteArrayOf(0xAA.toByte(), 0, sequence.toByte())
        else byteArrayOf(0xAA.toByte(), (payload.size + 2).toByte(), sequence.toByte(), (command shr 8).toByte(), command.toByte()) + payload
        return body + crc8(body).toByte()
    }

    override fun equals(other: Any?): Boolean = other is BcdFrame && sequence == other.sequence && command == other.command && ack == other.ack && payload.contentEquals(other.payload)

    override fun hashCode(): Int = ((sequence * 31 + command) * 31 + ack.hashCode()) * 31 + payload.contentHashCode()

    override fun toString(): String = if (ack) "BcdFrame(ack $sequence)" else "BcdFrame(seq $sequence, ${command.toString(16).padStart(4, '0')}, ${payload.joinToString("") { "%02x".format(it) }})"

    public companion object {
        /** SAE J1850 CRC-8 as the library computes it: table lookup, polynomial 1D, start 0, no final inversion. */
        public fun crc8(data: ByteArray): Int {
            var crc = 0
            for (b in data) {
                crc = crc xor (b.toInt() and 0xFF)
                repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor POLY) and 0xFF else (crc shl 1) and 0xFF }
            }
            return crc
        }

        public fun parse(raw: ByteArray): BcdFrame? {
            if (raw.size < 4 || raw[0] != 0xAA.toByte()) return null
            val length = raw[1].toInt() and 0xFF
            if (length == 1 || raw.size != length + 4 || crc8(raw.copyOfRange(0, raw.size - 1)) != (raw.last().toInt() and 0xFF)) return null
            val seq = raw[2].toInt() and 0xFF
            if (length == 0) return BcdFrame(seq, 0, ByteArray(0), ack = true)
            return BcdFrame(seq, ((raw[3].toInt() and 0xFF) shl 8) or (raw[4].toInt() and 0xFF), raw.copyOfRange(5, raw.size - 1))
        }

        /** Whole frames at the start of [buffer] and the rest. */
        public fun split(buffer: ByteArray): Pair<List<ByteArray>, ByteArray> {
            val frames = mutableListOf<ByteArray>()
            var at = 0
            while (buffer.size - at >= 2) {
                if (buffer[at] != 0xAA.toByte()) { at++; continue }
                val total = (buffer[at + 1].toInt() and 0xFF) + 4
                if (buffer.size - at < total) break
                frames += buffer.copyOfRange(at, at + total)
                at += total
            }
            return frames to buffer.copyOfRange(at, buffer.size)
        }

        private const val POLY = 0x1D
    }
}

/**
 * A session with one of the older Beats over BCD. Apple's app lists ANC for the Studio3 and AutoAnswer for the Flex only
 * (products_descriptor.json), and PairFree reads just what the model lists, after the product id; whatever a Beats answers is shown. Source analysis only: no
 * Beats has answered PairFree. The battery has a command but its layout is unread, and Android shows the battery through the
 * standard Bluetooth reports for these models.
 */
public class BcdSession(override val model: AppleModel?) : BeatsControl {
    override var state: EarbudState = EarbudState()
        private set

    private var sequence = 0
    private var listening: Int? = null
    private var autoAnswer: Int? = null
    private var productId: Int? = null
    private var mode: Int? = null

    override var outgoing: List<ByteArray> = emptyList()
        private set

    private fun frame(command: Int, vararg payload: Int): BcdFrame {
        sequence = sequence % MAX_SEQUENCE + 1
        return BcdFrame(sequence, command, ByteArray(payload.size) { payload[it].toByte() })
    }

    override fun start(): List<ByteArray> = buildList {
        add(frame(PRODUCT_ID_GET))
        if (model?.has("ANC") == true) add(frame(LISTENING_MODE_GET))
        if (model?.has("AutoAnswer") == true) add(frame(AUTO_ANSWER_GET))
    }.map { it.bytes() }

    override fun split(buffer: ByteArray): Pair<List<ByteArray>, ByteArray> = BcdFrame.split(buffer)

    override fun receiveBytes(raw: ByteArray): Boolean {
        outgoing = emptyList()
        val f = BcdFrame.parse(raw) ?: return false
        if (f.ack) return false
        outgoing = listOf(BcdFrame(f.sequence, 0, ack = true).bytes())
        val before = state to groups
        val p = f.payload
        when (f.command) {
            PRODUCT_ID_GET + 1 -> if (p.size == 2) productId = (p[0].toInt() and 0xFF) or ((p[1].toInt() and 0xFF) shl 8)
            LISTENING_MODE_GET + 1 -> if (p.size == 1) {
                listening = p[0].toInt() and 0xFF
                mode = null
            }
            AUTO_ANSWER_GET + 1 -> if (p.size == 1) autoAnswer = p[0].toInt() and 0xFF
        }
        refresh()
        return (state to groups) != before
    }

    /** Noise cancelling is on when the Beats answer 2 (Apple's getter compares with 2) or after the user switched it. */
    private fun on(): Boolean? = mode?.let { it == ON } ?: listening?.let { it == ON }

    private fun refresh() {
        val anc = on() ?: return
        state = state.copy(values = state.values + (Command.ANC_MODE to Value.Number(ancNumber(if (anc) "anc" else "off"))))
    }

    override val groups: List<SettingGroup>
        get() = listOfNotNull(
            autoAnswer?.let { SettingGroup("other", SettingTab.DEVICE, listOf(Setting.Toggle("auto_answer", it == AUTO_ON))) },
        )

    override fun change(change: SettingChange): List<ByteArray>? {
        if (change is SettingChange.Toggle && change.key == "auto_answer" && autoAnswer != null) {
            autoAnswer = if (change.on) AUTO_ON else AUTO_OFF
            return listOf(frame(AUTO_ANSWER_SET, autoAnswer!!).bytes())
        }
        return null
    }

    /** Off or noise cancelling: the older Beats have no transparency in this protocol. */
    override fun set(command: Command, value: Int): List<ByteArray>? {
        if (command != Command.ANC_MODE || on() == null) return null
        val anc = when (value) {
            ancNumber("anc") -> true
            ancNumber("off") -> false
            else -> return null
        }
        mode = if (anc) ON else OFF
        refresh()
        return listOf(frame(LISTENING_MODE_SET, mode!!).bytes())
    }

    override val answers: Map<String, Int?> get() = mapOf("listening_mode" to listening, "auto_answer" to autoAnswer)

    override val reportLines: List<String> get() = listOf("Product id:      ${productId?.let { "%04x".format(it) } ?: "unknown"}")

    private fun ancNumber(name: String): Int = Command.ANC_MODE.labels.entries.first { it.value == name }.key

    public companion object {
        public const val PRODUCT_ID_GET: Int = 0x0000
        public const val LISTENING_MODE_GET: Int = 0x01A4
        public const val LISTENING_MODE_SET: Int = 0x0226
        public const val AUTO_ANSWER_GET: Int = 0x024A
        public const val AUTO_ANSWER_SET: Int = 0x0248
        private const val MAX_SEQUENCE = 255
        private const val OFF = 1
        private const val ON = 2
        private const val AUTO_ON = 1
        private const val AUTO_OFF = 2
    }
}
