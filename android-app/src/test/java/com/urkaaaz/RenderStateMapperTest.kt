package com.urkaaaz

import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.ProjectileSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WindSnapshot
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

    @Test
    fun mapperExposesElapsedTimeAndWindDirectionForHud() {
        val state = RenderStateMapper.map(
            MatchSnapshot(
                remainingMilliseconds = 125_000L,
                wind = WindSnapshot(direction = -1f, strength = 0.8f),
            ),
        )

        assertEquals(125_000L, state.matchTimeRemainingMilliseconds)
        assertEquals(-1f, state.windDirection)
        assertEquals(0.8f, state.windStrength)
    }

    @Test
    fun mapperPreservesProjectileIdentityForIndependentTrails() {
        val state = RenderStateMapper.map(
            MatchSnapshot(
                projectiles = listOf(
                    ProjectileSnapshot(
                        entityId = EntityId("projectile-42"),
                        firedBy = Team.BLUE,
                        ammunitionType = "ROCK",
                        x = 120f,
                        y = 648f,
                        velocityX = 100f,
                        velocityY = -100f,
                    ),
                ),
            ),
        )

        assertEquals("projectile-42", state.projectiles.single().projectileId)
    }
}
