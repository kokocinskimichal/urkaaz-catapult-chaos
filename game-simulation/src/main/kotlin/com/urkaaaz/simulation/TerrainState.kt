package com.urkaaaz.simulation

import com.urkaaaz.domain.WorldPosition
import kotlin.math.max

/**
 * Minimal deterministic terrain representation.
 *
 * The first simulation slice stores a flat baseline plus crater samples.
 * Rendering and terrain mesh generation remain outside this module.
 */
data class TerrainState(
    val baselineHeight: Float,
    val craters: List<Crater> = emptyList(),
    val revision: Long = 0L,
) {
    fun heightAt(x: Float): Float =
        craters.fold(baselineHeight) { current, crater ->
            val horizontalDistance = kotlin.math.abs(x - crater.center.x)
            if (horizontalDistance >= crater.radius) {
                current
            } else {
                val profile = 1f - horizontalDistance / crater.radius
                max(current, baselineHeight + crater.depth * profile)
            }
        }

    fun contains(position: WorldPosition): Boolean = position.y >= heightAt(position.x)

    fun deform(center: WorldPosition, radius: Float, depth: Float): TerrainState {
        require(radius > 0f) { "crater radius must be positive" }
        require(depth >= 0f) { "crater depth must not be negative" }
        return copy(
            craters = craters + Crater(center, radius, depth),
            revision = revision + 1,
        )
    }
}

data class Crater(
    val center: WorldPosition,
    val radius: Float,
    val depth: Float,
)
