package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DemolisherAttackAnimationClockTest {
    @Test
    fun throwingProgressUsesOnlyTheForwardThrowWindowOfTheFullSource() {
        val clock = DemolisherAttackAnimationClock()
        val demolisher = unit(
            entityId = "demolisher",
            actionState = "THROWING",
            attackProgress = 0f,
        )

        clock.update(listOf(demolisher), frameCount = 97)
        assertEquals(
            DemolisherAttackAnimation.THROW_START_FRAME_INDEX,
            clock.frameIndex("demolisher", 97),
        )
        assertEquals(setOf("demolisher"), clock.activeEntityIds())

        clock.update(listOf(demolisher.copy(attackProgress = 0.5f)), frameCount = 97)
        assertEquals(45, clock.frameIndex("demolisher", 97))

        clock.update(listOf(demolisher.copy(attackProgress = 1f)), frameCount = 97)
        assertEquals(
            DemolisherAttackAnimation.THROW_END_FRAME_INDEX,
            clock.frameIndex("demolisher", 97),
        )

        clock.update(
            listOf(demolisher.copy(actionState = "DEAD", attackProgress = 1f)),
            frameCount = 97,
        )
        assertNull(clock.frameIndex("demolisher", 97))
        assertEquals(emptySet<String>(), clock.activeEntityIds())
    }

    @Test
    fun otherActionsDoNotPlayTheStructureThrowWindow() {
        val clock = DemolisherAttackAnimationClock()
        val demolisher = unit(
            entityId = "demolisher",
            actionState = "SEEKING_TARGET",
            attackProgress = 0.75f,
        )

        clock.update(listOf(demolisher), frameCount = 97)

        assertNull(clock.frameIndex(demolisher.entityId, 97))
        assertEquals(emptySet<String>(), clock.activeEntityIds())
    }

    private fun unit(
        entityId: String,
        actionState: String,
        attackProgress: Float,
    ) = UnitRenderState(
        entityId = entityId,
        teamLabel = "BLUE",
        unitType = "DEMOLISHER",
        x = 0f,
        y = 0f,
        health = 100,
        maxHealth = 100,
        moving = false,
        attackType = "CONTACT_EXPLOSIVE",
        actionState = actionState,
        targetId = "red-fortress",
        attackGroupId = null,
        attackCycleId = 1,
        attackProgress = attackProgress,
        facingDirection = 1f,
        sapperHasBomb = false,
    )
}
