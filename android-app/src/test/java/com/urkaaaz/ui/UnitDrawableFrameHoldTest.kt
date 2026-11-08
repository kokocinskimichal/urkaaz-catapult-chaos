package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitDrawableFrameHoldTest {
    @Test
    fun pendingFrameUsesLastReadyFrameForTheSameUnit() {
        val hold = UnitDrawableFrameHold<String>()
        val unit = unitKey()

        assertEquals("move-0", hold.resolve(unit, "move-0"))
        assertEquals("move-0", hold.resolve(unit, null))
        assertEquals("attack-0", hold.resolve(unit, "attack-0"))
        assertEquals("attack-0", hold.resolve(unit, null))
    }

    @Test
    fun heldFramesDoNotLeakAcrossUnitsTeamsOrMatchGenerations() {
        val hold = UnitDrawableFrameHold<String>()
        val blue = unitKey(teamLabel = "BLUE")
        val red = unitKey(teamLabel = "RED")

        hold.resolve(blue, "blue-frame")

        assertNull(hold.resolve(unitKey(entityId = "other-unit"), null))
        assertNull(hold.resolve(red, null))
        assertNull(hold.resolve(unitKey(matchGeneration = 2L), null))
    }

    @Test
    fun removedUnitsReleaseTheirHeldFrame() {
        val hold = UnitDrawableFrameHold<String>()
        val unit = unitKey()
        hold.resolve(unit, "frame")

        hold.retainOnly(emptySet())

        assertNull(hold.lastReady(unit))
        assertNull(hold.resolve(unit, null))
    }

    @Test
    fun heldFramesStayWithinTheConfiguredByteBudget() {
        val hold = UnitDrawableFrameHold<String>(
            maxSizeBytes = 2,
            sizeOf = { 1 },
        )
        val firstUnit = unitKey(entityId = "first")
        val secondUnit = unitKey(entityId = "second")
        val thirdUnit = unitKey(entityId = "third")

        hold.resolve(firstUnit, "a")
        hold.resolve(secondUnit, "b")
        assertEquals("a", hold.lastReady(firstUnit))
        hold.resolve(thirdUnit, "c")

        assertEquals("a", hold.lastReady(firstUnit))
        assertNull(hold.lastReady(secondUnit))
        assertEquals("c", hold.lastReady(thirdUnit))
    }

    private fun unitKey(
        matchId: String? = "match-1",
        matchGeneration: Long = 1L,
        entityId: String = "defender-1",
        unitType: String = "DEFENDER",
        teamLabel: String = "BLUE",
    ) = UnitDrawableFrameKey(
        matchId = matchId,
        matchGeneration = matchGeneration,
        entityId = entityId,
        unitType = unitType,
        teamLabel = teamLabel,
    )
}
