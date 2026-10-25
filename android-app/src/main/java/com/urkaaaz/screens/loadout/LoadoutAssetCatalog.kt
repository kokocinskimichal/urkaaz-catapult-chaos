package com.urkaaaz.screens.loadout

import com.urkaaaz.android.R

sealed interface LoadoutAssetLookup {
    data class Found(val drawableId: Int) : LoadoutAssetLookup
    data object Missing : LoadoutAssetLookup
}

internal object LoadoutAssetCatalog {
    fun lockedIcon(): Int = R.drawable.loadout_card_locked
    fun unlimitedIcon(): Int = R.drawable.loadout_unlimited_icon
    fun rockFixedIcon(): Int = R.drawable.loadout_rock_locked_icon
    fun headerFrame(): Int = R.drawable.loadout_header_frame
    fun summaryUnitsIcon(): Int = R.drawable.loadout_summary_units
    fun summaryAmmunitionIcon(): Int = R.drawable.loadout_summary_ammunition
    fun summarySpellsIcon(): Int = R.drawable.loadout_summary_spells
    fun backButtonBackground(): Int = R.drawable.loadout_start_match_button_xs_short
    fun nextLevelButtonBackground(): Int = R.drawable.loadout_start_match_button_xs_normal
    fun startMatchButtonBackground(): Int = R.drawable.loadout_start_match_button
    fun longButtonBackground(): Int = R.drawable.loadout_start_match_button_xs_long

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
            is LoadoutItemReference.Ammunition -> when (item.identifier) {
                "ROCK" -> LoadoutAssetLookup.Found(R.drawable.loadout_ammunition_rock_card)
                "SIEGE_BOMB" -> LoadoutAssetLookup.Found(R.drawable.loadout_ammunition_siege_bomb_card)
                "POWDER_BARREL" -> LoadoutAssetLookup.Found(R.drawable.loadout_ammunition_powder_barrel_card)
                "FIRE_RAIN" -> LoadoutAssetLookup.Found(R.drawable.loadout_ammunition_fire_rain_card)
                "PLAGUE_CAULDRON" -> LoadoutAssetLookup.Found(R.drawable.loadout_ammunition_plague_cauldron_card)
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
                "ROCK" -> LoadoutAssetLookup.Found(R.drawable.anim_ammunition_rock_flight_000)
                "SIEGE_BOMB" -> LoadoutAssetLookup.Found(R.drawable.ammo_icon_siege_bomb)
                "POWDER_BARREL" -> LoadoutAssetLookup.Found(R.drawable.ammo_icon_powder_barrel)
                "FIRE_RAIN" -> LoadoutAssetLookup.Found(R.drawable.fire_rain_icon)
                "PLAGUE_CAULDRON" -> LoadoutAssetLookup.Found(R.drawable.plague_cauldron_icon)
                else -> LoadoutAssetLookup.Missing
            }
            is LoadoutItemReference.Spell -> when (item.identifier) {
                "ICE_TRAP" -> LoadoutAssetLookup.Found(R.drawable.loadout_spell_ice_trap_card)
                "RUNIC_BASTION" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_runic_bastion_card)
                "HASTE" -> LoadoutAssetLookup.Found(R.drawable.loadout_spell_haste_card)
                "HEAL" -> LoadoutAssetLookup.Found(R.drawable.loadout_spell_heal_card)
                "FORTRESS_SHIELD" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_fortress_shield_card)
                "CATAPULT_LOCK" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_catapult_lock_card)
                "SLOWING_FIELD" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_slowing_field_card)
                "VAMPIRIC_BANNER" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_vampiric_banner_card)
                "STONE_SKIN" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_stone_skin_card)
                "SIEGE_FRENZY" ->
                    LoadoutAssetLookup.Found(R.drawable.loadout_spell_siege_frenzy_card)
                "BERSERKER" -> LoadoutAssetLookup.Found(R.drawable.loadout_spell_berserker_card)
                else -> LoadoutAssetLookup.Missing
            }
        }
}
