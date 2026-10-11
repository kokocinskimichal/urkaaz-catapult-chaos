package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapObjectPlacement
import com.urkaaaz.contracts.MapObjectType
import com.urkaaaz.contracts.ObjectCollision
import com.urkaaaz.contracts.TerrainProfile
import com.urkaaaz.contracts.TerrainSample
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun createCampaignTerrainProfile(
    chapter: Int,
    level: Int,
    worldWidth: Float,
    sampleStep: Float = 40f,
): TerrainProfile {
    val sampleCount = (worldWidth / sampleStep).toInt()
    val foundationEdge = 686f
    val playableWidth = worldWidth - foundationEdge * 2f
    val phase = (chapter * 0.73f + level * 0.41f).toDouble()
    val primaryAmplitude = 2_200f + ((chapter * 17 + level * 11) % 5) * 220f
    val secondaryAmplitude = 700f + ((chapter * 13 + level * 7) % 4) * 120f
    val primaryCycles = 0.45 + ((chapter + level) % 4) * 0.08
    val secondaryCycles = 0.9 + ((chapter * 2 + level) % 3) * 0.12
    val rawHeights = (0..sampleCount).map { index ->
        val x = index * sampleStep
        when {
            x <= foundationEdge || x >= worldWidth - foundationEdge -> 3_650f
            else -> {
                val progress = ((x - foundationEdge) / playableWidth).coerceIn(0f, 1f).toDouble()
                val envelope = sin(PI * progress)
                val offset = envelope * (
                    sin(PI * primaryCycles * progress + phase) * primaryAmplitude +
                        cos(PI * secondaryCycles * progress - phase * 0.7) * secondaryAmplitude
                    )
                3_650f + offset.toFloat()
            }
        }
    }
    val maximumRawDelta = rawHeights.zipWithNext()
        .maxOf { (left, right) -> kotlin.math.abs(right - left) }
    val terrainScale = (19.5f / maximumRawDelta).coerceAtMost(1f)
    val heights = rawHeights.map { height ->
        3_650f + (height - 3_650f) * terrainScale
    }
    return TerrainProfile(
        sampleStep = sampleStep,
        samples = heights.mapIndexed { index, height ->
            TerrainSample(index * sampleStep, height)
        },
    )
}

internal fun createCampaignMapDefinition(
    base: MapDefinition,
    chapter: Int,
    level: Int,
): MapDefinition {
    val terrain = createCampaignTerrainProfile(chapter, level, base.worldWidth)
    val fortressHeightOffset = (chapter - 5) * 3f + (level % 4 - 1.5f) * 2f
    val fortresses = base.fortresses.map { fortress ->
        fortress.copy(foundationHeight = fortress.foundationHeight + fortressHeightOffset)
    }
    val catapults = base.catapults.map { catapult ->
        catapult.copy(foundationHeight = catapult.foundationHeight + fortressHeightOffset)
    }
    val protectedZones = base.protectedTerrainZones.map { zone ->
        zone.copy(
            minHeight = zone.minHeight + fortressHeightOffset,
            maxHeight = zone.maxHeight + fortressHeightOffset,
        )
    }
    val gameplayObjects = base.objects.filter { it.type == MapObjectType.GAMEPLAY }.map { objectPlacement ->
        objectPlacement.copy(y = terrainHeightAt(terrain.samples, objectPlacement.x))
    }
    val objects = gameplayObjects + generateDecorations(
        terrain = terrain,
        chapter = chapter,
        level = level,
        reservedX = gameplayObjects.map { it.x },
    )
    return base.copy(
        levelId = "chapter-$chapter-level-$level",
        terrain = terrain,
        protectedTerrainZones = protectedZones,
        fortresses = fortresses,
        catapults = catapults,
        objects = objects,
        generationMetadata = base.generationMetadata.copy(
            seed = chapter * 10_000L + level,
            source = "campaign.chapter-$chapter.level-$level",
            authoringVersion = "lds-1",
        ),
    )
}

private fun generateDecorations(
    terrain: TerrainProfile,
    chapter: Int,
    level: Int,
    reservedX: List<Float>,
): List<MapObjectPlacement> {
    var state = chapter * 1_000_003L + level * 97_003L
    val placements = mutableListOf<MapObjectPlacement>()
    var candidateIndex = 0
    while (placements.size < 18 && candidateIndex < 240) {
        state = state * 1_664_525L + 1_013_904_223L
        val jitter = ((state ushr 16) % 161L).toFloat() - 80f
        val x = (820f + candidateIndex * 180f + jitter).coerceIn(760f, 6_440f)
        val y = terrainHeightAt(terrain.samples, x)
        val slope = slopeAt(terrain.samples, x)
        val tooClose = reservedX.any { kotlin.math.abs(it - x) < 80f } ||
            placements.any { kotlin.math.abs(it.x - x) < 80f }
        if (slope <= 0.35f && !tooClose) {
            val variant = ((state ushr 24) % 4L).toInt()
            val type = when (variant) {
                0 -> "tree"
                1 -> "rock"
                2 -> "bush"
                else -> "ruin"
            }
            placements += MapObjectPlacement(
                id = "chapter-$chapter-level-$level-decoration-${placements.size + 1}",
                type = MapObjectType.DECORATION,
                x = x,
                y = y,
                scale = 0.75f + ((state ushr 28) % 70L).toFloat() / 100f,
                assetKey = "decoration.chapter-$chapter.$type",
                collision = ObjectCollision.NONE,
            )
        }
        candidateIndex += 1
    }
    return placements
}

private fun slopeAt(samples: List<TerrainSample>, x: Float): Float {
    val nearestIndex = samples.indices.minBy { kotlin.math.abs(samples[it].x - x) }
    val nextIndex = (nearestIndex + 1).coerceAtMost(samples.lastIndex)
    val left = samples[nearestIndex]
    val right = samples[nextIndex]
    return if (left.x == right.x) 0f else {
        kotlin.math.abs(right.height - left.height) / (right.x - left.x)
    }
}

internal fun terrainHeightAt(samples: List<TerrainSample>, x: Float): Float {
    val clampedX = x.coerceIn(samples.first().x, samples.last().x)
    val leftIndex = samples.indexOfLast { it.x <= clampedX }.coerceAtLeast(0)
    val left = samples[leftIndex]
    val right = samples.getOrNull(leftIndex + 1) ?: left
    if (left.x == right.x) return left.height
    val progress = (clampedX - left.x) / (right.x - left.x)
    return left.height + (right.height - left.height) * progress
}
