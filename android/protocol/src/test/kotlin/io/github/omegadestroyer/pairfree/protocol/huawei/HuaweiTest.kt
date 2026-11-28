package io.github.omegadestroyer.pairfree.protocol.huawei

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.Identify
import io.github.omegadestroyer.pairfree.protocol.Maker
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.deadControls
import io.github.omegadestroyer.pairfree.protocol.hexBytes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Analytical and synthetic vectors except where the source of a published capture is named. */
class HuaweiTest {
    @Test fun vendorBatteryVectorCrcAndEverySplit() {
        val bytes = "5a00050001080100f53d".hexBytes()
        assertArrayEquals(bytes, HuaweiReads.battery().bytes())
        assertEquals(0x31c3, HuaweiPacket.crc16("123456789".encodeToByteArray()))
        for (split in 0..bytes.size) {
            val parser = HuaweiParser()
            val frames = parser.feed(bytes.copyOfRange(0, split)) + parser.feed(bytes.copyOfRange(split, bytes.size))
            assertEquals(1, frames.size)
            assertArrayEquals(bytes, frames.single().bytes())
        }
    }

    @Test fun malformedLengthsControlsCrcAndTlvsDoNotLeakPartialState() {
        val good = HuaweiReads.battery().bytes()
        val badCrc = good.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val parser = HuaweiParser()
        val frames = parser.feed("00115affff005a000301".hexBytes() + badCrc + good + good)
        assertEquals(2, frames.size)
        assertThrows(IllegalArgumentException::class.java) { HuaweiTlv(256).bytes() }
        assertThrows(IllegalArgumentException::class.java) { HuaweiTlv(1, ByteArray(256)).bytes() }
        assertThrows(IllegalArgumentException::class.java) { HuaweiPacket(-1, 1, emptyList()).bytes() }
        // Valid CRC with an incomplete TLV must still be rejected as a whole.
        val malformed = "5a0006000108010342".hexBytes()
        val crc = HuaweiPacket.crc16(malformed)
        assertTrue(HuaweiParser().feed(malformed + byteArrayOf((crc shr 8).toByte(), crc.toByte())).isEmpty())
        parser.feed(good.copyOf(5))
        parser.reset()
        assertEquals(1, parser.feed(good).size)
    }

    @Test fun unsignedBatteryValuesAndUnknownComponents() {
        val frame = HuaweiPacket(1, 8, listOf(HuaweiTlv(1, byteArrayOf(70)), HuaweiTlv(2, byteArrayOf(75, 80, -1))))
        val decoded = HuaweiParser().feed(frame.bytes()).single()
        assertEquals(HuaweiBattery(70, 75, 80, null), HuaweiBattery.of(decoded))
        assertNull(HuaweiBattery.of(frame.copy(command = 7)))
        assertEquals(HuaweiBattery(70, null, null, null), HuaweiBattery.of(frame.copy(fields = listOf(HuaweiTlv(1, byteArrayOf(70)), HuaweiTlv(2, byteArrayOf(80))))))
        val duplicate = frame.copy(fields = listOf(HuaweiTlv(1, byteArrayOf(70)), HuaweiTlv(1, byteArrayOf(80))))
        assertNull(HuaweiBattery.of(duplicate)?.global)
        assertEquals(2, HuaweiParser().feed(duplicate.bytes()).single().fields.size)
    }

    @Test fun usesKnownNamesAndNeverClaimsGenericSerialDevices() {
        HuaweiModels.all.forEach { assertEquals(Maker.HUAWEI, Identify.maker(it.name, emptyList()), it.name) }
        assertEquals("ZA02", HuaweiModels.fromName("HUAWEI FreeBuds 3i")?.id)
        assertEquals("ZA07", HuaweiModels.fromName("MermaidTWS")?.id)
        assertNull(Identify.maker("HUAWEI Watch", listOf("00001101-0000-1000-8000-00805F9B34FB")))
    }

