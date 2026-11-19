package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyAtSession
import io.github.omegadestroyer.pairfree.protocol.skullcandy.SkullcandyModels
import io.github.omegadestroyer.pairfree.protocol.skullcandy.model
import io.github.omegadestroyer.pairfree.protocol.skullcandy.service
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesAt
import io.github.omegadestroyer.pairfree.protocol.skullcandy.usesAtSerial

/**
 * Skullcandy's earbuds and headphones, from Skullcandy's own app (5.0.6, docs/skullcandy/PROTOCOL.md). The products are told by their Bluetooth name; the chip family and
 * the protocol follow from the app's own table: the Grind, Grind Fuel, Push Active and Crusher ANC 2 speak AT lines over their own serial service ([SkullcandyAtSession]).
 * The others are found but have no session yet.
 */
object SkullcandySpec : BrandSpec(Brand.SKULLCANDY) {
    override val officialApp = "Skullcandy"

    override val offersRecording: Boolean get() = false

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        SkullcandyModels.fromName(name)?.let { p -> Paired(address, name, Brand.SKULLCANDY, service = p.service, model = p.name) }

    override fun model(device: Paired, state: EarbudState): Model? = SkullcandyModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = SkullcandyDriver(host, device)

    override val mayHaveNoise: Boolean get() = true
}

/** The AT conversation ([SkullcandyAtSession]); the models no session speaks yet get no talk, so the channel stays quiet. */
class SkullcandyDriver(host: DriverHost, private val device: Paired) : TalkDriver<String, SkullcandyAtSession>(host) {
    val session: SkullcandyAtSession? get() = talk

    override fun talk(opened: ControlLink): SkullcandyAtSession? = SkullcandyModels.fromName(device.model ?: device.name)?.takeIf { it.usesAt || it.usesAtSerial }?.let { SkullcandyAtSession(it) }

    override fun report(info: ReportInfo): String? {
        val s = session ?: return null
        return buildString {
            reportHead(info)
            appendLine("Brand:           Skullcandy (AT)")
            appendLine("Model:           ${s.product.name}")
            appendLine("Chip family:     ${s.product.chip}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each line PairFree sent (line, then the reply lines):")
            reportReplies(s.replies)
        }
    }
}
