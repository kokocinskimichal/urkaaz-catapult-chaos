package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitDeathAnimationClockTest {
    @Test
    fun eachDeathAnimationStartsAtItsFirstFrameAndAdvancesIndependently() {
        val clock = UnitDeathAnimationClock()
        val firstDefender = unit("blue-defender")
        val secondDefender = unit("another-blue-defender")

        clock.advance(listOf(firstDefender))
        assertEquals(0, clock.frameIndex(firstDefender.entityId, 60))

        clock.advance(listOf(firstDefender))
        clock.advance(listOf(firstDefender))
        assertEquals(0, clock.frameIndex(firstDefender.entityId, 60))

        clock.advance(listOf(firstDefender, secondDefender))
        assertEquals(1, clock.frameIndex(firstDefender.entityId, 60))
        assertEquals(0, clock.frameIndex(secondDefender.entityId, 60))
    }

    @Test
    fun deathAnimationStopsAtLastFrameAndResetsAfterLeavingDeadState() {
        val clock = UnitDeathAnimationClock()
        val defender = unit("red-defender", "RED")

        repeat(300) { clock.advance(listOf(defender)) }
        assertEquals(59, clock.frameIndex(defender.entityId, 60))

        clock.advance(emptyList())
        assertEquals(0, clock.frameIndex(defender.entityId, 60))

        clock.advance(listOf(defender))
        assertEquals(0, clock.frameIndex(defender.entityId, 60))
        assertNull(clock.frameIndex(defender.entityId, 0))
    }

    private fun unit(entityId: String, teamLabel: String = "BLUE") = UnitRenderState(
        entityId = entityId,
        teamLabel = teamLabel,
        unitType = "DEFENDER",
        x = 0f,
        y = 0f,
        health = 0,
        maxHealth = 100,
        moving = false,
        attackType = "MELEE",
        actionState = "DEAD",
        targetId = null,
        attackGroupId = null,
        attackCycleId = 0,
        attackProgress = 0f,
        facingDirection = 1f,
        sapperHasBomb = true,
    )
}

class UnitDeathAnimationRetentionTest {
    @Test
    fun bothTeamsRemainVisibleUntilTheirGeneratedDeathAnimationsFinish() {
        listOf("BLUE", "RED").forEach { teamLabel ->
            val clock = UnitDeathAnimationClock()
            val retention = UnitDeathAnimationRetention(clock)
            val defender = defender("$teamLabel-defender", teamLabel)

            assertEquals(listOf(defender), retention.update(listOf(defender)))
            repeat(179) {
                assertEquals(listOf(defender), retention.update(emptyList()))
            }
            assertEquals(59, clock.frameIndex(defender.entityId, 60))
            assertEquals(listOf(defender), retention.update(emptyList()))
            assertEquals(emptyList<UnitRenderState>(), retention.update(emptyList()))
        }
    }

    @Test
    fun blueDemolisherRemainsVisibleUntilItsContactExplosionFinishes() {
        val clock = UnitDeathAnimationClock()
        val retention = UnitDeathAnimationRetention(clock)
        val demolisher = unit("blue-demolisher", "BLUE", "DEMOLISHER")

        assertEquals(listOf(demolisher), retention.update(listOf(demolisher)))
        repeat(179) {
            assertEquals(listOf(demolisher), retention.update(emptyList()))
        }
        assertEquals(59, clock.frameIndex(demolisher.entityId, 60))
        assertEquals(listOf(demolisher), retention.update(emptyList()))
        assertEquals(emptyList<UnitRenderState>(), retention.update(emptyList()))
    }

    @Test
    fun redDemolisherIsNotRetainedWithoutAnApprovedDeathAnimation() {
        val retention = UnitDeathAnimationRetention(UnitDeathAnimationClock())
        val demolisher = unit("red-demolisher", "RED", "DEMOLISHER")

        assertEquals(listOf(demolisher), retention.update(listOf(demolisher)))
        assertEquals(emptyList<UnitRenderState>(), retention.update(emptyList()))
    }

    private fun defender(entityId: String, teamLabel: String) = UnitRenderState(
        entityId = entityId,
        teamLabel = teamLabel,
        unitType = "DEFENDER",
        x = 0f,
        y = 0f,
        health = 0,
        maxHealth = 100,
        moving = false,
        attackType = "MELEE",
        actionState = "DEAD",
        targetId = null,
        attackGroupId = null,
        attackCycleId = 0,
        attackProgress = 0f,
        facingDirection = 1f,
        sapperHasBomb = true,
    )

    private fun unit(entityId: String, teamLabel: String, unitType: String) =
        UnitRenderState(
            entityId = entityId,
            teamLabel = teamLabel,
            unitType = unitType,
            x = 0f,
            y = 0f,
            health = 0,
            maxHealth = 100,
            moving = false,
            attackType = "MELEE",
            actionState = "DEAD",
            targetId = null,
            attackGroupId = null,
            attackCycleId = 0,
            attackProgress = 0f,
            facingDirection = 1f,
            sapperHasBomb = true,
        )
}
