package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.BandEq
import io.github.omegadestroyer.pairfree.protocol.BandEqReport
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * The C-Chip protocol of the six older Qualcomm models (Crusher ANC, Crusher Evo, Indy Fuel, Indy Evo, Push Ultra, Crusher 540 Active; docs/skullcandy/PROTOCOL.md section 17): the app's own Dart code
 * (`api/utils/skdygaia.dart`, `api/services/skdyearbudservice.dart`) writes a frame to a GATT characteristic of Qualcomm's GAIA service and reads the answers from its notifications.
 */
public object SkullcandyCChip {
    /** The service and its characteristics (the GAIA service of Qualcomm's CSR chips, as the app's `SKDYBT` writes to it): the command one is written, the response one notifies. */
    public const val SERVICE: String = "00001100-D102-11E1-9B23-00025B00A5A5"
    public const val WRITE: String = "00001101-D102-11E1-9B23-00025B00A5A5"
    public const val NOTIFY: String = "00001102-D102-11E1-9B23-00025B00A5A5"

    /** The command word every frame carries (63743 in the app's code). */
    public const val COMMAND: Int = 0xF8FF

    public const val AMBIENT_SET: Int = 1
    public const val AMBIENT_GET: Int = 2
    public const val EQ_SET: Int = 7
    public const val EQ_GET: Int = 8
    public const val BATTERY_LEFT: Int = 9
    public const val BATTERY_RIGHT: Int = 10
    public const val SIDE: Int = 11

    /** The frame: the command word, the type, the frame's length (the header's four bytes and the payload) and the payload. */
    public fun frame(type: Int, vararg payload: Int): ByteArray =
        byteArrayOf((COMMAND shr 8).toByte(), (COMMAND and 0xFF).toByte(), type.toByte(), (payload.size + 4).toByte()) + ByteArray(payload.size) { payload[it].toByte() }

    /** One frame read from the notifications. */
    public class Reply(public val type: Int, public val payload: ByteArray)

    /** Splits a notification into the frames it holds (a frame whose length byte is not at least four is dropped with the rest). */
    public fun parse(bytes: ByteArray): List<Reply> {
        val out = ArrayList<Reply>()
        var at = 0
        while (at + 4 <= bytes.size) {
            if ((bytes[at].toInt() and 0xFF) != (COMMAND shr 8) || (bytes[at + 1].toInt() and 0xFF) != (COMMAND and 0xFF)) break
            val total = bytes[at + 3].toInt() and 0xFF
            if (total < 4 || at + total > bytes.size) break
            out += Reply(bytes[at + 2].toInt() and 0xFF, bytes.copyOfRange(at + 4, at + total))
            at += total
        }
        return out
    }
}

/**
 * The conversation with those six models (docs/skullcandy/PROTOCOL.md section 17): the batteries, the ambient mode of the Indy models and the equaliser's three presets, each read on connecting and
 * written as the app's `activateFeature` writes it. A reply is a frame of the type it answers. *Inference:* the payload of a reply means what the matching write sends (the app's decoder closures
 * were not traced), so a value outside it is ignored.
 */
public class SkullcandyQualcommTalk(public val product: SkullcandyProduct) : SessionTalk<ByteArray>() {
    override val pacing: Pacing = Pacing(replyMs = 1500, gapMs = 150, startGapMs = 150, pollMs = Pacing.POLL_MS, resends = 1)

    private var current = EarbudState()
    override val state: EarbudState get() = current
    override val groups: List<SettingGroup> get() = listOfNotNull(SkullcandyTile.group(product))

    private val f = product.features
    public val hasAmbient: Boolean = "ambientMode" in f
    public val hasEq: Boolean = "definedEqualizer" in f

    private var ambient: Boolean? = null
    private var mode: Int? = null
    private var readBacks: List<ByteArray> = emptyList()

    /** The last payload of each type, for the device report. */
    public val replies: MutableMap<String, ByteArray> = LinkedHashMap()

    /** The presets the earbuds hold: the app's own three, by the number each is sent as. */
    public val eqPresets: List<Pair<String, Int>> = if (hasEq) PRESETS else emptyList()

    /** The preset the earbuds report, or null before they have. */
    public val eqPresetName: String? get() = mode?.let { m -> PRESETS.firstOrNull { it.second == m }?.first }

    override val eq: Any? get() = mode

    override fun start(): List<ByteArray> = buildList {
        add(SkullcandyCChip.frame(SkullcandyCChip.BATTERY_LEFT))
        add(SkullcandyCChip.frame(SkullcandyCChip.BATTERY_RIGHT))
        if (hasEq) add(SkullcandyCChip.frame(SkullcandyCChip.EQ_GET))
        if (hasAmbient) add(SkullcandyCChip.frame(SkullcandyCChip.AMBIENT_GET))
    }

