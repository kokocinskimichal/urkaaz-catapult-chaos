package com.urkaaaz.campaign

import com.urkaaaz.assets.AssetCatalog
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapDecoration
import com.urkaaaz.contracts.RelicSize
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.TerrainBiome
import com.urkaaaz.contracts.WorldDimensions
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class MapValidationResult(val errors: List<MapValidationError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class MapValidationError(val path: String, val message: String)

object MapDefinitionValidator {
    const val TERRAIN_SAMPLE_STEP = 40f
    const val MAX_TERRAIN_DELTA = 200f
    const val FOUNDATION_HEIGHT_TOLERANCE = 80f
    const val FLAT_TERRAIN_HEIGHT_TOLERANCE = 1f
    private val assetCatalog = AssetCatalog()

    fun validate(definition: MapDefinition): MapValidationResult {
        val errors = mutableListOf<MapValidationError>()
        if (definition.schemaVersion != 1) {
            errors += MapValidationError("schemaVersion", "only schema version 1 is supported")
        }
        if (definition.mapId.isBlank()) errors += MapValidationError("mapId", "map id must not be blank")
        if (TerrainBiome.fromMapId(definition.biome) == null) {
            errors += MapValidationError("biome", "unsupported biome '${definition.biome}'")
        } else if (definition.biome !in assetCatalog.availableBiomes()) {
            errors += MapValidationError("biome", "no bundled terrain assets are registered for '${definition.biome}'")
        }
        if (definition.world.width.toFloat() != WorldDimensions.WIDTH) {
            errors += MapValidationError("world.width", "must be ${WorldDimensions.WIDTH.toInt()}")
        }
        if (definition.world.height.toFloat() != WorldDimensions.HEIGHT) {
            errors += MapValidationError("world.height", "must be ${WorldDimensions.HEIGHT.toInt()}")
        }
        validateTerrain(definition, errors)
        validateZones(definition, errors)
        validateFortresses(definition, errors)
        validateDecorations(definition, errors)
        return MapValidationResult(errors)
    }

    fun requireValid(definition: MapDefinition): MapDefinition {
        val result = validate(definition)
        require(result.isValid) { result.errors.joinToString("; ") { "${it.path}: ${it.message}" } }
        return definition
    }

    private fun validateTerrain(definition: MapDefinition, errors: MutableList<MapValidationError>) {
        val samples = definition.terrain.samples
        if (definition.terrain.sampleStep !in setOf(20f, TERRAIN_SAMPLE_STEP)) {
            errors += MapValidationError("terrain.sampleStep", "must be 20 or $TERRAIN_SAMPLE_STEP")
        }
        if (samples.isEmpty()) {
            errors += MapValidationError("terrain.heightSamples", "must contain terrain samples")
            return
        }
        samples.forEachIndexed { index, sample ->
            if (!sample.x.isFinite() || !sample.height.isFinite()) {
                errors += MapValidationError("terrain.heightSamples[$index]", "coordinates must be finite")
            }
        }
        samples.zipWithNext().forEachIndexed { index, (left, right) ->
            if (right.x - left.x != definition.terrain.sampleStep) {
                errors += MapValidationError(
                    "terrain.heightSamples[${index + 1}].x",
                    "sample spacing must be ${definition.terrain.sampleStep}",
                )
            }
            if (abs(right.height - left.height) > MAX_TERRAIN_DELTA) {
                errors += MapValidationError(
                    "terrain.heightSamples[${index + 1}].height",
                    "terrain slope exceeds $MAX_TERRAIN_DELTA world units",
                )
            }
        }
        if (samples.first().x != 0f || samples.last().x != definition.world.width.toFloat()) {
            errors += MapValidationError("terrain.heightSamples", "must cover x=0 through x=${definition.world.width}")
        }
        if (samples.any { it.x !in 0f..definition.world.width.toFloat() || it.height !in 0f..definition.world.height.toFloat() }) {
            errors += MapValidationError("terrain.heightSamples", "all samples must be inside world bounds")
        }
    }

    private fun validateZones(definition: MapDefinition, errors: MutableList<MapValidationError>) {
        val zoneIds = definition.protectedTerrainZones.map { it.id }
        if (zoneIds.size != zoneIds.toSet().size) {
            errors += MapValidationError("protectedZones", "zone ids must be unique")
        }
        definition.protectedTerrainZones.forEachIndexed { index, zone ->
            val path = "protectedZones[$index]"
            if (zone.id.isBlank()) errors += MapValidationError("$path.id", "must not be blank")
            if (zone.minX < 0f || zone.maxX > definition.world.width || zone.minX >= zone.maxX ||
                zone.minHeight < 0f || zone.maxHeight > definition.world.height || zone.minHeight > zone.maxHeight
            ) {
                errors += MapValidationError(path, "protected zone bounds are invalid")
            }
        }
    }

    private fun validateFortresses(definition: MapDefinition, errors: MutableList<MapValidationError>) {
        if (definition.fortresses.size != 2 ||
            definition.fortresses.map { it.owner }.toSet() != setOf(Team.BLUE, Team.RED)
        ) {
            errors += MapValidationError("fortresses", "exactly one fortress per team is required")
        }
        val zoneIds = definition.protectedTerrainZones.map { it.id }.toSet()
        definition.fortresses.forEachIndexed { index, fortress ->
            val path = "fortresses[$index]"
            if (fortress.assetKey != "fortress.${fortress.owner.name.lowercase()}") {
                errors += MapValidationError("$path.assetKey", "must match the fortress owner")
            }
            if (fortress.hitboxProfile.isBlank()) errors += MapValidationError("$path.hitboxProfile", "must not be blank")
            if (!fortress.x.isFinite() || fortress.x !in 0f..definition.world.width.toFloat() ||
                !fortress.foundationHeight.isFinite() ||
                fortress.foundationHeight !in 0f..definition.world.height.toFloat() ||
                !fortress.halfWidth.isFinite() || fortress.halfWidth <= 0f
            ) {
                errors += MapValidationError(path, "fortress placement is outside world bounds")
            }
            if (fortress.protectedZoneId !in zoneIds) {
                errors += MapValidationError("$path.protectedZoneId", "must reference an existing protected zone")
            }
            val zone = definition.protectedTerrainZones.firstOrNull { it.id == fortress.protectedZoneId }
            if (zone != null &&
                (zone.minX > fortress.x - fortress.halfWidth || zone.maxX < fortress.x + fortress.halfWidth ||
                    fortress.foundationHeight !in zone.minHeight..zone.maxHeight)
            ) {
                errors += MapValidationError("$path.protectedZoneId", "zone must cover fortress foundation")
            }
            validateFoundation(definition, fortress.x, fortress.foundationHeight, path, errors)
        }
    }

    private fun validateFoundation(
        definition: MapDefinition,
        x: Float,
        foundationHeight: Float,
        path: String,
        errors: MutableList<MapValidationError>,
    ) {
        val samples = definition.terrain.samples.filter {
            it.x in (x - FOUNDATION_HEIGHT_TOLERANCE)..(x + FOUNDATION_HEIGHT_TOLERANCE)
        }
        if (samples.size < 3) {
            errors += MapValidationError(path, "foundation transition needs at least three terrain samples")
            return
        }
        val min = samples.minOf { it.height }
        val max = samples.maxOf { it.height }
        if (max - min > FOUNDATION_HEIGHT_TOLERANCE) {
            errors += MapValidationError(path, "foundation terrain must be flat")
        }
        if (foundationHeight !in (min - FOUNDATION_HEIGHT_TOLERANCE)..(max + FOUNDATION_HEIGHT_TOLERANCE)) {
            errors += MapValidationError("$path.foundationHeight", "foundation height is outside the allowed range")
        }
    }

    private fun validateDecorations(definition: MapDefinition, errors: MutableList<MapValidationError>) {
        val ids = definition.decorations.map { it.id }
        if (ids.size != ids.toSet().size) errors += MapValidationError("decorations", "decoration ids must be unique")
        if (definition.decorations.count { it.relicSize == RelicSize.LARGE } > 1) {
            errors += MapValidationError("decorations", "at most one LARGE relic is allowed per map")
        }
        definition.decorations.forEachIndexed { index, decoration ->
            val path = "decorations[$index]"
            validateDecoration(definition, decoration, path, errors)
        }
    }

    private fun validateDecoration(
        definition: MapDefinition,
        decoration: MapDecoration,
        path: String,
        errors: MutableList<MapValidationError>,
    ) {
        if (decoration.id.isBlank()) errors += MapValidationError("$path.id", "must not be blank")
        if (!assetCatalog.isKnownMapDecoration(decoration.assetKey, definition.biome)) {
            errors += MapValidationError("$path.assetKey", "unknown decoration asset '${decoration.assetKey}' for ${definition.biome}")
        }
        if (!decoration.x.isFinite() || !decoration.y.isFinite() ||
            !decoration.scale.isFinite() || decoration.scale <= 0f ||
            !decoration.rotationDegrees.isFinite()
        ) {
            errors += MapValidationError(path, "position and transform values must be finite and scale must be positive")
            return
        }
        if (decoration.relicSize == null) {
            if (decoration.x !in 0f..definition.world.width.toFloat() ||
                decoration.y !in 0f..definition.world.height.toFloat()
            ) errors += MapValidationError("$path.position", "decoration must be inside world bounds")
            val surface = terrainHeightAt(definition, decoration.x)
            if (abs(decoration.y - surface) > 1.5f) {
                errors += MapValidationError("$path.y", "grounded decoration bottom anchor must match terrain")
            }
            val left = terrainHeightAt(definition, decoration.x - TERRAIN_SAMPLE_STEP)
            val right = terrainHeightAt(definition, decoration.x + TERRAIN_SAMPLE_STEP)
            if (abs(left - surface) > FLAT_TERRAIN_HEIGHT_TOLERANCE ||
                abs(right - surface) > FLAT_TERRAIN_HEIGHT_TOLERANCE
            ) errors += MapValidationError("$path.position", "grounded decoration must be on flat terrain")
            return
        }
        validateRelic(definition, decoration, path, errors)
    }

    private fun validateRelic(
        definition: MapDefinition,
        relic: MapDecoration,
        path: String,
        errors: MutableList<MapValidationError>,
    ) {
        if (!relic.assetKey.startsWith("relic.${definition.biome}.small.")) {
            errors += MapValidationError("$path.assetKey", "relic asset must belong to its biome and size")
        }
        val size = relic.relicSize ?: return
        val radians = Math.toRadians(relic.rotationDegrees.toDouble())
        val halfWidth = abs(size.halfWidth * relic.scale * cos(radians).toFloat()) +
            abs(size.halfHeight * relic.scale * sin(radians).toFloat())
        val halfHeight = abs(size.halfWidth * relic.scale * sin(radians).toFloat()) +
            abs(size.halfHeight * relic.scale * cos(radians).toFloat())
        val left = relic.x - halfWidth
        val right = relic.x + halfWidth
        val top = relic.y - halfHeight
        val bottom = relic.y + halfHeight
        if (left < 0f || right > definition.world.width || top < 0f || bottom > definition.world.height) {
            errors += MapValidationError("$path.footprint", "relic footprint must stay inside world bounds")
        }
        if (top < maxTerrainHeightInRange(definition, left, right)) {
            errors += MapValidationError("$path.footprint", "relic footprint must be fully inside soil")
        }
    }

    private fun terrainHeightAt(definition: MapDefinition, x: Float): Float {
        val samples = definition.terrain.samples
        val clamped = x.coerceIn(samples.first().x, samples.last().x)
        val index = samples.indexOfLast { it.x <= clamped }.coerceAtLeast(0)
        val left = samples[index]
        val right = samples.getOrNull(index + 1) ?: left
        if (left.x == right.x) return left.height
        return left.height + (right.height - left.height) * ((clamped - left.x) / (right.x - left.x))
    }

    private fun maxTerrainHeightInRange(definition: MapDefinition, minX: Float, maxX: Float): Float =
        definition.terrain.samples.filter { it.x in minX..maxX }.maxOfOrNull { it.height }
            ?: maxOf(terrainHeightAt(definition, minX), terrainHeightAt(definition, maxX))
}
