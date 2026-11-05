package io.github.omegadestroyer.pairfree.protocol.jbl

/**
 * One chip variant of Airoha's gesture key map (data/jbl/commands.json, airoha.gesture). Entries are int arrays: the key
 * type (-1 for the gesture's own, [keyTypes]), the event and, in a write, the state.
 */
internal class AirohaGestureVariant(
    val keyId: Int,
    val keyIdFromRead: Boolean,
    val fillsUnmatched: Boolean,
    val keyTypes: Map<Int, Int>,
    val write: Map<Int, List<IntArray>>,
    val writeOverrides: Map<Pair<Int, Int>, List<IntArray>>,
    val readOrder: List<Int>,
    val read: List<Pair<Int, List<IntArray>>>,
)

/**
 * The touch controls of JBL's Airoha models (docs/jbl/PROTOCOL.md section 10.1), as Airoha's SDK in JBL's app keeps them: a
 * key map of 8-byte entries per earbud (key type, key id, event, state), in an NV key (`F2D6` on the v3 chips, `F2E7` on the
 * others) with a copy of the other earbud's in `F2EF`, or on the AB1562 behind its key map commands. JBL's gesture ids (1
 * left tap, 2 right tap, 3 and 4 double tap, 5 and 6 the second long press, 7 and 8 triple tap, 49 and 50 hold) and action
 * ids are the same as on JBL's other models; [AirohaGestureVariant] turns them into entries and back exactly as JBL's app
 * does (MmiStage_SetGesture* and MmiStage_GetGesture*, AirohaHmGestureParser1562).
 */
