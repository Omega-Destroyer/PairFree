package io.github.omegadestroyer.pairfree.protocol.skullcandy

import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingGroup
import io.github.omegadestroyer.pairfree.protocol.SettingTab

/**
 * Find with Tile (the manifest's `findWithTile`, 22 products): the earbuds carry Tile's tracker, which only Tile's own app and account reach. Skullcandy's app does no more than check
 * whether Tile's app (`com.thetileapp.tile`) is installed and offer to open it (`SKDYFindWithTileFeatureGuide`), so this is one row that opens Tile (docs/skullcandy/PROTOCOL.md section 18).
 */
internal object SkullcandyTile {
    const val KEY: String = "sk_tile"

    fun group(product: SkullcandyProduct): SettingGroup? =
        if ("findWithTile" in product.features) SettingGroup(KEY, SettingTab.DEVICE, listOf(Setting.Action(KEY))) else null
}
