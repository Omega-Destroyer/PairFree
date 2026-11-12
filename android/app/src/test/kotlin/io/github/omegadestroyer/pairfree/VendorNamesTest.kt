package io.github.omegadestroyer.pairfree

import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EarfunBandEq
import io.github.omegadestroyer.pairfree.eq.JblBandEq
import io.github.omegadestroyer.pairfree.eq.NothingBandEq
import io.github.omegadestroyer.pairfree.eq.OneMoreBandEq
import io.github.omegadestroyer.pairfree.eq.PixelBandEq
import io.github.omegadestroyer.pairfree.eq.SamsungBandEq
import io.github.omegadestroyer.pairfree.eq.SoundcoreBandEq
import io.github.omegadestroyer.pairfree.protocol.Models
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunModels
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunSession
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblSession
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreModels
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreSession
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelFamily
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelSession
import io.github.omegadestroyer.pairfree.protocol.sony.SonyEqs
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreEqSpec
import io.github.omegadestroyer.pairfree.ui.VendorNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.util.TreeMap
import java.util.TreeSet

/**
 * Every EQ preset name the Sound tab or the widget can show has an entry in the language file (res/values/strings.xml),
 * so a translator finds them all in one place: the names PairFree gives itself, and the vendors' own wording from their
 * tables. The names are gathered here from the same adapters the app shows them through, written to
 * `build/vendor-names.tsv`, and `python tools/gen_vendor_strings.py` turns that list into the two generated sections of
 * the language file and into [VendorNames]. This test fails while they are behind.
 */
class VendorNamesTest {
    private val own = TreeMap<String, TreeSet<String>>()
    private val vendor = TreeMap<String, TreeSet<String>>()

    private fun MutableMap<String, TreeSet<String>>.add(brand: String, names: Iterable<String>) {
        for (name in names) if (name.isNotBlank()) getOrPut(name) { TreeSet() } += brand
    }

    private fun gather() {
        // The names PairFree gives: the keys of these tables are the vendors', the words are PairFree's.
        own.add("Samsung", SamsungBandEq.NAMES.values)
        own.add("Nothing", NothingBandEq.NAMES.values)
        own.add("1MORE", OneMoreBandEq.NAMES.values)
        own.add("Pixel Buds", listOf(PixelBandEq.LAST_SAVED))

        // The vendors' own wording, from their tables.
        for (model in Models.ALL) vendor.add("SOUNDPEATS", model.eq?.presets.orEmpty().map { it.name })
        for (spec in SoundcoreEqSpec.ALL) vendor.add("Soundcore", BandEqTarget(SoundcoreBandEq(spec, { null }, true)).layout.presets.map { it.name })
        for (model in JblModels.ALL) if (model.eq?.written == true) vendor.add("JBL", JblBandEq(JblSession(model)).eqPresetNames)
        for (model in EarfunModels.ALL) model.eq?.let { vendor.add("EarFun", EarfunBandEq(EarfunSession(model), it).eqPresetNames) }
        for (model in PixelModels.ALL) if (model.family == PixelFamily.ALLEGRO) {
            vendor.add("Pixel Buds", PixelBandEq(PixelSession(model.transport, model)).eqPresetNames)
        }
        vendor.add("Sony", SonyEqs.ALL.filter { !it.custom }.map { it.label })
        for (model in OneplusModels.ALL) vendor.add("OnePlus", model.presets.map { it.name })
        // 1MORE preset sounds PairFree has no word for show under the vendor's key.
        for (model in OneMoreModels.ALL) vendor.add("1MORE", OneMoreBandEq(OneMoreSession(model)).eqPresetNames)
        vendor.keys.removeAll(own.keys)
    }

    @Test
    fun everyNameShownHasAnEntryInTheLanguageFile() {
        gather()
        val lines = own.map { (name, brands) -> "own\t$name\t${brands.joinToString(", ")}" } +
            vendor.map { (name, brands) -> "vendor\t$name\t${brands.joinToString(", ")}" }
        File("build").mkdirs()
        File("build/vendor-names.tsv").writeText(lines.joinToString("\n", postfix = "\n"))
        assertEquals(
            (own.keys + vendor.keys).toSortedSet().joinToString("\n"),
            VendorNames.ID.keys.toSortedSet().joinToString("\n"),
            "The names shown and the language file differ: run python tools/gen_vendor_strings.py (it reads android/app/build/vendor-names.tsv)",
        )
    }
}
