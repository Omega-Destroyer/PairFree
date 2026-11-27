package io.github.omegadestroyer.pairfree.protocol.jabra

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia3Packet
import io.github.omegadestroyer.pairfree.protocol.sennheiser.Gaia3Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

/**
 * Jabra's GAIA products (docs/jabra/PROTOCOL.md, "GAIA devices"). The replies are built from the plugins' own parsers in Sound+ 5.22.0; no Jabra product has been tried.
 */
class JabraGaiaSessionTest {
    private val elite4 = JabraModels.ALL.single { it.name == "Jabra Elite 4" }

    private fun reply(feature: Int, command: Int, vararg payload: Int, vendor: Int = JabraGaiaSession.VENDOR): ByteArray =
        Gaia3Packet(vendor, (feature shl 9) or (Gaia3Packet.TYPE_RESPONSE shl 7) or command, ByteArray(payload.size) { payload[it].toByte() }).bytes()

    private fun opened(): Pair<JabraGaiaSession, List<Gaia3Packet>> {
        val s = JabraGaiaSession(elite4)
        s.start()
        // The features the Elite 4 lists: no more to come, then a feature id and its version each.
        s.feed(reply(1, 1, 0, 5, 1, 10, 1, 4, 1, 3, 1, 16, 1, 9, 1, 12, 1, 7, 1))
        return s to s.takeOutbox()
    }

    private fun name(p: Gaia3Packet) = "%02x/%02x".format(p.feature, p.command)

    @Test
    fun theConversationOpensWithTheFeatureListAndThenReadsEachFeature() {
        val s = JabraGaiaSession(elite4)
        val start = s.start().single()
        assertEquals("ff030000" + "0067" + "0201", start.bytes().hex())
        val (_, reads) = opened()
        val names = reads.map(::name)
        // The notifications of the features that send them, then one read each; HearThrough's reads carry the plugin's dummy byte.
        assertEquals(5, names.count { it == "02/01" })
        for (read in listOf("05/01", "0a/04", "0a/06", "04/01", "04/03", "04/05", "04/07", "03/01", "03/04", "03/05", "10/01", "09/01", "0c/01", "0c/02", "07/02")) assertTrue(read in names, read)
        assertEquals("00", reads.first { name(it) == "04/03" }.payload.hex())
        assertTrue(reads.all { it.vendor == JabraGaiaSession.VENDOR })
    }

