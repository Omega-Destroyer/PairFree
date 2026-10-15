package io.github.omegadestroyer.pairfree.protocol.sony

/**
 * Noise cancelling and ambient sound (NCASM, docs/sony/PROTOCOL.md section 5) as the earbuds last
 * reported it: [payload] is their reply or notification, command byte first, in one of the layouts
 * Sony's app reads. A change is written in the same layout as that reply, with only the changed
 * bytes different, so whatever else it holds goes back as it was.
 */
public class SonyNoise private constructor(public val tableSet: Int, public val payload: ByteArray, private val layout: Layout) {
    public enum class Mode { OFF, NOISE_CANCELLING, AMBIENT }

    /** The type, the payload's second byte. */
    public val type: Int get() = payload.u8(1)

    /** Off, noise cancelling or ambient sound; null for a mode PairFree does not show (Sony's NcSS). */
    public val mode: Mode?
        get() {
            if (payload.u8(layout.on) == 0) return Mode.OFF
            return when (layout.kind) {
                Kind.NC_ONLY -> Mode.NOISE_CANCELLING
                Kind.ASM_ONLY -> Mode.AMBIENT
                Kind.NC_SWITCH -> if (payload.u8(layout.nc) != 0) Mode.NOISE_CANCELLING else Mode.AMBIENT
                Kind.MODE -> when (payload.u8(layout.nc)) {
                    MODE_NC -> Mode.NOISE_CANCELLING
                    MODE_ASM -> Mode.AMBIENT
                    else -> null
                }
            }
        }

    /** Whether the model has noise cancelling, and ambient sound, in this layout. */
    public val hasNoiseCancelling: Boolean get() = layout.kind != Kind.ASM_ONLY
    public val hasAmbient: Boolean get() = layout.kind != Kind.NC_ONLY

    /** The ambient sound level, where the layout has one. */
    public val level: Int? get() = layout.level?.let(payload::u8)

    /** Focus on voice, where the layout has it. */
    public val voice: Boolean? get() = layout.voice?.let { payload.u8(it) == 1 }

    /**
     * Noise cancelling against the wind (Sony's single setting, as against dual), where the layout
     * has the choice; null elsewhere.
     */
    public val wind: Boolean? get() = layout.dualSingle?.let { payload.u8(it) == SINGLE }

    /**
     * The SET that changes what is given and keeps the rest. Null when the layout cannot take the
     * change, such as noise cancelling on a model with ambient sound alone.
     */
    public fun set(mode: Mode? = null, level: Int? = null, voice: Boolean? = null, wind: Boolean? = null): SonyOut? {
        val p = payload.copyOf()
        p[0] = SET.toByte()
        if (tableSet == 2) p[2] = CHANGED.toByte()
        val target = mode ?: this.mode ?: return null
        when (target) {
            Mode.OFF -> p[layout.on] = 0
            Mode.NOISE_CANCELLING -> {
                if (!hasNoiseCancelling) return null
                p[layout.on] = layout.onValue.toByte()
                when (layout.kind) {
                    Kind.NC_SWITCH -> if (p.u8(layout.nc) == 0) p[layout.nc] = (if (layout.dualSingle != null) DUAL else 1).toByte()
                    Kind.MODE -> p[layout.nc] = MODE_NC.toByte()
                    else -> Unit
                }
                // Set 2 keeps the noise cancelling value apart from the mode; Gadgetbridge writes dual (2) with it.
                layout.ncValue?.let { if (p.u8(it) == 0) p[it] = DUAL.toByte() }
            }
            Mode.AMBIENT -> {
                if (!hasAmbient) return null
                p[layout.on] = layout.onValue.toByte()
                when (layout.kind) {
                    Kind.NC_SWITCH -> p[layout.nc] = 0
                    Kind.MODE -> p[layout.nc] = MODE_ASM.toByte()
                    else -> Unit
                }
            }
        }
        if (level != null) p[layout.level ?: return null] = level.toByte()
        if (voice != null) p[layout.voice ?: return null] = (if (voice) 1 else 0).toByte()
        if (wind != null) {
            if (target != Mode.NOISE_CANCELLING) return null
            p[layout.dualSingle ?: return null] = (if (wind) SINGLE else DUAL).toByte()
        }
        return SonyOut(1, p)
    }

    /** The state [out], a SET from [set], leads to, so it shows before the earbuds confirm it. */
    public fun after(out: SonyOut): SonyNoise {
        val p = out.payload.copyOf()
        p[0] = payload[0]
        if (layout.onValue != 1 && p.u8(layout.on) != 0) p[layout.on] = 1
        return SonyNoise(tableSet, p, layout)
    }

    private enum class Kind { NC_ONLY, ASM_ONLY, NC_SWITCH, MODE }

