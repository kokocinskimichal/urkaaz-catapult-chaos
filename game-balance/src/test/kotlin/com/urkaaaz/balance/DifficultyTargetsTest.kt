package com.urkaaaz.balance

import kotlin.test.Test
import kotlin.test.assertEquals

class DifficultyTargetsTest {
    @Test
    fun targetCurveIsPiecewiseLinearThroughTheFirstEliteLevel() {
        assertEquals(0.90, DifficultyTargets.targetWinRate(1), 1e-9)
        assertEquals(0.55, DifficultyTargets.targetWinRate(31), 1e-9)
        assertEquals(0.45, DifficultyTargets.targetWinRate(100), 1e-9)
        assertEquals(0.725, DifficultyTargets.targetWinRate(16), 1e-9)
    }
}
