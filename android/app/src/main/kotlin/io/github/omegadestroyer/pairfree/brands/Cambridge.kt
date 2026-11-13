package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeModels
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeSession
import io.github.omegadestroyer.pairfree.protocol.cambridge.CambridgeTalk

/** Cambridge Audio's legacy Melomania. All its settings are rows. */
object CambridgeSpec : BrandSpec(Brand.CAMBRIDGE) {
    override val officialApp = "Melomania by Cambridge Audio"

    override val offersRecording: Boolean get() = false

    override fun eqNote(device: Paired): Int = R.string.eq_cambridge

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        CambridgeModels.fromName(name)?.let { Paired(address, name, Brand.CAMBRIDGE, service = CambridgeModels.service(uuids), model = it.name) }

    override fun model(device: Paired, state: EarbudState): Model? = CambridgeModels.model(device.model ?: device.name)

    override fun driver(device: Paired, host: DriverHost): BrandDriver = CambridgeDriver(host, device)
}

/** Legacy Melomania ([CambridgeTalk]). It never takes the shared commands or SOUNDPEATS' EQ frames. */
class CambridgeDriver(host: DriverHost, private val device: Paired) : TalkDriver<ByteArray, CambridgeTalk>(host) {
    /** The session of the channel now or last open; for the device report. */
    val session: CambridgeSession? get() = talk?.session

    override fun talk(opened: ControlLink): CambridgeTalk? = CambridgeModels.fromName(device.model ?: device.name)?.let(::CambridgeTalk)

    /** Legacy Melomania's five gain controls have no established frequency grid. They use Sound-tab rows. */
    override fun eqSource(device: Paired, model: Model?): EqSource? = null

    override fun report(info: ReportInfo): String? {
        val session = session ?: return null
        return buildString {
            appendLine("PairFree device report")
            appendLine("Bluetooth name:  ${info.name}")
            appendLine("Brand:           Cambridge Audio (legacy Melomania)")
            appendLine("Model:           ${session.model.name}")
            appendLine("Firmware:        ${session.firmware ?: "unknown"}")
            reportApp(info)
            appendLine("Addresses redacted; only known fields from requested settings reads follow.")
            reportReplies(session.replies)
        }
    }
}
