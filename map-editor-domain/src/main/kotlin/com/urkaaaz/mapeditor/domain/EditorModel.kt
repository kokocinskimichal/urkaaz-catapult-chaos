package com.urkaaaz.mapeditor.domain

import com.urkaaaz.campaign.MapDefinitionValidator
import com.urkaaaz.contracts.*
import kotlin.random.Random

enum class EditorPhase {
    TERRAIN_EDITING,
    TERRAIN_LOCKED,
    DECORATION_EDITING,
    READY_TO_EXPORT,
}

data class EditorDocument(
    val schemaVersion: Int,
    val map: MapDefinition,
    val phase: EditorPhase = EditorPhase.TERRAIN_EDITING,
    val revision: Long = 0,
    val deletedObjectIds: Set<String> = emptySet(),
) {
    init {
        require(schemaVersion > 0)
        require(map.biome.isNotBlank())
    }

    fun nextObjectId(): String {
        var candidate = "object-${map.objects.size + 1}"
        var suffix = map.objects.size + 1
        while (candidate in map.objects.map { it.id } || candidate in deletedObjectIds) {
            candidate = "object-${suffix++}"
        }
        return candidate
    }

    fun withMap(map: MapDefinition, phase: EditorPhase = this.phase): EditorDocument =
        copy(map = map, phase = phase, revision = revision + 1)
}

sealed interface EditorCommand {
    data class PlaceTerrainTile(
        val tile: TerrainTilePlacement,
    ) : EditorCommand

    data class ReplaceTerrainTile(
        val gridX: Int,
        val gridY: Int,
        val tile: TerrainTilePlacement,
    ) : EditorCommand

    data class SmoothTerrain(
        val startSample: Int = 0,
        val endSample: Int = Int.MAX_VALUE,
    ) : EditorCommand

    data class SetFoundationHeight(
        val fortressIndex: Int,
        val height: Float,
    ) : EditorCommand

    data class GenerateRandomTerrain(val seed: Long) : EditorCommand

    data object LockTerrain : EditorCommand
    data object MarkReadyToExport : EditorCommand

    data class PlaceMapObject(
        val assetKey: String,
        val x: Float,
        val y: Float,
        val scale: Float = 1f,
        val rotationDegrees: Float = 0f,
        val id: String? = null,
    ) : EditorCommand

    data class MoveMapObject(val id: String, val x: Float, val y: Float) : EditorCommand
    data class RotateMapObject(val id: String, val rotationDegrees: Float) : EditorCommand
    data class ScaleMapObject(val id: String, val scale: Float) : EditorCommand
    data class RemoveMapObject(val id: String) : EditorCommand
}

sealed interface EditorResult {
    data class Success(val document: EditorDocument) : EditorResult
    data class Failure(val error: EditorError) : EditorResult
}

sealed interface EditorError {
    val code: String
    val message: String

    data class InvalidPhase(override val message: String) : EditorError {
        override val code = "INVALID_PHASE"
    }
    data class ProtectedArea(override val message: String) : EditorError {
        override val code = "PROTECTED_AREA"
    }
    data class Validation(override val message: String) : EditorError {
        override val code = "VALIDATION"
    }
    data class MissingObject(val id: String) : EditorError {
        override val code = "MISSING_OBJECT"
        override val message = "object does not exist: $id"
    }
    data class InvalidValue(override val message: String) : EditorError {
        override val code = "INVALID_VALUE"
    }
}

