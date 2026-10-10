package io.github.omegadestroyer.soundpeter.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RequestsTest {
    @Test
    fun framesMatchTheVectors() {
        assertEquals("ff040000000a0309", Requests.get(Command.FIRMWARE_VERSION).hex())
        assertEquals("ff040001000a031102", Requests.set(Command.ANC_MODE, 2).hex())
        assertEquals("ff040002000a03ab0306", Requests.set(Command.CUSTOM_KEYS, 3, 6).hex())
    }

    @Test
    fun resetAndRebootAreRefused() {
        assertThrows(IllegalArgumentException::class.java) { Requests.set(Command.FACTORY_RESET) }
        assertThrows(IllegalArgumentException::class.java) { Requests.set(Command.REBOOT) }
    }

    @Test
    fun factoryResetHasItsOwnNamedRequest() {
        assertEquals("ff040000000a0305", Requests.factoryResetConfirmedByUser().hex())
    }

    @Test
    fun readBack() {
        assertEquals(Command.ANC_MODE, Requests.readBack(Command.ANC_MODE))
        assertEquals(Command.CUSTOM_KEYS, Requests.readBack(Command.RESET_CUSTOM_KEYS))
        assertEquals(null, Requests.readBack(Command.FACTORY_RESET))
    }

    @Test
    fun onConnectOnlyReads() {
        assertTrue(Requests.ON_CONNECT.all { it.get != null })
    }

    @Test
    fun onConnectMatchesTheProbe() {
        // tools/probe.py: `    "name":   (0x0309, ...` inside COMMANDS = { ... }
        val probe = File(dataDir.parentFile, "tools/probe.py").readText()
        val table = probe.substringAfter("COMMANDS = {").substringBefore("\n}")
        val names = Regex("""^\s+"(\w+)":\s+\(0x""", RegexOption.MULTILINE).findAll(table).map { it.groupValues[1] }.toList()
        assertEquals(names, Requests.ON_CONNECT.map { it.id })
    }
}
