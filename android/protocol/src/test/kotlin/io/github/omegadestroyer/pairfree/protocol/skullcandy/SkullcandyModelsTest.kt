package io.github.omegadestroyer.pairfree.protocol.skullcandy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkullcandyModelsTest {
    @Test
    fun theTableHoldsTheAppsFiftySixProductsAndTheirChips() {
        assertEquals(56, SkullcandyProducts.ALL.size)
        assertEquals(41, SkullcandyProducts.ALL.count { it.chip == "airoha" })
        assertEquals(5, SkullcandyProducts.ALL.count { it.chip == "bes" })
        assertEquals(6, SkullcandyProducts.ALL.count { it.chip == "qualcomm" })
        assertEquals(setOf(0x34), SkullcandyProducts.ALL.filter { it.name == "Crusher 1080 ANC" }.map { it.id }.toSet())
    }

    @Test
    fun namesMatchWholeWithOrWithoutTheBrandWord() {
        assertEquals("Crusher ANC 2", SkullcandyModels.fromName("Skullcandy Crusher ANC 2")?.name)
        assertEquals("Crusher ANC 2", SkullcandyModels.fromName("S6CAW")?.name)
        assertEquals("Crusher ANC 2", SkullcandyModels.fromName("CHP200")?.name)
        assertEquals("Crusher 1080 ANC", SkullcandyModels.fromName("Skullcandy CRUSHER 1080 ANC")?.name)
        assertEquals("Method 360 ANC", SkullcandyModels.fromName("Skullcandy Method 360 ANC")?.name)
        assertEquals("Rail ANC", SkullcandyModels.fromName("railanc")?.name)
        assertEquals("Push Active", SkullcandyModels.fromName("Oppactive")?.name)
        assertNull(SkullcandyModels.fromName("Crusher"))
        assertNull(SkullcandyModels.fromName("JBL Tune 720BT"))
    }

    @Test
    fun theClassicEntryWinsWhenANameHasTwo() {
        val evo = SkullcandyModels.fromName("Crusher Evo")
        assertNotNull(evo)
        assertEquals(0x30, evo!!.id)
        assertTrue("bluetoothClassic" in evo.android)
    }

    @Test
    fun everyProductWithAnSdkConfigurationIsAnAirohaOne() {
        val withSdk = SkullcandyProducts.ALL.filter { it.sdk != null }
        assertEquals(setOf("PUSH_ACTIVE", "GRIND", "GRIND_FUEL", "CHP200", "T70", "T80_PLUS", "T99_PLUS", "T120", "CHP300"), withSdk.mapNotNull { it.sdk }.toSet())
        assertTrue(withSdk.all { it.chip == "airoha" })
    }
}
