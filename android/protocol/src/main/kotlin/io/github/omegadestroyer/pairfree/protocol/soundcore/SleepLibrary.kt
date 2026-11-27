package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.toHex2

/**
 * What PairFree remembers on the phone about the sounds it has prepared for a Sleep A30 (issue #23): a catalogue of prepared sounds, each with the media ID
 * written in its file, and which sound each earbud slot was last given. Two files with the same encoded audio and sound type are the same sound: they share
 * one entry and one media ID, so a sound already on the earbuds is not sent again. The earbuds' own inventory (`13 01`) is not read here, so what the
 * library says is what PairFree last sent, and a slot changed by the Soundcore app since is not noticed (the screen offers to send anyway).
 */
public class SleepLibrary(
    entries: List<Entry> = emptyList(),
    slots: List<Slot> = emptyList(),
) {
    /** A prepared sound. [hash] is the SHA-256 (hex) of its encoded audio and sound type, not of the container: the media ID does not change it. */
    public data class Entry(
        val hash: String,
        val mediaId: Long,
        val name: String,
        val seconds: Int,
        val bitRate: Int,
        val soundType: Int,
        val added: Long,
    )

    /** What PairFree last sent to [slot] of the earbuds at [address], and when. */
    public data class Slot(val address: String, val slot: Int, val hash: String, val time: Long)

    private val entryList = entries.toMutableList()
    private val slotList = slots.toMutableList()

    public val entries: List<Entry> get() = entryList.sortedByDescending { it.added }
    public val slots: List<Slot> get() = slotList.toList()

    public fun find(hash: String): Entry? = entryList.firstOrNull { it.hash == hash }

    public fun byMediaId(mediaId: Long): Entry? = entryList.firstOrNull { it.mediaId == mediaId }

    /** Adds [entry], or keeps the one already there with its media ID (a sound is stored once). Returns the stored entry. */
    public fun add(entry: Entry): Entry = find(entry.hash) ?: entry.also { entryList += it }

    /** True when the last send to [slot] of [address] was the sound with [hash]. */
    public fun holds(address: String, slot: Int, hash: String): Boolean =
        slotList.any { it.address == address && it.slot == slot && it.hash == hash }

    /** The sound last sent to [slot] of [address], if the library still has it. */
    public fun heldBy(address: String, slot: Int): Entry? =
        slotList.firstOrNull { it.address == address && it.slot == slot }?.let { find(it.hash) }

    /** The slots of [address] that hold [hash], by their numbers. */
    public fun slotsHolding(address: String, hash: String): List<Int> =
        slotList.filter { it.address == address && it.hash == hash }.map { it.slot }.sorted()

    public fun sent(address: String, slot: Int, hash: String, time: Long) {
        slotList.removeAll { it.address == address && it.slot == slot }
        slotList += Slot(address, slot, hash, time)
    }

    /** Takes a sound out of the catalogue. The earbuds are not touched; the slots that held it are forgotten. */
    public fun remove(hash: String) {
        entryList.removeAll { it.hash == hash }
        slotList.removeAll { it.hash == hash }
    }

    /** One line per entry or slot, fields separated by tabs. */
    public fun serialize(): String = buildString {
        for (e in entryList) appendLine(listOf("entry", e.hash, e.mediaId, clean(e.name), e.seconds, e.bitRate, e.soundType, e.added).joinToString("\t"))
        for (s in slotList) appendLine(listOf("slot", s.address, s.slot, s.hash, s.time).joinToString("\t"))
    }

    private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    public companion object {
        /** Reads what [serialize] wrote; a line it cannot read is skipped. */
        public fun parse(text: String): SleepLibrary {
            val entries = mutableListOf<Entry>()
            val slots = mutableListOf<Slot>()
            for (line in text.lineSequence()) {
                val f = line.split('\t')
                try {
                    when {
                        f[0] == "entry" && f.size >= 8 -> entries += Entry(f[1], f[2].toLong(), f[3], f[4].toInt(), f[5].toInt(), f[6].toInt(), f[7].toLong())
                        f[0] == "slot" && f.size >= 5 -> slots += Slot(f[1], f[2].toInt(), f[3], f[4].toLong())
                    }
                } catch (_: NumberFormatException) {
                }
            }
            return SleepLibrary(entries, slots)
        }

        /** The sound's identity: SHA-256 of [SleepAudioFile]'s encoded audio (after the header) and the sound type, as hex. */
        public fun hashOf(file: ByteArray, headerSize: Int = SleepAudioFile.HEADER_SIZE, soundType: Int): String {
            val data = file.copyOfRange(headerSize, file.size) + byteArrayOf(soundType.toByte())
            return Sha256.hex(data)
        }
    }
}

/** SHA-256 without java.*: the protocol module stays free of it so it can go multiplatform. */
internal object Sha256 {
    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b.toInt(), 0x59f111f1.toInt(), 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01.toInt(), 0x243185be.toInt(), 0x550c7dc3.toInt(), 0x72be5d74.toInt(), 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6.toInt(), 0x240ca1cc.toInt(), 0x2de92c6f.toInt(), 0x4a7484aa.toInt(), 0x5cb0a9dc.toInt(), 0x76f988da.toInt(),
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351.toInt(), 0x14292967.toInt(),
        0x27b70a85.toInt(), 0x2e1b2138.toInt(), 0x4d2c6dfc.toInt(), 0x53380d13.toInt(), 0x650a7354.toInt(), 0x766a0abb.toInt(), 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070.toInt(),
        0x19a4c116.toInt(), 0x1e376c08.toInt(), 0x2748774c.toInt(), 0x34b0bcb5.toInt(), 0x391c0cb3.toInt(), 0x4ed8aa4a.toInt(), 0x5b9cca4f.toInt(), 0x682e6ff3.toInt(),
        0x748f82ee.toInt(), 0x78a5636f.toInt(), 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    fun hex(data: ByteArray): String = digest(data).joinToString("") { it.toHex2() }

    fun digest(data: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372.toInt(), 0xa54ff53a.toInt(), 0x510e527f.toInt(), 0x9b05688c.toInt(), 0x1f83d9ab.toInt(), 0x5be0cd19.toInt())
        val bits = data.size.toLong() * 8
        val padded = ByteArray(((data.size + 8) / 64 + 1) * 64)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bits ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (block in 0 until padded.size / 64) {
            for (t in 0 until 16) {
                val o = block * 64 + t * 4
                w[t] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }
}
