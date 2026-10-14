package io.github.omegadestroyer.pairfree.protocol

/**
 * What a recorded EQ curve is, when it is one PairFree already knows: byte for byte the
 * base curve or a preset of a model in data/soundpeats/devices.json, or that base curve plus whole-dB
 * slider gains (the user's own curve in PeatsAudio, which teaches nothing new).
 */
public sealed interface CurveMatch {
    public val model: Model

    /** The base curve ([EqCapture.CLASSIC]) or one of the model's presets. */
    public data class Preset(override val model: Model, val name: String) : CurveMatch

    /** The base curve plus these slider gains. */
    public data class Custom(override val model: Model, val gains: List<Int>) : CurveMatch

    public companion object {
        /**
         * The known curve [write] sends, or null for a new one. The model whose firmware the
         * write came with is tried first, then every other model with a known EQ.
         */
        public fun of(write: EqCapture.Write): CurveMatch? {
            val models = Models.ALL.filter { it.eq != null }
            val ordered = models.filter { it.code == write.firmware?.code } + models.filter { it.code != write.firmware?.code }
            for (model in ordered) match(model, model.eq!!, write)?.let { return it }
            return null
        }

        private fun match(model: Model, layout: EqLayout, write: EqCapture.Write): CurveMatch? {
            if (write.command != layout.command || write.payloads.size != layout.bands.size) return null
            val zeros = List(layout.bands.size) { 0 }
            if (same(layout.payloads(zeros), write.payloads)) return Preset(model, EqCapture.CLASSIC)
            layout.presets.firstOrNull { same(layout.payloads(it), write.payloads) }?.let { return Preset(model, it.name) }
            // A slider curve: each band's gain is the base gain plus whole dB.
            val gains = write.bands.mapIndexed { i, b -> kotlin.math.round(b.gainDb - layout.bands[i].gainDb).toInt() }
            if (gains.any { it !in -layout.userRangeDb..layout.userRangeDb }) return null
            return if (same(layout.payloads(gains), write.payloads)) Custom(model, gains) else null
        }

        private fun same(a: List<ByteArray>, b: List<ByteArray>) = a.size == b.size && a.zip(b).all { (x, y) -> x.contentEquals(y) }
    }
}