    @Test fun legacyAndModernAncWritesHaveDifferentLayouts() {
        val shark = talk("FreeBuds 3")
        assertNull(shark.set(Command.ANC_MODE, 1))
        shark.receive(huaweiWrite(0x2b05, 1 to 0))
        val legacy = shark.set(Command.ANC_MODE, 1)!!.single()
        assertArrayEquals(byteArrayOf(1), legacy.field(1))
        assertTrue(shark.allowed(legacy))
        assertNull(shark.set(Command.ANC_MODE, 2))
        assertEquals(0x2b05, shark.takeReadBacks().single().op)

        val nile = talk("FreeLace Pro")
        nile.receive(huaweiWrite(0x2b05, 1 to 1))
        assertArrayEquals(byteArrayOf(2), nile.set(Command.ANC_MODE, 2)!!.single().field(1))
        nile.receive(huaweiPacket(0x2b2a, listOf(HuaweiTlv(1, byteArrayOf(0, 1)))))
        assertFalse(nile.feed(huaweiWrite(0x2b05, 1 to 0).bytes()).changed)
        assertEquals(1, nile.state.number(Command.ANC_MODE))
        val modern = nile.set(Command.ANC_MODE, 2)!!.single()
        assertArrayEquals(byteArrayOf(2, -1), modern.field(1))
        assertFalse(nile.allowed(legacy))
        assertTrue(nile.allowed(modern))
        assertEquals(0x2b2a, nile.takeReadBacks().single().op)
        assertFalse(nile.allowed(huaweiRead(0x0a0e, 0)))
    }

    @Test fun walrusHoldUsesOnlyTheRightSlotAndItsAdvertisedMask() {
        val walrus = talk("HONOR Magic Earbuds")
        assertNull(walrus.change(SettingChange.Choice("hw_hold", "hw_off_anc")))
        walrus.receive(huaweiPacket(0x2b17, listOf(HuaweiTlv(1, byteArrayOf(3)), HuaweiTlv(2, byteArrayOf(3)),
            HuaweiTlv(3, byteArrayOf(3, 5, -1)))))
        assertNull(walrus.change(SettingChange.Choice("hw_hold", "hw_anc_aware")))
        val write = walrus.change(SettingChange.Choice("hw_hold", "hw_off_anc_aware"))!!.single()
        assertEquals(0x2b16, write.op)
        assertEquals(2, write.fields.single().tag)
        assertEquals(5, write.scalar(2))
        assertTrue(walrus.allowed(write))
    }

    @Test fun eyewearUsesItsOwnBatteryPresentationAndLanguageWrite() {
        val eyewear = talk("HUAWEI Eyewear")
        eyewear.receive(huaweiPacket(0x0108, listOf(HuaweiTlv(2, byteArrayOf(85, 63, 29)))))
        assertEquals(63, eyewear.state.batteryLeft)
        assertNull(eyewear.state.batteryRight)
        assertEquals(29, eyewear.state.batteryCase)
        eyewear.receive(huaweiPacket(0x0c02, listOf(HuaweiTlv(3, "zh-CN,en-GB\u0000,en-US".encodeToByteArray()))))
        val choice = eyewear.change(SettingChange.Choice("hw_language", "en-GB"))!!.single()
        assertEquals(0x0c01, choice.op)
        assertTrue(eyewear.allowed(choice))
        assertArrayEquals("en-GB".encodeToByteArray(), choice.fields.single().value)
        assertNull(eyewear.change(SettingChange.Choice("hw_language", "en-US")))
    }

