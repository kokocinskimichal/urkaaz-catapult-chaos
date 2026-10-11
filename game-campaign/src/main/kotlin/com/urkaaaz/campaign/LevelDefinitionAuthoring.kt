package com.urkaaaz.campaign

import com.urkaaaz.contracts.CatapultPlacement
import com.urkaaaz.contracts.FortressPlacement
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapGenerationMetadata
import com.urkaaaz.contracts.MapObjectPlacement
import com.urkaaaz.contracts.ProtectedTerrainZone
import com.urkaaaz.contracts.TerrainProfile
import com.urkaaaz.contracts.TerrainSample

data class LevelAuthoringRequest(
    val levelId: String,
    val displayName: String,
    val biome: TerrainBiome,
    val chapter: Int,
    val levelNumber: Int,
    val worldWidth: Float,
    val worldHeight: Float,
    val terrainSamples: List<TerrainSample>,
    val protectedTerrainZones: List<ProtectedTerrainZone>,
    val fortresses: List<FortressPlacement>,
    val catapults: List<CatapultPlacement>,
    val objects: List<MapObjectPlacement>,
    val initialCameraAnchor: InitialCameraAnchor,
    val seed: Long,
    val generatorVersion: String,
    val authoringVersion: String,
    val source: String = "level-authoring",
    val notes: String = "",
) {
    init {
        require(levelId.isNotBlank())
        require(displayName.isNotBlank())
        require(chapter > 0)
        require(levelNumber > 0)
    }
}

object LevelDefinitionBuilder {
    fun build(request: LevelAuthoringRequest): MapDefinition =
        MapDefinition(
            levelId = request.levelId,
            schemaVersion = 1,
            worldWidth = request.worldWidth,
            worldHeight = request.worldHeight,
            terrain = TerrainProfile(samples = request.terrainSamples),
            protectedTerrainZones = request.protectedTerrainZones,
            fortresses = request.fortresses,
            catapults = request.catapults,
            objects = request.objects,
            initialCameraAnchor = request.initialCameraAnchor,
            generationMetadata = MapGenerationMetadata(
                seed = request.seed,
                generatorVersion = request.generatorVersion,
                authoringVersion = request.authoringVersion,
                source = request.source,
            ),
        )

    fun buildValidated(request: LevelAuthoringRequest): MapDefinition =
        MapDefinitionValidator.requireValid(build(request))
}
