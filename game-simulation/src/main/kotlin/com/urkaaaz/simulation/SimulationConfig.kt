package com.urkaaaz.simulation

import com.urkaaaz.domain.WorldBounds

/** Tunable, host-independent constants for the deterministic simulation. */
data class SimulationConfig(
    val bounds: WorldBounds,
    val gravityAccelerationY: Float = 420f,
    val windAccelerationX: Float = 0f,
    val fortressCollisionRadius: Float = 46f,
    val unitCollisionRadius: Float = 18f,
    val defaultCatapultHeightRatio: Float = 0.72f,
    val startingLimitedAmmunition: Int = 3,
) {
    init {
        require(gravityAccelerationY >= 0f) { "gravity must not be negative" }
        require(defaultCatapultHeightRatio in 0f..1f) {
            "default catapult height ratio must be between 0 and 1"
        }
        require(startingLimitedAmmunition >= 0) {
            "starting limited ammunition must not be negative"
        }
    }
}
