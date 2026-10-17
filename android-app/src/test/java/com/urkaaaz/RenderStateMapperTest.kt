package com.urkaaaz

import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.EffectSnapshot
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.FortressPlacement
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
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
    fun mapperExposesAllFireRainSplitsAndActiveEffects() {
        val matchId = MatchId("fire-rain-match")
        val firstSplit = MatchEvent.ProjectileSplit(
            eventId = EventId("split-1"),
            matchId = matchId,
            simulationTimeMilliseconds = 1_000L,
            projectileId = EntityId("container-1"),
            fragmentProjectileIds = listOf(EntityId("fragment-1"), EntityId("fragment-2")),
            ammunitionType = "FIRE_RAIN",
            x = 1_200f,
            y = 700f,
        )
        val secondSplit = firstSplit.copy(
            eventId = EventId("split-2"),
            projectileId = EntityId("container-2"),
            fragmentProjectileIds = listOf(EntityId("fragment-3"), EntityId("fragment-4")),
        )
        val ignoredSplit = firstSplit.copy(
            eventId = EventId("other-ammunition-split"),
            ammunitionType = "ROCK",
        )
        val state = RenderStateMapper.map(
            snapshot = MatchSnapshot(
                matchId = matchId,
                effects = listOf(
                    EffectSnapshot(
                        effectType = "FIRE_RAIN",
                        remainingMilliseconds = 2_500L,
                        effectId = EntityId("fire-1"),
                        sourceProjectileId = EntityId("fragment-1"),
                        x = 1_400f,
                        y = 800f,
                        radius = 68f,
                    ),
                    EffectSnapshot(
                        effectType = "FIRE_RAIN",
                        remainingMilliseconds = 1_700L,
                        effectId = EntityId("fire-2"),
                        sourceProjectileId = EntityId("fragment-2"),
                        x = 1_600f,
                        y = 820f,
                        radius = 68f,
                    ),
                    EffectSnapshot(
                        effectType = "PLAGUE_CAULDRON",
                        remainingMilliseconds = 5_000L,
                        effectId = EntityId("plague-1"),
                    ),
                ),
            ),
            events = listOf(firstSplit, ignoredSplit, secondSplit),
        )

        assertEquals("fire-rain-match", state.matchId)
        assertEquals(
            listOf("split-1", "split-2"),
            state.fireRainSplits.map { it.eventId },
        )
        assertEquals("container-1", state.fireRainSplits.first().projectileId)
        assertEquals(listOf("fragment-1", "fragment-2"), state.fireRainSplits.first().fragmentProjectileIds)
        assertEquals(listOf("fire-1", "fire-2"), state.fireRainEffects.map { it.effectId })
        assertEquals(68f, state.fireRainEffects.first().radius)
    }

    @Test
    fun mapperUsesMatchStartedEventAsPresentationGeneration() {
        val matchId = MatchId("restartable-match")
        val eventId = EventId("match-start-2")
        val state = RenderStateMapper.map(
            snapshot = MatchSnapshot(matchId = matchId),
            events = listOf(
                MatchEvent.MatchStarted(
                    eventId = eventId,
                    matchId = matchId,
                    simulationTimeMilliseconds = 0L,
                ),
            ),
        )

        assertEquals("restartable-match", state.matchId)
        assertEquals("match-start-2", state.matchStartEventId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mapperRejectsFireRainEffectWithoutStableId() {
        RenderStateMapper.map(
            MatchSnapshot(
                effects = listOf(
                    EffectSnapshot(
                        effectType = "FIRE_RAIN",
                        remainingMilliseconds = 1_000L,
                    ),
                ),
            ),
        )
    }

    @Test
    fun mapperPlacesEntityImpactAtCollisionCoordinates() {
        val impactX = 2_910f
        val impactY = 1_340f
        val state = RenderStateMapper.map(
            snapshot = MatchSnapshot(),
            events = listOf(
                MatchEvent.ProjectileHitEntity(
                    eventId = EventId("impact-1"),
                    matchId = MatchId("match-1"),
                    simulationTimeMilliseconds = 1_000L,
                    projectileId = EntityId("blue-projectile"),
                    targetId = EntityId("red-fortress"),
                    ammunitionType = "ROCK",
                    targetLayer = "FORTRESS",
                    x = impactX,
                    y = impactY,
                ),
            ),
        )

        assertEquals(impactX, state.impact?.x)
        assertEquals(impactY, state.impact?.y)
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
                        halfWidth = 470.4f,
                        assetKey = "fortress_blue",
                        hitboxProfile = "alpha_bounds",
                        protectedZoneId = "blue-foundation",
                    ),
                    FortressPlacement(
                        owner = Team.RED,
                        x = 6_680f,
                        foundationHeight = 3_180f,
                        halfWidth = 470.4f,
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
                initialCameraAnchor = InitialCameraAnchor.ENEMY_FORTRESS,
                initialCameraZoom = 1.3f,
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
        assertEquals(InitialCameraAnchor.ENEMY_FORTRESS, state.initialCameraAnchor)
        assertEquals(1.3f, state.initialCameraZoom)
    }
}
