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

    @Test
    fun prestartedDemolisherHoldsBeforeExplosionUntilSimulationConfirmsDeath() {
        val clock = UnitDeathAnimationClock()
        val aliveDemolisher = unit("demolisher").copy(
            unitType = "DEMOLISHER",
            actionState = "SEEKING_TARGET",
            health = 100,
            deathAnimationPrestarted = true,
        )

        repeat(100) { clock.advance(listOf(aliveDemolisher)) }
        assertEquals(12, clock.frameIndex(aliveDemolisher.entityId, 32))

        clock.advance(
            listOf(
                aliveDemolisher.copy(
                    actionState = "DEAD",
                    health = 0,
                    deathAnimationPrestarted = false,
                ),
            ),
        )
        assertEquals(13, clock.frameIndex(aliveDemolisher.entityId, 32))
    }

    @Test
    fun cancelledPrestartedAnimationDoesNotCarryIntoLaterDeath() {
        val clock = UnitDeathAnimationClock()
        val aliveDemolisher = unit("demolisher").copy(
            unitType = "DEMOLISHER",
            actionState = "SEEKING_TARGET",
            health = 100,
            deathAnimationPrestarted = true,
        )

        clock.advance(listOf(aliveDemolisher))
        clock.advance(emptyList())
        clock.advance(
            listOf(
                aliveDemolisher.copy(
                    actionState = "DEAD",
                    health = 0,
                    deathAnimationPrestarted = false,
                ),
            ),
        )

        assertEquals(0, clock.frameIndex(aliveDemolisher.entityId, 32))
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
    fun bothTeamsDemolisherRemainVisibleUntilTheirDeathAnimationFinishes() {
        listOf("BLUE", "RED").forEach { teamLabel ->
            val clock = UnitDeathAnimationClock()
            val retention = UnitDeathAnimationRetention(clock)
            val demolisher = unit("$teamLabel-demolisher", teamLabel, "DEMOLISHER")

            assertEquals(listOf(demolisher), retention.update(listOf(demolisher)))
            repeat(95) {
                assertEquals(listOf(demolisher), retention.update(emptyList()))
            }
            assertEquals(31, clock.frameIndex(demolisher.entityId, 32))
            assertEquals(listOf(demolisher), retention.update(emptyList()))
            assertEquals(emptyList<UnitRenderState>(), retention.update(emptyList()))
        }
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
