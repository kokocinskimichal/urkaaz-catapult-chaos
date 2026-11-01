package com.urkaaaz.simulation

import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.domain.WindBalance
import kotlin.random.Random

/** Deterministic wind transitions used by the projectile simulation. */
class WindSystem(
    initialAccelerationX: Float,
    private val balance: WindBalance,
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
        balance.initialDelaySeconds
    }

    fun update(deltaSeconds: Float) {
        require(deltaSeconds >= 0f) { "deltaSeconds must not be negative" }
        if (direction == 0f) return

        secondsUntilChange -= deltaSeconds
        if (transitionRemainingSeconds > 0f) {
            transitionRemainingSeconds =
                (transitionRemainingSeconds - deltaSeconds).coerceAtLeast(0f)
            val progress = 1f - transitionRemainingSeconds / balance.transitionSeconds
            strength = transitionStartStrength +
                (targetStrength - transitionStartStrength) * progress
        }

        if (secondsUntilChange <= 0f) {
            if (random.nextBoolean()) {
                direction = -direction
            }
            transitionStartStrength = strength
            targetStrength = balance.minimumStrength +
                random.nextFloat() * (balance.maximumStrength - balance.minimumStrength)
            transitionRemainingSeconds = balance.transitionSeconds
            secondsUntilChange = balance.minimumChangeIntervalSeconds +
                random.nextFloat() *
                (balance.maximumChangeIntervalSeconds - balance.minimumChangeIntervalSeconds)
        }
    }

    fun accelerationX(): Float = direction * strength * accelerationScale

    fun snapshot(): WindSnapshot = WindSnapshot(
        direction = direction,
        strength = strength,
    )

}
