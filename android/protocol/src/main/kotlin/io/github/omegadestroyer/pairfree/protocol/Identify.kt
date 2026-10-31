package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.apple.AppleWire
import io.github.omegadestroyer.pairfree.protocol.bose.BoseModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingWire
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyWire
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels

/** The brands PairFree controls. */
public enum class Maker { SOUNDPEATS, SOUNDCORE, SAMSUNG, SONY, BOSE, JBL, APPLE, NOTHING, PIXEL }

/**
 * Which brand a paired device is, from what Android keeps for it: its Bluetooth name and the services it lists
 * (data/identify.json, docs/DESIGN.md, Finding the earbuds). Brand first, model later: each brand's own code then
 * picks the model, and its protocol reports the model once connected.
 *
 * 1. A name in the not-ours list ([IdentifyTables.NOT_OURS]) is no brand of ours.
 * 2. A service only one brand's devices list decides; two such brands decide nothing.
 * 3. Else a whole word of the name that is one brand's ([IdentifyTables.BRAND_WORDS]).
 * 4. Else the name is exactly a model name of one brand (Sony's as Sony matches them: its names hold no brand word).
 *
 * Two brands claiming a device at any step leave it out rather than guess.
 */
public object Identify {
    public fun maker(name: String, uuids: List<String>): Maker? {
        val n = name.trim()
        val words = words(n)
        if (IdentifyTables.NOT_OURS.any { phrase -> containsPhrase(words, words(phrase)) }) return null
        single(Maker.entries.filter { hasOwnService(it, uuids) })?.let { return it.value }
        single(Maker.entries.filter { m -> IdentifyTables.BRAND_WORDS.getValue(m.name).any { w -> w.uppercase() in words } })?.let { return it.value }
        return single(Maker.entries.filter { modelName(it, n) })?.value
    }

    /** The one maker in [makers]; null inside when two or more claim it (left out), and no result when none does. */
    private fun single(makers: List<Maker>): Claim? = when (makers.size) {
        0 -> null
        1 -> Claim(makers[0])
        else -> Claim(null)
    }

    private class Claim(val value: Maker?)

    private fun hasOwnService(m: Maker, uuids: List<String>): Boolean {
        val list = uuids.map { it.lowercase() }
        return when (m) {
            Maker.SOUNDCORE -> list.any(SoundcoreModels::isVendorService)
            Maker.SAMSUNG -> SamsungModels.hasOwnService(list)
            Maker.SONY -> SonyWire.SET1_SERVICE.lowercase() in list || SonyWire.SET2_SERVICE.lowercase() in list
            Maker.BOSE -> BoseModels.hasService(list)
            Maker.APPLE -> AppleWire.SERVICE_UUID.lowercase() in list
            Maker.NOTHING -> NothingWire.SERVICE_UUID.lowercase() in list
            Maker.PIXEL -> PixelModels.hasService(list)
            // The standard serial service, and Airoha's, which other brands' earbuds offer too.
            Maker.SOUNDPEATS, Maker.JBL -> false
        }
    }

    private fun modelName(m: Maker, name: String): Boolean = when (m) {
        Maker.SOUNDPEATS -> Models.find(null, name) != null
        Maker.SOUNDCORE -> SoundcoreModels.fromName(name) != null
        Maker.SONY -> SonyModels.fromName(name) != null
        Maker.JBL -> JblModels.fromName(name) != null
        Maker.NOTHING -> NothingModels.fromName(name) != null
        Maker.PIXEL -> PixelModels.fromName(name) != null
        // Galaxy Buds names hold "Galaxy"; Bose's "Bose"; Beats without Apple's service use another protocol.
        Maker.SAMSUNG, Maker.BOSE, Maker.APPLE -> false
    }

    /** The name's words, upper case: letters, digits and +, split at anything else. */
    private fun words(s: String): List<String> = s.uppercase().split(Regex("[^A-Z0-9+]+")).filter { it.isNotEmpty() }

    private fun containsPhrase(words: List<String>, phrase: List<String>): Boolean =
        phrase.isNotEmpty() && (0..words.size - phrase.size).any { i -> phrase.indices.all { words[i + it] == phrase[it] } }
}
