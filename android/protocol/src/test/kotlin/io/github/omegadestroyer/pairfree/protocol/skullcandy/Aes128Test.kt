package io.github.omegadestroyer.pairfree.protocol.skullcandy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Aes128Test {
    private fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun fips197AppendixC1() {
        val key = hex("000102030405060708090a0b0c0d0e0f")
        val plain = hex("00112233445566778899aabbccddeeff")
        val cipher = Aes128.encryptBlock(key, plain)
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", hex(cipher))
        assertEquals(hex(plain), hex(Aes128.decryptBlock(key, cipher)))
    }

    @Test
    fun nistSp80038aCbcFirstBlocks() {
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val iv = hex("000102030405060708090a0b0c0d0e0f")
        val plain = hex("6bc1bee22e409f96e93d7e117393172a" + "ae2d8a571e03ac9c9eb76fac45af8e51")
        val cipher = Aes128.cbcEncryptZeroPadded(key, iv, plain)
        assertEquals("7649abac8119b246cee98e9b12e9197d" + "5086cb9b507219ee95db113a917678b2", hex(cipher))
        assertEquals(hex(plain), hex(Aes128.cbcDecrypt(key, iv, cipher)))
    }

    @Test
    fun zeroPaddingToWholeBlocks() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16) { it.toByte() }
        val data = byteArrayOf(1, 2, 3)
        val cipher = Aes128.cbcEncryptZeroPadded(key, iv, data)
        assertEquals(16, cipher.size)
        val back = Aes128.cbcDecrypt(key, iv, cipher)
        assertEquals("010203" + "0".repeat(26), hex(back))
    }
}
