package com.urkaaaz.ui

import com.urkaaaz.contracts.ActiveSpellSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HasteRenderStateTest {
    @Test
    fun renderStateExposesPlayerHasteTimerAndPerUnitStatus() {
        val state = RenderStateMapper.map(
            MatchSnapshot(
                units = listOf(
                    UnitSnapshot(
                        entityId = EntityId("blue-defender"),
                        team = Team.BLUE,
                        unitType = "DEFENDER",
                        x = 120f,
                        y = 400f,
                        health = 50,
                        statuses = mapOf("HASTED" to 12.5f),
                    ),
                ),
                activeSpells = listOf(
                    ActiveSpellSnapshot(
                        spellType = "HASTE",
                        team = Team.BLUE,
                        remainingMilliseconds = 12_500L,
                    ),
                    ActiveSpellSnapshot(
                        spellType = "HASTE",
                        team = Team.RED,
                        remainingMilliseconds = 9_000L,
                    ),
                ),
            ),
        )

        assertEquals(setOf("HASTE"), state.activeSpellTypes)
        assertEquals(12_500L, state.activeSpellRemainingMilliseconds["HASTE"])
        assertTrue(state.units.single().hasted)
    }
}
