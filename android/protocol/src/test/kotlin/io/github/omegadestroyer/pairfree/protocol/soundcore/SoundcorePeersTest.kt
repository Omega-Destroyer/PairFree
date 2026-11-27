package io.github.omegadestroyer.pairfree.protocol.soundcore

import io.github.omegadestroyer.pairfree.protocol.Setting
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The paired devices' list, laid out as in OpenSCQ30's parser tests. */
class SoundcorePeersTest {
    private fun device(connected: Boolean, last: Int, name: String): ByteArray {
        val padded = name.encodeToByteArray().copyOf(32)
        val mac = byteArrayOf(0x66, 0x14, 0x92.toByte(), 0x2C, 0x3A, last.toByte())
        return byteArrayOf((8 + padded.size).toByte(), if (connected) 1 else 0) + mac + padded
    }

    private fun SoundcorePeers.choices(key: String) = (rows().firstOrNull { it.key == key } as Setting.Choice?)?.options

    @Test
    fun theListInTwoPackets() {
        val peers = SoundcorePeers()
        assertTrue(!peers.receive(byteArrayOf(2, 1) + device(true, 1, "Pixel 6a")))
        assertTrue(peers.receive(byteArrayOf(2, 2) + device(false, 2, "Pixel 7a")))
        assertEquals(listOf("Pixel 7a"), peers.choices(SoundcorePeers.CONNECT))
        assertEquals(listOf("Pixel 6a"), peers.choices(SoundcorePeers.DISCONNECT))
        assertEquals(listOf("Pixel 6a", "Pixel 7a"), peers.choices(SoundcorePeers.DELETE))
        val connect = peers.change(SoundcorePeers.CONNECT, "Pixel 7a")!!
        assertEquals(SoundcoreCommand.PEER_CONNECT, connect.command)
        assertArrayEquals(byteArrayOf(0x66, 0x14, 0x92.toByte(), 0x2C, 0x3A, 2), connect.body)
        assertEquals(SoundcoreCommand.PEER_FORGET, peers.change(SoundcorePeers.DELETE, "Pixel 6a")!!.command)
        assertEquals(listOf("Pixel 7a"), peers.choices(SoundcorePeers.DELETE))
    }

    @Test
    fun aCutPacketChangesNothing() {
        val peers = SoundcorePeers()
        assertTrue(!peers.receive(byteArrayOf(1, 1) + device(true, 1, "Pixel 6a").copyOf(20)))
        assertTrue(peers.rows().isEmpty())
    }
}
