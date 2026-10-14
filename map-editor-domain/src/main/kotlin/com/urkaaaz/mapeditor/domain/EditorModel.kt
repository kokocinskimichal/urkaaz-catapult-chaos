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
    val deletedDecorationIds: Set<String> = emptySet(),
) {
    init {
        require(schemaVersion > 0)
    }

    fun nextDecorationId(): String {
        var suffix = map.decorations.size + 1
        var candidate = "decoration-$suffix"
        while (candidate in map.decorations.map { it.id } || candidate in deletedDecorationIds) {
            candidate = "decoration-${++suffix}"
        }
        return candidate
    }

    fun withMap(map: MapDefinition, phase: EditorPhase = this.phase): EditorDocument =
        copy(map = map, phase = phase, revision = revision + 1)
}

sealed interface EditorCommand {
    data class SmoothTerrain(val startSample: Int = 0, val endSample: Int = Int.MAX_VALUE) : EditorCommand
    data class SetFoundationHeight(val fortressIndex: Int, val height: Float) : EditorCommand
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
        val relicSize: RelicSize? = null,
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
        override val message = "decoration does not exist: $id"
    }
    data class InvalidValue(override val message: String) : EditorError {
        override val code = "INVALID_VALUE"
    }
}

object EditorReducer {
    fun apply(document: EditorDocument, command: EditorCommand): EditorResult = when (command) {
        is EditorCommand.SmoothTerrain -> editTerrain(document) {
            val samples = document.map.terrain.samples
            val from = command.startSample.coerceAtLeast(1)
            val to = command.endSample.coerceAtMost(samples.lastIndex - 1)
            if (from > to) return@editTerrain EditorResult.Success(document)
            val smoothed = samples.mapIndexed { index, sample ->
                if (index in from..to) sample.copy(
                    height = (samples[index - 1].height + sample.height + samples[index + 1].height) / 3f,
                ) else sample
            }
            EditorResult.Success(document.withMap(document.map.copy(
                terrain = document.map.terrain.copy(samples = smoothed),
            )))
        }
        is EditorCommand.SetFoundationHeight -> editTerrain(document) {
            if (!command.height.isFinite() || command.height !in 1f..(WorldDimensions.HEIGHT - 1f)) {
                return@editTerrain EditorResult.Failure(EditorError.InvalidValue("foundation height is outside the world"))
            }
            val fortress = document.map.fortresses.getOrNull(command.fortressIndex)
                ?: return@editTerrain EditorResult.Failure(EditorError.InvalidValue("fortress index is out of range"))
            val zoneIndex = document.map.protectedTerrainZones.indexOfFirst { it.id == fortress.protectedZoneId }
            if (zoneIndex < 0) return@editTerrain EditorResult.Failure(
                EditorError.InvalidValue("fortress protected zone is missing"),
            )
            val fortresses = document.map.fortresses.toMutableList()
            fortresses[command.fortressIndex] = fortress.copy(foundationHeight = command.height)
            val zones = document.map.protectedTerrainZones.toMutableList()
            zones[zoneIndex] = zones[zoneIndex].copy(minHeight = command.height, maxHeight = command.height)
            val samples = document.map.terrain.samples.map { sample ->
                if (sample.x in zones[zoneIndex].minX..zones[zoneIndex].maxX) {
                    sample.copy(height = command.height)
                } else sample
            }
            EditorResult.Success(document.withMap(document.map.copy(
                terrain = document.map.terrain.copy(samples = samples),
                fortresses = fortresses,
                protectedTerrainZones = zones,
            )))
        }
        is EditorCommand.GenerateRandomTerrain -> editTerrain(document) {
            val phase = Random(command.seed).nextFloat() * (2f * Math.PI.toFloat())
            val zones = document.map.protectedTerrainZones
            val samples = document.map.terrain.samples.map { sample ->
                val foundation = zones.firstOrNull { sample.x in it.minX..it.maxX }
                if (foundation != null) {
                    sample.copy(height = foundation.minHeight)
                } else {
                    val normalizedX = sample.x / WorldDimensions.WIDTH
                    sample.copy(height = (3_300f +
                        180f * kotlin.math.sin(normalizedX * 16f + phase) +
                        70f * kotlin.math.sin(normalizedX * 34f + phase * 0.7f))
                        .coerceIn(500f, WorldDimensions.HEIGHT - 300f))
                }
            }
            EditorResult.Success(document.withMap(document.map.copy(
                terrain = document.map.terrain.copy(samples = samples),
                generationMetadata = MapGenerationMetadata(command.seed, "map-editor"),
            )))
        }
        EditorCommand.LockTerrain -> {
            if (document.phase != EditorPhase.TERRAIN_EDITING) {
                EditorResult.Failure(EditorError.InvalidPhase("terrain is already locked"))
            } else {
                val validation = EditorValidation.validateForEditor(document.map)
                if (!validation.isValid) EditorResult.Failure(
                    EditorError.Validation(validation.errors.joinToString { "${it.path}: ${it.message}" }),
                ) else EditorResult.Success(document.withMap(document.map, EditorPhase.TERRAIN_LOCKED))
            }
        }
        EditorCommand.MarkReadyToExport -> {
            if (document.phase !in setOf(EditorPhase.TERRAIN_LOCKED, EditorPhase.DECORATION_EDITING)) {
                EditorResult.Failure(EditorError.InvalidPhase("terrain must be locked before export"))
            } else {
                val validation = EditorValidation.validateForEditor(document.map)
                if (!validation.isValid) EditorResult.Failure(
                    EditorError.Validation(validation.errors.joinToString { "${it.path}: ${it.message}" }),
                ) else EditorResult.Success(document.withMap(document.map, EditorPhase.READY_TO_EXPORT))
            }
        }
        is EditorCommand.PlaceMapObject -> placeDecoration(document, command)
        is EditorCommand.MoveMapObject -> updateDecoration(document, command.id) {
            it.copy(x = command.x, y = command.y)
        }
        is EditorCommand.RotateMapObject -> updateDecoration(document, command.id) {
            it.copy(rotationDegrees = command.rotationDegrees)
        }
        is EditorCommand.ScaleMapObject -> if (!command.scale.isFinite() || command.scale <= 0f) {
            EditorResult.Failure(EditorError.InvalidValue("scale must be positive and finite"))
        } else updateDecoration(document, command.id) { it.copy(scale = command.scale) }
        is EditorCommand.RemoveMapObject -> {
            if (document.phase !in setOf(EditorPhase.DECORATION_EDITING, EditorPhase.READY_TO_EXPORT)) {
                EditorResult.Failure(EditorError.InvalidPhase("no decoration is editable"))
            } else if (document.map.decorations.none { it.id == command.id }) {
                EditorResult.Failure(EditorError.MissingObject(command.id))
            } else EditorResult.Success(document.withMap(
                document.map.copy(decorations = document.map.decorations.filterNot { it.id == command.id }),
            ).copy(deletedDecorationIds = document.deletedDecorationIds + command.id))
        }
    }

