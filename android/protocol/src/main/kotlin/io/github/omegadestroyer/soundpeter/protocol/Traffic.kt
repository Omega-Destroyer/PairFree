package io.github.omegadestroyer.soundpeter.protocol

/**
 * One line for a log: vendor, command, which command it is and whether it is a GET, SET
 * or reply, then the payload. For example `000A 0311 anc_mode set 02`.
 */
public fun Frame.summary(): String {
    val what = when {
        vendor == Wire.VENDOR_EQ -> "eq"
        vendor != Wire.VENDOR_MAIN -> "unknown"
        else -> Replies.commandFor(this)?.let { "${it.id} reply" }
            ?: Command.entries.firstOrNull { it.set == command }?.let { "${it.id} set" }
            ?: Command.entries.firstOrNull { it.get == command }?.let { "${it.id} get" }
            ?: "unknown"
    }
    val bytes = payload.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "${vendor.hex4()} ${command.hex4()} $what $bytes".trimEnd()
}
