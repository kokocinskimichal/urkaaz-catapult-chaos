package com.urkaaaz.simulation

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.WorldScale
import com.urkaaaz.domain.WorldBounds

/** Tunable, host-independent constants for the deterministic simulation. */
data class SimulationConfig(
    val bounds: WorldBounds,
    val terrainBiome: String = "GREEN_FRONTIER",
    val gravityAccelerationY: Float = 420f,
    val windAccelerationX: Float = 0f,
    val windAccelerationScale: Float = 260f,
    val fortressCollisionRadius: Float = 46f,
    val unitCollisionRadius: Float = WorldScale.UNIT_COLLISION_RADIUS,
    val defaultCatapultHeightRatio: Float = 0.72f,
    val startingLimitedAmmunition: Int = 3,
    val matchDurationMilliseconds: Long = 300_000L,
    val mapDefinition: MapDefinition? = null,
) {
    init {
        require(gravityAccelerationY >= 0f) { "gravity must not be negative" }
        require(windAccelerationScale > 0f) {
            "wind acceleration scale must be positive"
        }
        require(defaultCatapultHeightRatio in 0f..1f) {
            "default catapult height ratio must be between 0 and 1"
        }
        require(startingLimitedAmmunition >= 0) {
            "starting limited ammunition must not be negative"
        }
        require(matchDurationMilliseconds > 0) {
            "match duration must be positive"
        }
    }
}
