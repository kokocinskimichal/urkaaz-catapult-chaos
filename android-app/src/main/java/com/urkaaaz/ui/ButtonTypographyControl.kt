package com.urkaaaz.ui

import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import com.urkaaaz.android.R

internal enum class ButtonTypographyControl(
    val controlId: String,
    val stringKey: String,
    @StringRes val stringResource: Int,
    @DimenRes val dimensionResource: Int,
) {
    COMMON_LOADOUT_BACK(
        "common_loadout_back",
        "loadout_back",
        R.string.loadout_back,
        R.dimen.button_text_size_common_loadout_back,
    ),
    COMMON_ITEM_NAME_ROCK(
        "common_item_name_rock",
        "loadout_name_rock",
        R.string.loadout_name_rock,
        R.dimen.button_text_size_common_item_name_rock,
    ),
    COMMON_ITEM_NAME_SIEGE_BOMB(
        "common_item_name_siege_bomb",
        "loadout_name_siege_bomb",
        R.string.loadout_name_siege_bomb,
        R.dimen.button_text_size_common_item_name_siege_bomb,
    ),
    COMMON_ITEM_NAME_POWDER_BARREL(
        "common_item_name_powder_barrel",
        "loadout_name_powder_barrel",
        R.string.loadout_name_powder_barrel,
        R.dimen.button_text_size_common_item_name_powder_barrel,
    ),
    COMMON_ITEM_NAME_FIRE_RAIN(
        "common_item_name_fire_rain",
        "loadout_name_fire_rain",
        R.string.loadout_name_fire_rain,
        R.dimen.button_text_size_common_item_name_fire_rain,
    ),
    COMMON_ITEM_NAME_PLAGUE_CAULDRON(
        "common_item_name_plague_cauldron",
        "loadout_name_plague_cauldron",
        R.string.loadout_name_plague_cauldron,
        R.dimen.button_text_size_common_item_name_plague_cauldron,
    ),
    COMMON_ITEM_NAME_DEFENDER(
        "common_item_name_defender",
        "loadout_name_defender",
        R.string.loadout_name_defender,
        R.dimen.button_text_size_common_item_name_defender,
    ),
    COMMON_ITEM_NAME_SAPPER(
        "common_item_name_sapper",
        "loadout_name_sapper",
        R.string.loadout_name_sapper,
        R.dimen.button_text_size_common_item_name_sapper,
    ),
    COMMON_ITEM_NAME_DEMOLISHER(
        "common_item_name_demolisher",
        "loadout_name_demolisher",
        R.string.loadout_name_demolisher,
        R.dimen.button_text_size_common_item_name_demolisher,
    ),
    COMMON_ITEM_NAME_RAGING_BOAR(
        "common_item_name_raging_boar",
        "loadout_name_raging_boar",
        R.string.loadout_name_raging_boar,
        R.dimen.button_text_size_common_item_name_raging_boar,
    ),
    COMMON_ITEM_NAME_SLINGMASTER(
        "common_item_name_slingmaster",
        "loadout_name_slingmaster",
        R.string.loadout_name_slingmaster,
        R.dimen.button_text_size_common_item_name_slingmaster,
    ),
    COMMON_ITEM_NAME_ICE_TRAP(
        "common_item_name_ice_trap",
        "loadout_name_ice_trap",
        R.string.loadout_name_ice_trap,
        R.dimen.button_text_size_common_item_name_ice_trap,
    ),
    COMMON_ITEM_NAME_RUNIC_BASTION(
        "common_item_name_runic_bastion",
        "loadout_name_runic_bastion",
        R.string.loadout_name_runic_bastion,
        R.dimen.button_text_size_common_item_name_runic_bastion,
    ),
    COMMON_ITEM_NAME_HASTE(
        "common_item_name_haste",
        "loadout_name_haste",
        R.string.loadout_name_haste,
        R.dimen.button_text_size_common_item_name_haste,
    ),
    COMMON_ITEM_NAME_HEAL(
        "common_item_name_heal",
        "loadout_name_heal",
        R.string.loadout_name_heal,
        R.dimen.button_text_size_common_item_name_heal,
    ),
    COMMON_ITEM_NAME_FORTRESS_SHIELD(
        "common_item_name_fortress_shield",
        "loadout_name_fortress_shield",
        R.string.loadout_name_fortress_shield,
        R.dimen.button_text_size_common_item_name_fortress_shield,
    ),
    COMMON_ITEM_NAME_CATAPULT_LOCK(
        "common_item_name_catapult_lock",
        "loadout_name_catapult_lock",
        R.string.loadout_name_catapult_lock,
        R.dimen.button_text_size_common_item_name_catapult_lock,
    ),
    COMMON_ITEM_NAME_SLOWING_FIELD(
        "common_item_name_slowing_field",
        "loadout_name_slowing_field",
        R.string.loadout_name_slowing_field,
        R.dimen.button_text_size_common_item_name_slowing_field,
    ),
    COMMON_ITEM_NAME_VAMPIRIC_BANNER(
        "common_item_name_vampiric_banner",
        "loadout_name_vampiric_banner",
        R.string.loadout_name_vampiric_banner,
        R.dimen.button_text_size_common_item_name_vampiric_banner,
    ),
    COMMON_ITEM_NAME_STONE_SKIN(
        "common_item_name_stone_skin",
        "loadout_name_stone_skin",
        R.string.loadout_name_stone_skin,
        R.dimen.button_text_size_common_item_name_stone_skin,
    ),
    COMMON_ITEM_NAME_SIEGE_FRENZY(
        "common_item_name_siege_frenzy",
        "loadout_name_siege_frenzy",
        R.string.loadout_name_siege_frenzy,
        R.dimen.button_text_size_common_item_name_siege_frenzy,
    ),
    COMMON_ITEM_NAME_BERSERKER(
        "common_item_name_berserker",
        "loadout_name_berserker",
        R.string.loadout_name_berserker,
        R.dimen.button_text_size_common_item_name_berserker,
    ),
    COMMON_ITEM_NAME_UNKNOWN(
        "common_item_name_unknown",
        "loadout_name_unknown",
        R.string.loadout_name_unknown,
        R.dimen.button_text_size_common_item_name_unknown,
    ),
    DASHBOARD_TEST_MODE(
        "dashboard_test_mode",
        "test_mode",
        R.string.test_mode,
        R.dimen.button_text_size_dashboard_test_mode,
    ),
    DASHBOARD_PROGRESS_RETRY(
        "dashboard_progress_retry",
        "dashboard_progress_retry",
        R.string.dashboard_progress_retry,
        R.dimen.button_text_size_dashboard_progress_retry,
    ),
    DASHBOARD_RESET_PROGRESS(
        "dashboard_reset_progress",
        "dashboard_reset_progress",
        R.string.dashboard_reset_progress,
        R.dimen.button_text_size_dashboard_reset_progress,
    ),
    DASHBOARD_BACK(
        "dashboard_back",
        "dashboard_back_action",
        R.string.dashboard_back_action,
        R.dimen.button_text_size_dashboard_back,
    ),
    DASHBOARD_CAMPAIGN(
        "dashboard_campaign",
        "dashboard_campaign_action",
        R.string.dashboard_campaign_action,
        R.dimen.button_text_size_dashboard_campaign,
    ),
    DASHBOARD_SHOP(
        "dashboard_shop",
        "dashboard_shop_action",
        R.string.dashboard_shop_action,
        R.dimen.button_text_size_dashboard_shop,
    ),
    DASHBOARD_AD_REWARD(
        "dashboard_ad_reward",
        "dashboard_ad_reward_action",
        R.string.dashboard_ad_reward_action,
        R.dimen.button_text_size_dashboard_ad_reward,
    ),
    DASHBOARD_RESET_CONFIRM(
        "dashboard_reset_confirm",
        "dashboard_reset_progress_confirm",
        R.string.dashboard_reset_progress_confirm,
        R.dimen.button_text_size_dashboard_reset_confirm,
    ),
    DASHBOARD_RESET_CANCEL(
        "dashboard_reset_cancel",
        "dashboard_reset_cancel",
        R.string.dashboard_reset_cancel,
        R.dimen.button_text_size_dashboard_reset_cancel,
    ),
    CAMPAIGN_START_LEVEL(
        "campaign_start_level",
        "campaign_start_level",
        R.string.campaign_start_level,
        R.dimen.button_text_size_campaign_start_level,
    ),
    CAMPAIGN_RETRY(
        "campaign_retry",
        "campaign_retry",
        R.string.campaign_retry,
        R.dimen.button_text_size_campaign_retry,
    ),
    LOADOUT_START_MATCH(
        "loadout_start_match",
        "loadout_start_match",
        R.string.loadout_start_match,
        R.dimen.button_text_size_loadout_start_match,
    ),
    LOADOUT_DETAIL_FIXED(
        "loadout_detail_fixed",
        "loadout_state_fixed",
        R.string.loadout_state_fixed,
        R.dimen.button_text_size_loadout_detail_fixed,
    ),
    LOADOUT_DETAIL_OPEN_SHOP(
        "loadout_detail_open_shop",
        "loadout_open_shop",
        R.string.loadout_open_shop,
        R.dimen.button_text_size_loadout_detail_open_shop,
    ),
    LOADOUT_DETAIL_REMOVE_ITEM(
        "loadout_detail_remove_item",
        "loadout_remove_item",
        R.string.loadout_remove_item,
        R.dimen.button_text_size_loadout_detail_remove_item,
    ),
    LOADOUT_DETAIL_SELECT_ITEM(
        "loadout_detail_select_item",
        "loadout_select_item",
        R.string.loadout_select_item,
        R.dimen.button_text_size_loadout_detail_select_item,
    ),
    LOADOUT_CARD_UNIMPLEMENTED(
        "loadout_card_unimplemented",
        "loadout_state_unimplemented",
        R.string.loadout_state_unimplemented,
        R.dimen.button_text_size_loadout_card_unimplemented,
    ),
    LOADOUT_CARD_OUT_OF_STOCK(
        "loadout_card_out_of_stock",
        "loadout_state_out_of_stock",
        R.string.loadout_state_out_of_stock,
        R.dimen.button_text_size_loadout_card_out_of_stock,
    ),
    LOADOUT_CARD_STOCK(
        "loadout_card_stock",
        "loadout_stock",
        R.string.loadout_stock,
        R.dimen.button_text_size_loadout_card_stock,
    ),
    LOADOUT_CARD_GOLD_COST(
        "loadout_card_gold_cost",
        "loadout_prop_gold_cost",
        R.string.loadout_prop_gold_cost,
        R.dimen.button_text_size_loadout_card_gold_cost,
    ),
    SHOP_BACK(
        "shop_back",
        "shop_back",
        R.string.shop_back,
        R.dimen.button_text_size_shop_back,
    ),
    SHOP_BUY(
        "shop_buy",
        "shop_buy",
        R.string.shop_buy,
        R.dimen.button_text_size_shop_buy,
    ),
    SHOP_TAB_SPELLS(
        "shop_tab_spells",
        "shop_tab_spells",
        R.string.shop_tab_spells,
        R.dimen.button_text_size_shop_tab_spells,
    ),
    SHOP_TAB_AMMUNITION(
        "shop_tab_ammunition",
        "shop_tab_ammunition",
        R.string.shop_tab_ammunition,
        R.dimen.button_text_size_shop_tab_ammunition,
    ),
    SHOP_OFFER_PRICE(
        "shop_offer_price",
        "shop_price",
        R.string.shop_price,
        R.dimen.button_text_size_shop_offer_price,
    ),
    SHOP_OFFER_STOCK(
        "shop_offer_stock",
        "shop_stock",
        R.string.shop_stock,
        R.dimen.button_text_size_shop_offer_stock,
    ),
    SHOP_OFFER_ART_UNAVAILABLE(
        "shop_offer_art_unavailable",
        "shop_art_unavailable",
        R.string.shop_art_unavailable,
        R.dimen.button_text_size_shop_offer_art_unavailable,
    ),
    MATCH_UNIT_SAPPER(
        "match_unit_sapper",
        "match_hud_unit_sapper",
        R.string.match_hud_unit_sapper,
        R.dimen.button_text_size_match_unit_sapper,
    ),
    MATCH_UNIT_DEFENDER(
        "match_unit_defender",
        "match_hud_unit_defender",
        R.string.match_hud_unit_defender,
        R.dimen.button_text_size_match_unit_defender,
    ),
    MATCH_UNIT_DEMOLISHER(
        "match_unit_demolisher",
        "match_hud_unit_demolisher",
        R.string.match_hud_unit_demolisher,
        R.dimen.button_text_size_match_unit_demolisher,
    ),
    MATCH_UNIT_BOAR(
        "match_unit_boar",
        "match_hud_unit_boar",
        R.string.match_hud_unit_boar,
        R.dimen.button_text_size_match_unit_boar,
    ),
    MATCH_UNIT_SLINGMASTER(
        "match_unit_slingmaster",
        "match_hud_unit_slingmaster",
        R.string.match_hud_unit_slingmaster,
        R.dimen.button_text_size_match_unit_slingmaster,
    ),
    MATCH_AMMO_ROCK(
        "match_ammo_rock",
        "match_hud_ammo_rock",
        R.string.match_hud_ammo_rock,
        R.dimen.button_text_size_match_ammo_rock,
    ),
    MATCH_AMMO_SIEGE(
        "match_ammo_siege",
        "match_hud_ammo_siege",
        R.string.match_hud_ammo_siege,
        R.dimen.button_text_size_match_ammo_siege,
    ),
    MATCH_AMMO_BARREL(
        "match_ammo_barrel",
        "match_hud_ammo_barrel",
        R.string.match_hud_ammo_barrel,
        R.dimen.button_text_size_match_ammo_barrel,
    ),
    MATCH_AMMO_FIRE_RAIN(
        "match_ammo_fire_rain",
        "match_hud_ammo_fire_rain",
        R.string.match_hud_ammo_fire_rain,
        R.dimen.button_text_size_match_ammo_fire_rain,
    ),
    MATCH_AMMO_PLAGUE(
        "match_ammo_plague",
        "match_hud_ammo_plague",
        R.string.match_hud_ammo_plague,
        R.dimen.button_text_size_match_ammo_plague,
    ),
    MATCH_SPELL_ICE_TRAP(
        "match_spell_ice_trap",
        "match_hud_spell_short_ice_trap",
        R.string.match_hud_spell_short_ice_trap,
        R.dimen.button_text_size_match_spell_ice_trap,
    ),
    MATCH_SPELL_RUNIC_BASTION(
        "match_spell_runic_bastion",
        "match_hud_spell_short_runic_bastion",
        R.string.match_hud_spell_short_runic_bastion,
        R.dimen.button_text_size_match_spell_runic_bastion,
    ),
    MATCH_SPELL_HASTE(
        "match_spell_haste",
        "loadout_name_haste",
        R.string.loadout_name_haste,
        R.dimen.button_text_size_match_spell_haste,
    ),
    MATCH_SPELL_HEAL(
        "match_spell_heal",
        "loadout_name_heal",
        R.string.loadout_name_heal,
        R.dimen.button_text_size_match_spell_heal,
    ),
    MATCH_SPELL_FORTRESS_SHIELD(
        "match_spell_fortress_shield",
        "match_hud_spell_short_fortress_shield",
        R.string.match_hud_spell_short_fortress_shield,
        R.dimen.button_text_size_match_spell_fortress_shield,
    ),
    MATCH_SPELL_CATAPULT_LOCK(
        "match_spell_catapult_lock",
        "match_hud_spell_short_catapult_lock",
        R.string.match_hud_spell_short_catapult_lock,
        R.dimen.button_text_size_match_spell_catapult_lock,
    ),
    MATCH_SPELL_SLOWING_FIELD(
        "match_spell_slowing_field",
        "match_hud_spell_short_slowing_field",
        R.string.match_hud_spell_short_slowing_field,
        R.dimen.button_text_size_match_spell_slowing_field,
    ),
    MATCH_SPELL_VAMPIRIC_BANNER(
        "match_spell_vampiric_banner",
        "match_hud_spell_short_vampiric_banner",
        R.string.match_hud_spell_short_vampiric_banner,
        R.dimen.button_text_size_match_spell_vampiric_banner,
    ),
    MATCH_SPELL_STONE_SKIN(
        "match_spell_stone_skin",
        "loadout_name_stone_skin",
        R.string.loadout_name_stone_skin,
        R.dimen.button_text_size_match_spell_stone_skin,
    ),
    MATCH_SPELL_SIEGE_FRENZY(
        "match_spell_siege_frenzy",
        "match_hud_spell_short_siege_frenzy",
        R.string.match_hud_spell_short_siege_frenzy,
        R.dimen.button_text_size_match_spell_siege_frenzy,
    ),
    MATCH_SPELL_BERSERKER(
        "match_spell_berserker",
        "loadout_name_berserker",
        R.string.loadout_name_berserker,
        R.dimen.button_text_size_match_spell_berserker,
    ),
    MATCH_AMMO_STOCK(
        "match_ammo_stock",
        "match_hud_stock",
        R.string.match_hud_stock,
        R.dimen.button_text_size_match_ammo_stock,
    ),
    MATCH_AMMO_RELOAD_SECONDS(
        "match_ammo_reload_seconds",
        "match_hud_reload_seconds",
        R.string.match_hud_reload_seconds,
        R.dimen.button_text_size_match_ammo_reload_seconds,
    ),
    MATCH_AMMO_READY(
        "match_ammo_ready",
        "match_hud_ready",
        R.string.match_hud_ready,
        R.dimen.button_text_size_match_ammo_ready,
    ),
    MATCH_DEBUG_UNIT_DEFENDER(
        "match_debug_unit_defender",
        "loadout_name_defender",
        R.string.loadout_name_defender,
        R.dimen.button_text_size_match_debug_unit_defender,
    ),
    MATCH_DEBUG_UNIT_SAPPER(
        "match_debug_unit_sapper",
        "loadout_name_sapper",
        R.string.loadout_name_sapper,
        R.dimen.button_text_size_match_debug_unit_sapper,
    ),
    MATCH_DEBUG_UNIT_DEMOLISHER(
        "match_debug_unit_demolisher",
        "loadout_name_demolisher",
        R.string.loadout_name_demolisher,
        R.dimen.button_text_size_match_debug_unit_demolisher,
    ),
    MATCH_DEBUG_UNIT_RAGING_BOAR(
        "match_debug_unit_raging_boar",
        "loadout_name_raging_boar",
        R.string.loadout_name_raging_boar,
        R.dimen.button_text_size_match_debug_unit_raging_boar,
    ),
    MATCH_DEBUG_UNIT_SLINGMASTER(
        "match_debug_unit_slingmaster",
        "loadout_name_slingmaster",
        R.string.loadout_name_slingmaster,
        R.dimen.button_text_size_match_debug_unit_slingmaster,
    ),
    MATCH_RESULT_NEXT_LEVEL(
        "match_result_next_level",
        "match_next_level",
        R.string.match_next_level,
        R.dimen.button_text_size_match_result_next_level,
    ),
    MATCH_RESULT_EXIT_CAMPAIGN(
        "match_result_exit_campaign",
        "match_result_exit_campaign",
        R.string.match_result_exit_campaign,
        R.dimen.button_text_size_match_result_exit_campaign,
    ),
    MATCH_RESULT_RETRY(
        "match_result_retry",
        "match_result_retry",
        R.string.match_result_retry,
        R.dimen.button_text_size_match_result_retry,
    ),
    MATCH_RESULT_RETRY_SAVE(
        "match_result_retry_save",
        "match_result_retry_save",
        R.string.match_result_retry_save,
        R.dimen.button_text_size_match_result_retry_save,
    ),
    MATCH_RESULT_RETRY_STOCK(
        "match_result_retry_stock",
        "match_result_retry_stock",
        R.string.match_result_retry_stock,
        R.dimen.button_text_size_match_result_retry_stock,
    ),
    MATCH_RESULT_BACK_DASHBOARD(
        "match_result_back_dashboard",
        "match_result_back_dashboard",
        R.string.match_result_back_dashboard,
        R.dimen.button_text_size_match_result_back_dashboard,
    ),
    MATCH_RESULT_BACK_TEST_LEVELS(
        "match_result_back_test_levels",
        "match_result_back_test_levels",
        R.string.match_result_back_test_levels,
        R.dimen.button_text_size_match_result_back_test_levels,
    ),
    MATCH_DEBUG_BUTTON(
        "match_debug_button",
        "match_debug_button",
        R.string.match_debug_button,
        R.dimen.button_text_size_match_debug_button,
    ),
    MATCH_DEBUG_HITBOXES(
        "match_debug_hitboxes",
        "match_debug_hitboxes",
        R.string.match_debug_hitboxes,
        R.dimen.button_text_size_match_debug_hitboxes,
    ),
    MATCH_DEBUG_CLOSE_SNAPSHOT(
        "match_debug_close_snapshot",
        "match_debug_close_snapshot",
        R.string.match_debug_close_snapshot,
        R.dimen.button_text_size_match_debug_close_snapshot,
    ),
    MATCH_LOADING_RETRY(
        "match_loading_retry",
        "match_loading_unit_assets_retry",
        R.string.match_loading_unit_assets_retry,
        R.dimen.button_text_size_match_loading_retry,
    ),
    MATCH_LOADING_BACK(
        "match_loading_back",
        "match_loading_unit_assets_back",
        R.string.match_loading_unit_assets_back,
        R.dimen.button_text_size_match_loading_back,
    ),
    MATCH_STOCK_RETRY(
        "match_stock_retry",
        "match_result_retry_stock",
        R.string.match_result_retry_stock,
        R.dimen.button_text_size_match_stock_retry,
    ),
    MATCH_RETREAT_CANCEL(
        "match_retreat_cancel",
        "match_retreat_cancel",
        R.string.match_retreat_cancel,
        R.dimen.button_text_size_match_retreat_cancel,
    ),
    MATCH_RETREAT_CONFIRM(
        "match_retreat_confirm",
        "match_retreat_confirm",
        R.string.match_retreat_confirm,
        R.dimen.button_text_size_match_retreat_confirm,
    ),
    MATCH_DEBUG_COORDINATES(
        "match_debug_coordinates",
        "match_debug_coordinates",
        R.string.match_debug_coordinates,
        R.dimen.button_text_size_match_debug_coordinates,
    ),
    MATCH_DEBUG_SNAPSHOT(
        "match_debug_snapshot",
        "match_debug_snapshot",
        R.string.match_debug_snapshot,
        R.dimen.button_text_size_match_debug_snapshot,
    ),
    MATCH_DEBUG_AI_UNIT_AUTOMATION(
        "match_debug_ai_unit_automation",
        "match_debug_ai_unit_automation",
        R.string.match_debug_ai_unit_automation,
        R.dimen.button_text_size_match_debug_ai_unit_automation,
    ),
    MATCH_DEBUG_RECRUIT_ENEMY(
        "match_debug_recruit_enemy",
        "match_debug_recruit_enemy",
        R.string.match_debug_recruit_enemy,
        R.dimen.button_text_size_match_debug_recruit_enemy,
    ),
    MATCH_DEBUG_SEND_ENEMY_WAVE(
        "match_debug_send_enemy_wave",
        "match_debug_send_enemy_wave",
        R.string.match_debug_send_enemy_wave,
        R.dimen.button_text_size_match_debug_send_enemy_wave,
    ),
    MATCH_DEBUG_CLOSE(
        "match_debug_close",
        "match_debug_close",
        R.string.match_debug_close,
        R.dimen.button_text_size_match_debug_close,
    ),
    TUTORIAL_SKIP(
        "tutorial_skip",
        "tutorial_skip",
        R.string.tutorial_skip,
        R.dimen.button_text_size_tutorial_skip,
    ),
    TUTORIAL_NEXT(
        "tutorial_next",
        "tutorial_next",
        R.string.tutorial_next,
        R.dimen.button_text_size_tutorial_next,
    ),
    TUTORIAL_START(
        "tutorial_start",
        "tutorial_start",
        R.string.tutorial_start,
        R.dimen.button_text_size_tutorial_start,
    ),
    TUTORIAL_RETRY(
        "tutorial_retry",
        "tutorial_retry",
        R.string.tutorial_retry,
        R.dimen.button_text_size_tutorial_retry,
    ),
    TEST_LEVEL(
        "test_level",
        "test_level_picker_level",
        R.string.test_level_picker_level,
        R.dimen.button_text_size_test_level,
    ),
    TEST_BOSS_LEVEL(
        "test_boss_level",
        "test_level_picker_boss_level",
        R.string.test_level_picker_boss_level,
        R.dimen.button_text_size_test_boss_level,
    );
    companion object {
        fun commonItemName(identifier: String): ButtonTypographyControl = when (identifier) {
            "ROCK" -> COMMON_ITEM_NAME_ROCK
            "SIEGE_BOMB" -> COMMON_ITEM_NAME_SIEGE_BOMB
            "POWDER_BARREL" -> COMMON_ITEM_NAME_POWDER_BARREL
            "FIRE_RAIN" -> COMMON_ITEM_NAME_FIRE_RAIN
            "PLAGUE_CAULDRON" -> COMMON_ITEM_NAME_PLAGUE_CAULDRON
            "DEFENDER" -> COMMON_ITEM_NAME_DEFENDER
            "SAPPER" -> COMMON_ITEM_NAME_SAPPER
            "DEMOLISHER" -> COMMON_ITEM_NAME_DEMOLISHER
            "RAGING_BOAR" -> COMMON_ITEM_NAME_RAGING_BOAR
            "SLINGMASTER" -> COMMON_ITEM_NAME_SLINGMASTER
            "ICE_TRAP" -> COMMON_ITEM_NAME_ICE_TRAP
            "RUNIC_BASTION" -> COMMON_ITEM_NAME_RUNIC_BASTION
            "HASTE" -> COMMON_ITEM_NAME_HASTE
            "HEAL" -> COMMON_ITEM_NAME_HEAL
            "FORTRESS_SHIELD" -> COMMON_ITEM_NAME_FORTRESS_SHIELD
            "CATAPULT_LOCK" -> COMMON_ITEM_NAME_CATAPULT_LOCK
            "SLOWING_FIELD" -> COMMON_ITEM_NAME_SLOWING_FIELD
            "VAMPIRIC_BANNER" -> COMMON_ITEM_NAME_VAMPIRIC_BANNER
            "STONE_SKIN" -> COMMON_ITEM_NAME_STONE_SKIN
            "SIEGE_FRENZY" -> COMMON_ITEM_NAME_SIEGE_FRENZY
            "BERSERKER" -> COMMON_ITEM_NAME_BERSERKER
            else -> COMMON_ITEM_NAME_UNKNOWN
        }

        fun matchUnit(type: String): ButtonTypographyControl = when (type) {
            "SAPPER" -> MATCH_UNIT_SAPPER
            "DEFENDER" -> MATCH_UNIT_DEFENDER
            "DEMOLISHER" -> MATCH_UNIT_DEMOLISHER
            "RAGING_BOAR" -> MATCH_UNIT_BOAR
            "SLINGMASTER" -> MATCH_UNIT_SLINGMASTER
            else -> error("Unknown match unit typography type: $type")
        }

        fun matchAmmunition(type: String): ButtonTypographyControl = when (type) {
            "ROCK" -> MATCH_AMMO_ROCK
            "SIEGE_BOMB" -> MATCH_AMMO_SIEGE
            "POWDER_BARREL" -> MATCH_AMMO_BARREL
            "FIRE_RAIN" -> MATCH_AMMO_FIRE_RAIN
            "PLAGUE_CAULDRON" -> MATCH_AMMO_PLAGUE
            else -> error("Unknown match ammunition typography type: $type")
        }

        fun matchSpell(type: String): ButtonTypographyControl = when (type) {
            "ICE_TRAP" -> MATCH_SPELL_ICE_TRAP
            "RUNIC_BASTION" -> MATCH_SPELL_RUNIC_BASTION
            "HASTE" -> MATCH_SPELL_HASTE
            "HEAL" -> MATCH_SPELL_HEAL
            "FORTRESS_SHIELD" -> MATCH_SPELL_FORTRESS_SHIELD
            "CATAPULT_LOCK" -> MATCH_SPELL_CATAPULT_LOCK
            "SLOWING_FIELD" -> MATCH_SPELL_SLOWING_FIELD
            "VAMPIRIC_BANNER" -> MATCH_SPELL_VAMPIRIC_BANNER
            "STONE_SKIN" -> MATCH_SPELL_STONE_SKIN
            "SIEGE_FRENZY" -> MATCH_SPELL_SIEGE_FRENZY
            "BERSERKER" -> MATCH_SPELL_BERSERKER
            else -> error("Unknown match spell typography type: $type")
        }

        fun matchDebugUnitName(type: String): ButtonTypographyControl = when (type) {
            "DEFENDER" -> MATCH_DEBUG_UNIT_DEFENDER
            "SAPPER" -> MATCH_DEBUG_UNIT_SAPPER
            "DEMOLISHER" -> MATCH_DEBUG_UNIT_DEMOLISHER
            "RAGING_BOAR" -> MATCH_DEBUG_UNIT_RAGING_BOAR
            "SLINGMASTER" -> MATCH_DEBUG_UNIT_SLINGMASTER
            else -> error("Unknown match debug unit typography type: $type")
        }
    }
}
