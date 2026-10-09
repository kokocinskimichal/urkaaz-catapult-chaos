package com.urkaaaz.ui

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

data class SlingshotAim(
    val angleDegrees: Float,
    val power: Float,
    val pullDistance: Float,
    val directionValid: Boolean,
    val shouldFire: Boolean,
)

object SlingshotAimCalculator {
    const val MIN_PULL_DISTANCE = 12f
    const val MAX_PULL_DISTANCE = 260f

    fun calculate(
        originX: Float,
        originY: Float,
        touchX: Float,
        touchY: Float,
    ): SlingshotAim {
        val launchX = originX - touchX
        val launchY = originY - touchY
        val pullDistance = hypot(launchX, launchY)
        val directionValid = launchX > 0f && launchY < 0f
        val angle = Math.toDegrees(
            atan2((-launchY).toDouble(), abs(launchX).coerceAtLeast(1f).toDouble()),
        ).toFloat().coerceIn(5f, 85f)
        val pullFraction = (pullDistance / MAX_PULL_DISTANCE).coerceIn(0f, 1f)

        return SlingshotAim(
            angleDegrees = angle,
            power = 10f + pullFraction * 90f,
            pullDistance = pullDistance,
            directionValid = directionValid,
            shouldFire = directionValid && pullDistance >= MIN_PULL_DISTANCE,
        )
    }
}
