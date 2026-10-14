package io.github.omegadestroyer.pairfree.protocol

/** The tab a [SettingGroup] shows on. */
public enum class SettingTab { HOME, SOUND, CONTROLS, DEVICE }

/**
 * Settings a brand's earbuds offer beyond the ones the screens know by [Command]: the screens draw
 * them as rows (a switch, a choice, a level or a button) under the heading [key] names, on [tab].
 * Each brand's session builds its groups from the earbuds' state, and only with what the model has.
 */
public data class SettingGroup(val key: String, val tab: SettingTab, val settings: List<Setting>)

/** One row of a [SettingGroup]. [key] names it for the screens' words and for the change that sets it. */
public sealed interface Setting {
    public val key: String

    /** A switch. Not [enabled] while another setting rules it out. */
    public data class Toggle(override val key: String, val on: Boolean, val enabled: Boolean = true) : Setting

    /**
     * One of [options], each a key for the screens' words. [value] can be an option the screens
     * show but cannot pick, such as one set in the brand's own app.
     */
    public data class Choice(override val key: String, val value: String?, val options: List<String>) : Setting

    /** A whole step from [min] to [max]. */
    public data class Level(override val key: String, val value: Int, val min: Int, val max: Int) : Setting

    /** A button; [active] while what it started is still going, when pressing it again stops it. */
    public data class Action(override val key: String, val active: Boolean = false) : Setting
}

/** A change to a [Setting] from the screens. */
public sealed interface SettingChange {
    public val key: String

    public data class Toggle(override val key: String, val on: Boolean) : SettingChange

    public data class Choice(override val key: String, val option: String) : SettingChange

    public data class Level(override val key: String, val value: Int) : SettingChange

    public data class Press(override val key: String) : SettingChange
}
