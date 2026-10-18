package com.urkaaaz.ui

import com.urkaaaz.contracts.EffectSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.ProjectileSnapshot
import com.urkaaaz.contracts.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FireRainPresentationClockTest {
    @Test
    fun splitEventStartsOnceAndRetainsProjectileRolesUntilTheyLeaveSnapshot() {
        val clock = FireRainPresentationClock()
        val event = splitEvent()
        val splitState = state(
            projectileIds = listOf("container", "fragment-0", "fragment-1"),
            events = listOf(event),
        )

        val firstFrame = clock.update(splitState, 0L)
        assertEquals(setOf("container"), firstFrame.hiddenProjectileIds)
        assertEquals(setOf("fragment-0", "fragment-1"), firstFrame.fragmentProjectileIds)
        assertEquals(0, firstFrame.splitFlashes.single().frameIndex)

        val repeatedFrame = clock.update(splitState, 100L)
        assertEquals(2, repeatedFrame.splitFlashes.single().frameIndex)

        val afterSplitFlash = clock.update(splitState, 600L)
        assertTrue(afterSplitFlash.splitFlashes.isEmpty())
        assertEquals(setOf("container"), afterSplitFlash.hiddenProjectileIds)

        val remainingFragment = clock.update(
            state(projectileIds = listOf("container", "fragment-1")),
            700L,
        )
        assertEquals(setOf("container"), remainingFragment.hiddenProjectileIds)
        assertEquals(setOf("fragment-1"), remainingFragment.fragmentProjectileIds)

        val noSplitProjectiles = clock.update(state(), 800L)
        assertTrue(noSplitProjectiles.hiddenProjectileIds.isEmpty())
        assertTrue(noSplitProjectiles.fragmentProjectileIds.isEmpty())
        assertTrue(noSplitProjectiles.splitFlashes.isEmpty())
    }

    @Test
    fun simultaneousEffectsAnimateIndependentlyAndFadeFromSnapshotRemainingTime() {
        val clock = FireRainPresentationClock()
        val first = fireEffect("fire-1", remainingMilliseconds = 3_000L)
        val second = fireEffect("fire-2", remainingMilliseconds = 500L)

        val initial = clock.update(state(effects = listOf(first, second)), 0L)
        assertEquals(listOf(0, 19), initial.fireFields.map { it.frameIndex })

        val later = clock.update(
            state(
                effects = listOf(
                    first.copy(remainingMilliseconds = 2_900L),
                    second.copy(remainingMilliseconds = 250L),
                ),
            ),
            100L,
        )
        assertEquals(listOf(0, 21), later.fireFields.map { it.frameIndex })
    }

    @Test
    fun fireFieldLoopsOnlyContinuousBurningFramesBeforeFading() {
        val clock = FireRainPresentationClock()
        val effect = fireEffect("fire-1", remainingMilliseconds = 9_000L)

        assertEquals(0, clock.update(state(effects = listOf(effect)), 0L)
            .fireFields.single().frameIndex)
        assertEquals(2, clock.update(
            state(effects = listOf(effect)),
            250L,
        ).fireFields.single().frameIndex)
        assertEquals(12, clock.update(
            state(effects = listOf(effect.copy(remainingMilliseconds = 7_750L))),
            1_250L,
        ).fireFields.single().frameIndex)
        assertEquals(2, clock.update(
            state(effects = listOf(effect.copy(remainingMilliseconds = 7_650L))),
            1_350L,
        ).fireFields.single().frameIndex)
    }

    @Test
    fun presentationClocksFreezeDuringPauseAndResumeWithoutCatchingUp() {
        val clock = FireRainPresentationClock()
        val effect = fireEffect("fire-1", remainingMilliseconds = 3_000L)

        clock.update(state(effects = listOf(effect)), 0L)
        val beforePause = clock.update(
            state(effects = listOf(effect.copy(remainingMilliseconds = 2_700L))),
            300L,
        )
        assertEquals(2, beforePause.fireFields.single().frameIndex)

        val pausedState = state(
            effects = listOf(effect.copy(remainingMilliseconds = 2_700L)),
        ).copy(isPaused = true)
        val paused = clock.update(pausedState, 400L)
        val stillPaused = clock.update(pausedState, 5_000L)
        assertEquals(2, paused.fireFields.single().frameIndex)
        assertEquals(2, stillPaused.fireFields.single().frameIndex)

        val resumed = clock.update(
            state(effects = listOf(effect.copy(remainingMilliseconds = 2_700L))),
            5_010L,
        )
        assertEquals(2, resumed.fireFields.single().frameIndex)
        val advanced = clock.update(
            state(effects = listOf(effect.copy(remainingMilliseconds = 2_600L))),
            5_110L,
        )
        assertEquals(3, advanced.fireFields.single().frameIndex)
    }

    @Test
    fun newMatchGenerationResetsPresentationEvenWhenMatchIdIsReused() {
        val clock = FireRainPresentationClock()
        val oldStart = MatchEvent.MatchStarted(
            eventId = EventId("start-old"),
            matchId = MatchId("match"),
            simulationTimeMilliseconds = 0L,
            randomSeed = 7L,
        )
        val stateBeforeRestart = state(
            projectileIds = listOf("container", "fragment-0"),
            effects = listOf(fireEffect("fire-1", 2_500L)),
            events = listOf(oldStart, splitEvent()),
        )
        val active = clock.update(stateBeforeRestart, 0L)
        assertEquals(setOf("container"), active.hiddenProjectileIds)
        assertEquals(setOf("fragment-0"), active.fragmentProjectileIds)

        val afterRestart = clock.update(
            stateBeforeRestart.copy(
                fireRainSplits = emptyList(),
                matchGeneration = 1L,
            ),
            100L,
        )

        assertTrue(afterRestart.hiddenProjectileIds.isEmpty())
        assertTrue(afterRestart.fragmentProjectileIds.isEmpty())
        assertTrue(afterRestart.splitFlashes.isEmpty())
        assertEquals(0, afterRestart.fireFields.single().frameIndex)
        assertEquals(0, afterRestart.projectileFrameIndex)
    }

    @Test
    fun clearDropsAllRetainedPresentationState() {
        val clock = FireRainPresentationClock()
        clock.update(
            state(
                projectileIds = listOf("container", "fragment-0"),
                events = listOf(splitEvent()),
                effects = listOf(fireEffect("fire-1", 2_000L)),
            ),
            0L,
        )

        clock.clear()
        val cleared = clock.update(state(), 100L)

        assertTrue(cleared.hiddenProjectileIds.isEmpty())
        assertTrue(cleared.fragmentProjectileIds.isEmpty())
        assertTrue(cleared.splitFlashes.isEmpty())
        assertTrue(cleared.fireFields.isEmpty())
        assertEquals(0, cleared.projectileFrameIndex)
    }

    private fun state(
        matchId: String = "match",
        projectileIds: List<String> = emptyList(),
        effects: List<EffectSnapshot> = emptyList(),
        events: List<MatchEvent> = emptyList(),
    ): RenderState {
        val snapshot = MatchSnapshot(
            status = MatchStatus.RUNNING,
            matchId = MatchId(matchId),
            projectiles = projectileIds.map { id ->
                ProjectileSnapshot(
                    entityId = EntityId(id),
                    firedBy = Team.BLUE,
                    ammunitionType = "FIRE_RAIN",
                    x = 400f,
                    y = 500f,
                    velocityX = 0f,
                    velocityY = 0f,
                )
            },
            effects = effects,
        )
        return RenderStateMapper.map(snapshot, events)
    }

    private fun splitEvent(): MatchEvent.ProjectileSplit =
        MatchEvent.ProjectileSplit(
            eventId = EventId("split-1"),
            matchId = MatchId("match"),
            simulationTimeMilliseconds = 1_000L,
            projectileId = EntityId("container"),
            fragmentProjectileIds = (0..5).map { EntityId("fragment-$it") },
            ammunitionType = "FIRE_RAIN",
            x = 400f,
            y = 500f,
        )

    private fun fireEffect(
        id: String,
        remainingMilliseconds: Long,
    ): EffectSnapshot = EffectSnapshot(
        effectType = "FIRE_RAIN",
        remainingMilliseconds = remainingMilliseconds,
        effectId = EntityId(id),
        sourceProjectileId = EntityId("fragment-0"),
        x = 400f,
        y = 500f,
        radius = 68f,
    )
}