object EditorReducer {
    fun apply(document: EditorDocument, command: EditorCommand): EditorResult = when (command) {
        is EditorCommand.PlaceTerrainTile -> terrainEdit(document) {
            if (isProtected(document.map, command.tile.gridX * command.tile.geometry.widthWorldUnits,
                    command.tile.gridY * command.tile.geometry.heightWorldUnits)
            ) return@terrainEdit EditorResult.Failure(EditorError.ProtectedArea("terrain tile intersects protected zone"))
            val tiles = document.map.terrainTiles.filterNot {
                it.gridX == command.tile.gridX && it.gridY == command.tile.gridY
            } + command.tile
            val terrain = applyTileToTerrain(document.map.terrain, command.tile)
            EditorResult.Success(document.withMap(document.map.copy(
                terrain = terrain,
                terrainTiles = tiles.sortedWith(compareBy({ it.gridY }, { it.gridX })),
            )))
        }
        is EditorCommand.ReplaceTerrainTile -> terrainEdit(document) {
            apply(document, EditorCommand.PlaceTerrainTile(command.tile.copy(gridX = command.gridX, gridY = command.gridY)))
        }
        is EditorCommand.SmoothTerrain -> terrainEdit(document) {
            val samples = document.map.terrain.samples
            val from = command.startSample.coerceAtLeast(1)
            val to = command.endSample.coerceAtMost(samples.lastIndex - 1)
            if (from > to) return@terrainEdit EditorResult.Success(document)
            val smoothed = samples.mapIndexed { index, sample ->
                if (index in from..to) sample.copy(height = (samples[index - 1].height + samples[index].height + samples[index + 1].height) / 3f)
                else sample
            }
            EditorResult.Success(document.withMap(document.map.copy(terrain = TerrainProfile(samples = smoothed))))
        }
        is EditorCommand.SetFoundationHeight -> terrainEdit(document) {
            val fortress = document.map.fortresses.getOrNull(command.fortressIndex)
                ?: return@terrainEdit EditorResult.Failure(EditorError.InvalidValue("fortress index is out of range"))
            val updated = document.map.fortresses.toMutableList()
            updated[command.fortressIndex] = fortress.copy(foundationHeight = command.height)
            EditorResult.Success(document.withMap(document.map.copy(fortresses = updated)))
        }
        is EditorCommand.GenerateRandomTerrain -> terrainEdit(document) {
            val random = Random(command.seed)
            var terrain = document.map.terrain
            val generatedTiles = buildList {
                for (gridX in 0 until (document.map.worldWidth / TerrainTilePreset.DEFAULT_TILE_SIZE).toInt()) {
                    val worldX = gridX * TerrainTilePreset.DEFAULT_TILE_SIZE
                    val preset = if (document.map.protectedTerrainZones.any {
                            worldX <= it.maxX && worldX + TerrainTilePreset.DEFAULT_TILE_SIZE >= it.minX
                        }) {
                        TerrainTileCatalog.byId("flat")
                    } else {
                        random.nextInt(100).let { roll ->
                            when {
                                roll < 40 -> TerrainTileCatalog.byId("flat")
                                roll < 55 -> TerrainTileCatalog.byId("gentle-up")
                                roll < 70 -> TerrainTileCatalog.byId("gentle-down")
                                roll < 80 -> TerrainTileCatalog.byId("slope-up")
                                roll < 90 -> TerrainTileCatalog.byId("slope-down")
                                roll < 95 -> TerrainTileCatalog.byId("ridge")
                                else -> TerrainTileCatalog.byId("valley")
                            }
                        }
                    }
                    val foundationHeight = document.map.protectedTerrainZones
                        .firstOrNull {
                            worldX <= it.maxX && worldX + TerrainTilePreset.DEFAULT_TILE_SIZE >= it.minX
                        }
                        ?.let { zone ->
                            document.map.fortresses.firstOrNull { it.protectedZoneId == zone.id }?.foundationHeight
                        }
                    val baseHeight = foundationHeight ?: terrainHeightAt(terrain, worldX)
                    val geometry = preset.geometry().copy(
                        heightSamples = preset.heightSamples.map { (baseHeight + it * TerrainTilePreset.DEFAULT_TILE_SIZE)
                            .coerceIn(1f, document.map.worldHeight - 1f) },
                    )
                    val tile = TerrainTilePlacement(
                        tileId = "terrain-${document.map.biome}-${preset.id}-$gridX",
                        gridX = gridX,
                        gridY = (baseHeight / TerrainTilePreset.DEFAULT_TILE_SIZE).toInt(),
                        geometry = geometry,
                    )
                    terrain = applyTileToTerrain(terrain, tile)
                    add(tile)
                }
            }
            EditorResult.Success(document.withMap(document.map.copy(terrain = terrain, terrainTiles = generatedTiles)))
        }
        EditorCommand.LockTerrain -> {
            if (document.phase != EditorPhase.TERRAIN_EDITING) EditorResult.Failure(EditorError.InvalidPhase("terrain is already locked"))
            else {
                val validation = EditorValidation.validateForEditor(document.map)
                if (!validation.isValid) EditorResult.Failure(EditorError.Validation(validation.errors.joinToString { it.message }))
                else EditorResult.Success(document.withMap(document.map, EditorPhase.TERRAIN_LOCKED))
            }
        }
        EditorCommand.MarkReadyToExport -> {
            if (document.phase !in setOf(EditorPhase.TERRAIN_LOCKED, EditorPhase.DECORATION_EDITING)) {
                EditorResult.Failure(EditorError.InvalidPhase("terrain must be locked before export"))
            } else {
                val validation = EditorValidation.validateForEditor(document.map)
                if (!validation.isValid) EditorResult.Failure(EditorError.Validation(validation.errors.joinToString { it.message }))
                else EditorResult.Success(document.withMap(document.map, EditorPhase.READY_TO_EXPORT))
            }
        }
        is EditorCommand.PlaceMapObject -> {
            if (document.phase !in setOf(EditorPhase.TERRAIN_LOCKED, EditorPhase.DECORATION_EDITING)) {
                EditorResult.Failure(EditorError.InvalidPhase("lock terrain before placing decorations"))
            } else if (command.assetKey.isBlank() || command.scale <= 0f) {
                EditorResult.Failure(EditorError.InvalidValue("asset key and scale must be valid"))
            } else if (isProtected(document.map, command.x, command.y)) {
                EditorResult.Failure(EditorError.ProtectedArea("decoration intersects protected zone"))
            } else {
                val id = command.id ?: document.nextObjectId()
                if (id in document.deletedObjectIds || document.map.objects.any { it.id == id }) {
                    EditorResult.Failure(EditorError.InvalidValue("object id is already used: $id"))
                } else {
                    val placement = MapObjectPlacement(id, MapObjectType.DECORATION, command.x, command.y,
                        command.scale, command.rotationDegrees, command.assetKey)
                    val candidate = document.map.copy(objects = document.map.objects + placement)
                    EditorResult.Success(document.withMap(candidate, EditorPhase.DECORATION_EDITING))
                }
            }
        }
        is EditorCommand.MoveMapObject -> {
            if (command.x !in 0f..document.map.worldWidth || command.y !in 0f..document.map.worldHeight) {
                EditorResult.Failure(EditorError.InvalidValue("object must remain inside the world"))
            } else if (isProtected(document.map, command.x, command.y)) {
                EditorResult.Failure(EditorError.ProtectedArea("object intersects protected zone"))
            } else {
                updateObject(document, command.id) { it.copy(x = command.x, y = command.y) }
            }
        }
        is EditorCommand.RotateMapObject -> updateObject(document, command.id) { it.copy(rotationDegrees = command.rotationDegrees) }
        is EditorCommand.ScaleMapObject -> if (command.scale <= 0f) EditorResult.Failure(EditorError.InvalidValue("scale must be positive"))
        else updateObject(document, command.id) { it.copy(scale = command.scale) }
        is EditorCommand.RemoveMapObject -> {
            if (document.phase !in setOf(EditorPhase.DECORATION_EDITING, EditorPhase.READY_TO_EXPORT)) EditorResult.Failure(EditorError.InvalidPhase("no decoration is editable"))
            else if (document.map.objects.none { it.id == command.id }) EditorResult.Failure(EditorError.MissingObject(command.id))
            else EditorResult.Success(document.withMap(document.map.copy(objects = document.map.objects.filterNot { it.id == command.id }))
                .copy(deletedObjectIds = document.deletedObjectIds + command.id))
        }
    }

