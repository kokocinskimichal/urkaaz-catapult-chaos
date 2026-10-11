package com.urkaaaz.campaign.chapter1

import com.urkaaaz.campaign.TerrainBiome
import com.urkaaaz.campaign.createCampaignTerrainProfile
import com.urkaaaz.campaign.terrainHeightAt
import com.urkaaaz.contracts.CatapultPlacement
import com.urkaaaz.contracts.Facing
import com.urkaaaz.contracts.FortressPlacement
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapGenerationMetadata
import com.urkaaaz.contracts.MapObjectPlacement
import com.urkaaaz.contracts.MapObjectType
import com.urkaaaz.contracts.ObjectCollision
import com.urkaaaz.contracts.ProtectedTerrainZone
import com.urkaaaz.contracts.Team

private val chapter1Level1Terrain = createCampaignTerrainProfile(
    chapter = 1,
    level = 1,
    worldWidth = 7_200f,
)

val chapter1Level1MapDefinition = MapDefinition(
    levelId = "chapter-1-level-1",
    schemaVersion = 1,
    worldWidth = 7_200f,
    worldHeight = 4_800f,
    terrain = chapter1Level1Terrain,
    protectedTerrainZones = listOf(
        ProtectedTerrainZone(
            id = "chapter-1-level-1-blue-foundation",
            minX = 0f,
            maxX = 686f,
            minHeight = 3_570f,
            maxHeight = 3_730f,
        ),
        ProtectedTerrainZone(
            id = "chapter-1-level-1-red-foundation",
            minX = 6_514f,
            maxX = 7_200f,
            minHeight = 3_590f,
            maxHeight = 3_750f,
        ),
    ),
    fortresses = listOf(
        FortressPlacement(
            owner = Team.BLUE,
            x = 490f,
            foundationHeight = 3_650f,
            assetKey = "fortress.blue.green-frontier",
            protectedZoneId = "chapter-1-level-1-blue-foundation",
        ),
        FortressPlacement(
            owner = Team.RED,
            x = 6_710f,
            foundationHeight = 3_668f,
            assetKey = "fortress.red.green-frontier",
            protectedZoneId = "chapter-1-level-1-red-foundation",
        ),
    ),
    catapults = listOf(
        CatapultPlacement(
            owner = Team.BLUE,
            x = 450f,
            foundationHeight = 3_650f,
            facing = Facing.RIGHT,
            assetKey = "catapult.blue.default",
            protectedZoneId = "chapter-1-level-1-blue-foundation",
        ),
        CatapultPlacement(
            owner = Team.RED,
            x = 6_750f,
            foundationHeight = 3_668f,
            facing = Facing.LEFT,
            assetKey = "catapult.red.default",
            protectedZoneId = "chapter-1-level-1-red-foundation",
        ),
    ),
    objects = listOf(
        MapObjectPlacement(
            id = "chapter-1-level-1-tree",
            type = MapObjectType.DECORATION,
            x = 2_400f,
            y = terrainHeightAt(chapter1Level1Terrain.samples, 2_400f),
            assetKey = "decoration.green-frontier.tree",
        ),
        MapObjectPlacement(
            id = "chapter-1-level-1-rock",
            type = MapObjectType.DECORATION,
            x = 4_800f,
            y = terrainHeightAt(chapter1Level1Terrain.samples, 4_800f),
            scale = 0.9f,
            assetKey = "decoration.green-frontier.rock",
        ),
        MapObjectPlacement(
            id = "chapter-1-level-1-marker",
            type = MapObjectType.GAMEPLAY,
            x = 3_600f,
            y = terrainHeightAt(chapter1Level1Terrain.samples, 3_600f),
            assetKey = "prop.green-frontier.marker",
            collision = ObjectCollision.SOLID,
        ),
    ),
    initialCameraAnchor = InitialCameraAnchor.PLAYER_FORTRESS,
    generationMetadata = MapGenerationMetadata(
        seed = 1001L,
        generatorVersion = "level-definition-1",
        authoringVersion = "addLevel-1",
        source = "campaign.chapter-1.level-1",
    ),
)

val chapter1Level1Biome: TerrainBiome = TerrainBiome.GREEN_FRONTIER
