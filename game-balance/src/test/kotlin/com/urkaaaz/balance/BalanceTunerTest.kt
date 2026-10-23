package com.urkaaaz.balance

import com.urkaaaz.domain.UnitConfigurations
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceTunerTest {
    @Test
    fun defaultCandidateReproducesShippedBalance() {
        val balance = TuningCandidate().toBalance()
        assertEquals(UnitConfigurations.DEFENDER.maxHealth, balance.units.byId("DEFENDER").maxHealth)
        assertEquals(20, balance.supply.startingSupply)
    }

    @Test
    fun searchIsDeterministicAndStartsWithReference() {
        val first = BalanceTuner.candidates(5, Random(3))
        assertEquals(first, BalanceTuner.candidates(5, Random(3)))
        assertEquals(TuningCandidate(), first.first())
        assertEquals(6, first.size)
    }

    @Test
    fun evaluationScoresDeviationFromTargetCurve() {
        val result = BalanceTuner.evaluate(TuningCandidate(), listOf(1), listOf(1L))
        assertTrue(result.score in 0.0..1.0)
    }
}
