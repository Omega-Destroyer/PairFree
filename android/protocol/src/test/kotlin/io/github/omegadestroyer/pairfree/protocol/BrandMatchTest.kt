package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.apple.AppleModels
import io.github.omegadestroyer.pairfree.protocol.bose.BoseModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyModels
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Earbuds of brands PairFree does not control must not pass for one it does: they would show and never connect. */
class BrandMatchTest {
    private val spp = "00001101-0000-1000-8000-00805f9b34fb"
    private val handsFree = "0000111e-0000-1000-8000-00805f9b34fb"

    private fun claims(name: String, uuids: List<String>): List<String> = listOfNotNull(
        "Soundcore".takeIf { SoundcoreModels.identify(name, uuids) != null },
        "Samsung".takeIf { SamsungModels.identify(name, uuids) != null },
        "Sony".takeIf { SonyModels.identify(name, uuids) != null },
        "Bose".takeIf { BoseModels.isBose(name, uuids) },
        "JBL".takeIf { JblModels.fromName(name) != null },
        "Apple".takeIf { AppleModels.identify(name, uuids) },
        "Nothing".takeIf { NothingModels.identify(name, uuids) },
        "SOUNDPEATS".takeIf { Models.isSupported(name) },
    )

    @Test
    fun googlesPixelBudsAreNobodysEarbuds() {
        // GitHub issue #4: a Pixel Buds was listed, as Galaxy Buds, and never connected.
        for (name in listOf("Pixel Buds Pro", "Pixel Buds Pro 2", "Pixel Buds A-Series", "Pixel Buds Live", "Pixel Buds 2a")) {
            for (uuids in listOf(emptyList(), listOf(spp, handsFree))) {
                assertTrue(claims(name, uuids).isEmpty(), "$name $uuids: ${claims(name, uuids)}")
            }
        }
    }
}
