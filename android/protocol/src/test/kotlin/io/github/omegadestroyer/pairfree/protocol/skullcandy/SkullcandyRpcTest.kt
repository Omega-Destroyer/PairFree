package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Bragi's protobuf RPC for the Method 360 and 540 ANC and the Crusher 1080 ANC (docs/skullcandy/PROTOCOL.md section 15), against a simulated earbud. */
class SkullcandyRpcTest {
    private fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    private fun crusher() = SkullcandyRpcTalk(SkullcandyModels.fromName("Crusher 1080 ANC")!!)
    private fun method() = SkullcandyRpcTalk(SkullcandyModels.fromName("Method 540 ANC")!!)

    /** A response of the earbuds to [request] with [body]. */
    private fun response(request: BragiRpc, body: ByteArray = byteArrayOf()): ByteArray =
        BragiRpc(SkullcandyRpc.RESPONSE, request.service, request.method, request.tag, body).frame().bytes()

    private fun status(request: BragiRpc, code: Int): ByteArray = BragiRpc(SkullcandyRpc.STATUS, request.service, request.method, request.tag, byteArrayOf(code.toByte())).frame().bytes()

    private fun u(field: Int, v: Int) = BragiProto.unsigned(field, v.toULong())

    /** The first request to the service and method. */
    private fun SkullcandyRpcTalk.first(service: Int, method: Int): BragiRpc = start().first { it.service == service && it.method == method }

    @Test
    fun theFirstRequestIsTheDocumentedFrame() {
        val first = crusher().start().first()
        assertEquals("7eff80940006" + "2d" + "10020100" + "0800" + "5480", hex(first.frame().bytes()))
    }

    @Test
    fun everyRequestIsOneThePhoneMaySend() {
        for (name in listOf("Crusher 1080 ANC", "Method 360 ANC", "Method 540 ANC")) {
            val talk = SkullcandyRpcTalk(SkullcandyModels.fromName(name)!!)
            assertTrue(talk.start().all { talk.allowed(it) }, name)
        }
        val talk = crusher()
        assertFalse(talk.allowed(BragiRpc(SkullcandyRpc.REQUEST, 25, 1, 0))) // the secure session
        assertFalse(talk.allowed(BragiRpc(SkullcandyRpc.REQUEST, 100, 1, 0))) // diagnostics
        assertFalse(talk.allowed(BragiRpc(SkullcandyRpc.REQUEST, 18, 1, 0))) // calibration
        assertFalse(talk.allowed(BragiRpc(SkullcandyRpc.REQUEST, 2, 6, 0))) // setting the time
        assertFalse(talk.allowed(BragiRpc(SkullcandyRpc.REQUEST, 7, 2, 0))) // renaming
    }

    @Test
    fun theModelsAskOnlyForTheirOwnServices() {
        val services = { t: SkullcandyRpcTalk -> t.start().map { it.service }.toSet() }
        assertTrue(23 in services(crusher()))
        assertTrue(17 in services(crusher()))
        assertTrue(12 in services(crusher()))
        assertFalse(23 in services(method()))
        assertFalse(12 in services(method()))
        assertTrue(4 in services(method()))
    }

    @Test
    fun repliesFillTheNoiseRowsAndTheBattery() {
        val talk = crusher()
        val sent = talk.start()
        val battery = sent.first { it.service == 2 && it.method == 3 }
        talk.feed(response(battery, BragiProto.bytes(1, u(1, 2) + u(2, 80) + u(3, 1)) + BragiProto.bytes(1, u(1, 1) + u(2, 70) + u(3, 1))))
        assertEquals(80, talk.state.number(Command.BATTERY_LEFT))
        assertEquals(70, talk.state.number(Command.BATTERY_RIGHT))
        talk.feed(response(sent.first { it.service == 4 && it.method == 3 }, BragiProto.bytes(1, u(1, 3) + u(2, 10)) + BragiProto.bytes(2, u(1, 2) + u(2, 20))))
        talk.feed(response(sent.first { it.service == 4 && it.method == 1 }, u(1, 2) + BragiProto.bytes(2, u(1, 1) + u(2, 6)) + BragiProto.bytes(3, u(1, 0) + u(2, 12))))
        assertEquals("anc", Command.ANC_MODE.labels[talk.state.number(Command.ANC_MODE)])
        val noise = talk.groups.first { it.key == "noise" }.settings
        assertEquals(listOf("sk_anc_level", "sk_anc_profile"), noise.map { it.key })
        assertEquals(Setting.Level("sk_anc_level", 6, 0, 10), noise[0])
        assertEquals(Setting.Level("sk_anc_profile", 1, 0, 3), noise[1])
        // a notification of a change arrives with the subscription's own tag
        talk.feed(response(sent.first { it.service == 4 && it.method == 1 }, u(1, 3) + BragiProto.bytes(3, u(1, 0) + u(2, 12))))
        assertEquals("transparency", Command.ANC_MODE.labels[talk.state.number(Command.ANC_MODE)])
        assertEquals(Setting.Level("sk_transparency_level", 12, 0, 20).key, talk.groups.first { it.key == "noise" }.settings.first().key)
    }

