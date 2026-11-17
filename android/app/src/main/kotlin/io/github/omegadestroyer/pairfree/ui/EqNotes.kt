package io.github.omegadestroyer.pairfree.ui

import androidx.annotation.StringRes
import io.github.omegadestroyer.pairfree.R
import io.github.omegadestroyer.pairfree.brands.EqNote

/** The language file's text for each note a brand gives while it has no equalizer to show. */
@get:StringRes
val EqNote.text: Int
    get() = when (this) {
        EqNote.UNAVAILABLE -> R.string.eq_unavailable
        EqNote.APPLE -> R.string.eq_apple
        EqNote.BEATS -> R.string.eq_beats
        EqNote.BOSE -> R.string.eq_bose
        EqNote.CAMBRIDGE -> R.string.eq_cambridge
        EqNote.CAMBRIDGE_CONNECT -> R.string.eq_cambridge_connect
        EqNote.EARFUN -> R.string.eq_earfun
        EqNote.JBL -> R.string.eq_jbl
        EqNote.NOTHING -> R.string.eq_nothing
        EqNote.ONEMORE -> R.string.eq_onemore
        EqNote.ONEPLUS -> R.string.eq_oneplus
        EqNote.PIXEL -> R.string.eq_pixel
        EqNote.QCY -> R.string.eq_qcy
        EqNote.SENNHEISER -> R.string.eq_sennheiser
        EqNote.SONY_NONE -> R.string.eq_sony_none
        EqNote.SOUNDCORE_UNCHECKED -> R.string.eq_soundcore_unchecked
    }
