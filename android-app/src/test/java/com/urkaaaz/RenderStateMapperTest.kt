package com.urkaaaz

import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.FortressPlacement
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MapDecoration
import com.urkaaaz.contracts.MapGenerationMetadata
import com.urkaaaz.contracts.MapWorldDimensions
import com.urkaaaz.contracts.TerrainProfile
import com.urkaaaz.contracts.TerrainSample
import com.urkaaaz.contracts.ProjectileSnapshot
import com.urkaaaz.contracts.RelicSize
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
    fun mapperUsesFortressCoordinatesFromSimulationSnapshot() {
        val state = RenderStateMapper.map(
            MatchSnapshot(
                fortresses = listOf(
                    FortressSnapshot(
                        EntityId("blue-fortress"),
                        Team.BLUE,
                        health = 500,
                        maxHealth = 500,
                        x = 160f,
                        foundationY = 432f,
                    ),
                    FortressSnapshot(
                        EntityId("red-fortress"),
                        Team.RED,
                        health = 500,
                        maxHealth = 500,
                        x = 840f,
                        foundationY = 432f,
                    ),
                ),
            ),
        )

        assertEquals(160f, state.blueFortressX)
        assertEquals(840f, state.redFortressX)
        assertEquals(432f, state.blueFortressFoundationY)
        assertEquals(432f, state.redFortressFoundationY)
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

    @Test
    fun mapperExposesTypedVisualMapDecorations() {
        val snapshot = MatchSnapshot(
            mapDefinition = MapDefinition(
                schemaVersion = 1,
                mapId = "render-map",
                biome = "green_frontier",
                world = MapWorldDimensions(7_200, 4_800),
                terrain = TerrainProfile(
                    sampleStep = 40f,
                    samples = listOf(
                        TerrainSample(0f, 50f),
                        TerrainSample(40f, 55f),
                        TerrainSample(7_200f, 60f),
                    ),
                ),
                protectedTerrainZones = emptyList(),
                fortresses = listOf(
                    FortressPlacement(
                        owner = Team.BLUE,
                        x = 520f,
                        foundationHeight = 3_180f,
                        halfWidth = 98f,
                        assetKey = "fortress_blue",
                        hitboxProfile = "alpha_bounds",
                        protectedZoneId = "blue-foundation",
                    ),
                    FortressPlacement(
                        owner = Team.RED,
                        x = 6_680f,
                        foundationHeight = 3_180f,
                        halfWidth = 98f,
                        assetKey = "fortress_red",
                        hitboxProfile = "alpha_bounds",
                        protectedZoneId = "red-foundation",
                    ),
                ),
                decorations = listOf(
                    MapDecoration(
                        id = "tree-1",
                        x = 40f,
                        y = 55f,
                        assetKey = "terrain_green_frontier_tree_00",
                        scale = 1f,
                        rotationDegrees = 0f,
                    ),
                    MapDecoration(
                        id = "relic-1",
                        x = 60f,
                        y = 100f,
                        assetKey = "relic.green_frontier.small.1",
                        scale = 1f,
                        rotationDegrees = 0f,
                        relicSize = RelicSize.SMALL,
                    ),
                ),
                generationMetadata = MapGenerationMetadata(
                    seed = 3L,
                    generatorVersion = "test",
                ),
            ),
        )

        val state = RenderStateMapper.map(snapshot)

        assertEquals("terrain_green_frontier_tree_00", state.mapObjects.first().assetKey)
        assertEquals("SMALL", state.mapObjects[1].relicSize)
        assertEquals(76f, state.mapObjects[1].relicRenderSize)
        assertEquals(520f, state.blueFortressX)
        assertEquals(3_180f, state.blueFortressFoundationY)
        assertEquals(6_680f, state.redFortressX)
        assertEquals(3_180f, state.redFortressFoundationY)
    }
}
