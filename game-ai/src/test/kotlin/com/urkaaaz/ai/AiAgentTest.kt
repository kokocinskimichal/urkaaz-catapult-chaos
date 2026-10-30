package com.urkaaaz.ai

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.BossSnapshot
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitSnapshot
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiAgentTest {
    private val snapshot = MatchSnapshot(
        status = MatchStatus.RUNNING,
        matchId = MatchId("match-1"),
        phase = MatchPhase.PLAYER_TURN,
        activeTeam = Team.BLUE,
        catapults = listOf(
            CatapultSnapshot(EntityId("blue-catapult"), Team.BLUE, 100f, 400f),
            CatapultSnapshot(EntityId("red-catapult"), Team.RED, 900f, 400f),
        ),
        resources = ResourceSnapshot(
            gold = 10,
            supply = 100,
            ammunition = mapOf("ROCK" to 1, "SIEGE_BOMB" to 1),
        ),
    )

    @Test
    fun cadetProducesValidatedCatapultCommandsFromSnapshot() {
        val commands = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                randomSeed = 42L,
            ),
        ).decide(snapshot, includeUnitActions = false)

        assertEquals(3, commands.size)
        assertTrue(commands[0] is MatchCommand.SelectAmmo)
        assertTrue(commands[1] is MatchCommand.Aim)
        assertTrue(commands[2] is MatchCommand.Fire)
        val aim = commands[1] as MatchCommand.Aim
        assertTrue(aim.directionDegrees in 0f..180f)
        assertTrue(aim.power in 0f..100f)
        assertTrue(commands.all { it.matchId == MatchId("match-1") })
        assertEquals(3, commands.map { it.commandId }.distinct().size)
    }

    @Test
    fun seededAimIsReproducible() {
        fun commands() = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                randomSeed = 42L,
            ),
        ).decide(snapshot, includeUnitActions = false)

        assertEquals(commands(), commands())
    }

    @Test
    fun aiTargetsTheCentralObjectiveWhenOneIsActive() {
        val commands = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                randomSeed = 42L,
            ),
        ).decide(
            snapshot.copy(
                boss = BossSnapshot(
                    entityId = EntityId("boss-oak"),
                    profileId = "boss.central-oak.level-05",
                    presentationKey = "ancient_war_oak",
                    archetype = BossArchetype.CENTRAL_OBJECTIVE,
                    phase = BossPhase.IDLE,
                    x = 500f,
                    groundY = 350f,
                    collisionWidth = 100f,
                    collisionHeight = 200f,
                    health = 10_000,
                    maxHealth = 12_000,
                ),
            ),
            includeUnitActions = false,
        )
        val aim = commands.filterIsInstance<MatchCommand.Aim>().single()
        val normalAim = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                randomSeed = 42L,
            ),
        ).decide(snapshot, includeUnitActions = false)
            .filterIsInstance<MatchCommand.Aim>()
            .single()

        assertNotEquals(normalAim.directionDegrees, aim.directionDegrees)
    }

    @Test
    fun aiUsesOnlyAllowedAmmunitionAndPrefersFortressDamageWhenAvailable() {
        val agent = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                allowedAmmunition = setOf(AmmunitionType.ROCK, AmmunitionType.SIEGE_BOMB),
            ),
        )
        val restrictedSnapshot = snapshot.copy(
            resources = snapshot.resources.copy(
                ammunition = mapOf("PLAGUE_CAULDRON" to 2, "SIEGE_BOMB" to 1),
            ),
        )

        val selected = agent.decide(restrictedSnapshot, includeUnitActions = false)
            .filterIsInstance<MatchCommand.SelectAmmo>()
            .single()

        assertEquals("SIEGE_BOMB", selected.ammunitionType)
        assertEquals(setOf("ROCK", "SIEGE_BOMB"), agent.allowedAmmunitionIds())
    }

    @Test
    fun simpleRecruitmentChoosesAnAffordableUnitAndHonorsSapperLimit() {
        val allowed = setOf(UnitFactory.defender(), UnitFactory.sapper())
        val agent = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                allowedUnits = allowed,
            ),
        )
        val deployment = agent.decide(
            snapshot.copy(resources = snapshot.resources.copy(supply = UnitFactory.defender().supplyCost)),
            includeUnitActions = true,
        ).filterIsInstance<MatchCommand.DeployUnit>().single()

        assertEquals(UnitFactory.defender().id, deployment.unitType)

        val withSapper = snapshot.copy(
            units = listOf(
                UnitSnapshot(
                    entityId = EntityId("red-sapper-1"),
                    team = Team.RED,
                    unitType = UnitFactory.sapper().id,
                    x = 700f,
                    y = 400f,
                    health = UnitFactory.sapper().maxHealth,
                ),
            ),
        )
        val noSecondSapper = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                allowedUnits = setOf(UnitFactory.sapper()),
            ),
        ).decide(withSapper).filterIsInstance<MatchCommand.DeployUnit>()

        assertTrue(noSecondSapper.isEmpty())
    }

    @Test
    fun veteranRecruitmentSelectsDefenderToCounterEnemySapper() {
        val deployment = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                allowedUnits = setOf(UnitFactory.defender(), UnitFactory.sapper()),
            ),
        ).decide(
            snapshot.copy(
                units = listOf(
                    UnitSnapshot(
                        entityId = EntityId("blue-sapper"),
                        team = Team.BLUE,
                        unitType = UnitFactory.sapper().id,
                        x = 300f,
                        y = 400f,
                        health = UnitFactory.sapper().maxHealth,
                    ),
                ),
            ),
        ).filterIsInstance<MatchCommand.DeployUnit>().single()

        assertEquals(UnitFactory.defender().id, deployment.unitType)
    }

    @Test
    fun profileProgressionIsBoundedAndImprovesEliteAccuracyAndReaction() {
        val catalog = AiProfileCatalog.standard()
        val start = catalog.tuning(AiProfileSelection(AiProfileId.ELITE))
        val end = catalog.tuning(AiProfileSelection(AiProfileId.ELITE, progressionPercent = 100))

        assertEquals(400L, start.reactionIntervalMilliseconds)
        assertEquals(200L, end.reactionIntervalMilliseconds)
        assertEquals(start.angleErrorDegrees * 0.5f, end.angleErrorDegrees)
        assertEquals(start.powerError * 0.5f, end.powerError)
        assertNotEquals(start, end)
    }

    @Test
    fun cadetHasWiderAimErrorThanHigherProfiles() {
        val catalog = AiProfileCatalog.standard()
        val cadet = catalog.tuning(AiProfileSelection(AiProfileId.CADET))
        val veteran = catalog.tuning(AiProfileSelection(AiProfileId.VETERAN))
        val elite = catalog.tuning(AiProfileSelection(AiProfileId.ELITE))

        assertEquals(28f, cadet.angleErrorDegrees)
        assertEquals(45f, cadet.powerError)
        assertEquals(8_000L, cadet.unitRecruitmentIntervalMilliseconds)
        assertEquals(3f, veteran.angleErrorDegrees)
        assertEquals(6f, veteran.powerError)
        assertEquals(0L, veteran.unitRecruitmentIntervalMilliseconds)
        assertEquals(1.25f, elite.angleErrorDegrees)
        assertEquals(2.5f, elite.powerError)
        assertEquals(0L, elite.unitRecruitmentIntervalMilliseconds)
    }

    @Test
    fun eliteLeadsMovingThreatsByItsReactionHorizon() {
        val stationaryThreat = UnitSnapshot(
            entityId = EntityId("blue-threat"),
            team = Team.BLUE,
            unitType = UnitFactory.defender().id,
            x = 300f,
            y = 400f,
            health = UnitFactory.defender().maxHealth,
            actionState = "SEEKING_TARGET",
        )
        val movingThreat = stationaryThreat.copy(moving = true)
        fun aimPower(threat: UnitSnapshot): Float =
            AiAgent(
                AiConfiguration(
                    playerId = PlayerId("ai-player"),
                    team = Team.RED,
                    profile = AiProfileSelection(AiProfileId.ELITE),
                    randomSeed = 51L,
                ),
            ).decide(
                snapshot.copy(units = listOf(threat)),
                includeUnitActions = false,
            ).filterIsInstance<MatchCommand.Aim>().single().power

        assertTrue(aimPower(movingThreat) < aimPower(stationaryThreat))
    }

    @Test
    fun registryCanComposeCustomStrategiesWithoutChangingTheAgent() {
        val customProfile = AiProfileId("test-profile")
        var calls = 0
        val noOpCatapult = object : CatapultDecisionStrategy {
            override val id = AiStrategyId("test-catapult")

            override fun decide(context: AiStrategyContext): List<MatchCommand> {
                calls += 1
                return emptyList()
            }
        }
        val noOpRecruitment = object : UnitRecruitmentStrategy {
            override val id = AiStrategyId("test-recruitment")
            override fun chooseUnit(context: AiStrategyContext): UnitDefinition? = null
        }
        val noOpWave = object : WaveDispatchStrategy {
            override val id = AiStrategyId("test-wave")
            override fun shouldSend(context: AiStrategyContext) = false
        }
        val registry = AiStrategyRegistry(
            catapultStrategies = listOf(noOpCatapult),
            recruitmentStrategies = listOf(noOpRecruitment),
            waveStrategies = listOf(noOpWave),
            profiles = AiProfileCatalog(
                listOf(
                    AiProfileDefinition(
                        profileId = customProfile,
                        catapultStrategy = noOpCatapult.id,
                        recruitmentStrategy = noOpRecruitment.id,
                        waveStrategy = noOpWave.id,
                        angleErrorDegrees = 0f,
                        powerError = 0f,
                        reactionIntervalMilliseconds = 500L,
                    ),
                ),
            ),
        )
        val agent = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(customProfile),
            ),
            strategies = registry,
        )

        assertTrue(agent.decide(snapshot, includeUnitActions = false).isEmpty())
        assertEquals(1, calls)
    }

    @Test
    fun profileRejectsMissingStrategyWithAnExplicitError() {
        val profile = AiProfileId("missing-strategy")
        val registry = AiStrategyRegistry(
            catapultStrategies = emptyList(),
            recruitmentStrategies = emptyList(),
            waveStrategies = emptyList(),
            profiles = AiProfileCatalog(
                listOf(
                    AiProfileDefinition(
                        profileId = profile,
                        catapultStrategy = AiStrategyId("absent"),
                        recruitmentStrategy = AiStrategyId("absent"),
                        waveStrategy = AiStrategyId("absent"),
                        angleErrorDegrees = 0f,
                        powerError = 0f,
                        reactionIntervalMilliseconds = 500L,
                    ),
                ),
            ),
        )

        kotlin.test.assertFailsWith<IllegalStateException> {
            AiAgent(
                AiConfiguration(
                    playerId = PlayerId("ai-player"),
                    team = Team.RED,
                    profile = AiProfileSelection(profile),
                ),
                strategies = registry,
            )
        }
    }

    @Test
    fun noAvailableDecisionIsReturnedForUnstartedMatches() {
        val agent = AiAgent(
            AiConfiguration(
                playerId = PlayerId("ai-player"),
                team = Team.RED,
            ),
        )

        assertTrue(agent.decide(MatchSnapshot()).isEmpty())
    }
}
