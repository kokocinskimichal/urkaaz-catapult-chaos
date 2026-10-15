package com.urkaaaz.assets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetCatalogTest {
    @Test
    fun parsesStableTerrainNames() {
        assertEquals(
            AssetKey("green_frontier", AssetCategory.TREE, "00", "terrain_green_frontier_tree_00.png"),
            TerrainAssetNaming.parse("terrain_green_frontier_tree_00.png"),
        )
        assertEquals(AssetCategory.RELIC, TerrainAssetNaming.parse("terrain_frost_relic_05.png")?.category)
    }

    @Test
    fun rejectsUnknownNames() {
        assertNull(TerrainAssetNaming.parse("background_green_frontier.png"))
        assertNull(TerrainAssetNaming.parse("terrain_green_frontier_unknown_00.png"))
    }

    @Test
    fun indexesSharedResourcesByBiome() {
        val catalog = AssetCatalog()
        assertTrue("green_frontier" in catalog.availableBiomes())
        assertTrue(catalog.assetsForBiome("green_frontier").any { it.category == AssetCategory.TREE })
    }

    @Test
    fun indexesAllImportedDecorationVariantsAndTheirResources() {
        val catalog = AssetCatalog()
        val expectedCounts = mapOf(
            "green_frontier" to mapOf(
                AssetCategory.TREE to 6,
                AssetCategory.ROCK to 6,
                AssetCategory.PROP to 6,
            ),
            "frostbound" to mapOf(
                AssetCategory.TREE to 6,
                AssetCategory.ROCK to 6,
                AssetCategory.PROP to 12,
            ),
            "copperwood" to mapOf(
                AssetCategory.TREE to 8,
                AssetCategory.ROCK to 6,
                AssetCategory.PROP to 6,
            ),
            "ashen_march" to mapOf(
                AssetCategory.TREE to 6,
                AssetCategory.ROCK to 6,
                AssetCategory.PROP to 8,
            ),
            "sunken_marshes" to mapOf(
                AssetCategory.TREE to 6,
                AssetCategory.ROCK to 6,
                AssetCategory.PROP to 6,
            ),
        )

        expectedCounts.forEach { (biome, counts) ->
            val assets = catalog.assetsForBiome(biome)
            counts.forEach { (category, expected) ->
                assertEquals(expected, assets.count { it.category == category }, "$biome $category")
            }
            assets.forEach { asset ->
                assertTrue(
                    catalog.open(asset)?.use { true } == true,
                    "Missing resource for ${asset.fileName}",
                )
            }
        }
        assertTrue("sunken_marshes" in catalog.availableBiomes())
    }
}
