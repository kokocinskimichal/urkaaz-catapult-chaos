package com.urkaaaz.ui

import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossAttackAnimationTiming
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class BossPresentationClockTest {
    @Test
    fun idleLoopAdvancesAndWrapsAtItsGeneratedDuration() {
        val clock = BossPresentationClock()
        val state = state()
        val animation = requireNotNull(
            BossPresentationCatalog.definition("ancient_war_oak").idleAnimation,
        )

        assertEquals(0, clock.update(state, 0L).idleFrameIndex)
        assertEquals(
            animation.frameCount / 2,
            clock.update(state, animation.durationMilliseconds / 2).idleFrameIndex,
        )
        assertEquals(
            animation.frameCount - 1,
            clock.update(state, animation.durationMilliseconds - 1L).idleFrameIndex,
        )
        assertEquals(0, clock.update(state, animation.durationMilliseconds).idleFrameIndex)
    }

    @Test
    fun pauseAndMatchRestartPreserveAndResetIdlePlayback() {
        val clock = BossPresentationClock()
        val state = state()
        clock.update(state, 0L)
        val beforePause = clock.update(state, 1_000L).idleFrameIndex

        assertEquals(
            beforePause,
            clock.update(state.copy(isPaused = true), 2_000L).idleFrameIndex,
        )
        assertEquals(beforePause, clock.update(state, 2_100L).idleFrameIndex)

        val restarted = clock.update(state.copy(matchGeneration = 1L), 2_200L)
        assertEquals(0, restarted.idleFrameIndex)
    }

    @Test
    fun boulderbloomWalkLoopAdvancesOnlyWhileMarchingAndPausesWithTheMatch() {
        val clock = BossPresentationClock()
        val original = state()
        val originalBoss = requireNotNull(original.boss)
        val moving = original.copy(
            boss = originalBoss.copy(
                presentationKey = "boulderbloom",
                phase = BossPhase.MARCHING,
            ),
        )
        val walk = requireNotNull(
            BossPresentationCatalog.definition("boulderbloom").walkAnimation,
        )

        assertEquals(0, clock.update(moving, 0L).walkFrameIndex)
        assertEquals(
            walk.frameCount / 2,
            clock.update(moving, walk.durationMilliseconds / 2L).walkFrameIndex,
        )
        assertEquals(
            walk.frameCount / 2,
            clock.update(moving.copy(isPaused = true), walk.durationMilliseconds).walkFrameIndex,
        )
        assertEquals(
            walk.frameCount / 2,
            clock.update(moving, walk.durationMilliseconds + 100L).walkFrameIndex,
        )

        val engaged = moving.copy(boss = requireNotNull(moving.boss).copy(phase = BossPhase.ENGAGED))
        assertEquals(null, clock.update(engaged, walk.durationMilliseconds + 200L).walkFrameIndex)
        assertEquals(0, clock.update(moving, walk.durationMilliseconds + 300L).walkFrameIndex)

        val enraged = moving.copy(
            boss = requireNotNull(moving.boss).copy(phase = BossPhase.ENRAGED),
        )
        val enragedClock = BossPresentationClock()
        assertEquals(null, enragedClock.update(enraged, 0L).walkFrameIndex)
        assertEquals(
            0,
            enragedClock.update(
                enraged.copy(boss = requireNotNull(enraged.boss).copy(x = 999f)),
                100L,
            ).walkFrameIndex,
        )
    }

    @Test
    fun rightwardAttackPlaysOnceFromItsStartEventAndReturnsToIdle() {
        val clock = BossPresentationClock()
        val state = state()
        val attack = requireNotNull(
            BossPresentationCatalog.definition("ancient_war_oak")
                .attackAnimation(BossAttackDirection.RIGHT),
        )
        clock.update(state, 0L)

        val trigger = state.copy(
            bossAttack = BossAttackRenderState(
                eventId = "attack-1",
                bossId = "boss-1",
                direction = BossAttackDirection.RIGHT,
            ),
        )
        val started = clock.update(trigger, 1_000L)
        assertEquals(BossAttackDirection.RIGHT, started.attackDirection)
        assertEquals(0, started.attackFrameIndex)

        // The same event id in later states must not restart the sequence.
        val halfway = clock.update(trigger, 1_000L + attack.durationMilliseconds / 2)
        assertEquals(attack.frameCount / 2, halfway.attackFrameIndex)

        val impact = clock.update(
            state,
            1_000L + BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_WINDUP_MILLISECONDS,
        )
        assertEquals(
            BossAttackAnimationTiming.ANCIENT_WAR_OAK_UNIT_ATTACK_IMPACT_FRAME_INDEX,
            impact.attackFrameIndex,
        )

        val finished = clock.update(state, 1_000L + attack.durationMilliseconds)
        assertEquals(null, finished.attackDirection)
        assertEquals(null, finished.attackFrameIndex)
        assertEquals(
            requireNotNull(BossPresentationCatalog.definition("ancient_war_oak").idleAnimation)
                .frameIndex(1_000L + attack.durationMilliseconds),
            finished.idleFrameIndex,
        )
    }

    @Test
    fun leftwardAttackPlaysItsSequenceFromTheStartEventAndReturnsToIdle() {
        val clock = BossPresentationClock()
        val state = state()
        val attack = requireNotNull(
            BossPresentationCatalog.definition("ancient_war_oak")
                .attackAnimation(BossAttackDirection.LEFT),
        )
        clock.update(state, 0L)

        val trigger = state.copy(
            bossAttack = BossAttackRenderState(
                eventId = "attack-left",
                bossId = "boss-1",
                direction = BossAttackDirection.LEFT,
            ),
        )
        val started = clock.update(trigger, 500L)
        assertEquals(BossAttackDirection.LEFT, started.attackDirection)
        assertEquals(0, started.attackFrameIndex)

        val halfway = clock.update(trigger, 500L + attack.durationMilliseconds / 2)
        assertEquals(attack.frameCount / 2, halfway.attackFrameIndex)

        val finished = clock.update(trigger, 500L + attack.durationMilliseconds)
        assertEquals(null, finished.attackDirection)
        assertEquals(null, finished.attackFrameIndex)
    }

    @Test
    fun oakDeathAnimationPlaysToItsLastFrameBeforeReportingCompletion() {
        val clock = BossPresentationClock()
        val alive = state()
        val boss = requireNotNull(alive.boss)
        val defeated = alive.copy(
            boss = boss.copy(health = 0, phase = BossPhase.DEFEATED),
        )
        val death = requireNotNull(
            BossPresentationCatalog.definition("ancient_war_oak").deathAnimation(),
        )

        val started = clock.update(defeated, 100L)
        assertEquals(0, started.deathAnimationFrameIndex)
        assertEquals(true, started.deathPresentationActive)
        assertEquals(false, started.deathPresentationComplete)
        assertEquals(false, clock.isDeathPresentationComplete(boss.entityId))

        val halfway = clock.update(
            defeated,
            100L + death.durationMilliseconds / 2L,
        )
        assertEquals(death.frameCount / 2, halfway.deathAnimationFrameIndex)
        assertEquals(false, halfway.deathPresentationComplete)

        val finished = clock.update(defeated, 100L + death.durationMilliseconds)
        assertEquals(death.frameCount - 1, finished.deathAnimationFrameIndex)
        assertEquals(true, finished.deathPresentationActive)
        assertEquals(true, finished.deathPresentationComplete)
        assertEquals(true, clock.isDeathPresentationComplete(boss.entityId))
    }

    @Test
    fun bossesWithoutDeathAssetsHoldTheirStaticPresentationForTheSharedDeathWindow() {
        val clock = BossPresentationClock()
        val alive = state()
        val boss = requireNotNull(alive.boss).copy(
            presentationKey = "thorncrown",
            health = 0,
            phase = BossPhase.DEFEATED,
        )
        val defeated = alive.copy(boss = boss)

        val started = clock.update(defeated, 0L)
        assertEquals(null, started.deathAnimationFrameIndex)
        assertEquals(true, started.deathPresentationActive)
        assertEquals(false, started.deathPresentationComplete)

        val finished = clock.update(
            defeated,
            BossPresentationClock.DEFAULT_DEATH_PRESENTATION_DURATION_MILLISECONDS,
        )
        assertEquals(true, finished.deathPresentationActive)
        assertEquals(true, finished.deathPresentationComplete)
        assertEquals(true, clock.isDeathPresentationComplete(boss.entityId))
    }

    private fun state(): RenderState =
        RenderStateMapper.map(
            MatchSnapshot(matchId = MatchId("boss-presentation-match")),
        ).copy(
            boss = BossRenderState(
                entityId = "boss-1",
                profileId = "boss.central-ancient-war-oak.level-05",
                presentationKey = "ancient_war_oak",
                archetype = BossArchetype.CENTRAL_OBJECTIVE,
                phase = BossPhase.MARCHING,
                x = 1_000f,
                groundY = 1_000f,
                collisionWidth = 1_440f,
                collisionHeight = 2_016f,
                health = 12_000,
                maxHealth = 12_000,
                blueDamage = 0,
                redDamage = 0,
            ),
        )
}
