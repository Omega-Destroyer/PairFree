package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.Setting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** BES's TOTA channel (docs/skullcandy/PROTOCOL.md section 12): the framing, the key from the two buffers, and a session against a simulated earbud that holds the same key. */
class SkullcandyBesTest {
    private fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun theKeyComesFromTheTwoBuffers() {
        // computed with Python's hashlib from the app's byte-wise rules (sum, product, AND, OR, XOR, sum of squares by index mod 6)
        val host = ByteArray(20) { (it * 7 + 3).toByte() }
        val theirs = ByteArray(17) { (it * 13 + 5).toByte() }
        assertEquals("08b4113c26c88080297ace90f8e421eca6248100", hex(BesTota.mix(theirs, host)))
        val hash = BesTota.hashOf(host, theirs)
        assertEquals("508b085fc9b113f48b0aa07ffbd7802b51c28df4572225b756a0f6811f9ce4e6", hex(hash))
        assertEquals("5008c9138ba0fb80518d572556f61fe4", hex(BesTota.keyOf(hash)))
    }

    @Test
    fun aFrameCarriesItsLengthAndCrc() {
        val frame = BesTota.frame(hex("0963020001 00"))
        assertEquals("48454144" + "06000000" + "096302000100" + "3c044dfb" + "5441494c", hex(frame))
        val wire = BesTota.wire(frame)
        assertEquals(0x9F, wire[0].toInt() and 0xFF)
        assertEquals(frame.size, wire[1].toInt())
        val reader = BesTota.Reader()
        // a read split in two arrives whole, a bad CRC is dropped
        assertTrue(reader.feed(wire.copyOfRange(0, 9)).isEmpty())
        assertEquals("096302000100", hex(reader.feed(wire.copyOfRange(9, wire.size)).single()))
        val bad = wire.copyOf().also { it[it.size - 6] = (it[it.size - 6] + 1).toByte() }
        assertTrue(BesTota.Reader().feed(bad).isEmpty())
    }

    @Test
    fun theHelloNamesTheBuffer() {
        val hello = BesTota.hello(ByteArray(16) { 1 })
        assertEquals("0110" + "1100" + "10" + "01".repeat(16), hex(hello))
    }

    @Test
    fun gainsRoundTripOverTheWireRange() {
        assertEquals("0080", hex(BesEq.gain(-5.0)))
        assertEquals("ff7f", hex(BesEq.gain(5.0)))
        assertEquals("0000", hex(BesEq.gain(0.0)))
        for (g in -5..5) {
            val b = BesEq.gain(g.toDouble())
            assertEquals(g.toDouble(), BesEq.db(b[0].toInt(), b[1].toInt()), 0.001)
        }
        assertEquals("0080", hex(BesEq.gain(-9.0)))
    }

    @Test
    fun theCurveWriteHasTheAppsLayout() {
        val m = BesEq.curve(listOf(60, 250, 1000, 4000, 12000), listOf(0.0, 0.0, 0.0, 0.0, 0.0))
        assertEquals("0863" + "2100" + "0300" + "05" + "01" + "0000" + "3c00" + "2a" + "01" + "0000" + "fa00" + "2a" + "01" + "0000" + "e803" + "2a" + "01" + "0000" + "a00f" + "2a" + "01" + "0000" + "e02e" + "2a", hex(m.payload))
    }

    /** An earbud that answers the handshake and every read as the app's code says the earbuds do. */
    private class Earbud(val host: SkullcandyBesTalk) {
        val theirs = ByteArray(24) { (it * 11 + 1).toByte() }
        var key: ByteArray? = null
        val reader = BesTota.Reader()
        val got = ArrayList<ByteArray>()

        fun receive(wire: ByteArray, hostBuffer: ByteArray): List<ByteArray> {
            val out = ArrayList<ByteArray>()
            for (payload in reader.feed(wire)) {
                if (key == null && payload[0].toInt() == 0x01) {
                    val n = payload[4].toInt() and 0xFF
                    val buf = payload.copyOfRange(5, 5 + n)
                    val h = BesTota.hashOf(buf, theirs)
                    key = BesTota.keyOf(h)
                    out += BesTota.plainWire(byteArrayOf(0x02, 0x10, (theirs.size + 1).toByte(), 0x00, theirs.size.toByte()) + theirs)
                } else if (payload[0].toInt() == 0x03) {
                    out += BesTota.plainWire(byteArrayOf(0x00, 0x10) + ByteArray(10) + "suc".encodeToByteArray() + ByteArray(4))
                } else {
                    val plain = Aes128.cbcDecrypt(key!!, BesTota.IV, payload)
                    got += plain
                }
            }
            return out
        }

