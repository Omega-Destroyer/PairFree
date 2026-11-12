package io.github.omegadestroyer.pairfree.protocol

import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeModels
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusModels
import io.github.omegadestroyer.pairfree.protocol.oneplus.OneplusWire

import io.github.omegadestroyer.pairfree.protocol.apple.AppleModels
import io.github.omegadestroyer.pairfree.protocol.apple.AppleWire
import io.github.omegadestroyer.pairfree.protocol.bose.BoseModels
import io.github.omegadestroyer.pairfree.protocol.earfun.EarfunModels
import io.github.omegadestroyer.pairfree.protocol.jbl.JblModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingModels
import io.github.omegadestroyer.pairfree.protocol.nothing.NothingWire
import io.github.omegadestroyer.pairfree.protocol.onemore.OneMoreModels
import io.github.omegadestroyer.pairfree.protocol.pixel.PixelModels
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyModels
import io.github.omegadestroyer.pairfree.protocol.samsung.SamsungModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyModels
import io.github.omegadestroyer.pairfree.protocol.sony.SonyWire
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreModels

/** The brands PairFree controls. */
public enum class Maker { SOUNDPEATS, SOUNDCORE, SAMSUNG, SONY, BOSE, JBL, APPLE, NOTHING, PIXEL, QCY, ONEMORE, EARFUN, CAMBRIDGE, ONEPLUS }

/**
 * Which brand a paired device is, from what Android keeps for it: its Bluetooth name and the services it lists
 * (data/identify.json, docs/DESIGN.md, Finding the earbuds). Brand first, model later: each brand's own code then
 * picks the model, and its protocol reports the model once connected.
 *
 * 1. A name in the not-ours list ([IdentifyTables.NOT_OURS]) is no brand of ours.
 * 2. A service only one brand's devices list decides; with two such brands the name (steps 3 and 4) decides between those two, else nothing.
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
        val byService = Maker.entries.filter { hasOwnService(it, uuids) }
        // Two brands' services on one device (a user's "Nothing Ear" listed Nothing's and another brand's, issue #30) are told apart by the name, never guessed.
        val only = byService.singleOrNull()
        if (only != null && only != Maker.ONEPLUS) return only
        // OnePlus's service may sit on other makers' OPPO-based chips (a hypothesis for issue #30), so a name that is another brand's decides over it.
        if (only == Maker.ONEPLUS) return byName(Maker.entries.filter { it != Maker.ONEPLUS }, n, words)?.value ?: Maker.ONEPLUS
        return byName(byService.ifEmpty { Maker.entries }, n, words)?.value
    }

    /** Steps 3 and 4 over [candidates]: a brand word of the name, else the name as a model name; null when neither says. */
    private fun byName(candidates: List<Maker>, name: String, words: List<String>): Claim? =
        single(candidates.filter { m -> IdentifyTables.BRAND_WORDS.getValue(m.name).any { w -> w.uppercase() in words } })
            ?: single(candidates.filter { modelName(it, name) })

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
            Maker.ONEPLUS -> OneplusWire.SPP_UUID.lowercase() in list
            Maker.PIXEL -> PixelModels.hasService(list)
            // The standard serial service, and Airoha's and JieLi's, which other brands' earbuds offer too.
            Maker.SOUNDPEATS, Maker.JBL, Maker.QCY, Maker.ONEMORE, Maker.EARFUN, Maker.CAMBRIDGE -> false
        }
    }

    private fun modelName(m: Maker, name: String): Boolean = when (m) {
        Maker.SOUNDPEATS -> Models.find(null, name) != null
        Maker.SOUNDCORE -> SoundcoreModels.fromName(name) != null
        Maker.SONY -> SonyModels.fromName(name) != null
        Maker.JBL -> JblModels.fromName(name) != null
        Maker.NOTHING -> NothingModels.fromName(name) != null
        Maker.PIXEL -> PixelModels.fromName(name) != null
        Maker.QCY -> QcyModels.fromName(name).isNotEmpty()
        Maker.ONEMORE -> OneMoreModels.fromName(name) != null
        Maker.EARFUN -> EarfunModels.fromName(name) != null
        Maker.CAMBRIDGE -> CambridgeModels.fromName(name) != null
        Maker.ONEPLUS -> OneplusModels.fromName(name) != null
        // Galaxy Buds names hold "Galaxy"; Bose's "Bose"; the Beats that speak B2P are known by name.
        Maker.APPLE -> AppleModels.b2p(name) != null || AppleModels.bcd(name) != null
        Maker.SAMSUNG, Maker.BOSE -> false
    }

    /** The name's words, upper case: letters, digits and +, split at anything else. */
    private fun words(s: String): List<String> = s.uppercase().split(Regex("[^A-Z0-9+]+")).filter { it.isNotEmpty() }

    private fun containsPhrase(words: List<String>, phrase: List<String>): Boolean =
        phrase.isNotEmpty() && (0..words.size - phrase.size).any { i -> phrase.indices.all { words[i + it] == phrase[it] } }
}