    /**
     * Where each field is. [on] is the on/off byte and [onValue] what a SET puts there for on; [nc] the
     * noise cancelling switch (NC_SWITCH) or the mode (MODE); [dualSingle] the byte that holds dual (2)
     * or single (1); [ncValue] set 2's noise cancelling value beside the mode.
     */
    private class Layout(
        val kind: Kind,
        val on: Int,
        val onValue: Int = 1,
        val nc: Int = -1,
        val dualSingle: Int? = null,
        val ncValue: Int? = null,
        val voice: Int? = null,
        val level: Int? = null,
    )

    public companion object {
        // Both table sets number it alike (data/sony/commands.json).
        private const val SET = SonyCommand.Set1.NCASM_SET_PARAM
        private const val CHANGED = 1
        private const val MODE_NC = 0
        private const val MODE_ASM = 1
        private const val SINGLE = 1
        private const val DUAL = 2

        /** Set 1's noise cancelling setting type that switches between dual, single and off. */
        private const val SET1_DUAL_SINGLE_OFF = 2

        /** Set 1 writes this for on and reports 1: Sony's "adjustment complete", as Gadgetbridge sends it. */
        private const val SET1_ON = 0x11

        /** The earbuds' reply or notification as a [SonyNoise], or null for a layout PairFree does not read. */
        public fun of(tableSet: Int, payload: ByteArray): SonyNoise? {
            if (payload.size < 2) return null
            val layout = layoutOf(tableSet, payload) ?: return null
            return SonyNoise(tableSet, payload.copyOf(), layout)
        }

        /** The type to ask for, by the functions the earbuds listed; null when they list none PairFree reads. */
        public fun typeFor(tableSet: Int, functions: Set<Int>): Int? =
            (if (tableSet == 1) SET1_TYPES else SET2_TYPES).entries.firstOrNull { it.key in functions }?.value

        /** Set 1's functions and the type each is read with (Sony's app: NcAsmInquiredType). */
        private val SET1_TYPES = linkedMapOf(
            SonyFunction.Set1.NOISE_CANCELLING_AND_AMBIENT_SOUND_MODE to 2,
            SonyFunction.Set1.NOISE_CANCELLING to 1,
            SonyFunction.Set1.AMBIENT_SOUND_MODE to 3,
        )

        /** Set 2's functions and the type each is read with, as Sony's app maps them; others are not read. */
        private val SET2_TYPES = linkedMapOf(
            SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AUTO_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT to 0x15,
            SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_SINGLE_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT to 0x16,
            SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT to 0x17,
            SonyFunction.Set2.MODE_NC_NCSS_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT_WITH_TEST_MODE to 0x18,
            SonyFunction.Set2.MODE_NC_ASM_NOISE_CANCELLING_DUAL_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT_NOISE_ADAPTATION to 0x19,
            SonyFunction.Set2.NOISE_CANCELLING_ONOFF_AND_AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT to 0x13,
            SonyFunction.Set2.AMBIENT_SOUND_MODE_LEVEL_ADJUSTMENT to 0x22,
        )

        /**
         * The layouts PairFree reads, from Sony's app's classes (docs/sony/PROTOCOL.md section 5).
         * Set 1: the type, then per type the rest. Set 2: the type, changing (0) or changed (1), on or
         * off, then per type the rest. Set 2's 0x17 is 7 bytes in Sony's app; Gadgetbridge reads it
         * with 8 from the WF-1000XM4, laid out as 0x15, so the length decides.
         */
        private fun layoutOf(tableSet: Int, p: ByteArray): Layout? {
            val type = p.u8(1)
            val size = p.size
            return if (tableSet == 1) {
                when {
                    // NC: setting type, then off (0) or on (1).
                    type == 1 && size == 4 -> Layout(Kind.NC_ONLY, on = 3)
                    // NC and ASM: effect, NC setting type, NC value, ASM setting type, voice, level.
                    type == 2 && size == 8 -> Layout(
                        Kind.NC_SWITCH, on = 2, onValue = SET1_ON, nc = 4, voice = 6, level = 7,
                        dualSingle = 4.takeIf { p.u8(3) == SET1_DUAL_SINGLE_OFF },
                    )
                    // ASM: effect, ASM setting type, voice, level.
                    type == 3 && size == 6 -> Layout(Kind.ASM_ONLY, on = 2, onValue = SET1_ON, voice = 4, level = 5)
                    else -> null
                }
            } else {
                when {
                    type == 0x13 && size == 7 -> Layout(Kind.NC_SWITCH, on = 3, nc = 4, voice = 5, level = 6)
                    (type == 0x15 || type == 0x16 || type == 0x17) && size == 8 -> Layout(
                        Kind.MODE, on = 3, nc = 4, ncValue = 5, voice = 6, level = 7,
                        dualSingle = 5.takeIf { type == 0x16 },
                    )
                    (type == 0x17 || type == 0x18) && size == 7 -> Layout(Kind.MODE, on = 3, nc = 4, voice = 5, level = 6)
                    type == 0x19 && size == 9 -> Layout(Kind.MODE, on = 3, nc = 4, voice = 5, level = 6)
                    type == 0x22 && size == 6 -> Layout(Kind.ASM_ONLY, on = 3, voice = 4, level = 5)
                    else -> null
                }
            }
        }
    }
}
