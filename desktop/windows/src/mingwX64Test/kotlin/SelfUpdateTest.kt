package io.github.omegadestroyer.pairfree.windows

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The signature check, against a signature made with OpenSSL (`openssl dgst -sha256 -sign`, ECDSA P-256) by a throwaway key made for this test:
 * Windows' own BCrypt must accept it, and refuse a changed message or a changed signature.
 */
class SelfUpdateTest {
    private val x = "b9f4e8b2136a80fcf6247745a7d656016fee98a0cb29e9e52b0eb09cfc22e217"
    private val y = "799567da45bf1d9d7a4548a5645be3a95a9e6fef477bb6e604aef54abdacdb33"
    private val signature = "a5f149a4fa8a7a05e58e07103c5b555d58c9c75327c81623b0afe4f19d3a298facbbd40d4734724797ece2e0dc253ac0c5b4102bcc9cfbbff07a8567332674c8"
    private val message = "PairFree update test message".encodeToByteArray()

    @Test
    fun aGoodSignatureIsAccepted() {
        assertTrue(SelfUpdate.verify(message, SelfUpdate.hex(signature), x, y))
    }

    @Test
    fun aChangedMessageOrSignatureIsRefused() {
        assertFalse(SelfUpdate.verify("PairFree update test messagE".encodeToByteArray(), SelfUpdate.hex(signature), x, y))
        val bad = SelfUpdate.hex(signature).also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertFalse(SelfUpdate.verify(message, bad, x, y))
        assertFalse(SelfUpdate.verify(message, SelfUpdate.hex(signature).copyOf(63), x, y))
    }

    @Test
    fun withoutAKeyNothingIsAccepted() {
        assertFalse(SelfUpdate.verify(message, SelfUpdate.hex(signature), "", ""))
    }

    @Test
    fun theProgramFindsItsOwnPath() {
        assertNotNull(SelfUpdate.programPath().takeIf { it.isNotEmpty() })
    }
}
