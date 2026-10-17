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
        screenSpaceScale: Float = 1f,
    ): AimGestureResult {
        val previousAim = currentAim.copy(
            pullDistance = 0f,
            directionValid = false,
            shouldFire = false,
        )
        state = AimGestureState(
            aim = calculateAim(originX, originY, x, y, screenSpaceScale),
            previousAim = previousAim,
            x = x,
            y = y,
        )
        return AimGestureResult.Changed(requireNotNull(state.aim))
    }

    fun move(
        originX: Float,
        originY: Float,
        x: Float,
        y: Float,
        screenSpaceScale: Float = 1f,
    ): AimGestureResult? {
        if (!isDragging) return null
        state = state.copy(
            aim = calculateAim(originX, originY, x, y, screenSpaceScale),
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

    private fun calculateAim(
        originX: Float,
        originY: Float,
        x: Float,
        y: Float,
        screenSpaceScale: Float,
    ): SlingshotAim {
        require(screenSpaceScale.isFinite() && screenSpaceScale > 0f) {
            "screen space scale must be finite and positive"
        }
        return SlingshotAimCalculator.calculate(
            originX,
            originY,
            originX + (x - originX) * screenSpaceScale,
            originY + (y - originY) * screenSpaceScale,
        )
    }

    private fun clear() {
        state = AimGestureState(null, null, 0f, 0f)
    }
}