    @Test fun bothEyewearGenerationsUseTheVendorDoubleTapAndSwitches() {
        for (model in listOf("HUAWEI Eyewear", "HUAWEI Eyewear II")) {
            val glasses = talk(model)
            assertEquals(listOf(3), glasses.start().single { it.op == 0x0120 }.fields.map(HuaweiTlv::tag))
            assertNull(glasses.change(SettingChange.Toggle("hw_greeting", true)))
            glasses.receive(huaweiWrite(0x0120, 1 to 0, 2 to 255))
            val tap = glasses.change(SettingChange.Choice("hw_glasses_double_right", "hw_play_pause"))!!.single()
            assertEquals(0x011f, tap.op)
            assertEquals(listOf(2), tap.fields.map(HuaweiTlv::tag))
            assertEquals(1, tap.scalar(2))
            assertTrue(glasses.allowed(tap))
            for ((key, op) in listOf("hw_greeting" to 0x2b0f, "hw_glasses_wear" to 0x2b11)) {
                glasses.receive(huaweiWrite(op, 1 to 0))
                val write = glasses.change(SettingChange.Toggle(key, true))!!.single()
                assertEquals(op - 1, write.op)
                assertEquals(1, write.scalar(1))
                assertTrue(glasses.allowed(write))
            }
        }
    }

    @Test fun fijiCallHoldAndShortPressUseDifferentCommands() {
        val first = talk("HUAWEI Eyewear")
        val fiji = talk("HUAWEI Eyewear II")
        val read = fiji.start().single { it.op == 0x2b17 }
        assertEquals(listOf(1, 2, 3), read.fields.map(HuaweiTlv::tag))
        assertFalse(first.allowed(read))
        assertNull(fiji.change(SettingChange.Choice("hw_glasses_hold", "hw_reject_call")))
        fiji.receive(huaweiWrite(0x2b17, 1 to 255))
        fiji.receive(huaweiWrite(0x2b21, 1 to 255))
        val hold = fiji.change(SettingChange.Choice("hw_glasses_hold", "hw_reject_call"))!!.single()
        assertEquals(0x2b16, hold.op)
        assertEquals(9, hold.scalar(1))
        assertTrue(fiji.allowed(hold))
        assertFalse(first.allowed(hold))
        assertNull(fiji.change(SettingChange.Choice("hw_glasses_hold", "hw_voice_memo")))
        val press = fiji.change(SettingChange.Choice("hw_glasses_press", "hw_voice_memo"))!!.single()
        assertEquals(0x2b20, press.op)
        assertEquals(1, press.scalar(1))
        assertTrue(fiji.allowed(press))
    }

    @Test fun nileUsesTheReportedSharedHoldAndWritesItOnTagOne() {
        val nile = talk("FreeLace Pro")
        nile.receive(huaweiWrite(0x2b17, 1 to 5, 2 to 6))
        val current = nile.groups.flatMap { it.settings }.filterIsInstance<Setting.Choice>().single { it.key == "hw_hold" }
        assertEquals("hw_anc_aware", current.value)
        val write = nile.change(SettingChange.Choice("hw_hold", "hw_off_aware"))!!.single()
        assertEquals(0x2b16, write.op)
        assertEquals(listOf(1), write.fields.map(HuaweiTlv::tag))
        assertEquals(9, write.scalar(1))
        assertTrue(nile.allowed(write))
        assertFalse(nile.allowed(huaweiWrite(0x2b16, 2 to 9)))
    }

    @Test fun pinchReadsAndWritesACallSlotWithoutChangingOtherSlots() {
        val pro = talk("FreeBuds Pro 5")
        val read = pro.start().first { it.op == 0x2b93 }
        val otherSlot = huaweiWrite(0x2b93, 1 to 1, 2 to 1, 3 to 1, 4 to 1)
        assertFalse(pro.answers(read, otherSlot))
        assertNull(pro.change(SettingChange.Choice("hw_pinch_answer", "hw_gesture_off")))
        pro.receive(huaweiWrite(0x2b93, 1 to 0, 2 to 1, 3 to 0, 4 to 0))
        val write = pro.change(SettingChange.Choice("hw_pinch_answer", "hw_gesture_off"))!!.single()
        assertEquals(255, write.scalar(3))
        assertEquals(255, write.scalar(4))
        assertTrue(pro.allowed(write))
        assertFalse(pro.allowed(huaweiWrite(0x2b92, 1 to 0, 2 to 1, 3 to 255, 4 to 0)))
    }

