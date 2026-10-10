package io.github.omegadestroyer.soundpeter.ui

import androidx.annotation.DrawableRes
import io.github.omegadestroyer.soundpeter.R

/** Drawings for one model: the left earbud (the right is mirrored) and the closed case. */
data class DeviceArt(@DrawableRes val bud: Int, @DrawableRes val case: Int)

/**
 * Picks the drawings by firmware code, falling back to the Bluetooth name while the code
 * is still unknown. The drawings come from tools/gen_device_art.py. Models without art
 * get none until the generic fallback drawings in docs/DESIGN.md exist.
 */
fun deviceArt(firmwareCode: String?, name: String): DeviceArt? = when {
    firmwareCode == "S5" || name.equals("SOUNDPEATS H3", ignoreCase = true) ->
        DeviceArt(R.drawable.device_h3_bud, R.drawable.device_h3_case)
    name.equals("SOUNDPEATS Air5 Pro+", ignoreCase = true) ->
        DeviceArt(R.drawable.device_air5_pro_plus_bud, R.drawable.device_air5_pro_plus_case)
    else -> null
}