        fun reply(command: Int, vararg data: Int, read: Boolean = true): ByteArray {
            val body = byteArrayOf(if (read) 0x0B else 0x0A, 0x63, (3 + data.size).toByte(), 0x00, command.toByte(), 0x00, 0x00) + ByteArray(data.size) { data[it].toByte() }
            return BesTota.secureWire(key!!, body)
        }
    }

    private fun handshake(talk: SkullcandyBesTalk, bud: Earbud) {
        val hello = talk.start().single()
        assertTrue(talk.allowed(hello))
        var wire = bud.receive(talk.bytes(hello), ByteArray(0))
        // the earbuds' buffer comes back, the session confirms
        wire.forEach { talk.feed(it) }
        val confirm = talk.takeOutbox().single()
        assertEquals("confirm", confirm.label)
        assertTrue(talk.allowed(confirm))
        wire = bud.receive(talk.bytes(confirm), ByteArray(0))
        assertFalse(talk.secured)
        wire.forEach { talk.feed(it) }
        assertTrue(talk.secured)
    }

    @Test
    fun theSessionAgreesAKeyThenReadsWhatTheModelHas() {
        val product = SkullcandyModels.fromName("DIME 540 ANC")!!
        val talk = SkullcandyBesTalk(product, Random(7))
        val bud = Earbud(talk)
        handshake(talk, bud)
        val reads = talk.takeOutbox()
        assertTrue(reads.all { talk.allowed(it) })
        assertEquals(BesCommand.BATTERY, reads.first().command)
        assertTrue(reads.any { it.command == BesCommand.HEARING })
        assertTrue(reads.any { it.command == BesCommand.CUSTOM_EQ })
        assertTrue(reads.none { it.command == BesCommand.SERIAL })
        // they are encrypted on the wire: the earbud reads them back
        reads.forEach { bud.receive(talk.bytes(it), ByteArray(0)) }
        assertEquals(reads.map { hex(it.payload) }, bud.got.map { hex(it).substring(0, 12) })
    }

    @Test
    fun repliesFillTheScreens() {
        val product = SkullcandyModels.fromName("DIME 540 ANC")!!
        val talk = SkullcandyBesTalk(product, Random(7))
        val bud = Earbud(talk)
        handshake(talk, bud)
        talk.takeOutbox()
        talk.feed(bud.reply(BesCommand.BATTERY, 80, 75))
        talk.feed(bud.reply(BesCommand.HEARING, 2, 60))
        talk.feed(bud.reply(BesCommand.MULTIPOINT, 1))
        talk.feed(bud.reply(BesCommand.LOW_LATENCY, 0))
        talk.feed(bud.reply(BesCommand.TOUCH, 1))
        assertEquals(80, talk.state.number(Command.BATTERY_LEFT))
        assertEquals(75, talk.state.number(Command.BATTERY_RIGHT))
        assertEquals("anc", Command.ANC_MODE.labels[talk.state.number(Command.ANC_MODE)])
        val rows = talk.groups.flatMap { it.settings }
        assertEquals(6, (rows.first { it.key == SkullcandyBesTalk.ANC_LEVEL } as Setting.Level).value)
        assertEquals(true, (rows.first { it.key == SkullcandyBesTalk.MULTIPOINT } as Setting.Toggle).on)
        assertEquals(false, (rows.first { it.key == SkullcandyBesTalk.GAMING_MODE } as Setting.Toggle).on)
        assertEquals(true, (rows.first { it.key == SkullcandyBesTalk.TOUCH_CONTROL } as Setting.Toggle).on)
    }

    @Test
    fun writesGoOutAsTheAppsCommands() {
        val product = SkullcandyModels.fromName("DIME 540 ANC")!!
        val talk = SkullcandyBesTalk(product, Random(7))
        val bud = Earbud(talk)
        handshake(talk, bud)
        talk.takeOutbox()
        talk.feed(bud.reply(BesCommand.HEARING, 0, 50))
        val anc = Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key
        assertEquals("086304000900" + "0232", hex(talk.set(Command.ANC_MODE, anc)!!.single().payload))
        val off = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
        assertEquals("08630300090000", hex(talk.set(Command.ANC_MODE, off)!!.single().payload))
        assertEquals("08630300050001", hex(talk.change(SettingChange.Toggle(SkullcandyBesTalk.MULTIPOINT, true))!!.single().payload))
        assertEquals("08630300060001", hex(talk.change(SettingChange.Toggle(SkullcandyBesTalk.GAMING_MODE, true))!!.single().payload))
        assertTrue(talk.allowed(talk.set(Command.ANC_MODE, anc)!!.single()))
    }

