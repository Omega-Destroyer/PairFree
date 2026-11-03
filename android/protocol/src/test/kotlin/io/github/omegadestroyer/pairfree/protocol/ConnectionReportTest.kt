package io.github.omegadestroyer.pairfree.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class ConnectionReportTest {
    @Test
    fun theOwnersNameIsMaskedAndTheModelKept() {
        assertEquals("XXX's Pixel Buds Pro", ReportPrivacy.name("Tyler's Pixel Buds Pro"))
        assertEquals("XXX’s AirPods Pro", ReportPrivacy.name("Anne-Marie’s AirPods Pro"))
        assertEquals("AirPods Pro de XXX", ReportPrivacy.name("AirPods Pro de Tyler"))
        assertEquals("AirPods von XXX", ReportPrivacy.name("AirPods von Jan Peter"))
        assertEquals("Galaxy Buds2 Pro (xxxx)", ReportPrivacy.name("Galaxy Buds2 Pro (A1B2)"))
        for (model in listOf("SOUNDPEATS H3", "soundcore Q20i", "JBL Live Buds 3", "ULT FIELD 5", "Nothing Ear (a)", "WF-1000XM5")) {
            assertEquals(model, ReportPrivacy.name(model))
        }
    }

    @Test
    fun addressesKeepOnlyTheMakersHalf() {
        assertEquals("read failed for AA:BB:CC:XX:XX:XX, ret -1", ReportPrivacy.addresses("read failed for AA:BB:CC:DD:EE:FF, ret -1"))
        assertEquals("aa-bb-cc-XX-XX-XX", ReportPrivacy.addresses("aa-bb-cc-dd-ee-ff"))
        assertEquals("0cf12d31-fac3-4553-bd80-d6832e7b3004", ReportPrivacy.addresses("0cf12d31-fac3-4553-bd80-d6832e7b3004"))
    }

    @Test
    fun theReportSaysWhatWasTriedWithoutTheAddress() {
        val attempt = ConnectionReport.Attempt(
            "Tyler's AirPods Pro",
            listOf("Brand" to "Apple", "Reason" to "could not open", "Device" to "AA:BB:CC:DD:EE:FF, bonded"),
            listOf("+0 L2CAP PSM 0x1001", "+5003 failed: IOException: read failed, socket might closed or timeout for AA:BB:CC:DD:EE:FF"),
        )
        val text = ConnectionReport.text(attempt, "https://example.org/issues")
        assertTrue("Bluetooth name:  XXX's AirPods Pro" in text, text)
        assertTrue("+5003 failed: IOException" in text)
        assertFalse("DD:EE:FF" in text, text)
        assertFalse("Tyler" in text)
    }

    @Test
    fun anExceptionReadsWithItsCauses() {
        val e = IOException("could not open", IllegalStateException("L2CAP refused"))
        assertEquals("IOException: could not open, caused by IllegalStateException: L2CAP refused", ConnectionReport.describe(e))
        assertEquals("IOException", ConnectionReport.describe(IOException()))
    }
}
