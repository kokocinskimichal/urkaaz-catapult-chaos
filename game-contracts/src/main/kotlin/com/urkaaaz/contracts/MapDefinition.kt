package com.urkaaaz.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MapDefinition(
    val schemaVersion: Int,
    val mapId: String,
    val biome: String,
    val world: MapWorldDimensions,
    val terrain: TerrainProfile,
    @SerialName("protectedZones")
    val protectedTerrainZones: List<ProtectedTerrainZone>,
    val fortresses: List<FortressPlacement>,
    val decorations: List<MapDecoration>,
    val initialCameraAnchor: InitialCameraAnchor = InitialCameraAnchor.PLAYER_FORTRESS,
    val initialCameraZoom: Float = 1f,
    val generationMetadata: MapGenerationMetadata? = null,
)

@Serializable
enum class InitialCameraAnchor {
    PLAYER_FORTRESS,
    ENEMY_FORTRESS,
    CENTER,
}

@Serializable
data class MapWorldDimensions(
    val width: Int,
    val height: Int,
)

@Serializable
data class TerrainProfile(
    val sampleStep: Float,
    @SerialName("heightSamples")
    val samples: List<TerrainSample>,
)

@Serializable
data class TerrainSample(
    val x: Float,
    val height: Float,
)

@Serializable
data class ProtectedTerrainZone(
    val id: String,
    val minX: Float,
    val maxX: Float,
    val minHeight: Float,
    val maxHeight: Float,
    val craterPolicy: CraterPolicy,
)

@Serializable
enum class CraterPolicy {
    PRESERVE_FOUNDATION,
}

@Serializable
data class FortressPlacement(
    val owner: Team,
    val x: Float,
    val foundationHeight: Float,
    val halfWidth: Float,
    val assetKey: String,
    val hitboxProfile: String,
    val protectedZoneId: String,
)

@Serializable
data class MapDecoration(
    val id: String,
    val assetKey: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val relicSize: RelicSize? = null,
)

@Serializable
enum class RelicSize(
    val halfWidth: Float,
    val halfHeight: Float,
    val renderSize: Float,
) {
    SMALL(56f, 40f, 76f),
    MEDIUM(96f, 72f, 112f),
    LARGE(160f, 120f, 176f),
}

@Serializable
data class MapGenerationMetadata(
    val seed: Long,
    val generatorVersion: String,
    val authoringVersion: String? = null,
)
