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
    fun missingSpellIllustrationsRemainExplicitlyMissingUntilAssetsAreAdded() {
        assertTrue(
            LoadoutAssetCatalog.illustration(LoadoutItemReference.Spell("HASTE"))
                is LoadoutAssetLookup.Missing,
        )
    }
}