    override fun poll(): List<ByteArray> = listOf(SkullcandyCChip.frame(SkullcandyCChip.BATTERY_LEFT), SkullcandyCChip.frame(SkullcandyCChip.BATTERY_RIGHT))

    override fun allowed(message: ByteArray): Boolean {
        val r = SkullcandyCChip.parse(message).singleOrNull() ?: return false
        return when (r.type) {
            SkullcandyCChip.BATTERY_LEFT, SkullcandyCChip.BATTERY_RIGHT -> r.payload.isEmpty()
            SkullcandyCChip.EQ_GET -> hasEq && r.payload.isEmpty()
            SkullcandyCChip.AMBIENT_GET -> hasAmbient && r.payload.isEmpty()
            SkullcandyCChip.EQ_SET -> hasEq && r.payload.size == 1 && (r.payload[0].toInt() and 0xFF) in 1..3
            SkullcandyCChip.AMBIENT_SET -> hasAmbient && r.payload.size == 1 && (r.payload[0].toInt() and 0xFF) in 1..2
            else -> false
        }
    }

    override fun bytes(message: ByteArray): ByteArray = message

    override fun describe(message: ByteArray): String = "cchip type ${message.getOrNull(2)?.toInt()?.and(0xFF)}"

    override fun answers(sent: ByteArray, got: Any): Boolean = (got as? SkullcandyCChip.Reply)?.type == (sent[2].toInt() and 0xFF)

    override fun takeReadBacks(): List<ByteArray> = readBacks.also { readBacks = emptyList() }

    override fun feed(bytes: ByteArray): Fed {
        var changed = false
        val answers = mutableListOf<Any>()
        for (r in SkullcandyCChip.parse(bytes)) {
            answers += r
            guarded { changed = apply(r) || changed }
        }
        return Fed(changed, answers)
    }

    private fun apply(r: SkullcandyCChip.Reply): Boolean {
        replies[r.type.toString()] = r.payload
        val v = r.payload.firstOrNull()?.toInt()?.and(0xFF) ?: return false
        return when (r.type) {
            SkullcandyCChip.BATTERY_LEFT -> level(Command.BATTERY_LEFT, v)
            SkullcandyCChip.BATTERY_RIGHT -> level(Command.BATTERY_RIGHT, v)
            SkullcandyCChip.EQ_GET -> if (v in 1..3) { mode = v; true } else false
            SkullcandyCChip.AMBIENT_GET -> {
                ambient = when (v) { 1 -> false; 2 -> true; else -> return false }
                noise()
            }
            else -> false
        }
    }

    private fun level(command: Command, v: Int): Boolean {
        if (v !in 0..100) return false
        val next = current.values + (command to Value.Number(v))
        if (next == current.values) return false
        current = current.copy(values = next)
        return true
    }

    private fun noise(): Boolean {
        val label = if (ambient == true) "transparency" else "off"
        val next = current.values + (Command.ANC_MODE to Value.Number(Command.ANC_MODE.labels.entries.first { it.value == label }.key))
        if (next == current.values) return false
        current = current.copy(values = next)
        return true
    }

    override fun set(command: Command, value: Int): List<ByteArray>? {
        if (command != Command.ANC_MODE || !hasAmbient) return null
        val on = when (Command.ANC_MODE.labels[value]) { "off" -> false; "transparency" -> true; else -> return null }
        ambient = on
        noise()
        readBacks = listOf(SkullcandyCChip.frame(SkullcandyCChip.AMBIENT_GET))
        return listOf(SkullcandyCChip.frame(SkullcandyCChip.AMBIENT_SET, if (on) 2 else 1))
    }

    /** The frame that picks the preset [name], or null for a name the model does not have. */
    public fun eqPreset(name: String): List<ByteArray>? {
        val n = eqPresets.firstOrNull { it.first == name }?.second ?: return null
        mode = n
        readBacks = listOf(SkullcandyCChip.frame(SkullcandyCChip.EQ_GET))
        return listOf(SkullcandyCChip.frame(SkullcandyCChip.EQ_SET, n))
    }

    public companion object {
        /** The presets the app's `activateFeature` sends: Music 1, Podcast 2, Movie 3. */
        public val PRESETS: List<Pair<String, Int>> = listOf("Music" to 1, "Podcast" to 2, "Movie" to 3)
    }
}

/** The Sound tab's view of the three presets of the Qualcomm models; they take no custom curve. */
public class SkullcandyQualcommBandEq(private val talk: SkullcandyQualcommTalk) : BandEq {
    override val eqBandsHz: List<Int> = emptyList()
    override val eqRangeDb: Int = 0
    override val eqPresetNames: List<String> = talk.eqPresets.map { it.first }

    override fun eqPresetFrames(name: String): List<ByteArray>? = talk.eqPreset(name)

    override fun eqCustomFrames(gains: List<Int>): List<ByteArray>? = null

    override val eqReport: BandEqReport? get() = talk.eqPresetName?.let { BandEqReport(it, null) }
}