    private fun terrainEdit(document: EditorDocument, block: () -> EditorResult): EditorResult =
        if (document.phase != EditorPhase.TERRAIN_EDITING) EditorResult.Failure(EditorError.InvalidPhase("terrain is locked"))
        else block()

    private fun updateObject(document: EditorDocument, id: String, update: (MapObjectPlacement) -> MapObjectPlacement): EditorResult {
        if (document.phase !in setOf(EditorPhase.DECORATION_EDITING, EditorPhase.READY_TO_EXPORT)) {
            return EditorResult.Failure(EditorError.InvalidPhase("terrain must be locked before editing objects"))
        }
        if (document.map.objects.none { it.id == id }) return EditorResult.Failure(EditorError.MissingObject(id))
        val candidate = document.map.copy(
            objects = document.map.objects.map { if (it.id == id) update(it) else it },
        )
        return EditorResult.Success(document.withMap(candidate))
    }

    private fun isProtected(map: MapDefinition, x: Float, y: Float): Boolean =
        map.protectedTerrainZones.any { x in it.minX..it.maxX && y in it.minHeight..it.maxHeight }

    private fun applyTileToTerrain(
        terrain: TerrainProfile,
        tile: TerrainTilePlacement,
    ): TerrainProfile {
        val startX = tile.gridX * tile.geometry.widthWorldUnits
        val endX = startX + tile.geometry.widthWorldUnits
        val updated = terrain.samples.toMutableList()
        tile.geometry.heightSamples.forEachIndexed { index, height ->
            val ratio = if (tile.geometry.heightSamples.size == 1) 0f
            else index.toFloat() / (tile.geometry.heightSamples.lastIndex)
            val targetX = startX + (endX - startX) * ratio
            val nearest = updated.indices.minByOrNull { kotlin.math.abs(updated[it].x - targetX) } ?: return@forEachIndexed
            updated[nearest] = updated[nearest].copy(height = height)
        }
        return terrain.copy(samples = updated)
    }

