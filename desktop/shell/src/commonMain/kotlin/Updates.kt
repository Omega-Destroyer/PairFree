package io.github.omegadestroyer.pairfree.shell

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

/** A release of PairFree on GitHub as the update check reads it. */
data class ReleaseInfo(
    /** The version, `x.y.z`. */
    val version: String,
    /** The release's page, which a user can open. */
    val pageUrl: String,
    /** The desktop program of this release for the system ([ReleaseInfo.exeName]), or null when the release has none. */
    val programUrl: String?,
    /** Its signature (`<name>.sig`, 64 bytes), or null. */
    val signatureUrl: String?,
)

/**
 * The desktop apps' update check (docs/DESKTOP.md, section 6): the only thing PairFree contacts is GitHub's release API
 * (`https://api.github.com/repos/Omega-Destroyer/PairFree/releases/latest`) and the files of the release it names, asked for
 * only when the user allowed it. Nothing about the user or the earbuds is sent.
 */
object Updates {
    const val LATEST_URL = "https://api.github.com/repos/Omega-Destroyer/PairFree/releases/latest"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The desktop program's file name in a release: `PairFree-0.15.0.exe` on Windows. */
    fun programName(version: String, extension: String = "exe") = "PairFree-$version.$extension"

    /** Reads GitHub's answer for the latest release, or null when it is not one. [extension] is the program's file type on this system. */
    fun parseLatest(text: String, extension: String = "exe"): ReleaseInfo? {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val tag = (root["tag_name"] as? JsonPrimitive)?.content ?: return null
        val version = tag.removePrefix("v")
        if (!Regex("\\d+\\.\\d+\\.\\d+").matches(version)) return null
        val page = (root["html_url"] as? JsonPrimitive)?.content ?: "https://github.com/Omega-Destroyer/PairFree/releases/latest"
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun urlOf(name: String) = assets.firstOrNull { (it["name"] as? JsonPrimitive)?.content == name }?.let { (it["browser_download_url"] as? JsonPrimitive)?.content }
        val name = programName(version, extension)
        return ReleaseInfo(version, page, urlOf(name), urlOf("$name.sig"))
    }

    /** True when version [latest] is newer than [current], both `x.y.z`. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/** SHA-256, written out so the same code checks a download on every system. */
object Sha256 {
    private val primes: List<Int> = generateSequence(2) { it + 1 }.filter { n -> (2 until n).none { n % it == 0 } }.take(64).toList()

    /** The first 32 bits of the fractional part of [x]: how SHA-256's constants are defined. */
    private fun fraction(x: Double): Int = ((x - kotlin.math.floor(x)) * 4294967296.0).toLong().toInt()

    private val K = IntArray(64) { fraction(kotlin.math.cbrt(primes[it].toDouble())) }

    private fun rotr(x: Int, n: Int) = (x ushr n) or (x shl (32 - n))

    fun digest(data: ByteArray): ByteArray {
        val h = IntArray(8) { fraction(kotlin.math.sqrt(primes[it].toDouble())) }
        val bitLength = data.size.toLong() * 8
        val padded = ByteArray(((data.size + 8) / 64 + 1) * 64)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (block in 0 until padded.size / 64) {
            for (i in 0 until 16) {
                val o = block * 64 + i * 4
                w[i] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }
}

/** The public half of the release key the program checks downloads with (made by `tools/release_signing.py keygen`). Empty until the owner made one. */
object UpdateKey {
    /** The key's two 32-byte coordinates as hex, or empty. */
    val X: String get() = UPDATE_KEY_X
    val Y: String get() = UPDATE_KEY_Y
    val present: Boolean get() = UPDATE_KEY_X.length == 64 && UPDATE_KEY_Y.length == 64
}
