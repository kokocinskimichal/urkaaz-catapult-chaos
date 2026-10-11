package com.urkaaaz.simulation

import com.urkaaaz.domain.WorldPosition
import kotlin.math.abs
import kotlin.math.cos

/**
 * Deterministic sampled terrain heightmap.
 *
 * Screen/world Y grows downward, therefore carving a crater increases the
 * ground Y samples. The same heightmap is used by projectile collision,
 * unit placement and rendering snapshots.
 */
data class TerrainState(
    val baselineHeight: Float,
    val craters: List<Crater> = emptyList(),
    val revision: Long = 0L,
    val worldWidth: Float = 1_600f,
    val sampleSpacing: Float = 8f,
    val heightSamples: List<Float> = emptyList(),
    val protectedRanges: List<Pair<Float, Float>> = emptyList(),
) {
    private val samples: List<Float>
        get() = if (heightSamples.isEmpty()) {
            List((worldWidth / sampleSpacing).toInt() + 2) { baselineHeight }
        } else {
            heightSamples
        }

    fun heightAt(x: Float): Float {
        val clampedX = x.coerceIn(0f, worldWidth)
        val position = clampedX / sampleSpacing
        val left = position.toInt().coerceIn(0, samples.lastIndex)
        val right = (left + 1).coerceAtMost(samples.lastIndex)
        val fraction = position - left
        return samples[left] + (samples[right] - samples[left]) * fraction
    }

    fun contains(position: WorldPosition): Boolean = position.y >= heightAt(position.x)

    fun deform(center: WorldPosition, radius: Float, depth: Float): TerrainState {
        require(radius > 0f) { "crater radius must be positive" }
        require(depth >= 0f) { "crater depth must not be negative" }
        val nextSamples = samples.toMutableList()
        val start = ((center.x - radius) / sampleSpacing).toInt().coerceAtLeast(0)
        val end = ((center.x + radius) / sampleSpacing)
            .toInt()
            .coerceAtMost(nextSamples.lastIndex)
        if (start <= end) {
            for (index in start..end) {
                val sampleX = index * sampleSpacing
                if (protectedRanges.any { sampleX in it.first..it.second }) continue
                val distance = abs(sampleX - center.x)
                val falloff = (1f - distance / radius).coerceIn(0f, 1f)
                nextSamples[index] += depth * falloff * falloff
            }
        }
        return copy(
            craters = craters + Crater(center, radius, depth),
            revision = revision + 1,
            heightSamples = nextSamples,
        )
    }

    fun protectRange(centerX: Float, halfWidth: Float): TerrainState =
        if (halfWidth <= 0f) this else copy(
            protectedRanges = protectedRanges + (centerX - halfWidth to centerX + halfWidth),
        )

    fun flattenFoundation(
        centerX: Float,
        halfWidth: Float,
        targetHeight: Float? = null,
    ): TerrainState {
        if (halfWidth <= 0f) return this
        val foundationHeight = targetHeight ?: heightAt(centerX)
        val transitionWidth = maxOf(110f, halfWidth * 1.25f)
        val nextSamples = samples.toMutableList()
        val start = ((centerX - halfWidth - transitionWidth) / sampleSpacing)
            .toInt()
            .coerceAtLeast(0)
        val end = ((centerX + halfWidth + transitionWidth) / sampleSpacing)
            .toInt()
            .coerceAtMost(nextSamples.lastIndex)
        for (index in start..end) {
            val distance = abs(index * sampleSpacing - centerX)
            val blend = when {
                distance <= halfWidth -> 1f
                distance >= halfWidth + transitionWidth -> 0f
                else -> {
                    val progress = (distance - halfWidth) / transitionWidth
                    val smoothProgress = progress * progress * (3f - 2f * progress)
                    1f - smoothProgress
                }
            }
            nextSamples[index] += (foundationHeight - nextSamples[index]) * blend
        }
        return copy(heightSamples = nextSamples)
    }

    companion object {
        /**
         * Builds the initial sampled surface using the legacy Ground rules:
         * smoothed control points plus rounded mountain ridges.
         */
        fun legacyProfile(
            baselineHeight: Float,
            worldWidth: Float,
            worldHeight: Float,
            sampleSpacing: Float = 8f,
        ): TerrainState {
            val controlPoints = listOf(
                0.00f to 0.66f,
                0.08f to 0.48f,
                0.18f to 0.52f,
                0.30f to 0.76f,
                0.42f to 0.84f,
                0.56f to 0.70f,
                0.70f to 0.46f,
                0.84f to 0.58f,
                1.00f to 0.64f,
            )
            val mountains = listOf(
                Triple(0.27f, 0.25f, 0.34f),
                Triple(0.52f, 0.30f, 0.43f),
                Triple(0.75f, 0.24f, 0.31f),
            )
            val sampleCount = (worldWidth / sampleSpacing).toInt() + 2
            val samples = List(sampleCount) { index ->
                val x = index * sampleSpacing
                val normalizedX = (x / worldWidth).coerceIn(0f, 1f)
                val controlHeight = interpolateControlPoints(
                    normalizedX,
                    controlPoints,
                    baselineHeight,
                )
                val mountainHeight = mountains.fold(0f) { total, mountain ->
                    val (center, width, height) = mountain
                    val normalizedDistance =
                        (abs(normalizedX - center) / (width / 2f)).coerceIn(0f, 1f)
                    val crestStart = 0.08f
                    val crestDistance =
                        ((normalizedDistance - crestStart) / (1f - crestStart))
                            .coerceIn(0f, 1f)
                    val profile = if (normalizedDistance <= crestStart) {
                        1f
                    } else {
                        cos(crestDistance * (Math.PI / 2.0)).toFloat()
                            .coerceAtLeast(0f)
                            .let { it * it }
                    }
                    total + height * profile
                }
                controlHeight - mountainHeight * worldHeight * 0.10f
            }
            return TerrainState(
                baselineHeight = baselineHeight,
                worldWidth = worldWidth,
                sampleSpacing = sampleSpacing,
                heightSamples = samples,
            )
        }

        private fun interpolateControlPoints(
            normalizedX: Float,
            controlPoints: List<Pair<Float, Float>>,
            baselineHeight: Float,
        ): Float {
            for (index in 0 until controlPoints.lastIndex) {
                val (x0, y0) = controlPoints[index]
                val (x1, y1) = controlPoints[index + 1]
                if (normalizedX in x0..x1) {
                    val t = if (x1 > x0) (normalizedX - x0) / (x1 - x0) else 0f
                    val smoothT = t * t * (3f - 2f * t)
                    return baselineHeight + (y0 + (y1 - y0) * smoothT - 0.70f) *
                        baselineHeight * 0.40f
                }
            }
            return baselineHeight +
                (controlPoints.last().second - 0.70f) * baselineHeight * 0.40f
        }
    }
}

data class Crater(
    val center: WorldPosition,
    val radius: Float,
    val depth: Float,
)
