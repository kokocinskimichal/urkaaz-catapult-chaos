package com.urkaaaz.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SapperAnimationPresentationTest {
    @Test
    fun keepsMoveAnimationBrieflyWhenSapperArrivesThenReturnsToIdle() {
        val clock = SapperMoveAnimationClock()
        val movingSapper = sapper()
        val arrivedSapper = movingSapper.copy(moving = false)

        clock.advance(listOf(movingSapper), isPaused = false)
        clock.advance(listOf(arrivedSapper), isPaused = false)
        assertTrue(clock.shouldUseMoveAnimation(arrivedSapper, attacking = false))

        repeat(3) {
            clock.advance(listOf(arrivedSapper), isPaused = false)
            assertTrue(clock.shouldUseMoveAnimation(arrivedSapper, attacking = false))
        }

        clock.advance(listOf(arrivedSapper), isPaused = false)
        assertFalse(clock.shouldUseMoveAnimation(arrivedSapper, attacking = false))
    }

    @Test
    fun doesNotStartArrivalGraceForAlreadyStationarySapper() {
        val clock = SapperMoveAnimationClock()
        val sapper = sapper().copy(moving = false)

        clock.advance(listOf(sapper), isPaused = false)

        assertFalse(clock.shouldUseMoveAnimation(sapper, attacking = false))
    }

    @Test
    fun cancelsArrivalGraceWhenAttackBegins() {
        val clock = SapperMoveAnimationClock()
        val movingSapper = sapper()
        val arrivedSapper = movingSapper.copy(moving = false)
        clock.advance(listOf(movingSapper), isPaused = false)
        clock.advance(listOf(arrivedSapper), isPaused = false)
        val attackingSapper = arrivedSapper.copy(actionState = "WINDING_UP")

        clock.advance(listOf(attackingSapper), isPaused = false)

        assertFalse(clock.shouldUseMoveAnimation(attackingSapper, attacking = true))
        assertFalse(
            clock.shouldUseMoveAnimation(
                attackingSapper.copy(actionState = "MOVING"),
                attacking = false,
            ),
        )
    }

    @Test
    fun doesNotStartArrivalGraceWithoutBomb() {
        val clock = SapperMoveAnimationClock()
        val movingSapper = sapper().copy(sapperHasBomb = false)
        val arrivedSapper = movingSapper.copy(moving = false)

        clock.advance(listOf(movingSapper), isPaused = false)
        clock.advance(listOf(arrivedSapper), isPaused = false)

        assertFalse(clock.shouldUseMoveAnimation(arrivedSapper, attacking = false))
    }

    private fun sapper() = UnitRenderState(
        entityId = "sapper",
        teamLabel = "BLUE",
        unitType = "SAPPER",
        x = 0f,
        y = 0f,
        health = 100,
        maxHealth = 100,
        moving = true,
        attackType = "SIEGE_MISSION",
        actionState = "MOVING",
        targetId = null,
        attackGroupId = null,
        attackCycleId = 0,
        attackProgress = 0f,
        facingDirection = 1f,
        sapperHasBomb = true,
    )
}
