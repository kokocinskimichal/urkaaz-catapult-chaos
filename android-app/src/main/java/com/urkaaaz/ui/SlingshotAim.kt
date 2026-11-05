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
        maximumScreenPullDistance: Float = MAX_PULL_DISTANCE,
    ): SlingshotAim {
        require(maximumScreenPullDistance.isFinite() && maximumScreenPullDistance > 0f) {
            "maximum screen-space pull distance must be finite and positive"
        }
        val launchX = originX - touchX
        val launchY = originY - touchY
        val pullDistance = hypot(launchX, launchY)
        val directionValid = launchX > 0f && launchY < 0f
        val angle = Math.toDegrees(
            atan2((-launchY).toDouble(), abs(launchX).coerceAtLeast(1f).toDouble()),
        ).toFloat().coerceIn(5f, 85f)
        val pullFraction =
            (pullDistance / maximumScreenPullDistance).coerceIn(0f, 1f)

        return SlingshotAim(
            angleDegrees = angle,
            power = 10f + pullFraction * 90f,
            pullDistance = pullDistance,
            directionValid = directionValid,
            shouldFire = directionValid && pullDistance >= MIN_PULL_DISTANCE,
        )
    }
}

internal object AimPullLimitCalculator {
    private const val MAX_PULL_WIDTH_FRACTION = 0.25f
    private const val EDGE_MARGIN = 8f
    private const val MIN_PULL_RADIUS = 24f

    fun maximumScreenPullDistance(
        viewportWidth: Float,
        viewportHeight: Float,
        originScreenX: Float,
        originScreenY: Float,
    ): Float {
        require(
            viewportWidth.isFinite() &&
                viewportHeight.isFinite() &&
                originScreenX.isFinite() &&
                originScreenY.isFinite() &&
                viewportWidth > 0f &&
                viewportHeight > 0f,
        ) { "aim viewport and origin must be finite with positive dimensions" }
        val availableScreenRadius = minOf(
            originScreenX,
            viewportWidth - originScreenX,
            originScreenY,
            viewportHeight - originScreenY,
        ) - EDGE_MARGIN
        val desiredScreenRadius = minOf(
            SlingshotAimCalculator.MAX_PULL_DISTANCE,
            (viewportWidth * MAX_PULL_WIDTH_FRACTION).coerceAtLeast(MIN_PULL_RADIUS),
        )
        return minOf(
            desiredScreenRadius,
            availableScreenRadius.coerceAtLeast(MIN_PULL_RADIUS),
        )
    }
}
