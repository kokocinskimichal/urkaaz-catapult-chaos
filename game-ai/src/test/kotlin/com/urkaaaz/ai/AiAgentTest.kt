package com.urkaaaz.ai

import com.urkaaaz.domain.GameBalanceFixtures
import com.urkaaaz.domain.AiProfileBalance
import com.urkaaaz.domain.UnitIds

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.BossSnapshot
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.TerrainSnapshot
import com.urkaaaz.contracts.UnitSnapshot
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.SpellType
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
                balance = GameBalanceFixtures.balance,
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
                balance = GameBalanceFixtures.balance,
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
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                randomSeed = 42L,
            ),
        ).decide(centralObjectiveSnapshot(), includeUnitActions = false)
        val aim = commands.filterIsInstance<MatchCommand.Aim>().single()
        val normalAim = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
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
    fun mobileBossDoesNotOverrideTheExistingCatapultTarget() {
        fun aim(snapshot: MatchSnapshot) = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                randomSeed = 42L,
            ),
        ).decide(snapshot, includeUnitActions = false)
            .filterIsInstance<MatchCommand.Aim>()
            .single()

        val ordinaryAim = aim(snapshot)
        val mobileBossAim = aim(
            centralObjectiveSnapshot().copy(
                boss = requireNotNull(centralObjectiveSnapshot().boss).copy(
                    archetype = BossArchetype.MOBILE_SIEGE_BOSS,
                ),
            ),
        )

        assertEquals(ordinaryAim.directionDegrees, mobileBossAim.directionDegrees)
        assertEquals(ordinaryAim.power, mobileBossAim.power)
    }

    @Test
    fun centralObjectiveSkipsReloadingAmmoBeforeScoring() {
        val commands = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                randomSeed = 42L,
            ),
        ).decide(
            centralObjectiveSnapshot().copy(
                reloadRemainingSecondsByAmmunition = mapOf(
                    Team.RED to mapOf("SIEGE_BOMB" to 2f),
                ),
            ),
            includeUnitActions = false,
        )

        assertEquals(
            "ROCK",
            commands.filterIsInstance<MatchCommand.SelectAmmo>().single().ammunitionType,
        )
    }

    @Test
    fun aiCastsOneUsefulSpellFromTheScenarioAvailability() {
        val spellSnapshot = snapshot.copy(
            fortresses = listOf(
                FortressSnapshot(
                    entityId = EntityId("red-fortress"),
                    team = Team.RED,
                    health = 100,
                    maxHealth = 500,
                ),
                FortressSnapshot(
                    entityId = EntityId("blue-fortress"),
                    team = Team.BLUE,
                    health = 500,
                    maxHealth = 500,
                ),
            ),
            units = listOf(
                UnitSnapshot(
                    entityId = EntityId("red-unit"),
                    team = Team.RED,
                    unitType = UnitIds.DEFENDER,
                    x = 700f,
                    y = 400f,
                    health = 100,
                    moving = true,
                    actionState = "ADVANCING",
                ),
                UnitSnapshot(
                    entityId = EntityId("blue-unit"),
                    team = Team.BLUE,
                    unitType = UnitIds.DEFENDER,
                    x = 800f,
                    y = 400f,
                    health = 100,
                    moving = true,
                    actionState = "ADVANCING",
                    targetId = EntityId("red-fortress"),
                ),
            ),
            resourcesByTeam = mapOf(
                Team.RED to ResourceSnapshot(mana = 100),
                Team.BLUE to ResourceSnapshot(mana = 100),
            ),
        )
        val commands = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                allowedSpells = setOf(
                    SpellType.FORTRESS_SHIELD,
                    SpellType.RUNIC_BASTION,
                    SpellType.HASTE,
                ),
            ),
        ).decide(spellSnapshot)

        assertEquals(1, commands.filterIsInstance<MatchCommand.CastSpell>().size)
    }

    @Test
    fun specialSpellPolicyRequiresTheMatchStock() {
        val balance = GameBalanceFixtures.balance.copy(
            spells = SpellCatalog(
                GameBalanceFixtures.balance.spells.all.map { definition ->
                    if (definition.type == SpellType.FORTRESS_SHIELD) {
                        definition.copy(special = true)
                    } else {
                        definition
                    }
                },
            ),
        )
        val spellSnapshot = snapshot.copy(
            fortresses = listOf(
                FortressSnapshot(
                    entityId = EntityId("red-fortress"),
                    team = Team.RED,
                    health = 100,
                    maxHealth = 500,
                ),
            ),
            resourcesByTeam = mapOf(Team.RED to ResourceSnapshot(mana = 100)),
        )
        val agent = AiAgent(
            AiConfiguration(
                balance = balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                allowedSpells = setOf(SpellType.FORTRESS_SHIELD),
            ),
        )

        assertTrue(agent.decide(spellSnapshot).none { it is MatchCommand.CastSpell })
        val stocked = spellSnapshot.copy(
            resourcesByTeam = mapOf(
                Team.RED to ResourceSnapshot(
                    mana = 100,
                    specialSpellStocks = mapOf(SpellType.FORTRESS_SHIELD.name to 1),
                ),
            ),
        )
        assertEquals(
            SpellType.FORTRESS_SHIELD.name,
            agent.decide(stocked).filterIsInstance<MatchCommand.CastSpell>().single().spellType,
        )
    }

    @Test
    fun everyImplementedSpellHasAnAiUsePolicy() {
        val implementedSpells = GameBalanceFixtures.balance.spells.all
            .filter { it.isImplemented }
            .map { it.type }
            .toSet()

        assertEquals(implementedSpells, AiStrategyRegistry.standard().spellTypes())
    }

    @Test
    fun spellPoliciesRequireTheirSituationalTriggers() {
        val redFortress = FortressSnapshot(
            entityId = EntityId("red-fortress"),
            team = Team.RED,
            health = 100,
            maxHealth = 500,
        )
        val manaResources = mapOf(Team.RED to ResourceSnapshot(mana = 100))
        val shieldSnapshot = snapshot.copy(
            fortresses = listOf(redFortress),
            resourcesByTeam = manaResources,
        )
        val hasteSnapshot = snapshot.copy(
            units = listOf(
                UnitSnapshot(
                    entityId = EntityId("red-unit"),
                    team = Team.RED,
                    unitType = UnitIds.DEFENDER,
                    x = 700f,
                    y = 400f,
                    health = 100,
                    moving = true,
                ),
            ),
            resourcesByTeam = manaResources,
        )
        val bastionSnapshot = snapshot.copy(
            fortresses = listOf(redFortress),
            units = listOf(
                UnitSnapshot(
                    entityId = EntityId("blue-unit"),
                    team = Team.BLUE,
                    unitType = UnitIds.DEFENDER,
                    x = 800f,
                    y = 400f,
                    health = 100,
                    targetId = redFortress.entityId,
                ),
            ),
            resourcesByTeam = manaResources,
        )

        assertEquals(
            SpellType.FORTRESS_SHIELD.name,
            spellCasts(SpellType.FORTRESS_SHIELD, shieldSnapshot).single().spellType,
        )
        assertTrue(
            spellCasts(
                SpellType.FORTRESS_SHIELD,
                shieldSnapshot.copy(
                    fortresses = listOf(redFortress.copy(health = 450)),
                ),
            ).isEmpty(),
        )
        assertEquals(SpellType.HASTE.name, spellCasts(SpellType.HASTE, hasteSnapshot).single().spellType)
        assertTrue(
            spellCasts(SpellType.HASTE, hasteSnapshot.copy(units = emptyList())).isEmpty(),
        )
        assertEquals(
            SpellType.RUNIC_BASTION.name,
            spellCasts(SpellType.RUNIC_BASTION, bastionSnapshot).single().spellType,
        )
        assertTrue(
            spellCasts(SpellType.RUNIC_BASTION, bastionSnapshot.copy(units = emptyList())).isEmpty(),
        )
    }

    @Test
    fun aiUsesOnlyAllowedAmmunitionAndPrefersFortressDamageWhenAvailable() {
        val agent = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
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
        val allowed = setOf(GameBalanceFixtures.unit(UnitIds.DEFENDER), GameBalanceFixtures.unit(UnitIds.SAPPER))
        val agent = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                allowedUnits = allowed,
            ),
        )
        val deployment = agent.decide(
            snapshot.copy(resources = snapshot.resources.copy(supply = GameBalanceFixtures.unit(UnitIds.DEFENDER).supplyCost)),
            includeUnitActions = true,
        ).filterIsInstance<MatchCommand.DeployUnit>().single()

        assertEquals(GameBalanceFixtures.unit(UnitIds.DEFENDER).id, deployment.unitType)

        val withSapper = snapshot.copy(
            units = listOf(
                UnitSnapshot(
                    entityId = EntityId("red-sapper-1"),
                    team = Team.RED,
                    unitType = GameBalanceFixtures.unit(UnitIds.SAPPER).id,
                    x = 700f,
                    y = 400f,
                    health = GameBalanceFixtures.unit(UnitIds.SAPPER).maxHealth,
                ),
            ),
        )
        val noSecondSapper = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                allowedUnits = setOf(GameBalanceFixtures.unit(UnitIds.SAPPER)),
            ),
        ).decide(withSapper).filterIsInstance<MatchCommand.DeployUnit>()

        assertTrue(noSecondSapper.isEmpty())
    }

    @Test
    fun simpleRecruitmentPrefersAnAffordableUnderrepresentedUnit() {
        val deployment = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                allowedUnits = setOf(
                    GameBalanceFixtures.unit(UnitIds.DEFENDER),
                    GameBalanceFixtures.unit(UnitIds.SAPPER),
                ),
            ),
        ).decide(
            snapshot.copy(
                resources = snapshot.resources.copy(supply = 100),
                units = listOf(
                    UnitSnapshot(
                        entityId = EntityId("red-defender"),
                        team = Team.RED,
                        unitType = UnitIds.DEFENDER,
                        x = 700f,
                        y = 400f,
                        health = GameBalanceFixtures.unit(UnitIds.DEFENDER).maxHealth,
                    ),
                ),
            ),
        ).filterIsInstance<MatchCommand.DeployUnit>().single()

        assertEquals(UnitIds.SAPPER, deployment.unitType)
    }

    @Test
    fun waveDispatchIncludesGarrisonUnitsWhileTheyAreStillRepositioning() {
        val commands = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                allowedUnits = setOf(GameBalanceFixtures.unit(UnitIds.DEFENDER)),
            ),
        ).decide(
            snapshot.copy(
                resources = snapshot.resources.copy(supply = 0),
                units = (1..3).map { index ->
                    UnitSnapshot(
                        entityId = EntityId("red-defender-$index"),
                        team = Team.RED,
                        unitType = UnitIds.DEFENDER,
                        x = 700f + index,
                        y = 400f,
                        health = GameBalanceFixtures.unit(UnitIds.DEFENDER).maxHealth,
                        moving = true,
                        actionState = "MOVING",
                        inGarrison = true,
                    )
                },
            ),
        )

        assertEquals(1, commands.filterIsInstance<MatchCommand.SendWave>().size)
    }

    @Test
    fun simpleWaveDispatchWaitsForTheConfiguredGarrisonGroup() {
        fun commandsForGarrisonSize(size: Int) = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.CADET),
                allowedUnits = setOf(GameBalanceFixtures.unit(UnitIds.DEFENDER)),
            ),
        ).decide(
            snapshot.copy(
                resources = snapshot.resources.copy(supply = 0),
                units = (1..size).map { index ->
                    UnitSnapshot(
                        entityId = EntityId("red-defender-$index"),
                        team = Team.RED,
                        unitType = UnitIds.DEFENDER,
                        x = 700f + index,
                        y = 400f,
                        health = GameBalanceFixtures.unit(UnitIds.DEFENDER).maxHealth,
                        moving = true,
                        actionState = "MOVING",
                        inGarrison = true,
                    )
                },
            ),
        )

        assertTrue(commandsForGarrisonSize(2).none { it is MatchCommand.SendWave })
        assertEquals(
            1,
            commandsForGarrisonSize(3).count { it is MatchCommand.SendWave },
        )
    }

    @Test
    fun waveDispatchDoesNotCountUnitsAlreadyOutsideTheGarrison() {
        val commands = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                allowedUnits = setOf(GameBalanceFixtures.unit(UnitIds.DEFENDER)),
            ),
        ).decide(
            snapshot.copy(
                resources = snapshot.resources.copy(supply = 0),
                units = (1..3).map { index ->
                    UnitSnapshot(
                        entityId = EntityId("red-defender-$index"),
                        team = Team.RED,
                        unitType = UnitIds.DEFENDER,
                        x = 700f + index,
                        y = 400f,
                        health = GameBalanceFixtures.unit(UnitIds.DEFENDER).maxHealth,
                        moving = true,
                        actionState = "MOVING",
                        inGarrison = false,
                    )
                },
            ),
        )

        assertTrue(commands.none { it is MatchCommand.SendWave })
    }

    @Test
    fun veteranRecruitmentSelectsDefenderToCounterEnemySapper() {
        val deployment = AiAgent(
            AiConfiguration(
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
                profile = AiProfileSelection(AiProfileId.VETERAN),
                allowedUnits = setOf(GameBalanceFixtures.unit(UnitIds.DEFENDER), GameBalanceFixtures.unit(UnitIds.SAPPER)),
            ),
        ).decide(
            snapshot.copy(
                units = listOf(
                    UnitSnapshot(
                        entityId = EntityId("blue-sapper"),
                        team = Team.BLUE,
                        unitType = GameBalanceFixtures.unit(UnitIds.SAPPER).id,
                        x = 300f,
                        y = 400f,
                        health = GameBalanceFixtures.unit(UnitIds.SAPPER).maxHealth,
                    ),
                ),
            ),
        ).filterIsInstance<MatchCommand.DeployUnit>().single()

        assertEquals(GameBalanceFixtures.unit(UnitIds.DEFENDER).id, deployment.unitType)
    }

    @Test
    fun profileProgressionIsBoundedAndImprovesEliteAccuracyAndReaction() {
        val catalog = AiProfileCatalog.standard()
        val start = catalog.tuning(
            AiProfileSelection(AiProfileId.ELITE),
            GameBalanceFixtures.balance.runtime.aiProfiles,
        )
        val end = catalog.tuning(
            AiProfileSelection(AiProfileId.ELITE, progressionPercent = 100),
            GameBalanceFixtures.balance.runtime.aiProfiles,
        )

        assertEquals(400L, start.reactionIntervalMilliseconds)
        assertEquals(200L, end.reactionIntervalMilliseconds)
        assertEquals(start.angleErrorDegrees * 0.5f, end.angleErrorDegrees)
        assertEquals(start.powerError * 0.5f, end.powerError)
        assertNotEquals(start, end)
    }

    @Test
    fun cadetHasWiderAimErrorThanHigherProfiles() {
        val catalog = AiProfileCatalog.standard()
        val profiles = GameBalanceFixtures.balance.runtime.aiProfiles
        val cadet = catalog.tuning(AiProfileSelection(AiProfileId.CADET), profiles)
        val veteran = catalog.tuning(AiProfileSelection(AiProfileId.VETERAN), profiles)
        val elite = catalog.tuning(AiProfileSelection(AiProfileId.ELITE), profiles)

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
            unitType = GameBalanceFixtures.unit(UnitIds.DEFENDER).id,
            x = 300f,
            y = 400f,
            health = GameBalanceFixtures.unit(UnitIds.DEFENDER).maxHealth,
            actionState = "SEEKING_TARGET",
        )
        val movingThreat = stationaryThreat.copy(moving = true)
        fun aimPower(threat: UnitSnapshot): Float =
            AiAgent(
                AiConfiguration(
                    balance = GameBalanceFixtures.balance,
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
        val customBalance = GameBalanceFixtures.balance.copy(
            runtime = GameBalanceFixtures.balance.runtime.copy(
                aiProfiles = GameBalanceFixtures.balance.runtime.aiProfiles +
                    AiProfileBalance(customProfile, 0f, 0f, 500L, 0L, 0f, 0f),
            ),
        )
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
                    ),
                ),
            ),
        )
        val agent = AiAgent(
            AiConfiguration(
                balance = customBalance,
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
        val balance = GameBalanceFixtures.balance.copy(
            runtime = GameBalanceFixtures.balance.runtime.copy(
                aiProfiles = GameBalanceFixtures.balance.runtime.aiProfiles +
                    AiProfileBalance(profile, 0f, 0f, 500L, 0L, 0f, 0f),
            ),
        )
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
                    ),
                ),
            ),
        )

        kotlin.test.assertFailsWith<IllegalStateException> {
            AiAgent(
                AiConfiguration(
                    balance = balance,
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
                balance = GameBalanceFixtures.balance,
                playerId = PlayerId("ai-player"),
                team = Team.RED,
            ),
        )

        assertTrue(agent.decide(MatchSnapshot()).isEmpty())
    }

    private fun centralObjectiveSnapshot(): MatchSnapshot = snapshot.copy(
        boss = BossSnapshot(
            entityId = EntityId("boss-oak"),
            profileId = "boss.central-test.level-05",
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
        terrain = TerrainSnapshot(
            worldWidth = 1_600f,
            sampleSpacing = 8f,
            heightSamples = List(202) { 500f },
        ),
    )

    private fun spellCasts(
        spellType: SpellType,
        snapshot: MatchSnapshot,
    ): List<MatchCommand.CastSpell> = AiAgent(
        AiConfiguration(
            balance = GameBalanceFixtures.balance,
            playerId = PlayerId("ai-player"),
            team = Team.RED,
            allowedSpells = setOf(spellType),
        ),
    ).decide(snapshot).filterIsInstance<MatchCommand.CastSpell>()
}
