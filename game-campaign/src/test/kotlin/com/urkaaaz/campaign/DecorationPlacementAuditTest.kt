package com.urkaaaz.campaign

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapObjectPlacement
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationPlacementAuditTest {
    @Test
    fun allDecorationsArePlacedOnFlatTerrain() {
        val invalid = (1..CampaignLevelDefinition.MAX_LEVELS)
            .flatMap { level ->
                val map = CampaignMapDefinitions.forLevel(level)
                map.objects
                    .filter { it.isTerrainDecoration() && !map.isFlatAt(it.x) }
                    .map { "$level:${it.id}" }
            }

        assertTrue(
            "Decorations on slopes: ${invalid.joinToString()}",
            invalid.isEmpty(),
        )
    }

    private fun MapObjectPlacement.isTerrainDecoration(): Boolean =
        type.name == "DECORATION" && relicSize == null

    private fun MapDefinition.isFlatAt(x: Float): Boolean {
        val left = terrainHeightAt(x - FLAT_CHECK_RADIUS)
        val center = terrainHeightAt(x)
        val right = terrainHeightAt(x + FLAT_CHECK_RADIUS)
        return abs(left - center) <= HEIGHT_TOLERANCE &&
            abs(center - right) <= HEIGHT_TOLERANCE
    }

    private fun MapDefinition.terrainHeightAt(x: Float): Float {
        val samples = terrain.samples
        val clampedX = x.coerceIn(samples.first().x, samples.last().x)
        val leftIndex = samples.indexOfLast { it.x <= clampedX }.coerceAtLeast(0)
        val left = samples[leftIndex]
        val right = samples.getOrNull(leftIndex + 1) ?: left
        if (left.x == right.x) return left.height
        val progress = (clampedX - left.x) / (right.x - left.x)
        return left.height + (right.height - left.height) * progress
    }

    private companion object {
        const val FLAT_CHECK_RADIUS = 40f
        const val HEIGHT_TOLERANCE = 1f
    }
}
