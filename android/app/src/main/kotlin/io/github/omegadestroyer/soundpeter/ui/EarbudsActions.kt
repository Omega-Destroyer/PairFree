package io.github.omegadestroyer.soundpeter.ui

import io.github.omegadestroyer.soundpeter.protocol.Command

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
}

/** Does nothing. For previews. */
object NoActions : EarbudsActions {
    override fun set(command: Command, vararg payload: Int) = Unit
    override fun factoryResetConfirmedByUser() = Unit
    override fun deviceReport(): String = ""
    override fun switchEarbuds() = Unit
}