    private fun terrainHeightAt(terrain: TerrainProfile, x: Float): Float {
        val left = terrain.samples.lastOrNull { it.x <= x } ?: terrain.samples.first()
        val right = terrain.samples.firstOrNull { it.x >= x } ?: terrain.samples.last()
        if (left.x == right.x) return left.height
        val ratio = (x - left.x) / (right.x - left.x)
        return left.height + (right.height - left.height) * ratio
    }
}

class EditorHistory(initial: EditorDocument) {
    private val undoStack = ArrayDeque<EditorDocument>()
    private val redoStack = ArrayDeque<EditorDocument>()
    var current: EditorDocument = initial
        private set

    fun execute(command: EditorCommand): EditorResult {
        return when (val result = EditorReducer.apply(current, command)) {
            is EditorResult.Failure -> result
            is EditorResult.Success -> {
                undoStack.addLast(current)
                current = result.document
                redoStack.clear()
                result
            }
        }
    }

    fun undo(): EditorDocument? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        return current
    }

    fun redo(): EditorDocument? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        return current
    }
}

object EditorDocumentFactory {
    fun newMap(mapId: String, biome: String): EditorDocument {
        val ground = 3648f
        val samples = (0..WorldDimensions.WIDTH.toInt() step TerrainTilePreset.DEFAULT_TILE_SIZE.toInt())
            .map { TerrainSample(it.toFloat(), ground) }
        val zones = listOf(
            ProtectedTerrainZone("foundation-blue", 300f, 680f, ground - 1f, ground + 1f),
            ProtectedTerrainZone("foundation-red", 6520f, 6900f, ground - 1f, ground + 1f),
        )
        val fortresses = listOf(
            FortressPlacement(Team.BLUE, 490f, ground, assetKey = "fortress.blue", protectedZoneId = "foundation-blue"),
            FortressPlacement(Team.RED, 6710f, ground, assetKey = "fortress.red", protectedZoneId = "foundation-red"),
        )
        val catapults = listOf(
            CatapultPlacement(Team.BLUE, 450f, ground, Facing.RIGHT, assetKey = "catapult.blue", protectedZoneId = "foundation-blue"),
            CatapultPlacement(Team.RED, 6750f, ground, Facing.LEFT, assetKey = "catapult.red", protectedZoneId = "foundation-red"),
        )
        return EditorDocument(
            schemaVersion = 1,
            map = MapDefinition(
                levelId = mapId,
                schemaVersion = 1,
                worldWidth = WorldDimensions.WIDTH,
                worldHeight = WorldDimensions.HEIGHT,
                terrain = TerrainProfile(
                    sampleStep = TerrainTilePreset.DEFAULT_TILE_SIZE,
                    samples = samples,
                ),
                protectedTerrainZones = zones,
                fortresses = fortresses,
                catapults = catapults,
                objects = emptyList(),
                initialCameraAnchor = InitialCameraAnchor.CENTER,
                generationMetadata = MapGenerationMetadata(0L, "editor", "map-editor-1", "map-editor"),
                biome = biome,
            ),
        )
    }
}
