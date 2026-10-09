package com.urkaaaz

import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.Team
import com.urkaaaz.ui.RenderStateMapper
import org.junit.Test
import org.junit.Assert.assertEquals

class RenderStateMapperTest {
    @Test
    fun mapperExposesOnlyPresentationValuesFromSnapshot() {
        val state = RenderStateMapper.map(
            MatchSnapshot(
                status = MatchStatus.RUNNING,
                phase = MatchPhase.PLAYER_TURN,
                activeTeam = Team.BLUE,
            ),
        )

        assertEquals("RUNNING", state.statusLabel)
        assertEquals("PLAYER_TURN", state.phaseLabel)
        assertEquals("BLUE", state.activeTeamLabel)
        assertEquals(0, state.projectileCount)
        assertEquals("GREEN_FRONTIER", state.terrainBiome)
        assertEquals(0, state.projectiles.size)
        assertEquals(0, state.windStrength.toInt())
        assertEquals(null, state.impact)
        assertEquals(null, state.outcomeLabel)
        assertEquals(0f, state.playerReloadRemainingSeconds)
        assertEquals(0f, state.enemyReloadRemainingSeconds)
        assertEquals(false, state.playerProjectileActive)
    }
}
