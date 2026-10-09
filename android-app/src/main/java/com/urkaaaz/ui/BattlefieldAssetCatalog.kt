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
    val decorations: List<Bitmap?>,
    val fallbackColor: Int,
)

data class ProjectileAnimation(
    val flightFrames: List<Bitmap>,
    val impactFrames: List<Bitmap>,
)

class BattlefieldAssetCatalog(private val context: Context) {
    private val resources = context.resources
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

    private val animations = mapOf(
        "ROCK" to ProjectileAnimation(rockFlightFrames, rockImpactFrames),
        "SIEGE_BOMB" to animation("siege_bomb"),
        "POWDER_BARREL" to animation("powder_barrel"),
        "CLUSTER_BOMB" to animation("cluster_bomb"),
        "FIRE_RAIN" to animation("fire_rain"),
        "PLAGUE_CAULDRON" to animation("plague_cauldron"),
    )

    private val themes = mapOf(
        "GREEN_FRONTIER" to BattlefieldTheme(
            background = loadBitmap("background_green_frontier"),
            soil = terrainSoil,
            cap = loadBitmap("terrain_green_frontier_cap") ?: grassCap,
            decorations = listOf(loadBitmap("terrain_tree_00"), loadBitmap("terrain_tree_01")),
            fallbackColor = Color.rgb(111, 145, 77),
        ),
        "FROSTBOUND" to BattlefieldTheme(
            background = loadBitmap("background_frostbound_pass"),
            soil = loadBitmap("terrain_frost_soil_fill_00"),
            cap = loadBitmap("terrain_frost_snow_cap"),
            decorations = listOf(
                loadBitmap("terrain_frost_tree_00"),
                loadBitmap("terrain_frost_tree_01"),
                loadBitmap("terrain_frost_rock_00"),
            ),
            fallbackColor = Color.rgb(169, 185, 193),
        ),
        "COPPERWOOD" to BattlefieldTheme(
            background = loadBitmap("background_copperwood"),
            soil = loadBitmap("terrain_copperwood_soil_fill_00"),
            cap = loadBitmap("terrain_copperwood_cap"),
            decorations = listOf(
                loadBitmap("terrain_copperwood_tree_00"),
                loadBitmap("terrain_copperwood_tree_01"),
                loadBitmap("terrain_copperwood_rock_00"),
            ),
            fallbackColor = Color.rgb(111, 75, 48),
        ),
        "ASHEN_MARCH" to BattlefieldTheme(
            background = loadBitmap("background_ashen_march"),
            soil = loadBitmap("terrain_ashen_march_soil_fill_00"),
            cap = loadBitmap("terrain_ashen_march_cap"),
            decorations = listOf(
                loadBitmap("terrain_ashen_march_tree_00"),
                loadBitmap("terrain_ashen_march_tree_01"),
                loadBitmap("terrain_ashen_march_rock_00"),
            ),
            fallbackColor = Color.rgb(81, 75, 68),
        ),
        "SUNKEN_MARSHES" to BattlefieldTheme(
            background = loadBitmap("background_sunken_marshes"),
            soil = terrainSoil,
            cap = grassCap,
            decorations = listOf(loadBitmap("terrain_prop_00"), loadBitmap("terrain_prop_01")),
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

    fun themeFor(biome: String): BattlefieldTheme =
        themes[biome] ?: themes.getValue("GREEN_FRONTIER")

    fun animationFor(ammunitionType: String): ProjectileAnimation =
        animations[ammunitionType] ?: animations.getValue("ROCK")

    private fun animation(prefix: String): ProjectileAnimation =
        ProjectileAnimation(
            flightFrames = loadAnimationFrames("${prefix}_flight"),
            impactFrames = loadAnimationFrames("${prefix}_impact"),
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
}
