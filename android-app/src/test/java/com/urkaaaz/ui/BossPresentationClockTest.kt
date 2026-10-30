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
    fun leftwardAttackWithoutASequenceKeepsIdlePlayback() {
        val clock = BossPresentationClock()
        val state = state()
        clock.update(state, 0L)

        val frame = clock.update(
            state.copy(
                bossAttack = BossAttackRenderState(
                    eventId = "attack-left",
                    bossId = "boss-1",
                    direction = BossAttackDirection.LEFT,
                ),
            ),
            500L,
        )
        assertEquals(null, frame.attackDirection)
        assertEquals(null, frame.attackFrameIndex)
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