    @Test fun eqReadsRealPro3PresetIdsAndProtectsExistingCustomSlots() {
        // LibreBuds c7b0833, test-vectors/round2/equalizer.jsonl, PRO3/T3.G-2B4A.
        val pro = talk("FreeBuds Pro 3")
        assertNull(pro.eqPresetFrames("Default"))
        assertTrue(pro.feed("5a0014002b4a01010102010503040203050904010508006e8b".hexBytes()).changed)
        assertEquals("Default", pro.eqReport?.preset)
        assertEquals(2, HuaweiParser().feed(pro.eqPresetFrames("Bass boost")!!.single()).single().scalar(1))
        assertEquals(10, pro.eqBandsHz.size)
        val gains = List(10) { if (it % 2 == 0) -6.0 else 1.3 }
        val write = HuaweiParser().feed(pro.eqCustomFramesDb(gains, 0)!!.single()).single()
        assertArrayEquals(byteArrayOf(-60, 13, -60, 13, -60, 13, -60, 13, -60, 13), write.field(3))
        assertTrue(pro.isEqWrite(write))
        val occupied = (100..102).flatMap { slot ->
            (byteArrayOf(slot.toByte(), 10) + ByteArray(10) + "Other".encodeToByteArray().copyOf(24)).toList()
        }.toByteArray()
        pro.receive(huaweiPacket(0x2b4a, listOf(HuaweiTlv(2, byteArrayOf(100)), HuaweiTlv(8, occupied))))
        assertEquals("Custom 1: Other", pro.eqReport?.preset)
        assertEquals(100, HuaweiParser().feed(pro.eqPresetFrames("Custom 1: Other")!!.single()).single().scalar(1))
        assertFalse(pro.isEqWrite(write))
        assertNull(pro.eqCustomFramesDb(gains, 0))
    }

    @Test fun adaptiveAwarenessAndHeadGesturesRequireTheirOwnReports() {
        val pro = talk("FreeBuds Pro 5")
        assertNull(pro.change(SettingChange.Level("hw_awareness_level", 7)))
        pro.receive(huaweiPacket(0x2b2a, listOf(HuaweiTlv(1, byteArrayOf(4, 2)), HuaweiTlv(2, byteArrayOf(6)))))
        val awareness = pro.change(SettingChange.Level("hw_awareness_level", 7))!!.single()
        assertArrayEquals(byteArrayOf(2, 4), awareness.field(1))
        assertEquals(7, awareness.scalar(4))
        assertTrue(pro.allowed(awareness))
        assertNull(pro.change(SettingChange.Level("hw_awareness_level", 11)))

        assertNull(pro.change(SettingChange.Choice("hw_shake", "hw_reject_call")))
        pro.receive(huaweiWrite(0x2bb4, 1 to 11, 2 to 1, 3 to 1, 4 to 0))
        val head = pro.change(SettingChange.Choice("hw_shake", "hw_reject_call"))!!.single()
        assertEquals(2, head.scalar(4))
        assertTrue(pro.allowed(head))
        assertFalse(pro.allowed(huaweiWrite(0x2bb4, 1 to 11, 3 to 1, 4 to 2)))
    }

