package io.github.omegadestroyer.soundpeter.ui

import androidx.annotation.DrawableRes

/**
 * Drawings for one model: the left earbud (the right is mirrored), or the whole product
 * for headphones, and the closed case when there is one.
 */
data class DeviceArt(@DrawableRes val bud: Int, @DrawableRes val case: Int?)

/**
 * Picks the drawings by Bluetooth name, then by firmware code; models without drawings
 * of their own get a generic earbud and case. The tables come from tools/gen_device_art.py.
 */
fun deviceArt(firmwareCode: String?, name: String): DeviceArt =
    ART_BY_NAME[name.lowercase()] ?: firmwareCode?.let { ART_BY_CODE[it] } ?: GENERIC_ART