internal class JblAirohaGesture private constructor(
    private var chip: RaceChip?,
    private var nvdmVersion: Int?,
    private val earbuds: Boolean,
    agentRight: Boolean,
) {
    /** Which earbud PairFree talks to, as the read found it (0CD4); the write's sides follow it. */
    var agentRight: Boolean = agentRight
        private set

    /** The chip's variant, once the chip and on an AB1568 its NVDM version are known. */
    lateinit var name: String
        private set

    private val variant: AirohaGestureVariant get() = AIROHA_GESTURE_VARIANTS.getValue(name)
    /** One step: the command, built when its turn comes, and what to do with the answer. */
    private class Step(val relay: Boolean, val build: () -> RacePacket, val answer: (RacePacket) -> Boolean)

    private val steps = ArrayDeque<Step>()
    private var waiting: Step? = null
    private var pendingId = 0
    private var partner: ByteArray? = null

    /** Every command this flow has sent, which [JblSession.allowed] lets through although some are never sent otherwise. */
    val sent: MutableSet<RacePacket> = mutableSetOf()

    /** True once every step has been answered. */
    var done: Boolean = false
        private set

    /** True when a step failed; nothing more is sent. */
    var failed: Boolean = false
        private set

    /** Each gesture's action as the earbuds hold them, by JBL's gesture id, from the reads this flow made. */
    val gestures: MutableMap<Int, Int> = mutableMapOf()

    private val ab1562: Boolean get() = name == AB1562

    /** The first command, or null when there is none. */
    fun start(): RacePacket? = advance()?.also { sent += it }

    /** Takes [p] if it answers the current step: true when taken, with the next command (if any) in [next]. */
    fun receive(p: RacePacket, next: (RacePacket) -> Unit): Boolean {
        val step = waiting ?: return false
        val answer = if (step.relay) {
            relayAnswer(p) ?: return false
        } else {
            if (p.type == JblRace.TYPE_COMMAND || p.type == JblRace.TYPE_COMMAND_NO_RESPONSE || p.id != pendingId) return false
            p
        }
        waiting = null
        if (!step.answer(answer)) {
            failed = true
            return true
        }
        advance()?.let {
            sent += it
            next(it)
        }
        return true
    }

    private fun advance(): RacePacket? {
        val step = steps.removeFirstOrNull() ?: run {
            done = true
            return null
        }
        val cmd = step.build()
        waiting = step
        if (!step.relay) {
            pendingId = cmd.id
            return cmd
        }
        val dst = partner ?: run {
            failed = true
            return null
        }
        pendingId = JblRace.RELAY
        return RacePacket(JblRace.RELAY, dst + cmd.bytes())
    }

    /** A relayed answer: an indication of 0D01 whose payload is the destination, then the other earbud's own frame. */
    private fun relayAnswer(p: RacePacket): RacePacket? {
        if (p.type != JblRace.TYPE_INDICATION || p.id != JblRace.RELAY || p.payload.size < 8) return null
        val raw = p.payload.copyOfRange(2, p.payload.size)
        val n = u16(raw, 2)
        if (raw.size < 4 + n || n < 2) return null
        return RacePacket(u16(raw, 4), raw.copyOfRange(6, 4 + n), raw[1].toInt() and 0xFF)
    }

    private fun findPartner() {
        steps += Step(false, { RacePacket(JblRace.RELAY_DST) }) { p ->
            val d = p.payload
            partner = (0 until d.size - 1 step 2).map { d.copyOfRange(it, it + 2) }.firstOrNull { (it[0].toInt() and 0xFF) == JblRace.PEQ_PARTNER_DST }
            partner != null
        }
    }

    private val ownKey: Int get() = if (name == V3) JblRace.GESTURE_NV_OWN_V3 else JblRace.GESTURE_NV_OWN

    /**
     * The key map read, as JBL's app reads it on opening the gesture page: which earbud PairFree talks to, on an AB1568
     * its NVDM version (which picks the variant), then the earbud's own key map and the other's.
     */
    private fun planRead() {
        steps += Step(false, { RacePacket.of(JblRace.AGENT_CHANNEL) }) { p ->
            if (p.status == 0 && p.data.isNotEmpty()) agentRight = p.data[0].toInt() and 0xFF == 1
            p.status == 0
        }
        val c = chip!!
        if (c == RaceChip.AB1568 && nvdmVersion == null) {
            steps += Step(false, { RacePacket.nvRead(JblRace.NV_NVDM_VERSION, JblRace.PEQ_READ_LENGTH) }) { p ->
                nvdmVersion = p.data.firstOrNull()?.toInt()?.and(0xFF) ?: 0
                name = variantOf(c, nvdmVersion)
                true
            }
        } else {
            name = variantOf(c, nvdmVersion)
        }
        if (earbuds) findPartner()
        for (relay in if (earbuds) listOf(false, true) else listOf(false)) {
            val read = { if (ab1562) RacePacket(JblRace.KEYMAP_GET) else RacePacket.nvRead(ownKey, JblRace.GESTURE_READ_LENGTH) }
            steps += Step(relay, read) { p ->
                val data = if (ab1562) p.payload else p.data
                gestures += decode(name, data, agentRight, relay)
                if (variant.keyIdFromRead && data.size >= ENTRY) keyId = data[data.size - ENTRY + 1].toInt() and 0xFF
                true
            }
        }
    }

    /** The key id the next write carries: the variant's own, or on the v3 and AB1562 the last entry read. */
    var keyId: Int? = null
        private set

    /**
     * The write of [list] (gesture id and action, in the order JBL's page lists them) merged with what the reads found for
     * every other gesture, as setGestureInfo merges them: the earbud's own key map, the copy of the other's, the reload, and
     * on earbuds the same relayed to the other earbud. On the AB1562 the key map write alone, then relayed.
     */
    private fun planWrite(list: List<Pair<Int, Int>>) {
        val all = list + gestures.entries.filter { (g, _) -> list.none { it.first == g } }.map { it.key to it.value }
        list.forEach { (g, a) -> gestures[g] = a }
        if (earbuds) findPartner()
        for (relay in if (earbuds) listOf(false, true) else listOf(false)) {
            if (ab1562) {
                steps += Step(relay, { RacePacket(JblRace.KEYMAP_SET, encode(name, keyId ?: variant.keyId, all, agentRight, relay, peer = false)) }, ::ok)
                continue
            }
            steps += Step(relay, { nvWrite(ownKey, encode(name, keyId ?: variant.keyId, all, agentRight, relay, peer = false)) }, ::ok)
            steps += Step(relay, { nvWrite(JblRace.GESTURE_NV_PEER, encode(name, keyId ?: variant.keyId, all, agentRight, relay, peer = true)) }, ::ok)
            steps += Step(relay, { RacePacket(JblRace.NV_RELOAD, key(ownKey)) }, ::ok)
        }
    }

    private fun ok(p: RacePacket): Boolean = p.status == 0

    private fun nvWrite(key: Int, data: ByteArray) = RacePacket(JblRace.NV_WRITE, key(key) + data)

    private fun key(k: Int) = byteArrayOf(k.toByte(), (k shr 8).toByte())

    private fun u16(d: ByteArray, at: Int): Int = (d[at].toInt() and 0xFF) or ((d[at + 1].toInt() and 0xFF) shl 8)

    companion object {
        const val V3 = "v3"
        const val AB1568 = "1568"
        const val PLAIN = "plain"
        const val AB1562 = "ab1562"
        private const val ENTRY = 8

        /** The variant JBL's app picks by chip, as AirohaConnector sets the chip type (the NVDM version above 1 makes an AB1568 a v3). */
        fun variantOf(chip: RaceChip, nvdmVersion: Int?): String = when (chip) {
            RaceChip.AB158X -> V3
            RaceChip.AB1568 -> if ((nvdmVersion ?: 0) > 1) V3 else AB1568
            RaceChip.AB1562 -> AB1562
            RaceChip.AB155X -> PLAIN
        }

        /** The read of both earbuds' key maps; [gestures] holds them once [done]. */
        fun read(chip: RaceChip, nvdmVersion: Int?, earbuds: Boolean): JblAirohaGesture =
            JblAirohaGesture(chip, nvdmVersion, earbuds, false).apply { planRead() }

        /** The write of [list] on top of [known] (what the last read found), with the key id that read left. */
        fun write(variant: String, earbuds: Boolean, agentRight: Boolean, known: Map<Int, Int>, keyId: Int?, list: List<Pair<Int, Int>>): JblAirohaGesture =
            JblAirohaGesture(null, null, earbuds, agentRight).apply {
                name = variant
                gestures += known
                keyId?.let { this.keyId = it }
                planWrite(list)
            }

        /**
         * One earbud's key map for [list]: the entries of the gestures that earbud holds (the agent's side, or with [relay] the
         * other's; [peer] the opposite, for the copy), in the list's order, or one empty entry when there are none.
         */
        fun encode(variant: String, keyId: Int, list: List<Pair<Int, Int>>, agentRight: Boolean, relay: Boolean, peer: Boolean): ByteArray {
            val v = AIROHA_GESTURE_VARIANTS.getValue(variant)
            val right = agentRight != (relay != peer)
            val out = ArrayList<Byte>()
            for ((g, a) in list) {
                if ((g % 2 == 0) != right) continue
                val entries = v.writeOverrides[g to a] ?: v.write[a] ?: continue
                val own = v.keyTypes[g] ?: continue
                for (e in entries) out += entry(if (e[0] == -1) own else e[0], keyId, e[1], e[2]).toList()
            }
            // Nothing for that earbud: eight zero bytes on the plain variant (MmiStage_SetGesture), else an entry of type 0.
            if (out.isEmpty()) return if (variant == PLAIN) ByteArray(ENTRY) else entry(0, keyId, 0, 0)
            return out.toByteArray()
        }

        private fun entry(type: Int, keyId: Int, event: Int, state: Int) = byteArrayOf(
            type.toByte(), keyId.toByte(), event.toByte(), (event shr 8).toByte(),
            state.toByte(), (state shr 8).toByte(), (state shr 16).toByte(), (state shr 24).toByte(),
        )

        /**
         * The actions [data] (one earbud's key map) gives, by JBL's gesture id: per key type in the variant's order, the last
         * rule all of whose entries are there; a key type with none is 0 where the variant's parser fills it in.
         */
        fun decode(variant: String, data: ByteArray, agentRight: Boolean, relay: Boolean): Map<Int, Int> {
            val v = AIROHA_GESTURE_VARIANTS.getValue(variant)
            val right = agentRight != relay
            // An empty key map: every gesture of that earbud at 0 (genDefaultResult), on every variant.
            if (data.isEmpty()) return v.keyTypes.filter { (g, t) -> (g % 2 == 0) == right && t in v.readOrder }.mapValues { 0 }
            val have = (0 until data.size / ENTRY).map { i ->
                val at = i * ENTRY
                (data[at].toInt() and 0xFF) to ((data[at + 2].toInt() and 0xFF) or (data[at + 3].toInt() shl 8)).toShort().toInt()
            }.toSet()
            val out = mutableMapOf<Int, Int>()
            for (type in v.readOrder) {
                var action = 0
                for ((a, req) in v.read) if (req.all { r -> ((if (r[0] == -1) type else r[0]) to r[1]) in have }) action = a
                val gesture = v.keyTypes.entries.firstOrNull { (g, t) -> t == type && (g % 2 == 0) == right }?.key ?: continue
                if (action != 0 || v.fillsUnmatched) out[gesture] = action
            }
            return out
        }
    }
}
