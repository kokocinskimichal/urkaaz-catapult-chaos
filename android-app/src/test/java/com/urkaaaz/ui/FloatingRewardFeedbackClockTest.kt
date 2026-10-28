package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingRewardFeedbackClockTest {
    @Test
    fun feedbackRisesForOneSecondPausesAndDoesNotRestartOnRepeatedState() {
        val clock = FloatingRewardFeedbackClock()
        val reward = CombatFeedbackRenderState(
            eventId = "reward-1",
            kind = CombatFeedbackRenderState.Kind.GOLD_REWARD,
            x = 500f,
            y = 700f,
            label = "+10 OrCoins",
        )

        assertEquals(0f, clock.update(listOf(reward), 1_000L, paused = false).single().progress)
        assertEquals(0.5f, clock.update(listOf(reward), 1_500L, paused = false).single().progress)
        assertEquals(0.5f, clock.update(emptyList(), 2_000L, paused = true).single().progress)
        assertTrue(clock.update(emptyList(), 2_500L, paused = false).isEmpty())
    }
}
