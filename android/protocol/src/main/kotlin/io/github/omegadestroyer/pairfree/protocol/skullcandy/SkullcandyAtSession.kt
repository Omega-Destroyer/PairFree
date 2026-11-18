package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Fed
import io.github.omegadestroyer.pairfree.protocol.Pacing
import io.github.omegadestroyer.pairfree.protocol.SessionTalk
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.Value

/**
 * The conversation with a Skullcandy product that Skullcandy's own app drives with AT lines over the serial service (docs/skullcandy/PROTOCOL.md section 9: Grind,
 * Grind Fuel, Push Active, Crusher ANC 2). A message is one AT line as text. The earbuds answer each line with `<prefix>:<value>` lines and `OK`, and the prefixes
 * do not tell noise cancelling from transparency, so what a reply means is what the line before it asked: the session keeps the last line it sent and reads the
 * answer against it, as the app's SDK does.
 */
public class SkullcandyAtSession(public val product: SkullcandyProduct) : SessionTalk<String>() {
    override val pacing: Pacing = Pacing(replyMs = 1500, gapMs = 150, startGapMs = 150, pollMs = Pacing.POLL_MS, resends = 1)

    private var current = EarbudState()
    override val state: EarbudState get() = current
    override val groups: List<SettingGroup> get() = emptyList()

    /** True when the manifest lists noise cancelling for the model. */
    public val hasAnc: Boolean = "adjustableNoiseCancellation" in product.features || "activeNoiseCancellation" in product.features

    /** True when it lists the stay-aware (transparency) mode. */
    public val hasTransparency: Boolean = "adjustableStayAwareMode" in product.features || "stayAwareMode" in product.features || "ambientMode" in product.features

    private var ancOn: Boolean? = null
    private var transparencyOn: Boolean? = null
    private var pending: String? = null
    private val buffer = StringBuilder()
    private var readBacks: List<String> = emptyList()

    /** The last reply lines to each line the session sent, for the device report: the line, then what came back. */
    public val replies: MutableMap<String, List<String>> = LinkedHashMap()

    override fun start(): List<String> = buildList {
        add(SkullcandyAt.BATTERY)
        if (hasAnc) add(SkullcandyAt.ANC)
        if (hasTransparency) add(SkullcandyAt.TRANSPARENCY)
    }

    override fun poll(): List<String> = listOf(SkullcandyAt.BATTERY)

    override fun allowed(message: String): Boolean = SkullcandyAt.isAllowed(message)

    override fun bytes(message: String): ByteArray {
        pending = message
        return SkullcandyAt.line(message)
    }

    override fun describe(message: String): String = message

    override fun feed(bytes: ByteArray): Fed {
        buffer.append(bytes.decodeToString())
        var changed = false
        val answers = mutableListOf<Any>()
        while (true) {
            val end = completeAt(buffer.toString()) ?: break
            val text = buffer.substring(0, end)
            buffer.delete(0, end)
            val reply = SkullcandyAt.parse(text)
            log?.invoke("reply ${reply.lines} ok=${reply.ok} error=${reply.error}")
            guarded {
                val line = pending
                if (line != null) replies[line] = reply.lines
                if (reply.ok && line != null) changed = apply(line, reply.lines) || changed
            }
            answers += reply
        }
        return Fed(changed, answers)
    }

    override fun answers(sent: String, got: Any): Boolean = got is SkullcandyAt.Reply

    override fun takeReadBacks(): List<String> = readBacks.also { readBacks = emptyList() }

    /** The index just after the first `OK` or `ERROR` line of [text], or null while no reply has ended. */
    private fun completeAt(text: String): Int? {
        var from = 0
        while (from < text.length) {
            val nl = text.indexOf('\n', from)
            if (nl < 0) return null
            val line = text.substring(from, nl).trimEnd('\r')
            if (line.contains("OK") || line.contains("ERROR") || line.contains("NO CARRIER")) return nl + 1
            from = nl + 1
        }
        return null
    }

    /** Applies [lines], the answer to [asked], to the state; true when it changed. */
    private fun apply(asked: String, lines: List<String>): Boolean {
        val first = lines.firstOrNull() ?: return false
        when (asked) {
            SkullcandyAt.BATTERY -> {
                val b = SkullcandyAt.batteryOf(first)
                val next = current.values.toMutableMap()
                if (b.left >= 0) next[Command.BATTERY_LEFT] = Value.Number(b.left)
                if (b.right >= 0) next[Command.BATTERY_RIGHT] = Value.Number(b.right)
                if (b.left < 0 && b.right < 0 && b.single >= 0) next[Command.BATTERY_LEFT] = Value.Number(b.single)
                return replace(next)
            }
            SkullcandyAt.ANC -> ancOn = SkullcandyAt.onOffOf(first) ?: return false
            SkullcandyAt.TRANSPARENCY -> transparencyOn = SkullcandyAt.transparencyOf(first).on
            else -> return false
        }
        return refreshMode()
    }

    private fun refreshMode(): Boolean {
        if ((hasAnc && ancOn == null) || (hasTransparency && transparencyOn == null)) return false
        val mode = when {
            ancOn == true -> "anc"
            transparencyOn == true -> "transparency"
            else -> "off"
        }
        val next = current.values.toMutableMap()
        next[Command.ANC_MODE] = Value.Number(Command.ANC_MODE.labels.entries.first { it.value == mode }.key)
        return replace(next)
    }

    private fun replace(next: Map<Command, Value>): Boolean {
        if (next == current.values) return false
        current = current.copy(values = next)
        return true
    }

    override fun set(command: Command, value: Int): List<String>? {
        if (command != Command.ANC_MODE || (!hasAnc && !hasTransparency)) return null
        val mode = Command.ANC_MODE.labels[value] ?: return null
        val lines = mutableListOf<String>()
        when (mode) {
            "anc" -> if (hasAnc) {
                lines += SkullcandyAt.setAnc(true)
                if (hasTransparency) lines += SkullcandyAt.setTransparency(false)
            } else return null
            "transparency" -> if (hasTransparency) {
                if (hasAnc) lines += SkullcandyAt.setAnc(false)
                lines += SkullcandyAt.setTransparency(true)
            } else return null
            "off" -> {
                if (hasAnc) lines += SkullcandyAt.setAnc(false)
                if (hasTransparency) lines += SkullcandyAt.setTransparency(false)
            }
            else -> return null
        }
        ancOn = mode == "anc"
        transparencyOn = mode == "transparency"
        refreshMode()
        readBacks = buildList {
            if (hasAnc) add(SkullcandyAt.ANC)
            if (hasTransparency) add(SkullcandyAt.TRANSPARENCY)
        }
        return lines
    }
}
