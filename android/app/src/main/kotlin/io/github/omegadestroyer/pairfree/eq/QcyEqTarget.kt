package io.github.omegadestroyer.pairfree.eq

import io.github.omegadestroyer.pairfree.protocol.EqBand
import io.github.omegadestroyer.pairfree.protocol.EqLayout
import io.github.omegadestroyer.pairfree.protocol.EqPreset
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyControl
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyEq
import io.github.omegadestroyer.pairfree.protocol.qcy.QcyWire

/**
 * QCY earbuds and headphones (docs/qcy/PROTOCOL.md section 7): a curve of the user's own over the bands the
 * earbuds reported, through the parametric EQ where they answered for it, else the EQ characteristic. QCY's
 * presets are curves its servers send, which PairFree does not have, so there are none; Stock is every band
 * at 0. The earbuds report what they hold, so the screen follows them.
 *
 * [frames] are the session's GATT messages, which the connection queues on the QCY link.
 */
class QcyEqTarget(val session: QcyControl, eq: QcyEq) : EqTarget {
    override val reportsEq: Boolean = true
    override val hasPreamp: Boolean = false
    override val hasStock: Boolean = true

    /** The screen's view: the bands the earbuds reported, each from minus to plus the model's range. */
    val layout: EqLayout = EqLayout(
        command = QcyWire.EQ_SHORT,
        masterGainDb = 0.0,
        userRangeDb = eq.maxDb,
        bands = eq.freqs.map { EqBand(it, 0.0, 0.0) },
    )

    /** The bands this target was made for; another set needs another target. */
    val freqs: List<Int> = eq.freqs

    override fun frames(layout: EqLayout, setting: EqSetting, preset: EqPreset?): List<ByteArray> =
        if (preset != null) emptyList() else session.eqCustom(setting.gains).orEmpty()

    /** What [eq] says the earbuds hold, as the screen's gains; null while they hold one of QCY's presets, whose curve is unknown. */
    fun reported(eq: QcyEq): List<Int>? = eq.gains.takeIf { eq.custom && eq.freqs == freqs }
}
