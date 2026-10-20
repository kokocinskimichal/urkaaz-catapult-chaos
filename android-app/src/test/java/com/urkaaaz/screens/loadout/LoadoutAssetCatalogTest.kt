package com.urkaaaz.screens.loadout

import com.urkaaaz.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadoutAssetCatalogTest {
    @Test
    fun unavailableCardLockUsesTheSuppliedAsset() {
        assertEquals(R.drawable.loadout_card_locked, LoadoutAssetCatalog.lockedIcon())
    }

    @Test
    fun unlimitedAmmunitionUsesTheSuppliedIcon() {
        assertEquals(
            R.drawable.loadout_unlimited_icon,
            LoadoutAssetCatalog.unlimitedIcon(),
        )
    }

    @Test
    fun fixedRockAmmunitionUsesTheSuppliedLockIcon() {
        assertEquals(
            R.drawable.loadout_rock_locked_icon,
            LoadoutAssetCatalog.rockFixedIcon(),
        )
    }

    @Test
    fun loadoutHeaderUsesTheSuppliedFrameAsset() {
        assertEquals(R.drawable.loadout_header_frame, LoadoutAssetCatalog.headerFrame())
    }

    @Test
    fun loadoutSummaryUsesTheThreeDedicatedCategoryIcons() {
        assertEquals(
            R.drawable.loadout_summary_units,
            LoadoutAssetCatalog.summaryUnitsIcon(),
        )
        assertEquals(
            R.drawable.loadout_summary_ammunition,
            LoadoutAssetCatalog.summaryAmmunitionIcon(),
        )
        assertEquals(
            R.drawable.loadout_summary_spells,
            LoadoutAssetCatalog.summarySpellsIcon(),
        )
    }

    @Test
    fun startMatchButtonUsesTheStatefulBackgroundAsset() {
        assertEquals(
            R.drawable.loadout_start_match_button,
            LoadoutAssetCatalog.startMatchButtonBackground(),
        )
    }

    @Test
    fun longButtonBackgroundUsesTheXsLongAssets() {
        assertEquals(
            R.drawable.loadout_start_match_button_xs_long,
            LoadoutAssetCatalog.longButtonBackground(),
        )
    }

    @Test
    fun navigationButtonsUseTheirMatchingXsLengthBackgrounds() {
        assertEquals(
            R.drawable.loadout_start_match_button_xs_short,
            LoadoutAssetCatalog.backButtonBackground(),
        )
        assertEquals(
            R.drawable.loadout_start_match_button_xs_normal,
            LoadoutAssetCatalog.nextLevelButtonBackground(),
        )
    }

    @Test
    fun existingUnitAndAmmunitionIllustrationsAreMappedExplicitly() {
        assertEquals(
            LoadoutAssetLookup.Found(R.drawable.defender_icon),
            LoadoutAssetCatalog.illustration(LoadoutItemReference.Unit("DEFENDER")),
        )
        assertEquals(
            LoadoutAssetLookup.Found(R.drawable.ammo_icon_siege_bomb),
            LoadoutAssetCatalog.illustration(LoadoutItemReference.Ammunition("SIEGE_BOMB")),
        )
    }

    @Test
    fun everyUnitUsesItsDedicatedFullCardArtworkInTheCarousel() {
        val expectedArt = mapOf(
            "DEFENDER" to R.drawable.loadout_unit_defender_card,
            "SAPPER" to R.drawable.loadout_unit_sapper_card,
            "DEMOLISHER" to R.drawable.loadout_unit_demolisher_card,
            "RAGING_BOAR" to R.drawable.loadout_unit_raging_boar_card,
            "SLINGMASTER" to R.drawable.loadout_unit_slingmaster_card,
        )

        expectedArt.forEach { (unitId, drawableId) ->
            assertEquals(
                LoadoutAssetLookup.Found(drawableId),
                LoadoutAssetCatalog.carouselArtwork(LoadoutItemReference.Unit(unitId)),
            )
        }
    }

    @Test
    fun everyAmmunitionTypeUsesItsDedicatedFullCardArtworkInTheCarousel() {
        val expectedArt = mapOf(
            "ROCK" to R.drawable.loadout_ammunition_rock_card,
            "SIEGE_BOMB" to R.drawable.loadout_ammunition_siege_bomb_card,
            "POWDER_BARREL" to R.drawable.loadout_ammunition_powder_barrel_card,
            "FIRE_RAIN" to R.drawable.loadout_ammunition_fire_rain_card,
            "PLAGUE_CAULDRON" to R.drawable.loadout_ammunition_plague_cauldron_card,
        )

        expectedArt.forEach { (ammunitionId, drawableId) ->
            assertEquals(
                LoadoutAssetLookup.Found(drawableId),
                LoadoutAssetCatalog.carouselArtwork(
                    LoadoutItemReference.Ammunition(ammunitionId),
                ),
            )
        }
    }

    @Test
    fun everySpellUsesItsDedicatedFullCardArtworkInTheCarousel() {
        val expectedArt = mapOf(
            "ICE_TRAP" to R.drawable.loadout_spell_ice_trap_card,
            "RUNIC_BASTION" to R.drawable.loadout_spell_runic_bastion_card,
            "HASTE" to R.drawable.loadout_spell_haste_card,
            "HEAL" to R.drawable.loadout_spell_heal_card,
            "FORTRESS_SHIELD" to R.drawable.loadout_spell_fortress_shield_card,
            "CATAPULT_LOCK" to R.drawable.loadout_spell_catapult_lock_card,
            "SLOWING_FIELD" to R.drawable.loadout_spell_slowing_field_card,
            "VAMPIRIC_BANNER" to R.drawable.loadout_spell_vampiric_banner_card,
            "STONE_SKIN" to R.drawable.loadout_spell_stone_skin_card,
            "SIEGE_FRENZY" to R.drawable.loadout_spell_siege_frenzy_card,
            "BERSERKER" to R.drawable.loadout_spell_berserker_card,
        )

        expectedArt.forEach { (spellId, drawableId) ->
            assertEquals(
                LoadoutAssetLookup.Found(drawableId),
                LoadoutAssetCatalog.carouselArtwork(LoadoutItemReference.Spell(spellId)),
            )
            assertEquals(
                LoadoutAssetLookup.Found(drawableId),
                LoadoutAssetCatalog.illustration(LoadoutItemReference.Spell(spellId)),
            )
        }
    }

    @Test
    fun unknownSpellIllustrationRemainsExplicitlyMissing() {
        assertTrue(
            LoadoutAssetCatalog.illustration(LoadoutItemReference.Spell("UNKNOWN"))
                is LoadoutAssetLookup.Missing,
        )
    }
}
