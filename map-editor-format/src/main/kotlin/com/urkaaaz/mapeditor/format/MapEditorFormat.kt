package com.urkaaaz.mapeditor.format

import com.urkaaaz.contracts.*
import com.urkaaaz.mapeditor.domain.EditorDocument
import com.urkaaaz.mapeditor.domain.EditorPhase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.moveTo

const val CURRENT_SCHEMA_VERSION = 1

@Serializable
data class MapDocumentJson(
    val schemaVersion: Int,
    val mapId: String,
    val biome: String,
    val world: WorldJson,
    val terrain: TerrainJson,
    val fortresses: List<FortressJson> = emptyList(),
    val catapults: List<CatapultJson> = emptyList(),
    val protectedZones: List<ProtectedZoneJson> = emptyList(),
    val objects: List<ObjectJson> = emptyList(),
    val metadata: MetadataJson,
)

@Serializable data class WorldJson(val width: Float, val height: Float, val tileSize: Float = 20f)
@Serializable data class TerrainJson(
    val sampleStep: Float,
    val heightSamples: List<SampleJson>,
    val tiles: List<TileJson> = emptyList(),
)
@Serializable data class SampleJson(val x: Float, val height: Float)
@Serializable data class TileJson(
    val tileId: String, val gridX: Int, val gridY: Int, val width: Float, val height: Float,
    val leftProfile: String, val rightProfile: String, val heightSamples: List<Float>,
)
@Serializable data class ProtectedZoneJson(val id: String, val minX: Float, val maxX: Float, val minHeight: Float, val maxHeight: Float)
@Serializable data class FortressJson(val owner: String, val x: Float, val foundationHeight: Float, val halfWidth: Float, val assetKey: String, val protectedZoneId: String)
@Serializable data class CatapultJson(val owner: String, val x: Float, val foundationHeight: Float, val facing: String, val halfWidth: Float, val assetKey: String, val protectedZoneId: String? = null)
@Serializable data class ObjectJson(
    val id: String, val type: String, val x: Float, val y: Float, val scale: Float,
    val rotationDegrees: Float, val assetKey: String, val collision: String, val allowOverlap: Boolean,
    val relicSize: String? = null,
)
@Serializable data class MetadataJson(
    val phase: String, val terrainLocked: Boolean, val seed: Long, val generatorVersion: String,
    val authoringVersion: String, val source: String, val author: String, val fingerprint: String? = null,
)

object MapDocumentCodec {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(document: EditorDocument): String = json.encodeToString(toJson(document))

    fun decode(text: String): EditorDocument {
        val raw = json.decodeFromString<MapDocumentJson>(text)
        require(raw.schemaVersion <= CURRENT_SCHEMA_VERSION) {
            "unsupported future schemaVersion ${raw.schemaVersion}"
        }
        return fromJson(raw)
    }

    fun read(path: Path): EditorDocument = decode(Files.readString(path))

    fun writeDraft(path: Path, document: EditorDocument) {
        atomicWrite(path, encode(document))
    }

    fun writeFinal(path: Path, document: EditorDocument) {
        require(document.phase == EditorPhase.READY_TO_EXPORT) { "document must be ready to export" }
        atomicWrite(path, encode(document))
    }

    private fun atomicWrite(path: Path, content: String) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = path.resolveSibling("${path.fileName}.writing")
        Files.writeString(temporary, content)
        temporary.moveTo(path, overwrite = true)
    }

    private fun toJson(document: EditorDocument): MapDocumentJson {
        val map = document.map
        return MapDocumentJson(
            CURRENT_SCHEMA_VERSION, map.levelId, map.biome,
            WorldJson(map.worldWidth, map.worldHeight),
            TerrainJson(map.terrain.sampleStep, map.terrain.samples.map { SampleJson(it.x, it.height) },
                map.terrainTiles.map { TileJson(it.tileId, it.gridX, it.gridY, it.geometry.widthWorldUnits, it.geometry.heightWorldUnits,
                    it.geometry.leftProfile.name, it.geometry.rightProfile.name, it.geometry.heightSamples) }),
            map.fortresses.map { FortressJson(it.owner.name, it.x, it.foundationHeight, it.halfWidth, it.assetKey, it.protectedZoneId) },
            map.catapults.map { CatapultJson(it.owner.name, it.x, it.foundationHeight, it.facing.name, it.halfWidth, it.assetKey, it.protectedZoneId) },
            map.protectedTerrainZones.map { ProtectedZoneJson(it.id, it.minX, it.maxX, it.minHeight, it.maxHeight) },
            map.objects.map { ObjectJson(it.id, it.type.name, it.x, it.y, it.scale, it.rotationDegrees, it.assetKey, it.collision.name, it.allowOverlap, it.relicSize?.name) },
            MetadataJson(document.phase.name, document.phase != EditorPhase.TERRAIN_EDITING, map.generationMetadata.seed,
                map.generationMetadata.generatorVersion, map.generationMetadata.authoringVersion, map.generationMetadata.source,
                map.generationMetadata.author, map.generationMetadata.fingerprint),
        )
    }

    private fun fromJson(raw: MapDocumentJson): EditorDocument {
        val map = MapDefinition(
            levelId = raw.mapId,
            schemaVersion = raw.schemaVersion,
            worldWidth = raw.world.width,
            worldHeight = raw.world.height,
            terrain = TerrainProfile(raw.terrain.sampleStep, raw.terrain.heightSamples.map { TerrainSample(it.x, it.height) }),
            protectedTerrainZones = raw.protectedZones.map { ProtectedTerrainZone(it.id, it.minX, it.maxX, it.minHeight, it.maxHeight) },
            fortresses = raw.fortresses.map { FortressPlacement(Team.valueOf(it.owner), it.x, it.foundationHeight, it.halfWidth, it.assetKey, protectedZoneId = it.protectedZoneId) },
            catapults = raw.catapults.map { CatapultPlacement(Team.valueOf(it.owner), it.x, it.foundationHeight, Facing.valueOf(it.facing), it.halfWidth, it.assetKey, it.protectedZoneId) },
            objects = raw.objects.map(::toPlacement),
            initialCameraAnchor = InitialCameraAnchor.CENTER,
            generationMetadata = MapGenerationMetadata(raw.metadata.seed, raw.metadata.generatorVersion, raw.metadata.authoringVersion, raw.metadata.source, raw.metadata.author, raw.metadata.fingerprint),
            biome = raw.biome,
            terrainTiles = raw.terrain.tiles.map {
                TerrainTilePlacement(it.tileId, it.gridX, it.gridY, TerrainTileGeometry(it.width, it.height, EdgeProfile.valueOf(it.leftProfile), EdgeProfile.valueOf(it.rightProfile), it.heightSamples))
            },
        )
        return EditorDocument(raw.schemaVersion, map, runCatching { EditorPhase.valueOf(raw.metadata.phase) }.getOrDefault(EditorPhase.TERRAIN_EDITING))
    }

    private fun toPlacement(value: ObjectJson) = MapObjectPlacement(
        value.id, MapObjectType.valueOf(value.type), value.x, value.y, value.scale, value.rotationDegrees, value.assetKey,
        ObjectCollision.valueOf(value.collision), value.allowOverlap, value.relicSize?.let(RelicSize::valueOf),
    )
}