    @Test
    fun batteryAndNoiseControlComeFromTheVendorsPlugins() {
        val (s, _) = opened()
        s.feed(reply(5, 1, 80, 70, 50))
        assertEquals(80, s.state.batteryLeft)
        assertEquals(70, s.state.batteryRight)
        assertEquals(50, s.state.batteryCase)
        s.feed(reply(10, 4, 1))
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "anc" }.key, s.state.number(Command.ANC_MODE))
        // A change on the earbuds arrives as a notification under another id; it updates the same value.
        s.feed(Gaia3Packet(JabraGaiaSession.VENDOR, (10 shl 9) or (Gaia3Packet.TYPE_NOTIFICATION shl 7) or 1, byteArrayOf(2)).bytes())
        assertEquals(Command.ANC_MODE.labels.entries.first { it.value == "transparency" }.key, s.state.number(Command.ANC_MODE))
        val off = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
        val write = s.set(Command.ANC_MODE, off)!!.single()
        assertEquals("0a05", "%02x%02x".format(write.feature, write.command))
        assertEquals("00", write.payload.hex())
    }

    @Test
    fun hearThroughLevelsRunStrongestFirstAndTheMixIsAThreeStateSwitch() {
        val (s, _) = opened()
        s.feed(reply(4, 7, 4))
        s.feed(reply(4, 3, 0))
        s.feed(reply(4, 5, 1))
        val noise = s.groups.first { it.key == "jb_noise" }.settings
        assertEquals(Setting.Level("jb_ht_level", 5, 1, 5), noise.single { it.key == "jb_ht_level" })
        assertEquals(true, (noise.single { it.key == "jb_ht_mix" } as Setting.Toggle).on)
        assertEquals("04", s.change(SettingChange.Level("jb_ht_level", 1))!!.single().let { "%02x".format(it.payload[0]) })
        val mix = s.change(SettingChange.Toggle("jb_ht_mix", false))!!.single()
        assertEquals("0406", name(mix).replace("/", ""))
        assertEquals("02", mix.payload.hex())
    }

    @Test
    fun theLoopIsOneToThreeDistinctModesAndSwitchesUseOneAndTwo() {
        val (s, _) = opened()
        s.feed(reply(10, 6, 2, 1))
        s.feed(reply(16, 5, 1))
        val rows = s.groups.flatMap { it.settings }
        assertEquals("HT_ANC", (rows.single { it.key == "jb_loop" } as Setting.Choice).value)
        // HearThrough, ANC, off: the sound mode ids (off 0, ANC 1, HearThrough 2), not the GNP loop's ordinals.
        val loop = s.change(SettingChange.Choice("jb_loop", "HT_ANC_Off"))!!.single()
        assertEquals("0a07", name(loop).replace("/", ""))
        assertEquals("020100", loop.payload.hex())
        assertEquals(true, (rows.single { it.key == "jb_gaia_auto_mute" } as Setting.Toggle).on)
        val off = s.change(SettingChange.Toggle("jb_gaia_auto_mute", false))!!.single()
        assertEquals("1006", name(off).replace("/", ""))
        assertEquals("02", off.payload.hex())
    }

    @Test
    fun theEqualizerReadsItsBandsOverTheRangeTheMaximumGainsName() {
        val (s, _) = opened()
        // Five bands, 6 dB each way; the band read follows the maximum gains' answer.
        s.feed(reply(7, 2, 0, 4, 6, 6, 6, 6, 6))
        val read = s.takeOutbox().single()
        assertEquals(JabraGaiaSession.QUALCOMM, read.vendor)
        assertEquals("0004", read.payload.hex())
        // Seven bytes a band: frequency, Q times 4096, a filter type and the gain in 1/60 dB.
        val bands = intArrayOf(0, 4) + listOf(60, 250, 1000, 4000, 8000).flatMap { hz -> listOf(hz shr 8, hz and 0xFF, 0x10, 0x00, 0, 0, 0) }
        s.feed(reply(5, 5, *bands, vendor = JabraGaiaSession.QUALCOMM))
        assertTrue(s.eqKnown)
        assertEquals(listOf(60, 250, 1000, 4000, 8000), s.eqBandsHz)
        assertEquals(6.0, s.eqMaxDb, 0.0)
        // Bass boost is 60 % and -33 % of 6 dB on the first two bands; the gains go as 1/60 dB, truncated toward zero, over the whole range.
        val frames = s.eqCurve(s.eqPresets.single { it.first == "BASS_BOOST" }.second)!!
        val packet = Gaia3Parser(setOf(JabraGaiaSession.QUALCOMM)).feed(frames.single()).single()
        assertEquals("0004" + "00d8" + "ff8a" + "0000" + "0000" + "0000", packet.payload.hex())
        assertNull(s.eqCurve(listOf(7.0, 0.0, 0.0, 0.0, 0.0)))
    }

    @Test
    fun onlyThePluginsOperationsGoOut() {
        val (s, _) = opened()
        assertTrue(s.allowed(Gaia3Packet(JabraGaiaSession.VENDOR, (10 shl 9) or 5, byteArrayOf(1))))
        // The mute reminder's own operation (10) is not sent, nor any feature the tables do not name.
        assertFalse(s.allowed(Gaia3Packet(JabraGaiaSession.VENDOR, (16 shl 9) or 10, byteArrayOf(1))))
        assertFalse(s.allowed(Gaia3Packet(JabraGaiaSession.VENDOR, (63 shl 9) or 1, byteArrayOf(1))))
        assertFalse(s.allowed(Gaia3Packet(JabraGaiaSession.QUALCOMM, (0 shl 9) or 1)))
        assertTrue(s.allowed(Gaia3Packet(JabraGaiaSession.QUALCOMM, (5 shl 9) or 6, byteArrayOf(0, 0, 0, 0))))
    }
}
