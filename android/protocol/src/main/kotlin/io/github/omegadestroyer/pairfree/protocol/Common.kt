package io.github.omegadestroyer.pairfree.protocol

import kotlin.math.pow as libPow

// What the protocol code needs that Kotlin has only on the JVM, written in common Kotlin so the module also compiles for
// Windows and macOS (Kotlin/Native). Nothing here may import java.*.

/** The few functions of Java's Math the ports of vendors' code use, under their Java names so a port reads like its source. */
internal object PMath {
    const val PI: Double = kotlin.math.PI

    /** Java's Math.round: the nearest whole number, a tie going up. */
    fun round(x: Double): Long = kotlin.math.floor(x + 0.5).toLong()
    fun round(x: Float): Int = kotlin.math.floor(x + 0.5f).toInt()
    fun pow(a: Double, b: Double): Double = a.libPow(b)
    fun sqrt(x: Double): Double = kotlin.math.sqrt(x)
    fun log10(x: Double): Double = kotlin.math.log10(x)
    fun ceil(x: Double): Double = kotlin.math.ceil(x)
    fun floor(x: Double): Double = kotlin.math.floor(x)
    fun sin(x: Double): Double = kotlin.math.sin(x)
    fun cos(x: Double): Double = kotlin.math.cos(x)
}

/** A byte as two small hex digits. */
internal fun Byte.toHex2(): String = (toInt() and 0xFF).toString(16).padStart(2, '0')

/** The low byte of a number as two small hex digits. */
internal fun Int.toHex2(): String = (this and 0xFF).toString(16).padStart(2, '0')

/** A number in small hex digits, filled with zeros to [width]. */
internal fun Int.toHex(width: Int): String = toString(16).padStart(width, '0')

/** A number with one digit after the point, a point whatever the phone's language. */
internal fun Double.oneDecimal(): String {
    val tenths = PMath.round(kotlin.math.abs(this) * 10)
    return (if (this < 0 && tenths != 0L) "-" else "") + (tenths / 10) + "." + (tenths % 10)
}

/** The values in the order of their keys. */
internal fun <K : Comparable<K>, V> Map<K, V>.sortedValues(): List<V> = entries.sortedBy { it.key }.map { it.value }

/** The same entries in the order of their keys. */
internal fun <K : Comparable<K>, V> Map<K, V>.sortedByKey(): Map<K, V> = entries.sortedBy { it.key }.associate { it.key to it.value }
