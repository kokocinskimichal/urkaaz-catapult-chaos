package com.urkaaaz.simulation

import com.urkaaaz.contracts.WindSnapshot
import kotlin.random.Random

/** Deterministic wind transitions used by the projectile simulation. */
class WindSystem(
    initialAccelerationX: Float,
    private val accelerationScale: Float = 1f,
    seed: Int = 0,
) {
    init {
        require(accelerationScale > 0f) {
            "wind acceleration scale must be positive"
        }
    }

    private val random = Random(seed)
    private var direction = when {
        initialAccelerationX < 0f -> -1f
        initialAccelerationX > 0f -> 1f
        else -> 0f
    }
    private var strength = kotlin.math.abs(initialAccelerationX)
    private var transitionStartStrength = strength
    private var targetStrength = strength
    private var transitionRemainingSeconds = 0f
    private var secondsUntilChange = if (direction == 0f) {
        Float.POSITIVE_INFINITY
    } else {
        INITIAL_DELAY_SECONDS
    }

    fun update(deltaSeconds: Float) {
        require(deltaSeconds >= 0f) { "deltaSeconds must not be negative" }
        if (direction == 0f) return

        secondsUntilChange -= deltaSeconds
        if (transitionRemainingSeconds > 0f) {
            transitionRemainingSeconds =
                (transitionRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
            val progress = 1f - transitionRemainingSeconds / TRANSITION_SECONDS
            strength = transitionStartStrength +
                (targetStrength - transitionStartStrength) * progress
        }

        if (secondsUntilChange <= 0f) {
            if (random.nextBoolean()) {
                direction = -direction
            }
            transitionStartStrength = strength
            targetStrength = MIN_STRENGTH +
                random.nextFloat() * (MAX_STRENGTH - MIN_STRENGTH)
            transitionRemainingSeconds = TRANSITION_SECONDS
            secondsUntilChange = MIN_CHANGE_INTERVAL_SECONDS +
                random.nextFloat() *
                (MAX_CHANGE_INTERVAL_SECONDS - MIN_CHANGE_INTERVAL_SECONDS)
        }
    }

    fun accelerationX(): Float = direction * strength * accelerationScale

    fun snapshot(): WindSnapshot = WindSnapshot(
        direction = direction,
        strength = strength,
    )

    private companion object {
        const val INITIAL_DELAY_SECONDS = 8f
        const val TRANSITION_SECONDS = 3f
        const val MIN_CHANGE_INTERVAL_SECONDS = 18f
        const val MAX_CHANGE_INTERVAL_SECONDS = 24f
        const val MIN_STRENGTH = 0.35f
        const val MAX_STRENGTH = 0.90f
    }
}
