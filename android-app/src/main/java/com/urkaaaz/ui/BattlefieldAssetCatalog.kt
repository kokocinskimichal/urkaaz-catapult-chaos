package com.urkaaaz.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.urkaaaz.android.R

data class BattlefieldTheme(
    val background: Bitmap?,
    val soil: Bitmap?,
    val cap: Bitmap?,
    val decorations: List<TerrainDecoration>,
    val fallbackColor: Int,
)

data class TerrainDecoration(
    val bitmap: Bitmap?,
    val kind: BattlefieldAssetKind,
)

data class ResolvedMapDecoration(
    val bitmap: Bitmap,
    val kind: BattlefieldAssetKind,
)

enum class BattlefieldAssetKind {
    TREE,
    ROCK,
    PROP,
    RELIC,
}

data class ProjectileAnimation(
    val flightFrames: List<Bitmap>,
    val impactFrames: List<Bitmap>,
    val flightRenderSize: Float = 36f,
    val impactRenderSize: Float = 108f,
    val impactFullRenderSizeStartFrame: Int = 0,
) {
    fun impactRenderSizeAt(frameIndex: Int): Float =
        if (frameIndex < impactFullRenderSizeStartFrame) {
            flightRenderSize
        } else {
            impactRenderSize
        }
}

data class FireRainAnimationSet(
    val containerFlightFrames: List<Bitmap>,
    val splitFlashFrames: List<Bitmap>,
    val fragmentFlightFrames: List<Bitmap>,
    val fireFieldFrames: List<Bitmap>,
)

class BattlefieldAssetCatalog(private val context: Context) {
    private val resources = context.resources
    private val unitBitmapCache = mutableMapOf<String, Bitmap?>()
    private val unitAnimationCache = mutableMapOf<String, List<Bitmap>>()
    private val terrainSoil = BitmapFactory.decodeResource(
        resources,
        R.drawable.terrain_soil_fill_00,
    )
    private val grassCap = BitmapFactory.decodeResource(
        resources,
        R.drawable.terrain_grass_cap,
    )

    private val rockFlightFrames = loadAnimationFrames("rock_flight")
    private val rockImpactFrames = loadAnimationFrames("rock_impact")
    val fireRainAnimations = FireRainAnimationSet(
        containerFlightFrames = loadRequiredFireRainFrames(
            phase = "container flight",
            prefix = "fire_rain_flight",
            frameCount = FIRE_RAIN_CONTAINER_FLIGHT_FRAME_COUNT,
        ),
        splitFlashFrames = loadRequiredFireRainFrames(
            phase = "split flash",
            prefix = "fire_rain_split_flash",
            frameCount = FIRE_RAIN_SPLIT_FLASH_FRAME_COUNT,
        ),
        fragmentFlightFrames = loadRequiredFireRainFrames(
            phase = "fragment flight",
            prefix = "fire_rain_fragment_flight",
            frameCount = FIRE_RAIN_FRAGMENT_FLIGHT_FRAME_COUNT,
        ),
        fireFieldFrames = loadRequiredFireRainFrames(
            phase = "fire field",
            prefix = "fire_rain_field",
            frameCount = FIRE_RAIN_FIELD_FRAME_COUNT,
        ),
    )

    private val animations = mapOf(
        "ROCK" to ProjectileAnimation(
            flightFrames = rockFlightFrames,
            impactFrames = rockImpactFrames,
            flightRenderSize = 18f,
            impactRenderSize = 36f,
            impactFullRenderSizeStartFrame = ROCK_IMPACT_BREAKUP_FRAME,
        ),
        "SIEGE_BOMB" to animation(
            prefix = "siege_bomb",
            flightRenderSize = 18f,
            impactRenderSize = 54f,
        ),
        "POWDER_BARREL" to animation("powder_barrel"),
        "FIRE_RAIN" to ProjectileAnimation(
            flightFrames = fireRainAnimations.containerFlightFrames,
            impactFrames = emptyList(),
        ),
        "PLAGUE_CAULDRON" to animation("plague_cauldron"),
    )

