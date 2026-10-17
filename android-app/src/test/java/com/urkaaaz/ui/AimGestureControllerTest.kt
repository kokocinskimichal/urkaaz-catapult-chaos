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
        )
        val zoomedIn = AimGestureController().begin(
            originX = originX,
            originY = originY,
            x = 180f,
            y = 320f,
            currentAim = currentAim,
            screenSpaceScale = 2f,
        )

        assertEquals(
            (normalZoom as AimGestureResult.Changed).aim,
            (zoomedIn as AimGestureResult.Changed).aim,
        )
    }
}