    private fun placeDecoration(
        document: EditorDocument,
        command: EditorCommand.PlaceMapObject,
    ): EditorResult {
        if (document.phase !in setOf(EditorPhase.TERRAIN_LOCKED, EditorPhase.DECORATION_EDITING)) {
            return EditorResult.Failure(EditorError.InvalidPhase("lock terrain before placing decorations"))
        }
        if (command.assetKey.isBlank() || !command.scale.isFinite() || command.scale <= 0f ||
            !command.rotationDegrees.isFinite() || !command.x.isFinite() || !command.y.isFinite()
        ) return EditorResult.Failure(EditorError.InvalidValue("decoration values must be valid"))
        val id = command.id ?: document.nextDecorationId()
        if (id in document.deletedDecorationIds || document.map.decorations.any { it.id == id }) {
            return EditorResult.Failure(EditorError.InvalidValue("decoration id is already used: $id"))
        }
        val decoration = MapDecoration(
            id = id,
            assetKey = command.assetKey,
            x = command.x,
            y = command.y,
            scale = command.scale,
            rotationDegrees = command.rotationDegrees,
            relicSize = command.relicSize,
        )
        return EditorResult.Success(document.withMap(
            document.map.copy(decorations = document.map.decorations + decoration),
            EditorPhase.DECORATION_EDITING,
        ))
    }

    private fun updateDecoration(
        document: EditorDocument,
        id: String,
        update: (MapDecoration) -> MapDecoration,
    ): EditorResult {
        if (document.phase !in setOf(EditorPhase.DECORATION_EDITING, EditorPhase.READY_TO_EXPORT)) {
            return EditorResult.Failure(EditorError.InvalidPhase("terrain must be locked before editing decorations"))
        }
        if (document.map.decorations.none { it.id == id }) return EditorResult.Failure(EditorError.MissingObject(id))
        return EditorResult.Success(document.withMap(document.map.copy(
            decorations = document.map.decorations.map { if (it.id == id) update(it) else it },
        )))
    }

    private fun editTerrain(document: EditorDocument, action: () -> EditorResult): EditorResult =
        if (document.phase != EditorPhase.TERRAIN_EDITING) {
            EditorResult.Failure(EditorError.InvalidPhase("terrain is locked"))
        } else action()
}

class EditorHistory(initial: EditorDocument) {
    private val undoStack = ArrayDeque<EditorDocument>()
    private val redoStack = ArrayDeque<EditorDocument>()
    var current: EditorDocument = initial
        private set

    fun execute(command: EditorCommand): EditorResult =
        when (val result = EditorReducer.apply(current, command)) {
            is EditorResult.Failure -> result
            is EditorResult.Success -> {
                undoStack.addLast(current)
                current = result.document
                redoStack.clear()
                result
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
        val foundationHeight = 3_180f
        val samples = (0..WorldDimensions.WIDTH.toInt() step 40)
            .map { TerrainSample(it.toFloat(), foundationHeight) }
        val zones = listOf(
            ProtectedTerrainZone(
                "blue-foundation", 0f, 1_080f, foundationHeight, foundationHeight,
                CraterPolicy.PRESERVE_FOUNDATION,
            ),
            ProtectedTerrainZone(
                "red-foundation", 6_120f, WorldDimensions.WIDTH, foundationHeight, foundationHeight,
                CraterPolicy.PRESERVE_FOUNDATION,
            ),
        )
        val fortresses = listOf(
            FortressPlacement(Team.BLUE, 490f, foundationHeight, 240f, "fortress.blue", "fortress.default", "blue-foundation"),
            FortressPlacement(Team.RED, 6_710f, foundationHeight, 240f, "fortress.red", "fortress.default", "red-foundation"),
        )
        return EditorDocument(
            schemaVersion = 1,
            map = MapDefinition(
                schemaVersion = 1,
                mapId = mapId,
                biome = biome,
                world = MapWorldDimensions(WorldDimensions.WIDTH.toInt(), WorldDimensions.HEIGHT.toInt()),
                terrain = TerrainProfile(40f, samples),
                protectedTerrainZones = zones,
                fortresses = fortresses,
                decorations = emptyList(),
            ),
        )
    }
}