    private val themes = mapOf(
        "GREEN_FRONTIER" to BattlefieldTheme(
            background = loadBitmap("background_green_frontier"),
            soil = terrainSoil,
            cap = loadBitmap("terrain_green_frontier_cap") ?: grassCap,
            decorations = listOf(
                TerrainDecoration(loadBitmap("terrain_green_frontier_tree_00"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_green_frontier_tree_01"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_green_frontier_rock_00"), BattlefieldAssetKind.ROCK),
                TerrainDecoration(loadBitmap("terrain_green_frontier_rock_01"), BattlefieldAssetKind.ROCK),
                TerrainDecoration(loadBitmap("terrain_green_frontier_prop_00"), BattlefieldAssetKind.PROP),
                TerrainDecoration(loadBitmap("terrain_green_frontier_prop_01"), BattlefieldAssetKind.PROP),
            ),
            fallbackColor = Color.rgb(111, 145, 77),
        ),
        "FROSTBOUND" to BattlefieldTheme(
            background = loadBitmap("background_frostbound_pass"),
            soil = loadBitmap("terrain_frost_soil_fill_00"),
            cap = loadBitmap("terrain_frost_snow_cap"),
            decorations = listOf(
                TerrainDecoration(loadBitmap("terrain_frost_tree_00"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_frost_tree_01"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_frost_rock_00"), BattlefieldAssetKind.ROCK),
                TerrainDecoration(loadBitmap("terrain_frost_prop_00"), BattlefieldAssetKind.PROP),
                TerrainDecoration(loadBitmap("terrain_frost_prop_01"), BattlefieldAssetKind.PROP),
            ),
            fallbackColor = Color.rgb(169, 185, 193),
        ),
        "COPPERWOOD" to BattlefieldTheme(
            background = loadBitmap("background_copperwood"),
            soil = loadBitmap("terrain_copperwood_soil_fill_00"),
            cap = loadBitmap("terrain_copperwood_cap"),
            decorations = listOf(
                TerrainDecoration(loadBitmap("terrain_copperwood_tree_00"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_copperwood_tree_01"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_copperwood_rock_00"), BattlefieldAssetKind.ROCK),
                TerrainDecoration(loadBitmap("terrain_copperwood_prop_00"), BattlefieldAssetKind.PROP),
                TerrainDecoration(loadBitmap("terrain_copperwood_prop_01"), BattlefieldAssetKind.PROP),
            ),
            fallbackColor = Color.rgb(111, 75, 48),
        ),
        "ASHEN_MARCH" to BattlefieldTheme(
            background = loadBitmap("background_ashen_march"),
            soil = loadBitmap("terrain_ashen_march_soil_fill_00"),
            cap = loadBitmap("terrain_ashen_march_cap"),
            decorations = listOf(
                TerrainDecoration(loadBitmap("terrain_ashen_march_tree_00"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_ashen_march_tree_01"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_ashen_march_rock_00"), BattlefieldAssetKind.ROCK),
                TerrainDecoration(loadBitmap("terrain_ashen_march_prop_00"), BattlefieldAssetKind.PROP),
                TerrainDecoration(loadBitmap("terrain_ashen_march_prop_01"), BattlefieldAssetKind.PROP),
            ),
            fallbackColor = Color.rgb(81, 75, 68),
        ),
        "SUNKEN_MARSHES" to BattlefieldTheme(
            background = loadBitmap("background_sunken_marshes"),
            soil = terrainSoil,
            cap = grassCap,
            decorations = listOf(
                TerrainDecoration(loadBitmap("terrain_green_frontier_prop_00"), BattlefieldAssetKind.PROP),
                TerrainDecoration(loadBitmap("terrain_green_frontier_prop_01"), BattlefieldAssetKind.PROP),
                TerrainDecoration(loadBitmap("terrain_green_frontier_tree_00"), BattlefieldAssetKind.TREE),
                TerrainDecoration(loadBitmap("terrain_green_frontier_rock_00"), BattlefieldAssetKind.ROCK),
            ),
            fallbackColor = Color.rgb(77, 98, 76),
        ),
    )

