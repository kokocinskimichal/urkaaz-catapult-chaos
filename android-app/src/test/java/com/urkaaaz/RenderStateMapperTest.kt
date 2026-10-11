package com.urkaaaz

import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.ProjectileSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.ui.RenderStateMapper
import com.urkaaaz.ui.CombatFeedbackRenderState
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

    @Test
    fun mapperExposesCombatFeedbackFromAttackEvents() {
        val attacker = EntityId("blue-defender-1")
        val target = EntityId("red-defender-1")
        val snapshot = MatchSnapshot(
            units = listOf(
                com.urkaaaz.contracts.UnitSnapshot(
                    entityId = target,
                    team = Team.RED,
                    unitType = "DEFENDER",
                    x = 820f,
                    y = 468f,
                    health = 200,
                    maxHealth = 260,
                ),
            ),
        )
        val event = com.urkaaaz.contracts.MatchEvent.AttackHit(
            eventId = com.urkaaaz.contracts.EventId("attack-hit"),
            matchId = com.urkaaaz.contracts.MatchId("combat"),
            simulationTimeMilliseconds = 500L,
            attackerId = attacker,
            targetId = target,
            attackType = "MELEE",
            attackCycleId = 1L,
            baseDamage = 30,
            rolledDamage = 32,
            synergyCount = 2,
            synergyMultiplier = 1.35f,
            finalDamage = 43,
        )

        val feedback = RenderStateMapper.map(snapshot, listOf(event)).combatFeedback.single()

        assertEquals(CombatFeedbackRenderState.Kind.DAMAGE, feedback.kind)
        assertEquals(820f, feedback.x)
        assertEquals(43, feedback.amount)
        assertEquals("x1.35", feedback.label)
    }
}