    @Test fun firmwareUsesOnlyTheVersionField() {
        val glasses = talk("HUAWEI Eyewear II")
        val read = glasses.start().single { it.op == 0x0107 }
        assertEquals(7, read.fields.single().tag)
        val arbitraryField = HuaweiTlv(9, "987654".encodeToByteArray())
        assertFalse(glasses.feed(huaweiPacket(0x0107, listOf(arbitraryField)).bytes()).changed)
        glasses.receive(huaweiPacket(0x0107, listOf(HuaweiTlv(7, "1.0.2.42 (build)".encodeToByteArray()), arbitraryField)))
        assertEquals("1.0.2.42", glasses.state.firmware?.version)
        val line = glasses.reportLines.single { it.startsWith("0107:") }
        assertTrue("09=" in line && "393837363534" !in line, "an unrequested field's bytes stay out of the report: $line")
    }

    @Test fun theReportBlanksCustomEqualizerNames() {
        val pro = talk("FreeBuds Pro 3")
        val record = byteArrayOf(100, 10) + ByteArray(10) + "Somebody's".encodeToByteArray().copyOf(24)
        pro.feed(huaweiPacket(0x2b4a, listOf(HuaweiTlv(2, byteArrayOf(100)), HuaweiTlv(8, record))).bytes())
        val line = pro.reportLines.single { it.startsWith("2b4a:") }
        assertTrue("536f6d65626f6479" !in line, line)
    }

    @Test fun aCatalogueModelIsOnlyRead() {
        val neo = talk("FreeBuds Neo")
        assertTrue(neo.start().all(neo::allowed))
        assertTrue(neo.start().any { it.op == 0x2b4a })
        neo.feed(huaweiPacket(0x2b4a, listOf(HuaweiTlv(1, byteArrayOf(1)), HuaweiTlv(2, byteArrayOf(1)), HuaweiTlv(3, byteArrayOf(1, 2)))).bytes())
        assertTrue(neo.groups.isEmpty())
        assertTrue(neo.eqPresetNames.isEmpty())
        assertFalse(neo.allowed(huaweiWrite(0x2b49, 1 to 1)))
        assertTrue(neo.reportLines.any { it.startsWith("2b4a:") })
    }

    /** The frames HuaweiPods (e2cf07e) writes out in full. */
    @Test fun spatialAudioAndCaseSoundMatchPublishedFrames() {
        assertArrayEquals("5a0009002bb401011802010170cc".hexBytes(), huaweiWrite(0x2bb4, 1 to 24, 2 to 1).bytes())
        assertArrayEquals("5a0006002bb101010025b5".hexBytes(), huaweiWrite(0x2bb1, 1 to 0).bytes())
        val clip = talk("FreeClip 2")
        assertArrayEquals("5a000a002bb4010118020003009b3f".hexBytes(), clip.start().single { it.op == 0x2bb4 && it.scalar(1) == 24 }.bytes())
        clip.receive(huaweiWrite(0x2bb4, 1 to 24, 2 to 0, 3 to 0))
        val scene = clip.change(SettingChange.Choice("hw_spatial_scene", "hw_scene_hall"))!!.single()
        assertArrayEquals("5a0009002bb401011803010367be".hexBytes(), scene.bytes())
        assertTrue(clip.allowed(scene))
    }

    @Test fun theNewSwitchesAndEarTipsWaitForTheirReads() {
        val pro = talk("FreeBuds Pro 5")
        assertNull(pro.change(SettingChange.Toggle("hw_rest", true)))
        pro.receive(huaweiWrite(0x2b61, 1 to 0))
        val rest = pro.change(SettingChange.Toggle("hw_rest", true))!!.single()
        assertEquals(0x2b60, rest.op)
        assertTrue(pro.allowed(rest))
        pro.receive(huaweiWrite(0x2b42, 1 to 0))
        val pickup = pro.change(SettingChange.Choice("hw_pickup", "hw_pickup_voices"))!!.single()
        assertEquals(0x2b41, pickup.op)
        assertEquals(1, pickup.scalar(1))
        pro.receive(huaweiWrite(0x2bb4, 1 to 8, 2 to 1))
        val tips = pro.change(SettingChange.Choice("hw_ear_tip", "hw_tips_foam"))!!.single()
        assertEquals(2, tips.scalar(2))
        assertTrue(pro.allowed(tips))
        assertFalse(pro.allowed(huaweiWrite(0x2bb4, 1 to 8, 2 to 3)))
        assertFalse(pro.allowed(huaweiPacket(0x010d, listOf(HuaweiTlv(1, byteArrayOf(1))))), "factory reset is never sent")
    }

