package io.github.omegadestroyer.pairfree.protocol.skullcandy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkullcandyAtTest {
    @Test
    fun requestsAreTheLinesTheSdkWrites() {
        assertEquals("AT.UIAUDIO=anc,on", SkullcandyAt.setAnc(true))
        assertEquals("AT.UIAUDIO=anc,off", SkullcandyAt.setAnc(false))
        assertEquals("AT.UIAUDIO=anc_filter,3", SkullcandyAt.setAncFilter(3))
        assertEquals("AT.UIAUDIO=anc_filter,off", SkullcandyAt.setAncFilter(null))
        assertEquals("AT.UIAUDIO=transparency,on", SkullcandyAt.setTransparency(true))
        assertEquals("AT.UIAUDIO=transparency,,45", SkullcandyAt.setTransparencyLevel(45))
        assertEquals("AT.UIAUDIO=transparency,,100", SkullcandyAt.setTransparencyLevel(180))
        assertEquals("AT.VOLUME=up,2", SkullcandyAt.volumeUp(2))
        assertEquals("AT.LATENCY=gaming_mode,on", SkullcandyAt.setGamingMode(true))
        assertEquals("AT.UIAUDIO=anc\r\n", SkullcandyAt.line(SkullcandyAt.ANC).decodeToString())
    }

    @Test
    fun repliesAreReadByTheirStatusAndValue() {
        val ok = SkullcandyAt.parse("AT.UIAUDIO:on\r\nOK\r\n")
        assertTrue(ok.ok)
        assertEquals(listOf("AT.UIAUDIO:on"), ok.lines)
        assertEquals(true, SkullcandyAt.onOffOf(ok.lines.first()))
        assertEquals(false, SkullcandyAt.onOffOf("+UIAUDIO:off"))
        assertNull(SkullcandyAt.onOffOf("+UIAUDIO:maybe"))
        val refused = SkullcandyAt.parse("ERROR\r\n")
        assertTrue(refused.error)
        assertFalse(refused.ok)
        assertTrue(SkullcandyAt.parse("NO CARRIER").error)
    }

    @Test
    fun filterAndTransparencyAndBatteryFollowTheSdksParsers() {
        assertNull(SkullcandyAt.ancFilterOf("+UIAUDIO:off"))
        assertEquals(4, SkullcandyAt.ancFilterOf("+UIAUDIO:4"))
        val t = SkullcandyAt.transparencyOf("+UIAUDIO:on,45")
        assertTrue(t.on)
        assertEquals(45, t.percent)
        assertFalse(SkullcandyAt.transparencyOf("+UIAUDIO:off").on)
        assertNull(SkullcandyAt.transparencyOf("+UIAUDIO:off").percent)
        val b = SkullcandyAt.batteryOf(".SYSINFO:battery\tright:70\tleft:65\tlocal:80")
        assertEquals(65, b.left)
        assertEquals(70, b.right)
        assertEquals(80, b.single)
        val missing = SkullcandyAt.batteryOf(".SYSINFO:single:55")
        assertEquals(-1, missing.left)
        assertEquals(55, missing.single)
    }

    @Test
    fun theNeverSendGroupsAreRefused() {
        assertTrue(SkullcandyAt.isAllowed("AT.UIAUDIO=anc,on"))
        assertFalse(SkullcandyAt.isAllowed("AT.FACTORY_RESET=default"))
        assertFalse(SkullcandyAt.isAllowed("AT.BLUETOOTH=pdlreset"))
        assertFalse(SkullcandyAt.isAllowed("AT.IMU=calibration,reset"))
        assertFalse(SkullcandyAt.isAllowed("AT.TIME=set,2026"))
        assertFalse(SkullcandyAt.isAllowed("AT.UI=inject,swt_c"))
        assertFalse(SkullcandyAt.isAllowed("ATZ"))
        // the app's own writes all pass
        for (c in listOf(SkullcandyAt.setAnc(true), SkullcandyAt.setAncFilter(2), SkullcandyAt.setTransparencyLevel(30), SkullcandyAt.volumeDown(1), SkullcandyAt.setGamingMode(false), SkullcandyAt.BATTERY)) {
            assertTrue(SkullcandyAt.isAllowed(c), c)
        }
    }
}
