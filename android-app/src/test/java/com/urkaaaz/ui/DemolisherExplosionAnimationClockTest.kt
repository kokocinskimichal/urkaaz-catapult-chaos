package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DemolisherExplosionAnimationClockTest {
    @Test
    fun startsAtFrameZeroAndAdvancesOncePerRenderFrameInterval() {
        val clock = DemolisherExplosionAnimationClock()
        val explosion = demolisherExplosion()

        assertEquals(0, clock.update(listOf(explosion), frameCount = 20, paused = false).single().animationFrameIndex)
        repeat(2) {
            clock.update(listOf(explosion), frameCount = 20, paused = false)
        }
        assertEquals(1, clock.update(listOf(explosion), frameCount = 20, paused = false).single().animationFrameIndex)
    }

    @Test
    fun pausesAndDoesNotRestartRepeatedEvents() {
        val clock = DemolisherExplosionAnimationClock()
        val explosion = demolisherExplosion()

        clock.update(listOf(explosion), frameCount = 20, paused = false)
        repeat(8) {
            clock.update(listOf(explosion), frameCount = 20, paused = false)
        }
        val paused = clock.update(listOf(explosion), frameCount = 20, paused = true).single()
        repeat(8) {
            assertEquals(
                paused.animationFrameIndex,
                clock.update(listOf(explosion), frameCount = 20, paused = true).single().animationFrameIndex,
            )
        }
        assertEquals(
            (paused.animationFrameIndex ?: 0) + 1,
            clock.update(listOf(explosion), frameCount = 20, paused = false).single().animationFrameIndex,
        )
    }

    @Test
    fun waitsForTheSourceDemolisherThrowBeforeStartingItsExplosion() {
        val clock = DemolisherExplosionAnimationClock()
        val explosion = demolisherExplosion().copy(sourceUnitId = "demolisher")
        val activeAttack = setOf("demolisher")

        assertEquals(
            emptyList<CombatFeedbackRenderState>(),
            clock.update(
                listOf(explosion),
                frameCount = 20,
                paused = false,
                activeDemolisherAttackUnitIds = activeAttack,
            ),
        )
        repeat(10) {
            assertEquals(
                emptyList<CombatFeedbackRenderState>(),
                clock.update(
                    emptyList(),
                    frameCount = 20,
                    paused = false,
                    activeDemolisherAttackUnitIds = activeAttack,
                ),
            )
        }
        assertEquals(
            0,
            clock.update(
                emptyList(),
                frameCount = 20,
                paused = false,
            ).single().animationFrameIndex,
        )
    }

    @Test
    fun hidesNoUnrelatedExplosionsAndRemovesFinishedDemolisherEffects() {
        val clock = DemolisherExplosionAnimationClock()
        val unrelated = demolisherExplosion().copy(
            eventId = "powder-barrel",
            sourceUnitType = null,
        )
        assertEquals(listOf(unrelated), clock.update(listOf(unrelated), frameCount = 20, paused = false))

        val explosion = demolisherExplosion()
        clock.update(listOf(explosion), frameCount = 20, paused = false)
        repeat(60) {
            clock.update(emptyList(), frameCount = 20, paused = false)
        }
        assertEquals(emptyList<CombatFeedbackRenderState>(), clock.update(emptyList(), frameCount = 20, paused = false))
    }

    private fun demolisherExplosion() = CombatFeedbackRenderState(
        eventId = "explosion-1",
        kind = CombatFeedbackRenderState.Kind.EXPLOSION,
        x = 0f,
        y = 0f,
        radius = 260f,
        sourceUnitType = "DEMOLISHER",
    )
}
