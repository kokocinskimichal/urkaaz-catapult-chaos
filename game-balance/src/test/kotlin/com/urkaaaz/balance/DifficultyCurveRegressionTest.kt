package com.urkaaaz.balance

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Guards the shipped difficulty curve; wider than the ±10 pp target because only 20 seeds are played. */
class DifficultyCurveRegressionTest {
    @Test
    fun shippedBalanceStaysNearTheDifficultyCurve() {
        val seeds = (1..20).map { it.toLong() }
        listOf(1, 10, 31).forEach { level ->
            val rate = BalanceBaseline.runLevel(level, seeds).blueWinRate
            val deviation = abs(rate - DifficultyTargets.targetWinRate(level))
            assertTrue(deviation <= 0.15, "level $level win rate $rate deviates ${"%.2f".format(deviation)} from target")
        }
    }
}