    @Test
    fun writesAreTheSchemasMessages() {
        val talk = crusher()
        val sent = talk.start()
        talk.feed(response(sent.first { it.service == 4 && it.method == 3 }, BragiProto.bytes(1, u(1, 3) + u(2, 10)) + BragiProto.bytes(2, u(1, 2) + u(2, 20))))
        talk.feed(response(sent.first { it.service == 4 && it.method == 1 }, u(1, 2) + BragiProto.bytes(2, u(1, 1) + u(2, 6))))
        val transparency = Command.ANC_MODE.labels.entries.first { it.value == "transparency" }.key
        val w = talk.set(Command.ANC_MODE, transparency)!!.single()
        assertEquals(4 to 2, w.service to w.method)
        assertEquals("08031001", hex(w.body))
        val off = Command.ANC_MODE.labels.entries.first { it.value == "off" }.key
        assertEquals("08011001", hex(talk.set(Command.ANC_MODE, off)!!.single().body))
        // the strength of the noise cancelling in force
        talk.feed(response(sent.first { it.service == 4 && it.method == 1 }, u(1, 2) + BragiProto.bytes(2, u(1, 1) + u(2, 6))))
        assertEquals("0802" + "1001" + "2008", hex(talk.change(SettingChange.Level("sk_anc_level", 8))!!.single().body))
        assertTrue(talk.allowed(talk.change(SettingChange.Level("sk_anc_level", 8))!!.single()))
        // no strength row to write while another mode is active
        assertNull(talk.change(SettingChange.Level("sk_transparency_level", 3)))
    }

    @Test
    fun theOtherRowsReadAndWrite() {
        val talk = crusher()
        val sent = talk.start()
        talk.feed(response(sent.first { it.service == 8 }, u(1, 3)))
        talk.feed(response(sent.first { it.service == 12 }, BragiProto.bool(1, true) + BragiProto.bool(2, false)))
        talk.feed(response(sent.first { it.service == 23 && it.method == 1 }, u(1, 10) + BragiProto.bool(2, false) + u(3, 3)))
        talk.feed(response(sent.first { it.service == 23 && it.method == 2 }, BragiProto.bool(1, true) + u(2, 4) + u(3, 1)))
        talk.feed(response(sent.first { it.service == 17 }, BragiProto.bool(1, true) + u(2, 2)))
        talk.feed(response(sent.first { it.service == 4 && it.method == 12 }, u(1, 10)))
        talk.feed(response(sent.first { it.service == 4 && it.method == 10 }, BragiProto.bool(1, true) + u(2, 5)))
        talk.feed(response(sent.first { it.service == 22 }, BragiProto.bool(1, true) + BragiProto.bool(2, false)))
        talk.feed(response(sent.first { it.service == 6 }, BragiProto.bytes(1, BragiProto.text(1, "en-US")) + BragiProto.bytes(2, BragiProto.text(1, "en-US")) + BragiProto.bytes(2, BragiProto.text(1, "de-DE"))))
        val rows = talk.groups.flatMap { it.settings }.associateBy { it.key }
        assertEquals(true, (rows["sk_gaming_mode"] as Setting.Toggle).on)
        assertEquals(true, (rows["sk_wear_detect"] as Setting.Toggle).on)
        assertEquals(false, (rows["sk_wear_transparency"] as Setting.Toggle).on)
        assertEquals(true, (rows["sk_crusher_active"] as Setting.Toggle).on)
        assertEquals(Setting.Level("sk_crusher_intensity", 4, 0, 10), rows["sk_crusher_intensity"])
        assertEquals("medium", (rows["sk_crusher_mode"] as Setting.Choice).value)
        assertEquals("fixed_to_head", (rows["sk_spatial"] as Setting.Choice).value)
        assertEquals(Setting.Level("sk_sidetone_level", 5, 0, 10), rows["sk_sidetone_level"])
        assertEquals(true, (rows["sk_reduced_prompts"] as Setting.Toggle).on)
        assertEquals(listOf("en-US", "de-DE"), (rows["sk_language"] as Setting.Choice).options)

        assertEquals("0801", hex(talk.change(SettingChange.Toggle("sk_wear_detect", true))!!.single().body))
        assertEquals("1000", hex(talk.change(SettingChange.Toggle("sk_wear_transparency", false))!!.single().body))
        assertEquals("0803", hex(talk.change(SettingChange.Toggle("sk_gaming_mode", true))!!.single().body))
        assertEquals("0801", hex(talk.change(SettingChange.Toggle("sk_gaming_mode", false))!!.single().body))
        assertEquals("1005", hex(talk.change(SettingChange.Level("sk_crusher_intensity", 5))!!.single().body))
        assertEquals("1802", hex(talk.change(SettingChange.Choice("sk_crusher_mode", "low"))!!.single().body))
        val language = talk.change(SettingChange.Choice("sk_language", "de-DE"))!!.single()
        assertEquals("0a07" + "0a05" + "64652d4445", hex(language.body))
        assertNull(talk.change(SettingChange.Choice("sk_language", "xx-XX")))
        assertTrue(listOf("sk_wear_detect", "sk_gaming_mode").all { k -> talk.allowed(talk.change(SettingChange.Toggle(k, true))!!.single()) })
    }

