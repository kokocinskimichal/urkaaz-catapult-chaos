export const TYPOGRAPHY_CATEGORIES = [
  "common",
  "dashboard",
  "campaign",
  "loadout",
  "shop",
  "match",
  "tutorial",
  "test",
] as const;

export type TypographyCategory = typeof TYPOGRAPHY_CATEGORIES[number];
export type TypographyLanguage = "en" | "pl" | "es";

export interface ButtonTypographyControlDefinition {
  controlId: string;
  category: TypographyCategory;
  stringKey: string;
}

export const BUTTON_TYPOGRAPHY_CONTROLS = [
  { controlId: "common_loadout_back", category: "common", stringKey: "loadout_back" },
  { controlId: "common_item_name_rock", category: "common", stringKey: "loadout_name_rock" },
  { controlId: "common_item_name_siege_bomb", category: "common", stringKey: "loadout_name_siege_bomb" },
  { controlId: "common_item_name_powder_barrel", category: "common", stringKey: "loadout_name_powder_barrel" },
  { controlId: "common_item_name_fire_rain", category: "common", stringKey: "loadout_name_fire_rain" },
  { controlId: "common_item_name_plague_cauldron", category: "common", stringKey: "loadout_name_plague_cauldron" },
  { controlId: "common_item_name_defender", category: "common", stringKey: "loadout_name_defender" },
  { controlId: "common_item_name_sapper", category: "common", stringKey: "loadout_name_sapper" },
  { controlId: "common_item_name_demolisher", category: "common", stringKey: "loadout_name_demolisher" },
  { controlId: "common_item_name_raging_boar", category: "common", stringKey: "loadout_name_raging_boar" },
  { controlId: "common_item_name_slingmaster", category: "common", stringKey: "loadout_name_slingmaster" },
  { controlId: "common_item_name_ice_trap", category: "common", stringKey: "loadout_name_ice_trap" },
  { controlId: "common_item_name_runic_bastion", category: "common", stringKey: "loadout_name_runic_bastion" },
  { controlId: "common_item_name_haste", category: "common", stringKey: "loadout_name_haste" },
  { controlId: "common_item_name_heal", category: "common", stringKey: "loadout_name_heal" },
  { controlId: "common_item_name_fortress_shield", category: "common", stringKey: "loadout_name_fortress_shield" },
  { controlId: "common_item_name_catapult_lock", category: "common", stringKey: "loadout_name_catapult_lock" },
  { controlId: "common_item_name_slowing_field", category: "common", stringKey: "loadout_name_slowing_field" },
  { controlId: "common_item_name_vampiric_banner", category: "common", stringKey: "loadout_name_vampiric_banner" },
  { controlId: "common_item_name_stone_skin", category: "common", stringKey: "loadout_name_stone_skin" },
  { controlId: "common_item_name_siege_frenzy", category: "common", stringKey: "loadout_name_siege_frenzy" },
  { controlId: "common_item_name_berserker", category: "common", stringKey: "loadout_name_berserker" },
  { controlId: "common_item_name_unknown", category: "common", stringKey: "loadout_name_unknown" },

  { controlId: "dashboard_test_mode", category: "dashboard", stringKey: "test_mode" },
  { controlId: "dashboard_progress_retry", category: "dashboard", stringKey: "dashboard_progress_retry" },
  { controlId: "dashboard_reset_progress", category: "dashboard", stringKey: "dashboard_reset_progress" },
  { controlId: "dashboard_back", category: "dashboard", stringKey: "dashboard_back_action" },
  { controlId: "dashboard_campaign", category: "dashboard", stringKey: "dashboard_campaign_action" },
  { controlId: "dashboard_shop", category: "dashboard", stringKey: "dashboard_shop_action" },
  { controlId: "dashboard_ad_reward", category: "dashboard", stringKey: "dashboard_ad_reward_action" },
  { controlId: "dashboard_reset_confirm", category: "dashboard", stringKey: "dashboard_reset_progress_confirm" },
  { controlId: "dashboard_reset_cancel", category: "dashboard", stringKey: "dashboard_reset_cancel" },

  { controlId: "campaign_start_level", category: "campaign", stringKey: "campaign_start_level" },
  { controlId: "campaign_retry", category: "campaign", stringKey: "campaign_retry" },

  { controlId: "loadout_start_match", category: "loadout", stringKey: "loadout_start_match" },
  { controlId: "loadout_detail_fixed", category: "loadout", stringKey: "loadout_state_fixed" },
  { controlId: "loadout_detail_open_shop", category: "loadout", stringKey: "loadout_open_shop" },
  { controlId: "loadout_detail_remove_item", category: "loadout", stringKey: "loadout_remove_item" },
  { controlId: "loadout_detail_select_item", category: "loadout", stringKey: "loadout_select_item" },
  { controlId: "loadout_card_unimplemented", category: "loadout", stringKey: "loadout_state_unimplemented" },
  { controlId: "loadout_card_out_of_stock", category: "loadout", stringKey: "loadout_state_out_of_stock" },
  { controlId: "loadout_card_stock", category: "loadout", stringKey: "loadout_stock" },
  { controlId: "loadout_card_gold_cost", category: "loadout", stringKey: "loadout_prop_gold_cost" },

  { controlId: "shop_back", category: "shop", stringKey: "shop_back" },
  { controlId: "shop_buy", category: "shop", stringKey: "shop_buy" },
  { controlId: "shop_tab_spells", category: "shop", stringKey: "shop_tab_spells" },
  { controlId: "shop_tab_ammunition", category: "shop", stringKey: "shop_tab_ammunition" },
  { controlId: "shop_offer_price", category: "shop", stringKey: "shop_price" },
  { controlId: "shop_offer_stock", category: "shop", stringKey: "shop_stock" },
  { controlId: "shop_offer_art_unavailable", category: "shop", stringKey: "shop_art_unavailable" },

  { controlId: "match_unit_sapper", category: "match", stringKey: "match_hud_unit_sapper" },
  { controlId: "match_unit_defender", category: "match", stringKey: "match_hud_unit_defender" },
  { controlId: "match_unit_demolisher", category: "match", stringKey: "match_hud_unit_demolisher" },
  { controlId: "match_unit_boar", category: "match", stringKey: "match_hud_unit_boar" },
  { controlId: "match_unit_slingmaster", category: "match", stringKey: "match_hud_unit_slingmaster" },
  { controlId: "match_ammo_rock", category: "match", stringKey: "match_hud_ammo_rock" },
  { controlId: "match_ammo_siege", category: "match", stringKey: "match_hud_ammo_siege" },
  { controlId: "match_ammo_barrel", category: "match", stringKey: "match_hud_ammo_barrel" },
  { controlId: "match_ammo_fire_rain", category: "match", stringKey: "match_hud_ammo_fire_rain" },
  { controlId: "match_ammo_plague", category: "match", stringKey: "match_hud_ammo_plague" },
  { controlId: "match_spell_ice_trap", category: "match", stringKey: "match_hud_spell_short_ice_trap" },
  { controlId: "match_spell_runic_bastion", category: "match", stringKey: "match_hud_spell_short_runic_bastion" },
  { controlId: "match_spell_haste", category: "match", stringKey: "loadout_name_haste" },
  { controlId: "match_spell_heal", category: "match", stringKey: "loadout_name_heal" },
  { controlId: "match_spell_fortress_shield", category: "match", stringKey: "match_hud_spell_short_fortress_shield" },
  { controlId: "match_spell_catapult_lock", category: "match", stringKey: "match_hud_spell_short_catapult_lock" },
  { controlId: "match_spell_slowing_field", category: "match", stringKey: "match_hud_spell_short_slowing_field" },
  { controlId: "match_spell_vampiric_banner", category: "match", stringKey: "match_hud_spell_short_vampiric_banner" },
  { controlId: "match_spell_stone_skin", category: "match", stringKey: "loadout_name_stone_skin" },
  { controlId: "match_spell_siege_frenzy", category: "match", stringKey: "match_hud_spell_short_siege_frenzy" },
  { controlId: "match_spell_berserker", category: "match", stringKey: "loadout_name_berserker" },
  { controlId: "match_ammo_stock", category: "match", stringKey: "match_hud_stock" },
  { controlId: "match_ammo_reload_seconds", category: "match", stringKey: "match_hud_reload_seconds" },
  { controlId: "match_ammo_ready", category: "match", stringKey: "match_hud_ready" },
  { controlId: "match_debug_unit_defender", category: "match", stringKey: "loadout_name_defender" },
  { controlId: "match_debug_unit_sapper", category: "match", stringKey: "loadout_name_sapper" },
  { controlId: "match_debug_unit_demolisher", category: "match", stringKey: "loadout_name_demolisher" },
  { controlId: "match_debug_unit_raging_boar", category: "match", stringKey: "loadout_name_raging_boar" },
  { controlId: "match_debug_unit_slingmaster", category: "match", stringKey: "loadout_name_slingmaster" },
  { controlId: "match_result_next_level", category: "match", stringKey: "match_next_level" },
  { controlId: "match_result_exit_campaign", category: "match", stringKey: "match_result_exit_campaign" },
  { controlId: "match_result_retry", category: "match", stringKey: "match_result_retry" },
  { controlId: "match_result_retry_save", category: "match", stringKey: "match_result_retry_save" },
  { controlId: "match_result_retry_stock", category: "match", stringKey: "match_result_retry_stock" },
  { controlId: "match_result_back_dashboard", category: "match", stringKey: "match_result_back_dashboard" },
  { controlId: "match_result_back_test_levels", category: "match", stringKey: "match_result_back_test_levels" },
  { controlId: "match_debug_button", category: "match", stringKey: "match_debug_button" },
  { controlId: "match_debug_hitboxes", category: "match", stringKey: "match_debug_hitboxes" },
  { controlId: "match_debug_close_snapshot", category: "match", stringKey: "match_debug_close_snapshot" },
  { controlId: "match_loading_retry", category: "match", stringKey: "match_loading_unit_assets_retry" },
  { controlId: "match_loading_back", category: "match", stringKey: "match_loading_unit_assets_back" },
  { controlId: "match_stock_retry", category: "match", stringKey: "match_result_retry_stock" },
  { controlId: "match_retreat_cancel", category: "match", stringKey: "match_retreat_cancel" },
  { controlId: "match_retreat_confirm", category: "match", stringKey: "match_retreat_confirm" },
  { controlId: "match_debug_coordinates", category: "match", stringKey: "match_debug_coordinates" },
  { controlId: "match_debug_snapshot", category: "match", stringKey: "match_debug_snapshot" },
  { controlId: "match_debug_ai_unit_automation", category: "match", stringKey: "match_debug_ai_unit_automation" },
  { controlId: "match_debug_recruit_enemy", category: "match", stringKey: "match_debug_recruit_enemy" },
  { controlId: "match_debug_send_enemy_wave", category: "match", stringKey: "match_debug_send_enemy_wave" },
  { controlId: "match_debug_close", category: "match", stringKey: "match_debug_close" },

  { controlId: "tutorial_skip", category: "tutorial", stringKey: "tutorial_skip" },
  { controlId: "tutorial_next", category: "tutorial", stringKey: "tutorial_next" },
  { controlId: "tutorial_start", category: "tutorial", stringKey: "tutorial_start" },
  { controlId: "tutorial_retry", category: "tutorial", stringKey: "tutorial_retry" },

  { controlId: "test_level", category: "test", stringKey: "test_level_picker_level" },
  { controlId: "test_boss_level", category: "test", stringKey: "test_level_picker_boss_level" },
] as const satisfies readonly ButtonTypographyControlDefinition[];

export interface ButtonTypographyValues {
  en: number;
  pl: number;
  es: number;
}

export interface ButtonTypographyEntry extends ButtonTypographyControlDefinition {
  labels: Record<TypographyLanguage, string>;
  fontSizeSp: ButtonTypographyValues;
}

export interface ButtonTypographyUpdate {
  controlId: string;
  fontSizeSp: ButtonTypographyValues;
}

export interface TypographyDocumentResponse {
  entries: ButtonTypographyEntry[];
  hash: string;
}

export interface TypographySaveRequest {
  entries: ButtonTypographyUpdate[];
  expectedHash: string;
}

export interface TypographyValidationIssue {
  severity: "error";
  path: string;
  message: string;
}

export interface TypographyValidationResponse {
  valid: boolean;
  issues: TypographyValidationIssue[];
}
