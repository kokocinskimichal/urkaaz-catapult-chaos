package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AimGestureControllerTest {
    @Test
    fun sameScreenDragProducesSameAimAtDifferentCameraScales() {
        val originX = 200f
        val originY = 300f
        val currentAim = SlingshotAim(
            angleDegrees = 45f,
            power = 50f,
            pullDistance = 0f,
            directionValid = false,
            shouldFire = false,
        )
        val normalZoom = AimGestureController().begin(
            originX = originX,
            originY = originY,
            x = 160f,
            y = 340f,
            currentAim = currentAim,
            maximumScreenPullDistance = 96f,
        )
        val zoomedIn = AimGestureController().begin(
            originX = originX,
            originY = originY,
            x = 180f,
            y = 320f,
            currentAim = currentAim,
            screenSpaceScale = 2f,
            maximumScreenPullDistance = 96f,
        )

        assertEquals(
            (normalZoom as AimGestureResult.Changed).aim,
            (zoomedIn as AimGestureResult.Changed).aim,
        )
    }

    @Test
    fun pullPowerSaturatesAtScreenLimitWithoutChangingTheAimAngle() {
        val atLimit = SlingshotAimCalculator.calculate(
            originX = 0f,
            originY = 0f,
            touchX = -60f,
            touchY = 80f,
            maximumScreenPullDistance = 100f,
        )
        val beyondLimit = SlingshotAimCalculator.calculate(
            originX = 0f,
            originY = 0f,
            touchX = -120f,
            touchY = 160f,
            maximumScreenPullDistance = 100f,
        )

        assertEquals(100f, atLimit.power, 0.001f)
        assertEquals(100f, beyondLimit.power, 0.001f)
        assertEquals(atLimit.angleDegrees, beyondLimit.angleDegrees, 0.001f)
        assertEquals(200f, beyondLimit.pullDistance, 0.001f)
    }

    @Test
    fun maximumPullRadiusAdaptsToScreenWidthAndAvailableSpace() {
        assertEquals(
            200f,
            AimPullLimitCalculator.maximumScreenPullDistance(
                viewportWidth = 800f,
                viewportHeight = 1200f,
                originScreenX = 400f,
                originScreenY = 600f,
            ),
            0.001f,
        )
        assertEquals(
            120f,
            AimPullLimitCalculator.maximumScreenPullDistance(
                viewportWidth = 800f,
                viewportHeight = 1200f,
                originScreenX = 128f,
                originScreenY = 600f,
            ),
            0.001f,
        )
    }
}