    val blueFortress: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.fortress_chapter_01_left)
    val redFortress: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.fortress_chapter_01_right)
    val blueCatapult: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.catapult_left)
    val redCatapult: Bitmap =
        BitmapFactory.decodeResource(resources, R.drawable.catapult_right)

    fun unitBitmap(unitType: String, teamLabel: String): Bitmap? {
        val side = if (teamLabel == "BLUE") "left" else "right"
        val resourceName = when (unitType) {
            "SAPPER" -> "sapper_$side"
            "DEFENDER" -> "defender_$side"
            "DEMOLISHER" -> "demolisher_$side"
            "RAGING_BOAR" -> "goblin_raging_boar_idle${if (side == "right") "_right" else ""}_00"
            "SLINGMASTER" -> "goblin_slingmaster_run_00"
            else -> return null
        }

        return unitBitmapCache.getOrPut(resourceName) {
            val resourceId = resources.getIdentifier(resourceName, "drawable", context.packageName)
            resourceId.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
        }
    }

    fun unitAnimationFrames(
        unitType: String,
        teamLabel: String,
        moving: Boolean,
        attacking: Boolean = false,
    ): List<Bitmap> {
        val side = if (teamLabel == "BLUE") "left" else "right"
        val cacheKey = "$unitType:$side:$moving:$attacking"
        return unitAnimationCache.getOrPut(cacheKey) {
            if (attacking) {
                val prefix = when (unitType) {
                    "DEFENDER" -> "defender_${side}_fight"
                    "RAGING_BOAR" -> "goblin_raging_boar_attack${if (side == "right") "_right" else ""}"
                    else -> ""
                }
                when {
                    prefix.isEmpty() -> emptyList()
                    unitType == "DEFENDER" -> loadThreeDigitAnimationFrames(prefix)
                    else -> loadAnimationFrames(prefix)
                }
            } else if (!moving) {
                when (unitType) {
                    "DEFENDER" -> loadThreeDigitAnimationFrames("defender_${side}_idle")
                    "DEMOLISHER" -> loadThreeDigitAnimationFrames("demolisher_${side}_idle")
                    "RAGING_BOAR" -> loadAnimationFrames(
                        "goblin_raging_boar_idle${if (side == "right") "_right" else ""}",
                    )
                    else -> emptyList()
                }
            } else {
                val prefix = when (unitType) {
                    "SAPPER" -> "sapper_${side}_anim"
                    "DEFENDER" -> "defender_${side}_jog"
                    "DEMOLISHER" -> "demolisher_${side}_jog"
                    "RAGING_BOAR" -> "goblin_raging_boar_run${if (side == "right") "_right" else ""}"
                    "SLINGMASTER" -> "goblin_slingmaster_run"
                    else -> return@getOrPut emptyList()
                }
                if (unitType == "DEMOLISHER") {
                    loadThreeDigitAnimationFrames(prefix)
                } else if (unitType == "DEFENDER") {
                    loadThreeDigitAnimationFrames(prefix)
                } else {
                    loadAnimationFrames(prefix)
                }
            }
        }

    }

    fun unitDeathAnimationFrames(unitType: String, teamLabel: String): List<Bitmap> {
        val side = if (teamLabel == "BLUE") "left" else "right"
        val cacheKey = "death:$unitType:$side"
        return unitAnimationCache.getOrPut(cacheKey) {
            when (unitType) {
                "DEFENDER" -> {
                    loadThreeDigitAnimationFrames("defender_${side}_death_anim")
                }
                "RAGING_BOAR" -> loadAnimationFrames(
                    "goblin_raging_boar_death${if (side == "right") "_right" else ""}",
                )
                "DEMOLISHER" -> loadThreeDigitAnimationFrames("demolisher_${side}_death")
                else -> emptyList()
            }
        }
    }

    fun themeFor(biome: String): BattlefieldTheme =
        themes[biome] ?: error("No battlefield theme is registered for biome '$biome'")

    fun relicBitmap(assetKey: String): Bitmap? {
        val resourceName = RELIC_ASSET_RESOURCES[assetKey]
            ?: SHARED_MEDIUM_RELIC_KEY.matchEntire(assetKey)
                ?.groupValues
                ?.get(1)
                ?.let { "relic_medium_$it" }
            ?: return null
        return loadBitmap(resourceName)
    }

    fun mapDecoration(
        assetKey: String,
    ): ResolvedMapDecoration? {
        val kind = when {
            "_tree_" in assetKey -> BattlefieldAssetKind.TREE
            "_rock_" in assetKey -> BattlefieldAssetKind.ROCK
            "_prop_" in assetKey -> BattlefieldAssetKind.PROP
            else -> return null
        }
        val bitmap = loadBitmap(assetKey) ?: error("Missing map decoration drawable '$assetKey'")
        return ResolvedMapDecoration(bitmap, kind)
    }

    fun animationFor(ammunitionType: String): ProjectileAnimation =
        animations[ammunitionType]
            ?: error("No projectile animation is registered for ammunition '$ammunitionType'")

    private fun animation(
        prefix: String,
        flightRenderSize: Float = 36f,
        impactRenderSize: Float = 108f,
    ): ProjectileAnimation =
        ProjectileAnimation(
            flightFrames = loadAnimationFrames("${prefix}_flight"),
            impactFrames = loadAnimationFrames("${prefix}_impact"),
            flightRenderSize = flightRenderSize,
            impactRenderSize = impactRenderSize,
        )

    private fun loadBitmap(name: String): Bitmap? {
        val resourceId = resources.getIdentifier(name, "drawable", context.packageName)
        return resourceId.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
    }

    private fun loadAnimationFrames(prefix: String): List<Bitmap> =
        (0..31).mapNotNull { index ->
            val resourceId = resources.getIdentifier(
                "${prefix}_%02d".format(index),
                "drawable",
                context.packageName,
            )
            resourceId.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
        }

    private fun loadRequiredFireRainFrames(
        phase: String,
        prefix: String,
        frameCount: Int,
    ): List<Bitmap> {
        val frames = (0 until frameCount).map { index ->
            val resourceName = "${prefix}_%02d".format(index)
            val resourceId = resources.getIdentifier(
                resourceName,
                "drawable",
                context.packageName,
            )
            check(resourceId != 0) {
                "FIRE_RAIN $phase is missing frame $index ('$resourceName')"
            }
            val bitmap = BitmapFactory.decodeResource(resources, resourceId)
                ?: error("FIRE_RAIN $phase frame $index ('$resourceName') could not be decoded")
            check(bitmap.width == FIRE_RAIN_FRAME_WIDTH &&
                bitmap.height == FIRE_RAIN_FRAME_HEIGHT
            ) {
                "FIRE_RAIN $phase frame $index ('$resourceName') has invalid size " +
                    "${bitmap.width}x${bitmap.height}; expected " +
                    "${FIRE_RAIN_FRAME_WIDTH}x$FIRE_RAIN_FRAME_HEIGHT"
            }
            check(bitmap.hasAlpha()) {
                "FIRE_RAIN $phase frame $index ('$resourceName') has no alpha channel"
            }
            bitmap
        }
        val unexpectedResourceName = "${prefix}_%02d".format(frameCount)
        check(
            resources.getIdentifier(
                unexpectedResourceName,
                "drawable",
                context.packageName,
            ) == 0,
        ) {
            "FIRE_RAIN $phase has an unexpected frame at index $frameCount " +
                "('$unexpectedResourceName')"
        }
        return frames
    }

    private fun loadThreeDigitAnimationFrames(prefix: String): List<Bitmap> =
        (0..99).mapNotNull { index ->
            val resourceId = resources.getIdentifier(
                "${prefix}_%03d".format(index),
                "drawable",
                context.packageName,
            )
            resourceId.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
        }

    private companion object {
        const val ROCK_IMPACT_BREAKUP_FRAME = 9
        const val FIRE_RAIN_FRAME_WIDTH = 512
        const val FIRE_RAIN_FRAME_HEIGHT = 512
        const val FIRE_RAIN_CONTAINER_FLIGHT_FRAME_COUNT = 24
        const val FIRE_RAIN_SPLIT_FLASH_FRAME_COUNT = 12
        const val FIRE_RAIN_FRAGMENT_FLIGHT_FRAME_COUNT = 24
        const val FIRE_RAIN_FIELD_FRAME_COUNT = 24
        val RELIC_ASSET_RESOURCES = buildMap {
            val biomeResources = mapOf(
                "green_frontier" to listOf(
                    "terrain_green_frontier_relic_01_cracked_watering_can",
                    "terrain_green_frontier_relic_02_rabbit_skull_flower",
                    "terrain_green_frontier_relic_03_potato_shaped_stone",
                    "terrain_green_frontier_relic_04_mini_horseshoe",
                    "terrain_green_frontier_relic_05_peeking_mole",
                    "terrain_green_frontier_relic_06_helmet_worm",
                    "terrain_green_frontier_relic_07_large_tibia_bone",
                    "terrain_green_frontier_relic_08_partial_skeleton",
                    "terrain_green_frontier_relic_09_worn_boot",
                    "terrain_green_frontier_relic_10_hand_shovel",
                ),
                "frostbound" to listOf(
                    "terrain_frost_relic_01_frozen_goblin_chest",
                    "terrain_frost_relic_02_buried_orc_helmet",
                    "terrain_frost_relic_03_ice_wolf_skeleton",
                    "terrain_frost_relic_04_cracked_runic_tablet",
                    "terrain_frost_relic_05_frozen_coin_pouch",
                    "terrain_frost_relic_06_expedition_sled_fragment",
                    "terrain_frost_relic_07_buried_miner_pickaxe",
                    "terrain_frost_relic_08_ice_creature_egg",
                    "terrain_frost_relic_09_buried_snowman_mask",
                    "terrain_frost_relic_10_frozen_alarm_horn",
                ),
                "copperwood" to listOf(
                    "terrain_copperwood_detail_01_copper_coin",
                    "terrain_copperwood_detail_02_metal_acorn",
                    "terrain_copperwood_detail_03_orc_wooden_spoon",
                    "terrain_copperwood_detail_04_cracked_leaf_cup",
                    "terrain_copperwood_detail_05_deer_skull",
                    "terrain_copperwood_detail_06_caped_leaf",
                    "terrain_copperwood_detail_07_dwarven_key",
                    "terrain_copperwood_detail_08_pawprint_stone",
                    "terrain_copperwood_detail_09_blank_cracked_plaque",
                    "terrain_copperwood_detail_10_neutral_buried_detail",
                ),
                "ashen_march" to listOf(
                    "terrain_ashen_march_relic_01_burned_goblin_helmet",
                    "terrain_ashen_march_relic_02_burned_spoon",
                    "terrain_ashen_march_relic_03_cracked_ash_bottle",
                    "terrain_ashen_march_relic_04_charred_tower_bone",
                    "terrain_ashen_march_relic_05_melted_paw_coin",
                    "terrain_ashen_march_relic_06_cart_wheel_fragment",
                    "terrain_ashen_march_relic_07_extinguished_candle",
                    "terrain_ashen_march_relic_08_burned_glove",
                    "terrain_ashen_march_relic_09_charred_carrot",
                    "terrain_ashen_march_relic_10_empty_ash_pot",
                ),
                "sunken_marshes" to listOf(
                    "terrain_green_frontier_relic_01_cracked_watering_can",
                    "terrain_green_frontier_relic_02_rabbit_skull_flower",
                    "terrain_green_frontier_relic_03_potato_shaped_stone",
                    "terrain_green_frontier_relic_04_mini_horseshoe",
                    "terrain_green_frontier_relic_05_peeking_mole",
                    "terrain_green_frontier_relic_06_helmet_worm",
                    "terrain_green_frontier_relic_07_large_tibia_bone",
                    "terrain_green_frontier_relic_08_partial_skeleton",
                    "terrain_green_frontier_relic_09_worn_boot",
                    "terrain_green_frontier_relic_10_hand_shovel",
                ),
            )
            biomeResources.forEach { (biome, resources) ->
                resources.forEachIndexed { index, resourceName ->
                    put("relic.$biome.small.${index + 1}", resourceName)
                }
            }
        }
        val SHARED_MEDIUM_RELIC_KEY = Regex("""relic\.shared\.medium\.([a-z0-9_]+)""")
    }
}
