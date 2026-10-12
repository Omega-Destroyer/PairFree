package io.github.omegadestroyer.soundpeter.protocol

/**
 * The lines tools/probe.py prints, built from the replies the app has received, so a
 * report copied from a phone reads the same as one from the probe. A test checks them
 * against data/reports/S5_0.4.9.txt.
 */
public object DeviceReport {
    // The probe's short names, so the lines match its output.
    private val GESTURES = mapOf(
        1 to "tap L", 2 to "tap R", 3 to "double L", 4 to "double R",
        5 to "triple L", 6 to "triple R", 7 to "hold L", 8 to "hold R",
    )
    private val ACTIONS = mapOf(
        0 to "none", 1 to "vol+", 2 to "vol-", 3 to "play/pause", 4 to "game mode",
        5 to "prev", 6 to "next", 7 to "ANC", 8 to "assistant",
    )

    /** One line per command in [asked], in order: the decoded reply and its raw bytes, or `no reply`. */
    /**
     * The GAIA v3 features a Qualcomm model listed, in the same layout, for example
     * `gaia_features        0001  00 01 05 06 07 08 09 0c 0d`. tools/gen_models_table.py reads
     * it to see which models have the fit test (feature `09`).
     */
    public fun gaiaLine(features: Set<Int>): String =
        "${"gaia_features".padEnd(20)} ${Gaia.GET_FEATURES.hex4()}  " +
            features.sorted().joinToString(" ") { it.toString(16).padStart(2, '0') }

    public fun lines(replies: Map<Command, ByteArray>, asked: List<Command> = Requests.ON_CONNECT): List<String> =
        asked.map { command -> line(command, replies[command]) }

    public fun line(command: Command, payload: ByteArray?): String {
        val get = requireNotNull(command.get) { "${command.id} has no GET" }
        val result = if (payload == null) "no reply" else "${describe(command, payload).padEnd(40)} raw=${payload.spaced()}"
        return "${command.id.padEnd(20)} ${get.hex4()}  $result"
    }

    /** The probe's `describe`: what a reply says, without its raw bytes. */
    public fun describe(command: Command, payload: ByteArray): String {
        if (payload.isEmpty()) return "(empty)"
        val u = IntArray(payload.size) { payload[it].toInt() and 0xFF }
        val status = u[0]
        val body = u.copyOfRange(1, u.size)
        if (command == Command.FIRMWARE_VERSION) {
            val text = (if (status == 0) payload.copyOfRange(1, payload.size) else payload).decodeToString()
            return "'$text'"
        }
        if (status != 0 && u.size <= 2) return "status $status (not supported or error)"
        if (command == Command.CUSTOM_KEYS) {
            return pairs(body).joinToString(", ") { (g, a) -> "${GESTURES[g] ?: g} -> ${ACTIONS[a] ?: a}" }
        }
        if (command == Command.ANC_LEVEL || command == Command.TRANSPARENCY_LEVEL) {
            // a leading status byte is dropped only when the length is odd, as the official app does
            val levels = if (u.size % 2 == 1 && status == 0) body else u
            return pairs(levels).joinToString(", ") { (level, on) ->
                "0x" + level.toString(16).uppercase().padStart(2, '0') + if (on == 1) "*" else ""
            } + "   (* = active)"
        }
        if (body.size == 1) return body[0].toString()
        return body.joinToString(" ") { it.toString(16).padStart(2, '0') }
    }

    private fun pairs(u: IntArray): List<Pair<Int, Int>> = (0 until u.size - 1 step 2).map { u[it] to u[it + 1] }

    private fun ByteArray.spaced(): String = joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