    @Test
    fun theEqualiserReadsBandsThenWritesACurve() {
        val product = SkullcandyModels.fromName("DIME 540 ANC")!!
        val talk = SkullcandyBesTalk(product, Random(7))
        val bud = Earbud(talk)
        handshake(talk, bud)
        talk.takeOutbox()
        assertNull(talk.eqCurve(List(5) { 0.0 }))
        val bands = intArrayOf(5) + listOf(60, 250, 1000, 4000, 12000).flatMap { hz -> listOf(1, 0, 0, hz and 0xFF, hz shr 8, 0x2A) }
        talk.feed(bud.reply(BesCommand.CUSTOM_EQ, *bands))
        talk.feed(bud.reply(BesCommand.EQ_MODE, 3))
        assertEquals(listOf(60, 250, 1000, 4000, 12000), talk.eqBandsHz)
        assertEquals("Bass Boost", talk.eqPresetName)
        val frames = talk.eqCurve(listOf(1.0, 0.0, -2.0, 0.0, 3.0))!!
        assertEquals("08630300020065", hex(frames[0]))
        assertTrue(frames.all { talk.allowed(talk.messageOf(it)) })
        assertEquals("05", hex(frames[1]).substring(12, 14))
        val eq = SkullcandyBesBandEq(talk)
        assertEquals(5, eq.eqRangeDb)
        assertEquals(listOf(1, 0, -2, 0, 3), eq.eqReport!!.gains)
        assertNotNull(eq.eqPresetNames.firstOrNull { it == "Music" })
    }

    @Test
    fun theButtonsAreReadAndWrittenWhole() {
        val product = SkullcandyModels.fromName("DIME 540 ANC")!!
        val talk = SkullcandyBesTalk(product, Random(7))
        val bud = Earbud(talk)
        handshake(talk, bud)
        assertTrue(talk.takeOutbox().any { it.command == BesCommand.BUTTONS })
        // single press play/pause, double next, triple back, hold voice assistant
        talk.feed(bud.reply(BesCommand.BUTTONS, 2, 0x08, 3, 0, 2, 0x09, 2, 0, 2, 0x0A, 1, 0, 2, 0x06, 6, 0))
        val rows = talk.groups.single { it.key == "sk_buttons" }.settings.map { it as io.github.omegadestroyer.pairfree.protocol.Setting.Choice }
        assertEquals(listOf("sk_button_single", "sk_button_double", "sk_button_triple", "sk_button_hold"), rows.map { it.key })
        assertEquals(listOf("play_pause", "track_forward", "track_back", "voice_assistant"), rows.map { it.value })
        val out = talk.change(SettingChange.Choice("sk_button_double", "volume_up"))!!.single()
        assertEquals("08631200" + "0400" + "02080300" + "02090400" + "020a0100" + "02060600", hex(out.payload))
        assertTrue(talk.allowed(out))
        assertEquals("volume_up", (talk.groups.single { it.key == "sk_buttons" }.settings[1] as io.github.omegadestroyer.pairfree.protocol.Setting.Choice).value)
        // a model without custom button actions has no such read
        val icon = SkullcandyBesTalk(SkullcandyModels.fromName("Icon 180")!!, Random(7))
        val bud2 = Earbud(icon)
        handshake(icon, bud2)
        assertTrue(icon.takeOutbox().none { it.command == BesCommand.BUTTONS })
    }

    @Test
    fun theSerialNumberIsNeverRead() {
        val product = SkullcandyModels.fromName("DIME 540 ANC")!!
        val talk = SkullcandyBesTalk(product, Random(1))
        val bud = Earbud(talk)
        handshake(talk, bud)
        assertFalse(talk.allowed(BesCommand.read(BesCommand.SERIAL)))
        assertFalse(talk.allowed(BesCommand.write(BesCommand.SERIAL, 1)))
        assertFalse(talk.allowed(BesCommand.write(BesCommand.FIRMWARE, 1)))
    }
}
