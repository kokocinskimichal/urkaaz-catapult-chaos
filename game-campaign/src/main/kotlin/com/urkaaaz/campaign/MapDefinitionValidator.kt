package com.urkaaaz.campaign

import com.urkaaaz.contracts.CatapultPlacement
import com.urkaaaz.contracts.FortressPlacement
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapObjectPlacement
import com.urkaaaz.contracts.MapObjectType
import com.urkaaaz.contracts.ObjectCollision
import com.urkaaaz.contracts.RelicSize
import com.urkaaaz.contracts.TerrainSample
import com.urkaaaz.contracts.Team
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class MapValidationResult(
    val errors: List<MapValidationError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

sealed interface MapValidationError {
    val path: String
    val message: String

    data class TerrainSlopeExceeded(
        override val path: String,
        val left: TerrainSample,
        val right: TerrainSample,
        val maximumDelta: Float,
    ) : MapValidationError {
        override val message = "terrain slope exceeds $maximumDelta world units"
    }

    data class FoundationNotFlat(
        override val path: String,
        val minimumHeight: Float,
        val maximumHeight: Float,
    ) : MapValidationError {
        override val message = "foundation transition is not flat"
    }

    data class FoundationOutsideAllowedHeight(
        override val path: String,
        val foundationHeight: Float,
        val minimumAllowed: Float,
        val maximumAllowed: Float,
    ) : MapValidationError {
        override val message = "foundation height is outside the allowed range"
    }

    data class FoundationTransitionTooShort(
        override val path: String,
        val sampleCount: Int,
    ) : MapValidationError {
        override val message = "foundation transition needs at least three samples"
    }

    data class DecorationOnFoundation(
        override val path: String,
    ) : MapValidationError {
        override val message = "decoration overlaps a foundation or its margin"
    }

    data class DecorationOnSteepSlope(
        override val path: String,
    ) : MapValidationError {
        override val message = "decoration is placed on a steep slope"
    }

    data class ObjectOverlapsAnotherObject(
        override val path: String,
        val otherId: String,
    ) : MapValidationError {
        override val message = "object overlaps another object: $otherId"
    }

    data class MissingAssetKey(
        override val path: String,
    ) : MapValidationError {
        override val message = "asset key must not be blank"
    }

    data class CatapultOutsideFoundation(
        override val path: String,
    ) : MapValidationError {
        override val message = "catapult is outside its foundation"
    }

    data class InvalidInitialCameraAnchor(
        override val path: String,
    ) : MapValidationError {
        override val message = "initial camera anchor cannot be resolved"
    }

    data class InvalidDefinition(
        override val path: String,
        override val message: String,
    ) : MapValidationError
}

object MapDefinitionValidator {
    const val TERRAIN_SAMPLE_STEP = 40f
    const val MAX_TERRAIN_DELTA = 200f
    const val FOUNDATION_HEIGHT_TOLERANCE = 80f
    const val MIN_FOUNDATION_TRANSITION_SAMPLES = 3
    const val DECORATION_MARGIN = 40f

    fun validate(definition: MapDefinition): MapValidationResult {
        val errors = buildList {
            validateTerrain(definition, this)
            validateProtectedZones(definition, this)
            validateFortresses(definition, this)
            validateCatapults(definition, this)
            validateObjects(definition, this)
            validateCamera(definition, this)
            validateDuplicateIds(definition, this)
        }
        return MapValidationResult(errors)
    }

    fun requireValid(definition: MapDefinition): MapDefinition {
        val result = validate(definition)
        require(result.isValid) {
            result.errors.joinToString("; ") { "${it.path}: ${it.message}" }
        }
        return definition
    }

    private fun validateTerrain(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        val samples = definition.terrain.samples
        if (samples.isEmpty()) {
            errors += MapValidationError.InvalidDefinition(
                "terrain.samples",
                "terrain profile must contain samples",
            )
            return
        }
        if (definition.terrain.sampleStep != TERRAIN_SAMPLE_STEP) {
            errors += MapValidationError.InvalidDefinition(
                "terrain.sampleStep",
                "sample step must be $TERRAIN_SAMPLE_STEP",
            )
        }
        samples.zipWithNext().forEachIndexed { index, (left, right) ->
            if (right.x - left.x != TERRAIN_SAMPLE_STEP) {
                errors += MapValidationError.InvalidDefinition(
                    "terrain.samples[$index]",
                    "sample spacing must be $TERRAIN_SAMPLE_STEP",
                )
            }
            val delta = abs(right.height - left.height)
            if (delta > MAX_TERRAIN_DELTA) {
                errors += MapValidationError.TerrainSlopeExceeded(
                    path = "terrain.samples[$index]",
                    left = left,
                    right = right,
                    maximumDelta = MAX_TERRAIN_DELTA,
                )
            }
        }
        if (samples.first().x != 0f || samples.last().x != definition.worldWidth) {
            errors += MapValidationError.InvalidDefinition(
                "terrain.samples",
                "terrain profile must cover the complete world width",
            )
        }
    }

    private fun validateProtectedZones(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        definition.protectedTerrainZones.forEachIndexed { index, zone ->
            if (zone.minX < 0f || zone.maxX > definition.worldWidth) {
                errors += MapValidationError.InvalidDefinition(
                    "protectedTerrainZones[$index]",
                    "protected zone must be inside world bounds",
                )
            }
        }
    }

    private fun validateFortresses(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        if (definition.fortresses.size != 2 ||
            definition.fortresses.map { it.owner }.toSet() != setOf(Team.BLUE, Team.RED)
        ) {
            errors += MapValidationError.InvalidDefinition(
                "fortresses",
                "exactly one fortress per team is required",
            )
        }
        definition.fortresses.forEachIndexed { index, fortress ->
            validateAsset(fortress.assetKey, "fortresses[$index]", errors)
            validateWorldPosition(fortress.x, fortress.foundationHeight, "fortresses[$index]", definition, errors)
            validateFoundation(
                placement = fortress,
                path = "fortresses[$index]",
                definition = definition,
                errors = errors,
            )
        }
    }

    private fun validateCatapults(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        if (definition.catapults.map { it.owner }.toSet().size != 2) {
            errors += MapValidationError.InvalidDefinition(
                "catapults",
                "exactly one catapult per team is required",
            )
        }
        definition.catapults.forEachIndexed { index, catapult ->
            validateAsset(catapult.assetKey, "catapults[$index]", errors)
            val foundation = definition.fortresses.firstOrNull { it.owner == catapult.owner }
            if (foundation == null ||
                catapult.x !in (foundation.x - foundation.halfWidth)..(foundation.x + foundation.halfWidth) ||
                abs(catapult.foundationHeight - foundation.foundationHeight) > FOUNDATION_HEIGHT_TOLERANCE
            ) {
                errors += MapValidationError.CatapultOutsideFoundation(
                    "catapults[$index]",
                )
            }
            val protectedZone = catapult.protectedZoneId?.let { id ->
                definition.protectedTerrainZones.firstOrNull { zone ->
                    zone.id == id &&
                        zone.minX <= catapult.x - catapult.halfWidth &&
                        zone.maxX >= catapult.x + catapult.halfWidth &&
                        catapult.foundationHeight in zone.minHeight..zone.maxHeight
                }
            }
            if (catapult.protectedZoneId != null && protectedZone == null) {
                errors += MapValidationError.InvalidDefinition(
                    "catapults[$index].protectedZoneId",
                    "catapult must have a covering protected terrain zone",
                )
            }
            validateWorldPosition(catapult.x, catapult.foundationHeight, "catapults[$index]", definition, errors)
        }
    }

    private fun validateObjects(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        val largeRelicCount = definition.objects.count { it.relicSize == RelicSize.LARGE }
        if (largeRelicCount > 1) {
            errors += MapValidationError.InvalidDefinition(
                "objects",
                "at most one LARGE relic is allowed per map",
            )
        }
        definition.objects.forEachIndexed { index, objectPlacement ->
            val path = "objects[$index]"
            validateAsset(objectPlacement.assetKey, path, errors)
            if (objectPlacement.relicSize != null) {
                validateRelic(definition, objectPlacement, path, errors)
                return@forEachIndexed
            }
            if (objectPlacement.type == MapObjectType.DECORATION) {
                if (definition.fortresses.any {
                        abs(objectPlacement.x - it.x) <= it.halfWidth + DECORATION_MARGIN
                    }) {
                    errors += MapValidationError.DecorationOnFoundation(path)
                }
                if (definition.catapults.any {
                        abs(objectPlacement.x - it.x) <= it.halfWidth + DECORATION_MARGIN
                    }) {
                    errors += MapValidationError.DecorationOnFoundation(path)
                }
                val slope = slopeAt(definition.terrain.samples, objectPlacement.x)
                if (slope > MAX_TERRAIN_DELTA / TERRAIN_SAMPLE_STEP) {
                    errors += MapValidationError.DecorationOnSteepSlope(path)
                }
            }
            if (objectPlacement.x !in 0f..definition.worldWidth ||
                objectPlacement.y !in 0f..definition.worldHeight
            ) {
                errors += MapValidationError.InvalidDefinition(
                    "$path.position",
                    "object must be inside world bounds",
                )
            }
            val terrainHeight = terrainHeightAt(definition, objectPlacement.x)
            if (abs(objectPlacement.y - terrainHeight) > 1.5f) {
                errors += MapValidationError.InvalidDefinition(
                    "$path.y",
                    "object bottom anchor must match terrain height",
                )
            }
            definition.objects.drop(index + 1).forEach { other ->
                if (!objectPlacement.allowOverlap &&
                    !other.allowOverlap &&
                    abs(objectPlacement.x - other.x) < 40f
                ) {
                    errors += MapValidationError.ObjectOverlapsAnotherObject(
                        path,
                        other.id,
                    )
                }
            }
            if (objectPlacement.collision != ObjectCollision.NONE &&
                objectPlacement.type == MapObjectType.DECORATION
            ) {
                // Explicit collision is allowed; this branch documents that it
                // must be intentional rather than the default.
            }
        }
    }

    private fun validateRelic(
        definition: MapDefinition,
        relic: MapObjectPlacement,
        path: String,
        errors: MutableList<MapValidationError>,
    ) {
        val size = relic.relicSize ?: return
        if (relic.type != MapObjectType.DECORATION) {
            errors += MapValidationError.InvalidDefinition(
                "$path.type",
                "relics must be decorations",
            )
        }
        if (relic.collision != ObjectCollision.NONE) {
            errors += MapValidationError.InvalidDefinition(
                "$path.collision",
                "decorative relics cannot have gameplay collision",
            )
        }
        if (!relic.assetKey.startsWith("relic.")) {
            errors += MapValidationError.InvalidDefinition(
                "$path.assetKey",
                "relic asset keys must use the relic. namespace",
            )
        }

        val rotationRadians = Math.toRadians(relic.rotationDegrees.toDouble())
        val rotatedHalfWidth =
            abs(size.halfWidth * relic.scale * cos(rotationRadians).toFloat()) +
                abs(size.halfHeight * relic.scale * sin(rotationRadians).toFloat())
        val rotatedHalfHeight =
            abs(size.halfWidth * relic.scale * sin(rotationRadians).toFloat()) +
                abs(size.halfHeight * relic.scale * cos(rotationRadians).toFloat())
        val left = relic.x - rotatedHalfWidth
        val right = relic.x + rotatedHalfWidth
        val top = relic.y - rotatedHalfHeight
        val bottom = relic.y + rotatedHalfHeight

        if (left < 0f || right > definition.worldWidth || top < 0f || bottom > definition.worldHeight) {
            errors += MapValidationError.InvalidDefinition(
                "$path.footprint",
                "relic footprint must stay inside world bounds",
            )
        }

        val soilSurface = maxTerrainHeightInRange(definition, left, right)
        if (top < soilSurface) {
            errors += MapValidationError.InvalidDefinition(
                "$path.footprint",
                "relic footprint must be fully inside soil",
            )
        }

        if (definition.fortresses.any {
                horizontalFootprintsOverlap(
                    relic.x,
                    rotatedHalfWidth,
                    it.x,
                    it.halfWidth + DECORATION_MARGIN,
                )
            }
        ) {
            errors += MapValidationError.DecorationOnFoundation(path)
        }
        if (definition.catapults.any {
                horizontalFootprintsOverlap(
                    relic.x,
                    rotatedHalfWidth,
                    it.x,
                    it.halfWidth + DECORATION_MARGIN,
                )
            }
        ) {
            errors += MapValidationError.DecorationOnFoundation(path)
        }
        if (definition.protectedTerrainZones.any {
                relic.leftEdge(rotatedHalfWidth) < it.maxX &&
                    relic.rightEdge(rotatedHalfWidth) > it.minX
            }
        ) {
            errors += MapValidationError.DecorationOnFoundation(path)
        }

        val slope = slopeAt(definition.terrain.samples, relic.x)
        if (slope > MAX_TERRAIN_DELTA / TERRAIN_SAMPLE_STEP) {
            errors += MapValidationError.DecorationOnSteepSlope(path)
        }

        definition.objects.filter { it.id != relic.id }.forEach { other ->
            val otherHalfWidth = other.relicSize?.halfWidth?.times(other.scale) ?: 40f
            if (!relic.allowOverlap &&
                !other.allowOverlap &&
                horizontalFootprintsOverlap(relic.x, rotatedHalfWidth, other.x, otherHalfWidth)
            ) {
                errors += MapValidationError.ObjectOverlapsAnotherObject(path, other.id)
            }
        }
    }

    private fun horizontalFootprintsOverlap(
        leftCenter: Float,
        leftHalfWidth: Float,
        rightCenter: Float,
        rightHalfWidth: Float,
    ): Boolean = abs(leftCenter - rightCenter) < leftHalfWidth + rightHalfWidth

    private fun MapObjectPlacement.leftEdge(halfWidth: Float): Float = x - halfWidth

    private fun MapObjectPlacement.rightEdge(halfWidth: Float): Float = x + halfWidth

    private fun maxTerrainHeightInRange(
        definition: MapDefinition,
        minX: Float,
        maxX: Float,
    ): Float = definition.terrain.samples
        .filter { it.x in minX..maxX }
        .map { it.height }
        .plus(terrainHeightAt(definition, minX))
        .plus(terrainHeightAt(definition, maxX))
        .maxOrNull()
        ?: terrainHeightAt(definition, minX)

    private fun validateCamera(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        val canResolve = when (definition.initialCameraAnchor) {
            InitialCameraAnchor.CENTER -> true
            InitialCameraAnchor.PLAYER_FORTRESS ->
                definition.fortresses.any { it.owner == Team.BLUE }
            InitialCameraAnchor.ENEMY_FORTRESS ->
                definition.fortresses.any { it.owner == Team.RED }
        }
        if (!canResolve) {
            errors += MapValidationError.InvalidInitialCameraAnchor("initialCameraAnchor")
        }
    }

    private fun validateDuplicateIds(
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        val ids = definition.objects.map { it.id }
        if (ids.size != ids.toSet().size) {
            errors += MapValidationError.InvalidDefinition(
                "objects",
                "object identifiers must be unique",
            )
        }
    }

    private fun validateFoundation(
        placement: FortressPlacement,
        path: String,
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        val samples = definition.terrain.samples.filter {
            it.x in (placement.x - FOUNDATION_HEIGHT_TOLERANCE)..(
                placement.x + FOUNDATION_HEIGHT_TOLERANCE
            )
        }
        if (samples.size < MIN_FOUNDATION_TRANSITION_SAMPLES) {
            errors += MapValidationError.FoundationTransitionTooShort(path, samples.size)
            return
        }
        val minimum = samples.minOf { it.height }
        val maximum = samples.maxOf { it.height }
        if (maximum - minimum > FOUNDATION_HEIGHT_TOLERANCE) {
            errors += MapValidationError.FoundationNotFlat(path, minimum, maximum)
        }
        if (placement.foundationHeight !in (minimum - FOUNDATION_HEIGHT_TOLERANCE)..(
                maximum + FOUNDATION_HEIGHT_TOLERANCE
            )
        ) {
            errors += MapValidationError.FoundationOutsideAllowedHeight(
                path,
                placement.foundationHeight,
                minimum - FOUNDATION_HEIGHT_TOLERANCE,
                maximum + FOUNDATION_HEIGHT_TOLERANCE,
            )
        }
        val zone = definition.protectedTerrainZones.firstOrNull {
            it.id == placement.protectedZoneId &&
                it.minX <= placement.x - placement.halfWidth &&
            it.maxX >= placement.x + placement.halfWidth &&
            placement.foundationHeight in it.minHeight..it.maxHeight
        }
        if (zone == null) {
            errors += MapValidationError.InvalidDefinition(
                "$path.protectedZoneId",
                "foundation must have a covering protected terrain zone",
            )
        }
    }

    private fun validateAsset(
        assetKey: String,
        path: String,
        errors: MutableList<MapValidationError>,
    ) {
        if (assetKey.isBlank()) errors += MapValidationError.MissingAssetKey(path)
    }

    private fun validateWorldPosition(
        x: Float,
        height: Float,
        path: String,
        definition: MapDefinition,
        errors: MutableList<MapValidationError>,
    ) {
        if (x !in 0f..definition.worldWidth || height !in 0f..definition.worldHeight) {
            errors += MapValidationError.InvalidDefinition(
                "$path.position",
                "placement must be inside world bounds",
            )
        }
    }

    private fun slopeAt(samples: List<TerrainSample>, x: Float): Float {
        val nearest = samples.minBy { abs(it.x - x) }
        val next = samples.firstOrNull { it.x > nearest.x } ?: nearest
        return if (next.x == nearest.x) 0f else {
            abs(next.height - nearest.height) / (next.x - nearest.x)
        }
    }

    private fun terrainHeightAt(definition: MapDefinition, x: Float): Float {
        val samples = definition.terrain.samples
        val clampedX = x.coerceIn(samples.first().x, samples.last().x)
        val leftIndex = samples.indexOfLast { it.x <= clampedX }.coerceAtLeast(0)
        val left = samples[leftIndex]
        val right = samples.getOrNull(leftIndex + 1) ?: left
        if (left.x == right.x) return left.height
        val progress = (clampedX - left.x) / (right.x - left.x)
        return left.height + (right.height - left.height) * progress
    }
}
