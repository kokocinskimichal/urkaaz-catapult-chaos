package com.urkaaaz.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.util.LruCache
import com.urkaaaz.android.R
import com.urkaaaz.contracts.BossPhase
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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

internal data class ProjectileAnimation(
    val flightFrames: BitmapFrameSequence,
    val impactFrames: BitmapFrameSequence,
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

internal data class FireRainAnimationSet(
    val containerFlightFrames: BitmapFrameSequence,
    val splitFlashFrames: BitmapFrameSequence,
    val fragmentFlightFrames: BitmapFrameSequence,
    val fireFieldFrames: BitmapFrameSequence,
)

internal class BattlefieldAssetCatalog(
    context: Context,
    onBitmapReady: () -> Unit = {},
) {
    private val context = context.applicationContext
    private val resources = this.context.resources
    private val resourceIdCache = mutableMapOf<String, Int>()
    private val decodedStaticBitmaps = mutableMapOf<String, Bitmap?>()
    private val decodeExecutor = ThreadPoolExecutor(
        1,
        2,
        ASSET_DECODER_KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(ASSET_DECODER_QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "UrkaaazAssetDecoder").apply { isDaemon = true }
        },
    ).apply {
        allowCoreThreadTimeOut(true)
    }
    private val bitmapCache = AsyncAssetCache<Bitmap>(
        maxKilobytes = bitmapCacheKilobytes(),
        executor = decodeExecutor,
        sizeOf = { (it.allocationByteCount / 1024).coerceAtLeast(1) },
        onAssetReady = onBitmapReady,
    )
    private val unitAnimationCache = mutableMapOf<String, BitmapFrameSequence>()
    private val teamTextureBitmapCache = TeamTextureBitmapCache()
    private val teamTextureConfigurationCache = mutableMapOf<String, Map<String, String>>()
    private val teamTextureCache = mutableMapOf<String, Bitmap>()
    private val teamMaskCache = object : LruCache<String, Bitmap>(4 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    private val terrainSoil = BitmapFactory.decodeResource(
        resources,
        R.drawable.terrain_soil_fill_00,
    )
    private val grassCap = BitmapFactory.decodeResource(
        resources,
        R.drawable.terrain_grass_cap,
    )

    private val rockFlightFrames = loadAnimationFrames("anim_ammunition_rock_flight")
    private val rockImpactFrames = loadAnimationFrames("anim_ammunition_rock_impact")
    val demolisherExplosionFrames =
        loadAnimationFrames("anim_ammunition_demolisher_explosion")
    val fireRainAnimations = FireRainAnimationSet(
        containerFlightFrames = loadRequiredFireRainFrames(
            phase = "container flight",
            prefix = "anim_ammunition_fire_rain_flight",
            frameCount = FIRE_RAIN_CONTAINER_FLIGHT_FRAME_COUNT,
        ),
        splitFlashFrames = loadRequiredFireRainFrames(
            phase = "split flash",
            prefix = "anim_ammunition_fire_rain_split_flash",
            frameCount = FIRE_RAIN_SPLIT_FLASH_FRAME_COUNT,
        ),
        fragmentFlightFrames = loadRequiredFireRainFrames(
            phase = "fragment flight",
            prefix = "anim_ammunition_fire_rain_fragment_flight",
            frameCount = FIRE_RAIN_FRAGMENT_FLIGHT_FRAME_COUNT,
        ),
        fireFieldFrames = loadRequiredFireRainFrames(
            phase = "fire field",
            prefix = "anim_ammunition_fire_rain_field",
            frameCount = FIRE_RAIN_FIELD_FRAME_COUNT,
        ),
    )

    val fortressShieldDomeFrames: BitmapFrameSequence = loadRequiredFortressShieldDomeFrames()
    val fortressShieldHitFrames: BitmapFrameSequence = loadRequiredFortressShieldFrames(
        prefix = FORTRESS_SHIELD_HIT_PREFIX,
        phase = "hit flash",
    )
    val hasteOrbitingChevronFrames: BitmapFrameSequence = loadRequiredHasteOrbitingChevronFrames()
    val runicBastionCreationFrames: BitmapFrameSequence =
        loadRequiredRunicBastionFrames(
            prefix = RUNIC_BASTION_CREATION_PREFIX,
            phase = "creation",
            frameCount = RunicBastionPresentation.FRAME_COUNT,
        )
    val runicBastionIdleFrames: BitmapFrameSequence =
        loadRequiredRunicBastionFrames(
            prefix = RUNIC_BASTION_IDLE_PREFIX,
            phase = "idle",
            frameCount = RunicBastionPresentation.IDLE_FRAME_COUNT,
        )
    val runicBastionDestructionFrames: BitmapFrameSequence =
        loadRequiredRunicBastionFrames(
            prefix = RUNIC_BASTION_DESTRUCTION_PREFIX,
            phase = "destruction",
            frameCount = RunicBastionPresentation.DESTRUCTION_FRAME_COUNT,
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
            prefix = "anim_ammunition_siege_bomb",
            flightRenderSize = 18f,
            impactRenderSize = 54f,
        ),
        "POWDER_BARREL" to animation("anim_ammunition_powder_barrel"),
        "FIRE_RAIN" to ProjectileAnimation(
            flightFrames = fireRainAnimations.containerFlightFrames,
            impactFrames = emptyFrameSequence("fire-rain-impact"),
        ),
        "PLAGUE_CAULDRON" to animation("anim_ammunition_plague_cauldron"),
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
            "RAGING_BOAR" -> "raging_boar_idle_$side"
            "SLINGMASTER" -> "anim_character_slingmaster_move_000"
            else -> return null
        }
        return if (unitType == "RAGING_BOAR") {
            unitAnimationFrames(unitType, teamLabel, moving = false).bitmapIfReady(0)
        } else {
            requestBitmap(resourceName, required = false)
        }
    }

    fun unitAnimationFrames(
        unitType: String,
        teamLabel: String,
        moving: Boolean,
        attacking: Boolean = false,
    ): BitmapFrameSequence {
        val side = if (teamLabel == "BLUE") "left" else "right"
        val team = if (teamLabel == "BLUE") "blue" else "red"
        val cacheKey = "$unitType:$side:$moving:$attacking"
        return unitAnimationCache.getOrPut(cacheKey) {
            if (attacking) {
                when (unitType) {
                    "DEFENDER" -> loadTeamTextureAnimationFrames(
                        "defender",
                        "attack",
                        team,
                        side,
                    )
                    "SAPPER" -> loadTeamTextureAnimationFrames(
                        "sapper",
                        "attack",
                        team,
                        side,
                    )
                    "RAGING_BOAR" -> loadTeamTextureAnimationFrames(
                        "raging_boar",
                        "attack",
                        team,
                        side,
                    )
                    "DEMOLISHER" -> loadTeamTextureAnimationFrames(
                        "demolisher",
                        "attack",
                        team,
                        side,
                    )
                    else -> emptyFrameSequence(cacheKey)
                }
            } else if (!moving) {
                when (unitType) {
                    "DEFENDER" -> loadTeamTextureAnimationFrames(
                        "defender",
                        "idle",
                        team,
                        side,
                    )
                    "SAPPER" -> loadTeamTextureAnimationFrames(
                        "sapper",
                        "idle",
                        team,
                        side,
                    )
                    "DEMOLISHER" -> loadTeamTextureAnimationFrames(
                        "demolisher",
                        "idle",
                        team,
                        side,
                    )
                    "RAGING_BOAR" -> loadTeamTextureAnimationFrames(
                        "raging_boar",
                        "idle",
                        team,
                        side,
                    )
                    else -> emptyFrameSequence(cacheKey)
                }
            } else {
                val prefix = when (unitType) {
                    "SAPPER" -> "anim_character_sapper_move_${team}"
                    "DEFENDER" -> "anim_character_defender_move_${team}"
                    "DEMOLISHER" -> "anim_character_demolisher_move_${team}"
                    "RAGING_BOAR" -> "anim_character_raging_boar_move"
                    "SLINGMASTER" -> "anim_character_slingmaster_move"
                    else -> return@getOrPut emptyFrameSequence(cacheKey)
                }
                if (unitType == "DEFENDER") {
                    loadTeamTextureAnimationFrames("defender", "move", team, side)
                } else if (unitType == "DEMOLISHER") {
                    loadTeamTextureAnimationFrames("demolisher", "move", team, side)
                } else if (unitType == "SAPPER") {
                    loadTeamTextureAnimationFrames("sapper", "move", team, side)
                } else if (unitType == "RAGING_BOAR") {
                    loadTeamTextureAnimationFrames("raging_boar", "move", team, side)
                } else {
                    loadThreeDigitAnimationSequence(prefix)
                }
            }
        }
    }

    fun unitDeathAnimationFrames(unitType: String, teamLabel: String): BitmapFrameSequence {
        val side = if (teamLabel == "BLUE") "left" else "right"
        val team = if (teamLabel == "BLUE") "blue" else "red"
        val cacheKey = "death:$unitType:$side"
        return unitAnimationCache.getOrPut(cacheKey) {
            when (unitType) {
                "DEFENDER" -> loadTeamTextureAnimationFrames(
                    "defender",
                    "death",
                    team,
                    side,
                )
                "RAGING_BOAR" -> loadTeamTextureAnimationFrames(
                    "raging_boar",
                    "death",
                    team,
                    side,
                )
                else -> emptyFrameSequence(cacheKey)
            }
        }
    }

    fun sapperTurnAnimationFrames(teamLabel: String): BitmapFrameSequence =
        sapperAnimationFrames("turn", teamLabel)

    fun sapperReturnAnimationFrames(teamLabel: String): BitmapFrameSequence =
        sapperAnimationFrames("return", teamLabel)

    private fun sapperAnimationFrames(
        animation: String,
        teamLabel: String,
    ): BitmapFrameSequence {
        val side = if (teamLabel == "BLUE") "left" else "right"
        val team = if (teamLabel == "BLUE") "blue" else "red"
        return unitAnimationCache.getOrPut("SAPPER:$animation:$side") {
            loadTeamTextureAnimationFrames("sapper", animation, team, side).also { frames ->
                check(frames.frameCount == SAPPER_RETURN_FRAME_COUNT) {
                    "Sapper $animation animation has ${frames.frameCount} frames; " +
                        "expected $SAPPER_RETURN_FRAME_COUNT"
                }
            }
        }
    }

    private fun loadTeamTextureAnimationFrames(
        target: String,
        animation: String,
        team: String,
        side: String,
    ): BitmapFrameSequence {
        val sourceFrames = loadThreeDigitAnimationResources(
            "anim_character_${target}_$animation",
        )
        return BitmapFrameSequence(
            frameCount = sourceFrames.size,
            cache = bitmapCache,
            frameKey = { index -> "team:$target:$animation:$team:$index" },
            decodeFrame = { index ->
                val frameName = index.toString().padStart(3, '0')
                val neutralFrame = decodeResourceBitmap(sourceFrames[index])
                val mask = loadTeamMask(
                    target = target,
                    animation = animation,
                    side = side,
                    frameName = frameName,
                    expectedWidth = neutralFrame.width,
                    expectedHeight = neutralFrame.height,
                )
                val texture = loadTeamTexture(target, team)
                val source = if (side == "right") {
                    mirrorBitmap(neutralFrame)
                } else {
                    neutralFrame
                }
                teamTextureBitmapCache.getOrCreate(
                    "$target:$animation:$team:$index",
                    source,
                    mask,
                    texture,
                )
            },
        )
    }

    private fun loadTeamMask(
        target: String,
        animation: String,
        side: String,
        frameName: String,
        expectedWidth: Int,
        expectedHeight: Int,
    ): Bitmap {
        val key = "$target:$animation:$side:$frameName"
        synchronized(teamMaskCache) {
            teamMaskCache.get(key)?.let { return it }
            val sourceMask = context.assets.open(
                "animations/$target/masks/$animation/$frameName.png",
            ).use { input ->
                BitmapFactory.decodeStream(input)
                    ?: error("Could not decode $target $animation mask frame $frameName")
            }
            require(
                sourceMask.width == expectedWidth && sourceMask.height == expectedHeight,
            ) {
                "$target $animation mask frame $frameName has mismatched dimensions"
            }
            val mask = if (side == "right") mirrorBitmap(sourceMask) else sourceMask
            teamMaskCache.put(key, mask)
            return mask
        }
    }

    private fun loadTeamTexture(target: String, team: String): Bitmap {
        val textures = synchronized(teamTextureConfigurationCache) {
            teamTextureConfigurationCache.getOrPut(target) {
                loadTeamTextureConfiguration(target)
            }
        }
        val texturePath = textures[team.uppercase()]
            ?: error("No $target texture is configured for team '$team'")
        synchronized(teamTextureCache) {
            return teamTextureCache.getOrPut(texturePath) {
                context.assets.open(texturePath).use { input ->
                    BitmapFactory.decodeStream(input)
                        ?: error("Could not decode $target texture '$texturePath'")
                }
            }
        }
    }

    private fun loadTeamTextureConfiguration(target: String): Map<String, String> {
        val json = context.assets.open("animations/$target/palettes.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val teams = json.getJSONObject("teams")
        return listOf("BLUE", "RED").associateWith { team ->
            teams.getString(team)
        }
    }

    private fun mirrorBitmap(bitmap: Bitmap): Bitmap =
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { setScale(-1f, 1f) },
            true,
        )

    fun themeFor(biome: String): BattlefieldTheme =
        themes[biome] ?: error("No battlefield theme is registered for biome '$biome'")

    fun relicBitmap(assetKey: String): Bitmap? {
        val resourceName = RELIC_ASSET_RESOURCES[assetKey]
            ?: SHARED_MEDIUM_RELIC_KEY.matchEntire(assetKey)
                ?.groupValues
                ?.get(1)
                ?.let { "relic_medium_$it" }
            ?: return null
        return requestBitmap(resourceName, required = false)
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
        val bitmap = requestBitmap(assetKey, required = true) ?: return null
        return ResolvedMapDecoration(bitmap, kind)
    }

    fun animationFor(ammunitionType: String): ProjectileAnimation =
        animations[ammunitionType]
            ?: error("No projectile animation is registered for ammunition '$ammunitionType'")

    fun bossSprite(presentationKey: String, phase: BossPhase): Bitmap? {
        val name = BossPresentationCatalog.definition(presentationKey).spriteFor(phase)
        return requestBitmap(name, required = true)
    }

    fun bossIdleFrame(presentationKey: String, frameIndex: Int): Bitmap? {
        val animation = BossPresentationCatalog.definition(presentationKey).idleAnimation
            ?: return null
        return bossAnimationFrame(
            animation = animation,
            frameIndex = frameIndex,
            frameName = { index ->
                BossPresentationCatalog.idleFrameSpriteName(presentationKey, index)
            },
        )
    }

    fun bossWalkFrame(presentationKey: String, frameIndex: Int): Bitmap? {
        val animation = BossPresentationCatalog.definition(presentationKey).walkAnimation
            ?: return null
        return bossAnimationFrame(
            animation = animation,
            frameIndex = frameIndex,
            frameName = { index ->
                BossPresentationCatalog.walkFrameSpriteName(presentationKey, index)
            },
        )
    }

    fun bossAttackFrame(
        presentationKey: String,
        direction: BossAttackDirection,
        frameIndex: Int,
    ): Bitmap? {
        val animation = BossPresentationCatalog.definition(presentationKey)
            .attackAnimation(direction) ?: return null
        return bossAnimationFrame(
            animation = animation,
            frameIndex = frameIndex,
            frameName = { index ->
                BossPresentationCatalog.attackFrameSpriteName(presentationKey, direction, index)
            },
        )
    }

    fun bossDeathFrame(presentationKey: String, frameIndex: Int): Bitmap? {
        val animation = BossPresentationCatalog.definition(presentationKey).deathAnimation()
            ?: return null
        return bossAnimationFrame(
            animation = animation,
            frameIndex = frameIndex,
            frameName = { index ->
                BossPresentationCatalog.deathFrameSpriteName(presentationKey, index)
            },
        )
    }

    private fun bossAnimationFrame(
        animation: BossSpriteAnimationDefinition,
        frameIndex: Int,
        frameName: (Int) -> String,
    ): Bitmap? {
        var currentFrame: Bitmap? = null
        repeat(BOSS_FRAME_PREFETCH_AHEAD + 1) { offset ->
            val requestedIndex = if (animation.loops) {
                (frameIndex + offset) % animation.frameCount
            } else {
                (frameIndex + offset).coerceAtMost(animation.frameCount - 1)
            }
            val name = frameName(requestedIndex)
            val frame = requestBitmap(name, required = true) { bitmap ->
                check(bitmap.width == animation.frameWidth &&
                    bitmap.height == animation.frameHeight
                ) {
                    "Boss animation frame '$name' must be " +
                        "${animation.frameWidth}x${animation.frameHeight}"
                }
            }
            if (offset == 0) currentFrame = frame
        }
        return currentFrame
    }

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
        if (decodedStaticBitmaps.containsKey(name)) return decodedStaticBitmaps[name]
        val resourceId = drawableResourceId(name)
        val bitmap = resourceId.takeIf { it != 0 }
            ?.let { BitmapFactory.decodeResource(resources, it) }
        decodedStaticBitmaps[name] = bitmap
        bitmap?.let { bitmapCache.putReady(bitmapCacheKey(name), it) }
        return bitmap
    }

    private fun requestBitmap(
        name: String,
        required: Boolean,
        validate: (Bitmap) -> Unit = {},
    ): Bitmap? {
        val resourceId = drawableResourceId(name)
        if (resourceId == 0) {
            if (required) error("Missing drawable '$name'")
            return null
        }
        return when (
            val result = bitmapCache.request(bitmapCacheKey(name)) {
                val bitmap = BitmapFactory.decodeResource(resources, resourceId)
                    ?: error("Could not decode drawable '$name'")
                validate(bitmap)
                bitmap
            }
        ) {
            is AsyncAssetResult.Ready -> result.value
            AsyncAssetResult.Pending -> null
            is AsyncAssetResult.Failed ->
                throw IllegalStateException("Could not load drawable '$name'", result.cause)
        }
    }

    private fun drawableResourceId(name: String): Int =
        synchronized(resourceIdCache) {
            resourceIdCache.getOrPut(name) {
                resources.getIdentifier(name, "drawable", context.packageName)
            }
        }

    private fun bitmapCacheKey(resourceName: String): String = "drawable:$resourceName"

    private fun emptyFrameSequence(key: String) = BitmapFrameSequence(
        frameCount = 0,
        cache = bitmapCache,
        frameKey = { "$key:unused:$it" },
        decodeFrame = { error("Empty animation sequence '$key' has no frames") },
    )

    private data class DrawableResource(val name: String, val id: Int)

    private fun loadAnimationFrames(prefix: String): BitmapFrameSequence {
        val frames = (0..31).mapNotNull { index ->
            val name = "${prefix}_%03d".format(index)
            drawableResourceId(name).takeIf { it != 0 }?.let { DrawableResource(name, it) }
        }
        return BitmapFrameSequence(
            frameCount = frames.size,
            cache = bitmapCache,
            frameKey = { index -> bitmapCacheKey(frames[index].name) },
            decodeFrame = { index -> decodeResourceBitmap(frames[index]) },
        )
    }

    private fun loadRequiredFireRainFrames(
        phase: String,
        prefix: String,
        frameCount: Int,
    ): BitmapFrameSequence =
        requiredFrameSequence(
            label = "FIRE_RAIN $phase",
            frameCount = frameCount,
            resourceName = { index -> "${prefix}_%03d".format(index) },
            width = FIRE_RAIN_FRAME_WIDTH,
            height = FIRE_RAIN_FRAME_HEIGHT,
            requireAlpha = true,
        )

    private fun loadRequiredFortressShieldDomeFrames(): BitmapFrameSequence =
        loadRequiredFortressShieldFrames(
            prefix = FORTRESS_SHIELD_DOME_PREFIX,
            phase = "dome",
        )

    private fun loadRequiredFortressShieldFrames(
        prefix: String,
        phase: String,
    ): BitmapFrameSequence =
        requiredFrameSequence(
            label = "Fortress Shield $phase",
            frameCount = FortressShieldPresentation.FRAME_COUNT,
            resourceName = { index -> "$prefix%03d".format(index) },
            width = FortressShieldPresentation.FRAME_WIDTH,
            height = FortressShieldPresentation.FRAME_HEIGHT,
            requireAlpha = true,
        )

    private fun loadRequiredHasteOrbitingChevronFrames(): BitmapFrameSequence =
        requiredFrameSequence(
            label = "HASTE orbiting chevrons",
            frameCount = HasteOrbitingChevronPresentation.FRAME_COUNT,
            resourceName = { index -> "$HASTE_ORBITING_CHEVRON_PREFIX%03d".format(index) },
            width = HasteOrbitingChevronPresentation.FRAME_WIDTH,
            height = HasteOrbitingChevronPresentation.FRAME_HEIGHT,
            requireAlpha = true,
        )

    private fun loadRequiredRunicBastionFrames(
        prefix: String,
        phase: String,
        frameCount: Int,
    ): BitmapFrameSequence =
        requiredFrameSequence(
            label = "Runic Bastion $phase",
            frameCount = frameCount,
            resourceName = { index -> "$prefix%03d".format(index) },
            width = RunicBastionPresentation.FRAME_WIDTH,
            height = RunicBastionPresentation.FRAME_HEIGHT,
            requireAlpha = true,
        )

    private fun requiredFrameSequence(
        label: String,
        frameCount: Int,
        resourceName: (Int) -> String,
        width: Int,
        height: Int,
        requireAlpha: Boolean,
    ): BitmapFrameSequence {
        val frames = (0 until frameCount).map { index ->
            val name = resourceName(index)
            val id = drawableResourceId(name)
            check(id != 0) { "$label is missing frame $index ('$name')" }
            DrawableResource(name, id)
        }
        val unexpectedFrame = resourceName(frameCount)
        check(drawableResourceId(unexpectedFrame) == 0) {
            "$label has an unexpected frame at index $frameCount ('$unexpectedFrame')"
        }
        return BitmapFrameSequence(
            frameCount = frames.size,
            cache = bitmapCache,
            frameKey = { index -> bitmapCacheKey(frames[index].name) },
            decodeFrame = { index ->
                decodeResourceBitmap(frames[index]).also { bitmap ->
                    check(bitmap.width == width && bitmap.height == height) {
                        "$label frame $index ('${frames[index].name}') has invalid size " +
                            "${bitmap.width}x${bitmap.height}; expected ${width}x$height"
                    }
                    if (requireAlpha) {
                        check(bitmap.hasAlpha()) {
                            "$label frame $index ('${frames[index].name}') has no alpha channel"
                        }
                    }
                }
            },
        )
    }

    private fun loadThreeDigitAnimationSequence(prefix: String): BitmapFrameSequence {
        val frames = loadThreeDigitAnimationResources(prefix)
        return BitmapFrameSequence(
            frameCount = frames.size,
            cache = bitmapCache,
            frameKey = { index -> bitmapCacheKey(frames[index].name) },
            decodeFrame = { index -> decodeResourceBitmap(frames[index]) },
        )
    }

    private fun loadThreeDigitAnimationResources(prefix: String): List<DrawableResource> =
        (0..99).mapNotNull { index ->
            val name = "${prefix}_%03d".format(index)
            drawableResourceId(name).takeIf { it != 0 }?.let { DrawableResource(name, it) }
        }

    private fun decodeResourceBitmap(resource: DrawableResource): Bitmap =
        BitmapFactory.decodeResource(resources, resource.id)
            ?: error("Could not decode drawable '${resource.name}'")

    fun cancelPendingLoads() {
        bitmapCache.cancelPendingLoads()
    }

    private companion object {
        const val MIN_BITMAP_CACHE_KILOBYTES = 8 * 1024
        const val MAX_BITMAP_CACHE_KILOBYTES = 64 * 1024
        const val BITMAP_CACHE_MEMORY_FRACTION = 8
        const val ASSET_DECODER_KEEP_ALIVE_SECONDS = 30L
        const val ASSET_DECODER_QUEUE_CAPACITY = 128
        const val BOSS_FRAME_PREFETCH_AHEAD = 2
        const val SAPPER_RETURN_FRAME_COUNT = 15
        const val ROCK_IMPACT_BREAKUP_FRAME = 9
        const val FIRE_RAIN_FRAME_WIDTH = 512
        const val FIRE_RAIN_FRAME_HEIGHT = 512
        const val FIRE_RAIN_CONTAINER_FLIGHT_FRAME_COUNT = 24
        const val FIRE_RAIN_SPLIT_FLASH_FRAME_COUNT = 12
        const val FIRE_RAIN_FRAGMENT_FLIGHT_FRAME_COUNT = 24
        const val FIRE_RAIN_FIELD_FRAME_COUNT = 24
        const val FORTRESS_SHIELD_DOME_PREFIX = "anim_spell_fortress_shield_dome_"
        const val FORTRESS_SHIELD_HIT_PREFIX = "anim_spell_fortress_shield_hit_"
        const val HASTE_ORBITING_CHEVRON_PREFIX = "anim_spell_haste_orbiting_chevron_"
        const val RUNIC_BASTION_CREATION_PREFIX = "anim_spell_runic_bastion_creation_"
        const val RUNIC_BASTION_IDLE_PREFIX = "anim_spell_runic_bastion_idle_"
        const val RUNIC_BASTION_DESTRUCTION_PREFIX = "anim_spell_runic_bastion_destruction_"

        fun bitmapCacheKilobytes(): Int =
            (
                Runtime.getRuntime().maxMemory() /
                    1024 /
                    BITMAP_CACHE_MEMORY_FRACTION
                )
                .coerceIn(
                    MIN_BITMAP_CACHE_KILOBYTES.toLong(),
                    MAX_BITMAP_CACHE_KILOBYTES.toLong(),
                )
                .toInt()

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
