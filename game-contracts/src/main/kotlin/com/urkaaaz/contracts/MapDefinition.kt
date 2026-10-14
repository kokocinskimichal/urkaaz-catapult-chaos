package com.urkaaaz.contracts

data class MapDefinition(
    val levelId: String,
    val schemaVersion: Int,
    val worldWidth: Float,
    val worldHeight: Float,
    val terrain: TerrainProfile,
    val protectedTerrainZones: List<ProtectedTerrainZone>,
    val fortresses: List<FortressPlacement>,
    val catapults: List<CatapultPlacement>,
    val objects: List<MapObjectPlacement>,
    val initialCameraAnchor: InitialCameraAnchor,
    val generationMetadata: MapGenerationMetadata,
    val biome: String = "green_frontier",
    val terrainTiles: List<TerrainTilePlacement> = emptyList(),
) {
    init {
        require(levelId.isNotBlank())
        require(schemaVersion > 0)
        require(worldWidth > 0f)
        require(worldHeight > 0f)
    }

}

data class TerrainTilePlacement(
    val tileId: String,
    val gridX: Int,
    val gridY: Int,
    val geometry: TerrainTileGeometry,
)

data class TerrainTileGeometry(
    val widthWorldUnits: Float,
    val heightWorldUnits: Float,
    val leftProfile: EdgeProfile,
    val rightProfile: EdgeProfile,
    val heightSamples: List<Float>,
)

enum class EdgeProfile {
    FLAT,
    SLOPE_UP,
    SLOPE_DOWN,
    RIDGE,
    VALLEY,
}

data class TerrainProfile(
    val sampleStep: Float = 40f,
    val samples: List<TerrainSample>,
) {
    init {
        require(sampleStep > 0f)
        require(samples.isNotEmpty())
    }
}

data class TerrainSample(
    val x: Float,
    val height: Float,
)

data class ProtectedTerrainZone(
    val id: String,
    val minX: Float,
    val maxX: Float,
    val minHeight: Float,
    val maxHeight: Float,
    val craterPolicy: CraterPolicy = CraterPolicy.PRESERVE_FOUNDATION,
) {
    init {
        require(id.isNotBlank())
        require(minX <= maxX)
        require(minHeight <= maxHeight)
    }
}

enum class CraterPolicy {
    PRESERVE_FOUNDATION,
}

data class FortressPlacement(
    val owner: Team,
    val x: Float,
    val foundationHeight: Float,
    val halfWidth: Float = WorldScale.FORTRESS_HALF_WIDTH,
    val assetKey: String,
    val hitboxProfile: String = "fortress.default",
    val protectedZoneId: String,
) {
    init {
        require(halfWidth > 0f)
    }
}

data class CatapultPlacement(
    val owner: Team,
    val x: Float,
    val foundationHeight: Float,
    val facing: Facing,
    val halfWidth: Float = 72f,
    val assetKey: String,
    val protectedZoneId: String? = null,
) {
    init {
        require(halfWidth > 0f)
    }
}

enum class Facing {
    LEFT,
    RIGHT,
}

data class MapObjectPlacement(
    val id: String,
    val type: MapObjectType,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val assetKey: String,
    val collision: ObjectCollision = ObjectCollision.NONE,
    val allowOverlap: Boolean = false,
    val relicSize: RelicSize? = null,
) {
    init {
        require(id.isNotBlank())
        require(scale > 0f)
        require(type == MapObjectType.DECORATION || relicSize == null)
    }
}

enum class RelicSize(
    val halfWidth: Float,
    val halfHeight: Float,
    val renderSize: Float,
) {
    SMALL(56f, 40f, 76f),
    MEDIUM(96f, 72f, 112f),
    LARGE(160f, 120f, 176f),
}

enum class MapObjectType {
    DECORATION,
    GAMEPLAY,
}

enum class ObjectCollision {
    NONE,
    SOLID,
}

enum class InitialCameraAnchor {
    PLAYER_FORTRESS,
    ENEMY_FORTRESS,
    CENTER,
}

data class MapGenerationMetadata(
    val seed: Long,
    val generatorVersion: String,
    val authoringVersion: String,
    val source: String,
    val author: String = "Urkaaaz",
    val fingerprint: String? = null,
) {
    init {
        require(generatorVersion.isNotBlank())
        require(authoringVersion.isNotBlank())
        require(source.isNotBlank())
    }
}
