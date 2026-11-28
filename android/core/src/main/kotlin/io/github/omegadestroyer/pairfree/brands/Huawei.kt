package io.github.omegadestroyer.pairfree.brands

import io.github.omegadestroyer.pairfree.bluetooth.Brand
import io.github.omegadestroyer.pairfree.bluetooth.ControlLink
import io.github.omegadestroyer.pairfree.bluetooth.Paired
import io.github.omegadestroyer.pairfree.eq.BandEqTarget
import io.github.omegadestroyer.pairfree.eq.EqSource
import io.github.omegadestroyer.pairfree.protocol.EarbudState
import io.github.omegadestroyer.pairfree.protocol.Model
import io.github.omegadestroyer.pairfree.protocol.Wire
import io.github.omegadestroyer.pairfree.protocol.huawei.HuaweiModels
import io.github.omegadestroyer.pairfree.protocol.huawei.HuaweiPacket
import io.github.omegadestroyer.pairfree.protocol.huawei.HuaweiParser
import io.github.omegadestroyer.pairfree.protocol.huawei.HuaweiProduct
import io.github.omegadestroyer.pairfree.protocol.huawei.HuaweiTalk
import io.github.omegadestroyer.pairfree.protocol.widget.NoiseChoice

/** Huawei AI Life and the Honor models in its Smart Audio plugin. */
object HuaweiSpec : BrandSpec(Brand.HUAWEI) {
    override val officialApp = "HUAWEI AI Life"

    override fun paired(address: String, name: String, uuids: List<String>): Paired? =
        (HuaweiModels.fromName(name) ?: HuaweiModels.unknown(name))?.let {
            Paired(address, name, brand, Wire.SPP_UUID, it.id)
        }

    override fun model(device: Paired, state: EarbudState): Model? = product(device)?.model

    override fun driver(device: Paired, host: DriverHost): BrandDriver =
        HuaweiDriver(host, checkNotNull(product(device)))

    override val mayHaveNoise = true

    override fun noiseOffline(device: Paired): List<NoiseChoice>? =
        product(device)?.noiseModes?.map { NoiseChoice(listOf("off", "anc", "transparency")[it]) }

    override fun mayHaveEq(device: Paired): Boolean = product(device)?.mayHaveEq == true

    private fun product(device: Paired): HuaweiProduct? =
        HuaweiModels.of(device.model) ?: HuaweiModels.fromName(device.name) ?: HuaweiModels.unknown(device.name)
}

private class HuaweiDriver(host: DriverHost, private val product: HuaweiProduct) : TalkDriver<HuaweiPacket, HuaweiTalk>(host) {
    override fun talk(opened: ControlLink): HuaweiTalk = HuaweiTalk(product)

    override val followsEqReports: Boolean get() = true

    override fun eqSource(device: Paired, model: Model?): EqSource? {
        val current = talk ?: return null
        if (current.eqPresetNames.isEmpty() && current.eqBandsHz.isEmpty()) return null
        val target = BandEqTarget(current)
        return EqSource(listOf(current.eqPresetNames, current.eqBandsHz, current.eqPresetGains), target.layout, target) { existing ->
            current.eqReport?.let { target.reportedAs(it, existing) }
        }
    }

    override fun sendEq(frames: List<ByteArray>) {
        val current = talk ?: return
        val parser = HuaweiParser()
        val packets = frames.flatMap(parser::feed).filter(current::isEqWrite)
        if (packets.isNotEmpty()) send(current, packets, current.eqReadBack(), EQ_WRITE)
    }

    override fun report(info: ReportInfo): String? {
        val current = talk ?: return null
        return buildString {
            reportHead(info, bluetoothName = false)
            appendLine("Brand:           Huawei AI Life")
            appendLine("Model:           ${product.name}")
            appendLine("Firmware:        ${current.state.firmware?.version ?: "unknown"}")
            reportApp(info)
            appendLine()
            appendLine("The last answer to each of PairFree's reads (service and command, then tag=bytes in hex):")
            current.reportLines.forEach { appendLine("  $it") }
        }
    }
}
