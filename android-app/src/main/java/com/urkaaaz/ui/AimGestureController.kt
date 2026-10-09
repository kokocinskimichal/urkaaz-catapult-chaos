package com.urkaaaz.ui

import kotlin.math.hypot

data class AimGestureState(
    val aim: SlingshotAim?,
    val previousAim: SlingshotAim?,
    val x: Float,
    val y: Float,
)

sealed interface AimGestureResult {
    data class Changed(val aim: SlingshotAim) : AimGestureResult
    data class Released(val aim: SlingshotAim) : AimGestureResult
    data class Cancelled(val previousAim: SlingshotAim?) : AimGestureResult
}

class AimGestureController {
    var state: AimGestureState = AimGestureState(null, null, 0f, 0f)
        private set

    val isDragging: Boolean
        get() = state.aim != null

    fun begin(
        originX: Float,
        originY: Float,
        x: Float,
        y: Float,
        currentAim: SlingshotAim,
    ): AimGestureResult {
        val previousAim = currentAim.copy(
            pullDistance = 0f,
            directionValid = false,
            shouldFire = false,
        )
        state = AimGestureState(
            aim = SlingshotAimCalculator.calculate(originX, originY, x, y),
            previousAim = previousAim,
            x = x,
            y = y,
        )
        return AimGestureResult.Changed(requireNotNull(state.aim))
    }

    fun move(originX: Float, originY: Float, x: Float, y: Float): AimGestureResult? {
        if (!isDragging) return null
        state = state.copy(
            aim = SlingshotAimCalculator.calculate(originX, originY, x, y),
            x = x,
            y = y,
        )
        return AimGestureResult.Changed(requireNotNull(state.aim))
    }

    fun release(): AimGestureResult? {
        val aim = state.aim ?: return null
        val result = if (aim.shouldFire) {
            AimGestureResult.Released(aim)
        } else {
            AimGestureResult.Cancelled(state.previousAim)
        }
        clear()
        return result
    }

    fun cancel(): AimGestureResult? {
        if (!isDragging) return null
        val result = AimGestureResult.Cancelled(state.previousAim)
        clear()
        return result
    }

    fun pullDistance(originX: Float, originY: Float): Float =
        hypot(state.x - originX, state.y - originY)

    private fun clear() {
        state = AimGestureState(null, null, 0f, 0f)
    }
}