    @Test
    fun aRefusalIsRememberedForTheReport() {
        val talk = crusher()
        val sent = talk.start()
        talk.feed(status(sent.first { it.service == 23 && it.method == 1 }, 41))
        assertTrue(talk.denied)
        assertEquals(41, talk.failures[23 to 1])
        talk.feed(response(sent.first { it.service == 2 && it.method == 1 }, BragiProto.bool(12, true) + BragiProto.text(9, "SERIAL123")))
        assertEquals(true, talk.authenticatedAccess)
        // the serial never reaches the report's copy of the reply
        assertFalse(talk.replies.values.any { hex(it).contains(hex("SERIAL123".encodeToByteArray())) })
    }

    @Test
    fun theCrusherEqualiserIsTheEarbudsOwn() {
        val talk = crusher()
        val sent = talk.start()
        talk.feed(response(sent.first { it.service == 4 && it.method == 6 }, BragiProto.float(4, 0f) + BragiProto.float(5, 20f)))
        fun filter(id: Int, editable: Boolean, name: String, gains: List<Float>) =
            u(1, id) + BragiProto.bool(2, editable) + BragiProto.text(3, name) +
                gains.withIndex().fold(byteArrayOf()) { acc, (i, g) -> acc + BragiProto.bytes(4, u(1, listOf(60, 400, 3000)[i]) + BragiProto.float(2, g) + BragiProto.float(3, 1.0f)) }
        val conf = sent.first { it.service == 4 && it.method == 5 }
        talk.feed(response(conf, filter(1, false, "Music", listOf(10f, 10f, 10f))))
        talk.feed(response(conf, filter(0, true, "Custom", listOf(12f, 10f, 7f))))
        talk.feed(response(sent.first { it.service == 4 && it.method == 4 }, BragiProto.bool(1, true) + u(2, 0)))
        assertTrue(talk.hasEq)
        assertEquals(listOf(60, 400, 3000), talk.eqBandsHz)
        assertEquals(10, talk.eqRangeDb)
        assertEquals(listOf("Music"), talk.eqPresetNames)
        val eq = SkullcandyRpcBandEq(talk)
        assertEquals(listOf(2, 0, -3), eq.eqReport!!.gains)
        val frame = BragiRpc.of(eq.eqCustomFrames(listOf(-4, 0, 5))!!.single())!!
        assertEquals(4 to 7, frame.service to frame.method)
        assertTrue(talk.allowed(frame))
        val m = BragiProto.parse(frame.body)!!
        assertEquals(0, m.field(1)!!.int())
        assertEquals(true, m.field(2)!!.bool())
        val bands = m.fields(4).map { it.message()!! }
        assertEquals(listOf(60, 400, 3000), bands.map { it.field(1)!!.int() })
        assertEquals(listOf(6f, 10f, 15f), bands.map { it.field(2)!!.float() })
        assertEquals(listOf(1f, 1f, 1f), bands.map { it.field(3)!!.float() })
        val preset = BragiRpc.of(eq.eqPresetFrames("Music")!!.single())!!
        assertEquals(1, BragiProto.parse(preset.body)!!.field(1)!!.int())
        assertNotNull(talk.eq)
    }

