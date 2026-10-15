package io.github.omegadestroyer.pairfree.ui

import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.SettingChange
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreButtons
import io.github.omegadestroyer.pairfree.protocol.soundcore.SoundcoreTouch
import io.github.omegadestroyer.pairfree.status.StatusChoice

/** What the connected screens can ask for. The app passes its view model; previews pass [NoActions]. */
interface EarbudsActions {
    /** Sends a SET. The change shows at once and is read back from the earbuds afterwards. */
    fun set(command: Command, vararg payload: Int)

    /** Factory reset. Call only from a confirmation the user has just accepted. */
    fun factoryResetConfirmedByUser()

    /** The probe-style report of what the earbuds answered, for the clipboard. */
    fun deviceReport(): String

    /** Back to the list of paired earbuds. */
    fun switchEarbuds()

    /** Opens the EQ capture: recording PeatsAudio's curves for a model (docs/DESIGN.md). */
    fun openCapture()

    /** Starts the ear tip fit test; the result arrives in the earbud state. */
    fun startFitTest()

    /** Changes Soundcore touch controls, made from the last state the earbuds sent. */
    fun changeTouch(make: (SoundcoreTouch) -> SoundcoreButtons.Change?)

    /** Puts Soundcore touch actions back to the model's defaults. Call only from a confirmation. */
    fun resetTouch()

    /** Changes one of the brand's own settings (the connection's settings groups). */
    fun changeSetting(change: SettingChange)

    /** Picks the battery notification for the connected earbuds. */
    fun chooseStatus(choice: StatusChoice)

    /** After the user answered a notification or companion prompt from the Device tab. */
    fun statusChanged()
}

/** Does nothing. For previews. */
object NoActions : EarbudsActions {
    override fun set(command: Command, vararg payload: Int) = Unit
    override fun factoryResetConfirmedByUser() = Unit
    override fun deviceReport(): String = ""
    override fun switchEarbuds() = Unit
    override fun openCapture() = Unit
    override fun startFitTest() = Unit
    override fun changeTouch(make: (SoundcoreTouch) -> SoundcoreButtons.Change?) = Unit
    override fun resetTouch() = Unit
    override fun changeSetting(change: SettingChange) = Unit
    override fun chooseStatus(choice: StatusChoice) = Unit
    override fun statusChanged() = Unit
}
