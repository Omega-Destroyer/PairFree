package io.github.omegadestroyer.soundpeter.protocol.soundcore

/**
 * Everything on the Controls tab for Soundcore earbuds, read from one state reply: the touch
 * actions ([SoundcoreButtons]), touch tone, touch lock, whether touch controls are on at all,
 * and which noise modes the noise-control action cycles through. Each is there only when
 * OpenSCQ30 offers it on the model ([SoundcoreLayout.features]) and the reply holds it. Every
 * change returns its packets and the state as it should be once the earbuds have taken it.
 */
public class SoundcoreTouch private constructor(public val state: SoundcoreState, public val spec: SoundcoreButtonSpec?) {
    private val layout = state.layout
    private fun offers(feature: String) = feature in layout.features

    /** Each group of buttons with the buttons as the earbuds hold them now. */
    public val groups: List<Pair<SoundcoreButtonGroup, List<TouchButton>>> =
        spec?.groups.orEmpty().mapNotNull { g -> SoundcoreButtons.read(g, state)?.let { g to it } }

    public val touchTone: Boolean? get() = flag("touch_tone", "touch_tone")

    public val touchLock: Boolean? get() = flag("touch_lock", "touch_lock")

    /** False while every touch control is off; null on models without that switch. */
    public val touchEnabled: Boolean? get() = disableField?.let { f -> state.byte(f)?.let { it != 1 } }

    private val disableField: String? = when {
        offers("disable_all_buttons") -> "disable_all_buttons"
        offers("d1301_tap_controls_disabled") -> "tap_controls_disabled"
        else -> null
    }

    private val cycleTws = offers("ambient_sound_mode_cycle_tws")

    /** The noise modes the noise-control action cycles through, or null on models without the setting. */
    public val noiseCycle: Set<AmbientMode>?
        get() {
            if (!offers("ambient_sound_mode_cycle") && !cycleTws) return null
            val byte = state.byte(CYCLE) ?: return null
            val bits = if (cycleTws && !SoundcoreButtons.tws(state)) byte shr 4 else byte and 0xF
            return CYCLE_BITS.filter { (_, bit) -> bits and bit != 0 }.map { it.first }.toSet()
        }

    public val canReset: Boolean get() = spec?.reset == true && groups.isNotEmpty()

    /** Both earbuds are in, as the TWS status says; [groups] then show the actions for both. */
    public val bothIn: Boolean get() = SoundcoreButtons.tws(state)

    /** The model keeps separate actions for when one earbud is used alone. */
    public val hasOneEarbudActions: Boolean
        get() = groups.any { (g, _) -> g.presses.values.any { it.action == ButtonByte.TWS } }

    /** Nothing to show: the model has none of these, or the reply does not hold them. */
    public val isEmpty: Boolean
        get() = groups.isEmpty() && touchTone == null && touchLock == null && touchEnabled == null && noiseCycle == null

    public fun setButton(group: SoundcoreButtonGroup, side: TouchSide, press: TouchPress, actionId: Int?): SoundcoreButtons.Change? =
        SoundcoreButtons.set(group, state, side, press, actionId)

    public fun setTouchTone(on: Boolean): SoundcoreButtons.Change? =
        touchTone?.let { change(SoundcoreCommand.TOUCH_TONE, "touch_tone", if (on) 1 else 0) }

    public fun setTouchLock(on: Boolean): SoundcoreButtons.Change? =
        touchLock?.let { change(SoundcoreCommand.TOUCH_LOCK, "touch_lock", if (on) 1 else 0) }

    public fun setTouchEnabled(on: Boolean): SoundcoreButtons.Change? =
        disableField?.takeIf { touchEnabled != null }?.let { change(SoundcoreCommand.BUTTONS_DISABLED, it, if (on) 0 else 1) }

    /** Null when the model has no cycle setting, or [modes] is empty. */
    public fun setNoiseCycle(modes: Set<AmbientMode>): SoundcoreButtons.Change? {
        if (noiseCycle == null || modes.isEmpty()) return null
        val bits = CYCLE_BITS.filter { it.first in modes }.sumOf { it.second }
        val old = state.byte(CYCLE) ?: return null
        val byte = when {
            !cycleTws -> bits
            SoundcoreButtons.tws(state) -> (old and 0xF0) or bits
            else -> (old and 0x0F) or (bits shl 4)
        }
        return change(SoundcoreCommand.SOUND_MODE_CYCLE, CYCLE, byte)
    }

    /** The request that restores the model's default touch actions; read the state afterwards. */
    public fun reset(): SoundcorePacket? = if (canReset) SoundcorePacket(SoundcoreCommand.RESET_BUTTONS, ByteArray(0)) else null

    private fun flag(feature: String, field: String): Boolean? = if (offers(feature)) state.byte(field)?.let { it == 1 } else null

    private fun change(command: Int, field: String, value: Int): SoundcoreButtons.Change =
        SoundcoreButtons.Change(
            listOf(SoundcorePacket(command, byteArrayOf(value.toByte()))),
            state.with(field, byteArrayOf(value.toByte())),
        )

    public companion object {
        private const val CYCLE = "sound_mode_cycle"

        /** OpenSCQ30's bits for each mode in the cycle byte. */
        private val CYCLE_BITS = listOf(AmbientMode.NOISE_CANCELLING to 1, AmbientMode.TRANSPARENCY to 2, AmbientMode.NORMAL to 4)

        public fun of(state: SoundcoreState): SoundcoreTouch = SoundcoreTouch(state, SoundcoreButtonSpec.of(state.layout.model))
    }
}
