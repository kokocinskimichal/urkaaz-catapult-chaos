package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SapperReturnAnimationClockTest {
    @Test
    fun playsTurnOnceThenLoopsReturnAnimation() {
        val clock = SapperReturnAnimationClock()
        val sapper = sapper("blue-sapper")

        clock.advance(listOf(sapper), isPaused = false)
        assertFrame(clock, sapper, SapperReturnAnimationFrame.Phase.TURN, 0)

        repeat(TICKS_PER_FRAME * FRAME_COUNT - 1) {
            clock.advance(listOf(sapper), isPaused = false)
        }
        assertFrame(clock, sapper, SapperReturnAnimationFrame.Phase.TURN, FRAME_COUNT - 1)

        clock.advance(listOf(sapper), isPaused = false)
        assertFrame(clock, sapper, SapperReturnAnimationFrame.Phase.RETURN, 0)

        repeat(TICKS_PER_FRAME * FRAME_COUNT) {
            clock.advance(listOf(sapper), isPaused = false)
        }
        assertFrame(clock, sapper, SapperReturnAnimationFrame.Phase.RETURN, 0)
    }

    @Test
    fun tracksSappersIndependentlyAndPausesTheirPlayback() {
        val clock = SapperReturnAnimationClock()
        val first = sapper("first")
        val second = sapper("second", "RED")

        clock.advance(listOf(first), isPaused = false)
        repeat(TICKS_PER_FRAME * 2) {
            clock.advance(listOf(first), isPaused = false)
        }
        clock.advance(listOf(first, second), isPaused = true)

        assertFrame(clock, first, SapperReturnAnimationFrame.Phase.TURN, 2)
        assertFrame(clock, second, SapperReturnAnimationFrame.Phase.TURN, 0)

        repeat(TICKS_PER_FRAME) {
            clock.advance(listOf(first, second), isPaused = false)
        }
        assertFrame(clock, first, SapperReturnAnimationFrame.Phase.TURN, 3)
        assertFrame(clock, second, SapperReturnAnimationFrame.Phase.TURN, 1)
    }

    @Test
    fun leavingReturningResetsPlaybackAndNewReturnStartsWithTurn() {
        val clock = SapperReturnAnimationClock()
        val sapper = sapper("blue-sapper")

        repeat(TICKS_PER_FRAME * 8) {
            clock.advance(listOf(sapper), isPaused = false)
        }
        clock.advance(listOf(sapper.copy(actionState = "SEEKING_TARGET")), isPaused = false)
        clock.advance(listOf(sapper), isPaused = false)

        assertFrame(clock, sapper, SapperReturnAnimationFrame.Phase.TURN, 0)
    }

    private fun assertFrame(
        clock: SapperReturnAnimationClock,
        sapper: UnitRenderState,
        phase: SapperReturnAnimationFrame.Phase,
        index: Int,
    ) {
        assertEquals(
            SapperReturnAnimationFrame(phase, index),
            clock.frame(sapper.entityId, FRAME_COUNT, FRAME_COUNT),
        )
    }

    private fun sapper(entityId: String, teamLabel: String = "BLUE") = UnitRenderState(
        entityId = entityId,
        teamLabel = teamLabel,
        unitType = "SAPPER",
        x = 0f,
        y = 0f,
        health = 100,
        maxHealth = 100,
        moving = true,
        attackType = "SIEGE_MISSION",
        actionState = "RETURNING",
        targetId = null,
        attackGroupId = null,
        attackCycleId = 0,
        attackProgress = 0f,
        facingDirection = -1f,
        sapperHasBomb = false,
    )

    private companion object {
        const val FRAME_COUNT = 15
        const val TICKS_PER_FRAME = 3
    }
}
