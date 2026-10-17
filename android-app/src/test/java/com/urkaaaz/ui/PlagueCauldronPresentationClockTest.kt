package com.urkaaaz.ui

import com.urkaaaz.contracts.EffectSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlagueCauldronPresentationClockTest {
    @Test
    fun cloudGrowsLoopsAndFadesUsingTheImpactSequence() {
        val clock = PlagueCauldronPresentationClock()
        val effect = plagueEffect(remainingMilliseconds = 5_000L)

        val initial = clock.update(state(effect), 0L).clouds.single()
        assertEquals(PlagueCauldronVisualContract.CLOUD_FIRST_FRAME, initial.frameIndex)
        assertEquals(0f, initial.growthProgress)
        assertEquals(0.5f, initial.opacity, 0.001f)

        val growing = clock.update(
            state(effect.copy(remainingMilliseconds = 4_400L)),
            600L,
        ).clouds.single()
        assertEquals(0.5f, growing.growthProgress, 0.001f)
        assertEquals(7, growing.frameIndex)

        val mature = clock.update(
            state(effect.copy(remainingMilliseconds = 3_800L)),
            1_200L,
        ).clouds.single()
        assertEquals(1f, mature.growthProgress)
        assertEquals(PlagueCauldronVisualContract.LOOP_FIRST_FRAME, mature.frameIndex)

        val fading = clock.update(
            state(effect.copy(remainingMilliseconds = 500L)),
            1_400L,
        ).clouds.single()
        assertEquals(12, fading.frameIndex)
        assertEquals(0.25f, fading.opacity, 0.001f)
    }

    @Test
    fun cloudAnimationFreezesWhilePausedAndDisappearsWithTheEffect() {
        val clock = PlagueCauldronPresentationClock()
        val effect = plagueEffect(remainingMilliseconds = 4_000L)
        clock.update(state(effect), 0L)

        val beforePause = clock.update(
            state(effect.copy(remainingMilliseconds = 3_700L)),
            300L,
        ).clouds.single()
        val pausedState = state(effect.copy(remainingMilliseconds = 3_700L)).copy(isPaused = true)
        val paused = clock.update(pausedState, 400L).clouds.single()
        val stillPaused = clock.update(pausedState, 5_000L).clouds.single()
        assertEquals(beforePause.frameIndex, paused.frameIndex)
        assertEquals(beforePause.growthProgress, stillPaused.growthProgress, 0.001f)

        assertTrue(clock.update(state(null), 5_100L).clouds.isEmpty())
    }

    private fun state(effect: EffectSnapshot?): RenderState = RenderStateMapper.map(
        MatchSnapshot(
            matchId = MatchId("plague-match"),
            effects = listOfNotNull(effect),
        ),
    )

    private fun plagueEffect(remainingMilliseconds: Long): EffectSnapshot = EffectSnapshot(
        effectType = "PLAGUE_CAULDRON",
        remainingMilliseconds = remainingMilliseconds,
        effectId = EntityId("plague-effect"),
        sourceProjectileId = EntityId("plague-projectile"),
        x = 1_200f,
        y = 700f,
        radius = 300f,
    )
}
