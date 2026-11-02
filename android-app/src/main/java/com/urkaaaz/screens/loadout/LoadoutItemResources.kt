package com.urkaaaz.screens.loadout

import com.urkaaaz.android.R

internal object LoadoutItemResources {
    fun name(identifier: String): Int = when (identifier) {
        "DEFENDER" -> R.string.loadout_name_defender
        "SAPPER" -> R.string.loadout_name_sapper
        "DEMOLISHER" -> R.string.loadout_name_demolisher
        "RAGING_BOAR" -> R.string.loadout_name_raging_boar
        "SLINGMASTER" -> R.string.loadout_name_slingmaster
        "ROCK" -> R.string.loadout_name_rock
        "SIEGE_BOMB" -> R.string.loadout_name_siege_bomb
        "POWDER_BARREL" -> R.string.loadout_name_powder_barrel
        "FIRE_RAIN" -> R.string.loadout_name_fire_rain
        "PLAGUE_CAULDRON" -> R.string.loadout_name_plague_cauldron
        "ICE_TRAP" -> R.string.loadout_name_ice_trap
        "RUNIC_BASTION" -> R.string.loadout_name_runic_bastion
        "HASTE" -> R.string.loadout_name_haste
        "HEAL" -> R.string.loadout_name_heal
        "FORTRESS_SHIELD" -> R.string.loadout_name_fortress_shield
        "CATAPULT_LOCK" -> R.string.loadout_name_catapult_lock
        "SLOWING_FIELD" -> R.string.loadout_name_slowing_field
        "VAMPIRIC_BANNER" -> R.string.loadout_name_vampiric_banner
        "STONE_SKIN" -> R.string.loadout_name_stone_skin
        "SIEGE_FRENZY" -> R.string.loadout_name_siege_frenzy
        "BERSERKER" -> R.string.loadout_name_berserker
        else -> R.string.loadout_name_unknown
    }

    fun description(identifier: String): Int = when (identifier) {
        "DEFENDER" -> R.string.loadout_desc_defender
        "SAPPER" -> R.string.loadout_desc_sapper
        "DEMOLISHER" -> R.string.loadout_desc_demolisher
        "RAGING_BOAR" -> R.string.loadout_desc_raging_boar
        "SLINGMASTER" -> R.string.loadout_desc_slingmaster
        "ROCK" -> R.string.loadout_desc_rock
        "SIEGE_BOMB" -> R.string.loadout_desc_siege_bomb
        "POWDER_BARREL" -> R.string.loadout_desc_powder_barrel
        "FIRE_RAIN" -> R.string.loadout_desc_fire_rain
        "PLAGUE_CAULDRON" -> R.string.loadout_desc_plague_cauldron
        "ICE_TRAP" -> R.string.loadout_desc_ice_trap
        "RUNIC_BASTION" -> R.string.loadout_desc_runic_bastion
        "HASTE" -> R.string.loadout_desc_haste
        "HEAL" -> R.string.loadout_desc_heal
        "FORTRESS_SHIELD" -> R.string.loadout_desc_fortress_shield
        "CATAPULT_LOCK" -> R.string.loadout_desc_catapult_lock
        "SLOWING_FIELD" -> R.string.loadout_desc_slowing_field
        "VAMPIRIC_BANNER" -> R.string.loadout_desc_vampiric_banner
        "STONE_SKIN" -> R.string.loadout_desc_stone_skin
        "SIEGE_FRENZY" -> R.string.loadout_desc_siege_frenzy
        "BERSERKER" -> R.string.loadout_desc_berserker
        else -> R.string.loadout_desc_unknown
    }
}
