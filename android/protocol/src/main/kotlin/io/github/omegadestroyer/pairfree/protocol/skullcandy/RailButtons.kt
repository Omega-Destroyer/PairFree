package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.jbl.RacePacket

/**
 * The Rail and Rail ANC's button settings and voice control over Skullcandy's RACE commands (docs/skullcandy/PROTOCOL.md section 16), as the app's `RaceCommandBragiButtonSettingsGet` and `Set` and the voice control classes build them: a key map of
 * eight-byte entries (the gesture's type, the key, the event as two bytes little endian and four bytes of state), read with command 7 and written with command 3, each for one earbud.
 */
public object RailButtons {
    /** The gestures the app accepts for these two models, by the wire's type byte (the app's `SKDYGesture` single, double and triple press and the short press and hold). */
    public val GESTURES: List<Pair<Int, String>> = listOf(0x00 to "single", 0x05 to "double", 0x06 to "triple", 0x02 to "hold")

    /** The state word the app writes with each gesture. */
    private fun state(type: Int): List<Int> = if (type == 0x05) listOf(0x0E, 0, 0, 0) else listOf(0x08, 0, 0, 0)

    /** The ten actions the manifest lists for both models, by the event the app writes for each (`_actionToBytes`). */
    public val EVENTS: List<Pair<Int, String>> = listOf(
        0x60 to "play_pause", 0x94 to "stay_aware", 0x5A to "track_forward", 0x5B to "track_back", 0x0A to "volume_up", 0x0B to "volume_down",
        0xBE to "spotify", 0xC0 to "take_photo", 0x33 to "voice_assistant", 0x00 to "none",
    )

    public val OPTIONS: List<String> = EVENTS.map { it.second }

    public fun nameOf(event: Int): String? = EVENTS.firstOrNull { it.first == event }?.second

    /** One entry of the key map (the key is the button's: 0 is the power button). */
    public class Entry(public val type: Int, public val key: Int, public val event: Int)

    /** The read of the key map of one earbud (1 left, 2 right): `05 5A 03 00 07 00 <side>`. */
    public fun readHex(side: Int): String = "05 5A 03 00 07 00 0$side"

    /** The entries of a read's answer (the bytes after the command id), of the power button and of the four gestures, or null when it is not whole entries. */
    public fun parse(payload: ByteArray): List<Entry>? {
        if (payload.size < 8) return null
        val out = ArrayList<Entry>()
        var at = 0
        while (at + 8 <= payload.size) {
            val type = payload[at].toInt() and 0xFF
            val key = payload[at + 1].toInt() and 0xFF
            val event = (payload[at + 2].toInt() and 0xFF) or ((payload[at + 3].toInt() and 0xFF) shl 8)
            if (key == 0 && GESTURES.any { it.first == type }) out += Entry(type, key, event)
            at += 8
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** The write of one earbud's entries: command 3, the side and each entry as the app builds it. */
    public fun write(side: Int, entries: List<Entry>): RacePacket {
        val bytes = ArrayList<Int>()
        bytes += side
        for (e in entries) {
            bytes += e.type
            bytes += e.key
            bytes += e.event and 0xFF
            bytes += (e.event shr 8) and 0xFF
            bytes += state(e.type)
        }
        return RacePacket.of(WRITE_ID, *bytes.toIntArray())
    }

    public const val WRITE_ID: Int = 0x0003
    public const val READ_ID: Int = 0x0007

    /** Voice control on and off: `05 5A 03 00 01 00 <10|00>`. */
    public fun voiceSet(on: Boolean): String = "05 5A 03 00 01 00 " + (if (on) "10" else "00")

    /** The language the earbuds' voice control holds, read as `05 5A 03 00 09 00 01`; the answer is text. */
    public const val VOICE_LANGUAGE_GET: String = "05 5A 03 00 09 00 01"

    /** True for a frame of these commands PairFree built: the reads, the key map write and the voice control state. */
    public fun isAllowed(p: RacePacket): Boolean {
        val b = p.payload
        return when (p.id) {
            READ_ID -> b.size == 1 && (b[0].toInt() == 1 || b[0].toInt() == 2)
            WRITE_ID -> b.size >= 9 && (b.size - 1) % 8 == 0 && (b[0].toInt() == 1 || b[0].toInt() == 2) && (0 until (b.size - 1) / 8).all { i ->
                val t = b[1 + 8 * i].toInt() and 0xFF
                val ev = (b[3 + 8 * i].toInt() and 0xFF) or ((b[4 + 8 * i].toInt() and 0xFF) shl 8)
                GESTURES.any { it.first == t } && b[2 + 8 * i].toInt() == 0 && EVENTS.any { it.first == ev }
            }
            0x0001 -> b.size == 1 && (b[0].toInt() == 0x10 || b[0].toInt() == 0x00)
            0x0009 -> b.size == 1 && b[0].toInt() == 1
            else -> false
        }
    }
}