    @Test
    fun theMethodModelsLeaveTheEqualiserToRace() {
        val talk = method()
        assertFalse(talk.hasEq)
        assertTrue(talk.start().none { it.method == 6 && it.service == 4 })
        val race = SkullcandyRaceTalk(SkullcandyModels.fromName("Method 540 ANC")!!)
        assertTrue(race.hasCustomEq)
        assertTrue(race.hasMultipoint)
        val crusherRace = SkullcandyRaceTalk(SkullcandyModels.fromName("Crusher 1080 ANC")!!)
        assertFalse(crusherRace.hasCustomEq)
        assertFalse(crusherRace.hasWearDetect)
        assertTrue(crusherRace.hasMultipoint)
        assertTrue(SkullcandyModels.fromName("Crusher 1080 ANC")!!.usesRpc)
        assertEquals(SkullcandyServices.RPC, SkullcandyModels.fromName("Crusher 1080 ANC")!!.service)
    }

    @Test
    fun theButtonsAreAskedForOnlyWhatTheEarbudsListAndOnlyTheirActionsAreOffered() {
        val talk = method()
        val sent = talk.start()
        val info = sent.first { it.service == 3 && it.method == 7 }
        fun item(field: Int, name: String) = BragiProto.bytes(1, BragiProto.bytes(field, BragiProto.text(1, name)))
        talk.feed(
            response(
                info,
                item(1, "left") + item(1, "right") + item(2, "power") + item(3, "click") + item(3, "click2") + item(3, "click3") + item(3, "hold_up") +
                    item(5, "media.play_pause") + item(5, "media.next") + item(5, "volume.up") + item(5, "device.no_action"),
            ),
        )
        val asks = talk.takeOutbox()
        // two earbuds and four gestures of the power key
        assertEquals(8, asks.size)
        assertTrue(asks.all { it.service == 3 && it.method == 2 && talk.allowed(it) })
        val left = asks.first { String(BragiProto.parse(it.body)!!.field(1)!!.data) == "left" && String(BragiProto.parse(it.body)!!.field(3)!!.data) == "power.click2" }
        talk.feed(response(left, BragiProto.text(1, "left") + u(2, 1) + BragiProto.text(3, "power.click2") + BragiProto.bytes(4, BragiProto.text(1, "media.next"))))
        val row = talk.groups.flatMap { it.settings }.first { it.key == "sk_rbutton_left_double" } as Setting.Choice
        assertEquals("track_forward", row.value)
        // only the actions the earbuds list are offered, of the model's own
        assertEquals(listOf("play_pause", "track_forward", "volume_up", "none"), row.options)
        val out = talk.change(SettingChange.Choice("sk_rbutton_left_double", "volume_up"))!!.single()
        assertTrue(talk.allowed(out))
        val m = BragiProto.parse(out.body)!!
        assertEquals("left", String(m.field(1)!!.data))
        assertEquals("power.click2", String(m.field(2)!!.data))
        assertEquals("volume.up", String(m.field(3)!!.message()!!.field(1)!!.data))
        // an action the earbuds do not list is not sent
        assertNull(talk.change(SettingChange.Choice("sk_rbutton_left_double", "take_photo")))
        // the Crusher's joystick has its own key, and no single press
        val crusher = SkullcandyRpcTalk(SkullcandyModels.fromName("Crusher 1080 ANC")!!)
        assertEquals("centr", RpcButtons.keyOf(crusher.product))
        assertEquals(listOf("double", "triple", "hold"), RpcButtons.gesturesOf(crusher.product).map { it.first })
    }
}