    @Test fun studioTouchUsesItsExactQueryAndWaitsForState() {
        val studio = talk("FreeBuds Studio")
        val query = studio.start().single { it.op == 0x012d }
        assertArrayEquals("5a000500012d0101882a".hexBytes(), studio.bytes(query))
        assertTrue(HuaweiParser().feed(studio.bytes(query)).isEmpty())
        assertNull(studio.change(SettingChange.Toggle("hw_touch_enabled", false)))
        studio.receive(huaweiWrite(0x012d, 1 to 1))
        val lock = studio.change(SettingChange.Toggle("hw_touch_enabled", false))!!.single()
        assertEquals(0x012c, lock.op)
        assertEquals(0, lock.scalar(1))
        assertTrue(studio.allowed(lock))
        val mermaid = talk("FreeBuds Pro")
        assertFalse(mermaid.allowed(query))
        assertFalse(mermaid.allowed(lock))
    }

    /** A session whose every read was answered with a plausible value. */
    private fun answered(product: HuaweiProduct): HuaweiTalk {
        val talk = HuaweiTalk(product)
        val offered = byteArrayOf(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        for (read in talk.start()) {
            val op = read.service shl 8 or read.command
            fun one(tag: Int, value: Int = 1) = HuaweiTlv(tag, byteArrayOf(value.toByte()))
            val fields = when (op) {
                0x0108 -> listOf(one(1, 80), HuaweiTlv(2, byteArrayOf(80, 81, 60)))
                0x0107 -> listOf(HuaweiTlv(7, "1.2.3".encodeToByteArray()))
                0x2b2a -> listOf(HuaweiTlv(1, byteArrayOf(0, 1)))
                0x0c02 -> listOf(HuaweiTlv(1, "en-GB".encodeToByteArray()), HuaweiTlv(3, "en-GB,zh-CN".encodeToByteArray()))
                0x2b4a -> listOf(one(1), one(2), HuaweiTlv(3, byteArrayOf(1, 2, 3, 5, 9)))
                0x2bb4 -> listOf(read.fields.first(), one(2), one(3), one(4, 2))
                0x2b93 -> listOf(read.fields[0], read.fields[1], one(3, 255), one(4, 255))
                0x2b5e -> listOf(HuaweiTlv(2, byteArrayOf(read.fields.single().value.single(), 1)))
                else -> read.fields.map { if (it.tag == 3) HuaweiTlv(3, offered) else one(it.tag) } +
                    listOfNotNull(HuaweiTlv(3, offered).takeIf { op == 0x0120 || op == 0x2b17 }.takeIf { _ -> read.fields.none { it.tag == 3 } })
            }
            talk.feed(HuaweiPacket(read.service, read.command, fields).bytes())
        }
        return talk
    }

    /** Every control shown for every model writes something (ShownControls.kt). */
    @Test fun everyShownControlWrites() {
        val dead = HuaweiModels.all.flatMap { product ->
            deadControls(product.name, { answered(product) }, { it.state }, { it.groups },
                { talk, command, value -> talk.set(command, value) }, { talk, change -> talk.change(change)?.also { list -> assertTrue(list.all(talk::allowed), "${product.name} $change") } })
        }
        assertEquals(emptyList<String>(), dead)
    }

    private fun talk(name: String): HuaweiTalk = HuaweiTalk(checkNotNull(HuaweiModels.fromName(name)))
    private fun HuaweiTalk.receive(packet: HuaweiPacket) { assertTrue(feed(packet.bytes()).changed) }
}
