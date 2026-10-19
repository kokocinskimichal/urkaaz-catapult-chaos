package com.urkaaaz.screens.loadout

import com.urkaaaz.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadoutAssetCatalogTest {
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
    fun missingSpellIllustrationsRemainExplicitlyMissingUntilAssetsAreAdded() {
        assertTrue(
            LoadoutAssetCatalog.illustration(LoadoutItemReference.Spell("HASTE"))
                is LoadoutAssetLookup.Missing,
        )
    }
}
