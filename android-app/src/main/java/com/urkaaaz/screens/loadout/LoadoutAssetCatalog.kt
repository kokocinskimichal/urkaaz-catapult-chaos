package com.urkaaaz.screens.loadout

import com.urkaaaz.android.R

sealed interface LoadoutAssetLookup {
    data class Found(val drawableId: Int) : LoadoutAssetLookup
    data object Missing : LoadoutAssetLookup
}

internal object LoadoutAssetCatalog {
    fun carouselArtwork(item: LoadoutItemReference): LoadoutAssetLookup =
        when (item) {
            is LoadoutItemReference.Unit -> when (item.identifier) {
                "DEFENDER" -> LoadoutAssetLookup.Found(R.drawable.loadout_unit_defender_card)
                "SAPPER" -> LoadoutAssetLookup.Found(R.drawable.loadout_unit_sapper_card)
                "DEMOLISHER" -> LoadoutAssetLookup.Found(R.drawable.loadout_unit_demolisher_card)
                "RAGING_BOAR" -> LoadoutAssetLookup.Found(R.drawable.loadout_unit_raging_boar_card)
                "SLINGMASTER" -> LoadoutAssetLookup.Found(R.drawable.loadout_unit_slingmaster_card)
                else -> LoadoutAssetLookup.Missing
            }
            else -> illustration(item)
        }

    fun illustration(item: LoadoutItemReference): LoadoutAssetLookup =
        when (item) {
            is LoadoutItemReference.Unit -> when (item.identifier) {
                "DEFENDER" -> LoadoutAssetLookup.Found(R.drawable.defender_icon)
                "SAPPER" -> LoadoutAssetLookup.Found(R.drawable.sapper_icon)
                "DEMOLISHER" -> LoadoutAssetLookup.Found(R.drawable.demolisher_icon)
                "RAGING_BOAR" -> LoadoutAssetLookup.Found(R.drawable.goblin_raging_boar_icon)
                "SLINGMASTER" -> LoadoutAssetLookup.Found(R.drawable.goblin_slingmaster_icon)
                else -> LoadoutAssetLookup.Missing
            }
            is LoadoutItemReference.Ammunition -> when (item.identifier) {
                "ROCK" -> LoadoutAssetLookup.Found(R.drawable.rock_flight_00)
                "SIEGE_BOMB" -> LoadoutAssetLookup.Found(R.drawable.ammo_icon_siege_bomb)
                "POWDER_BARREL" -> LoadoutAssetLookup.Found(R.drawable.ammo_icon_powder_barrel)
                "FIRE_RAIN" -> LoadoutAssetLookup.Found(R.drawable.fire_rain_icon)
                "PLAGUE_CAULDRON" -> LoadoutAssetLookup.Found(R.drawable.plague_cauldron_icon)
                else -> LoadoutAssetLookup.Missing
            }
            is LoadoutItemReference.Spell -> LoadoutAssetLookup.Missing
        }
}
