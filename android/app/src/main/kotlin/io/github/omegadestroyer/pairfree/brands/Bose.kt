package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.Command
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.bose.BoseModels
import io.github.omegadestroyer.pairfree.protocol.bose.BosePacket
import io.github.omegadestroyer.pairfree.protocol.bose.BoseSession
import io.github.omegadestroyer.pairfree.protocol.bose.BoseTalk

/** Bose (docs/bose/PROTOCOL.md). It has no shared noise mode: its settings are all rows. */
object BoseSpec : BrandSpec(Brand.BOSE) {
    override val officialApp = "Bose"

    override fun eqNote(device: Paired): Int = R.string.eq_bose

    override fun paired(address: String, name: String, uuids: List<String>): Paired = Paired(address, name, Brand.BOSE)

    /** By the product the device reports, which names the firmware line; before then, its name. */
    override fun model(device: Paired, state: EarbudState): Model? =
        BoseModels.model(state.firmware?.code ?: device.name, if (state.supports(Command.BATTERY_RIGHT)) Form.EARBUDS else Form.HEADPHONES)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = BoseDriver(host)
}

/** Bose headphones and earbuds ([BoseTalk]). */
class BoseDriver(host: DriverHost) : TalkDriver<BosePacket, BoseTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: BoseSession? get() = talk?.session

    override fun talk(opened: ControlLink): BoseTalk = BoseTalk()

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Bose")
            val id = session.productId?.let { "%04X".format(it) } ?: "unknown"
            appendLine("Model:           ${session.model?.name ?: "not in PairFree's table"} (product $id, variant ${session.variant ?: "?"})")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last message of each kind the device sent (block.function, operator: payload as hex;")
            appendLine("operator 3 is a value, 4 an error, whose first byte says which):")
            reportReplies(session.replies)
        }
    }
}
