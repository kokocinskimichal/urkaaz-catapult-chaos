package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.CommandResult
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.contracts.BattlefieldConfig
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.FortressHealthOverrides
import com.urkaaaz.contracts.PlayerConfig
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import com.urkaaaz.application.loadout.LoadoutCommandRejectedException
import com.urkaaaz.application.loadout.LoadoutError
import com.urkaaaz.application.reward.MatchEventObserver
import com.urkaaaz.application.balance.GameBalanceRepository
import java.io.ByteArrayInputStream

class MatchSessionTest {
    @Test
    fun playerTeamUsesScenarioAssignmentsEvenWhenPlayerIdsSuggestTheOppositeTeam() {
        val bluePlayerId = PlayerId("red-named-blue-player")
        val redPlayerId = PlayerId("blue-named-red-player")
        val scenario = MatchScenario(
            scenarioId = "typed-player-teams",
            battlefield = BattlefieldConfig(1_000, 600),
            players = listOf(
                PlayerConfig(bluePlayerId, Team.BLUE),
                PlayerConfig(redPlayerId, Team.RED),
            ),
            availableUnits = listOf("DEFENDER"),
            availableAmmunition = listOf("ROCK", "SIEGE_BOMB"),
        )
        val session = MatchSession(scenario = scenario)
        val matchId = MatchId("typed-player-teams")
        session.dispatch(
            MatchCommand.Start(CommandId("typed-player-teams-start"), matchId, bluePlayerId),
        )

        val selected = session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("typed-player-teams-ammo"),
                matchId,
                bluePlayerId,
                "SIEGE_BOMB",
            ),
        )
        val deployed = session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("typed-player-teams-deploy"),
                matchId,
                redPlayerId,
                "DEFENDER",
            ),
        )

        assertEquals(
            2f,
            selected.reloadRemainingSecondsByAmmunition[Team.BLUE]?.get("SIEGE_BOMB"),
        )
        assertEquals(0f, selected.reloadRemainingSecondsByAmmunition[Team.RED]?.get("SIEGE_BOMB"))
        assertEquals(Team.RED, deployed.units.single().team)
    }

    @Test
    fun unknownPlayerIsRejectedInsteadOfInferringTeamFromItsId() {
        val playerId = PlayerId("known-blue-player")
        val scenario = MatchScenario(
            scenarioId = "unknown-player",
            battlefield = BattlefieldConfig(1_000, 600),
            players = listOf(PlayerConfig(playerId, Team.BLUE)),
            availableUnits = listOf("DEFENDER"),
        )
        val session = MatchSession(scenario = scenario)
        val matchId = MatchId("unknown-player")
        session.dispatch(MatchCommand.Start(CommandId("unknown-player-start"), matchId, playerId))

        val rejection = assertFailsWith<MatchCommandRejectedException> {
            session.dispatch(
                MatchCommand.DeployUnit(
                    CommandId("unknown-player-deploy"),
                    matchId,
                    PlayerId("ai-unknown"),
                    "DEFENDER",
                ),
            )
        }

        assertEquals(MatchError.PLAYER_NOT_IN_MATCH, rejection.error)
    }

    @Test
    fun replayPreservesExplicitTeamsForLocalMatchesWithoutScenario() {
        val redPlayerId = PlayerId("nonstandard-red-player")
        val session = MatchSession(
            playerTeams = mapOf(redPlayerId to Team.RED),
            randomSeedProvider = { 41L },
        )
        val matchId = MatchId("explicit-player-teams")
        val bluePlayerId = PlayerId("nonstandard-blue-player")
        session.dispatch(
            MatchCommand.Start(CommandId("explicit-teams-start"), matchId, bluePlayerId),
        )
        session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("explicit-teams-deploy-red"),
                matchId,
                redPlayerId,
                "DEFENDER",
            ),
        )

        val replay = requireNotNull(session.replay())
        val replayResult = MatchReplayPlayer.play(replay)

        assertEquals(mapOf(redPlayerId to Team.RED), replay.configuration.playerTeams)
        assertEquals(Team.RED, session.snapshot().units.single().team)
        assertEquals(session.snapshot(), replayResult.snapshot)
    }

    @Test
    fun eventObserverReceivesStartAndRestartBatchesIndependentlyOfEventConsumption() {
        val batches = mutableListOf<List<MatchEvent>>()
        val session = MatchSession(
            eventObserver = MatchEventObserver { batches += it },
        )
        val matchId = MatchId("observer-match")
        val playerId = PlayerId("observer-player")

        session.dispatch(MatchCommand.Start(CommandId("observer-start"), matchId, playerId))
        assertEquals(1, batches.size)
        assertIs<MatchEvent.MatchStarted>(batches.single().first())
        session.consumeEvents()

        session.restart(matchId, playerId)
        assertEquals(2, batches.size)
        assertIs<MatchEvent.MatchStarted>(batches.last().first())
    }

    @Test
    fun restartUsesUpdatedPlayerInventoryAndRecordsItInReplay() {
        val playerId = PlayerId("inventory-player")
        val initialScenario = MatchScenario(
            scenarioId = "inventory-scenario",
            battlefield = BattlefieldConfig(1_000, 600),
            players = listOf(
                PlayerConfig(playerId, Team.BLUE),
                PlayerConfig(PlayerId("inventory-ai"), Team.RED),
            ),
            availableUnits = listOf("DEFENDER"),
            availableAmmunition = listOf("ROCK", "POWDER_BARREL"),
            availableSpells = listOf("FORTRESS_SHIELD"),
            playerLoadout = PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", "POWDER_BARREL", null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
            playerInventory = PlayerInventoryConfig(
                playerId = playerId,
                ammunitionStocks = mapOf("POWDER_BARREL" to 2),
                specialSpellStocks = emptyMap(),
            ),
        )
        val session = MatchSession(scenario = initialScenario)
        val matchId = MatchId("inventory-restart")
        session.dispatch(MatchCommand.Start(CommandId("inventory-start"), matchId, playerId))

        assertEquals(
            2,
            session.snapshot().resourcesByTeam[Team.BLUE]?.ammunition?.get("POWDER_BARREL"),
        )
        val updatedScenario = initialScenario.copy(
            playerInventory = initialScenario.playerInventory!!.copy(
                ammunitionStocks = mapOf("POWDER_BARREL" to 1),
            ),
        )
        session.restart(matchId, playerId, updatedScenario)

        assertEquals(
            1,
            session.snapshot().resourcesByTeam[Team.BLUE]?.ammunition?.get("POWDER_BARREL"),
        )
        assertEquals(
            3,
            session.snapshot().resourcesByTeam[Team.RED]?.ammunition?.get("POWDER_BARREL"),
        )
        assertEquals(
            updatedScenario,
            session.replay()?.configuration?.scenario,
        )
    }

    @Test
    fun runicBastionCastBlocksAnotherCastUntilTheStructureIsDestroyed() {
        val session = MatchSession(
            scenario = spellScenario(loadoutSpells = listOf("RUNIC_BASTION")),
        )
        val matchId = MatchId("bastion-match")
        val playerId = PlayerId("spell-player")
        session.dispatch(MatchCommand.Start(CommandId("bastion-start"), matchId, playerId))

        val result = session.dispatchResult(
            MatchCommand.CastSpell(
                commandId = CommandId("bastion-cast"),
                matchId = matchId,
                playerId = playerId,
                spellType = "RUNIC_BASTION",
            ),
        )

        val accepted = assertIs<CommandResult.Accepted>(result)
        val castEvent = assertIs<MatchEvent.SpellCast>(accepted.events.single())
        val bastion = session.snapshot().runicBastions.single()
        assertEquals("RUNIC_BASTION", castEvent.spellType)
        assertEquals(70, castEvent.manaCost)
        assertEquals(bastion.entityId, castEvent.targetId)
        assertEquals(360, bastion.health)
        assertEquals(30, session.snapshot().resourcesByTeam[Team.BLUE]?.mana)

        val repeatedCast = session.dispatchResult(
            MatchCommand.CastSpell(
                CommandId("bastion-repeat"),
                matchId,
                playerId,
                "RUNIC_BASTION",
            ),
        )

        assertEquals(
            CommandResult.Rejected(
                CommandId("bastion-repeat"),
                MatchError.SPELL_ALREADY_ACTIVE,
            ),
            repeatedCast,
        )
        assertEquals(30, session.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        assertEquals(1, session.snapshot().runicBastions.size)
    }

    @Test
    fun fortressShieldCastSpendsManaAndReturnsTypedEvents() {
        val session = MatchSession(scenario = spellScenario())
        val matchId = MatchId("spell-match")
        val playerId = PlayerId("spell-player")
        session.dispatch(MatchCommand.Start(CommandId("spell-start"), matchId, playerId))

        val result = session.dispatchResult(
            MatchCommand.CastSpell(
                commandId = CommandId("shield-cast"),
                matchId = matchId,
                playerId = playerId,
                spellType = "FORTRESS_SHIELD",
            ),
        )

        val accepted = assertIs<CommandResult.Accepted>(result)
        val castEvent = assertIs<MatchEvent.SpellCast>(accepted.events.single())
        assertEquals("FORTRESS_SHIELD", castEvent.spellType)
        assertEquals(80, castEvent.manaCost)
        assertEquals(EntityId("blue-fortress"), castEvent.targetId)
        assertEquals(20, session.snapshot().resourcesByTeam[Team.BLUE]?.mana)
        val blueFortress = session.snapshot().fortresses.single { it.team == Team.BLUE }
        assertEquals(150, blueFortress.shieldHealth)
        assertEquals(150, blueFortress.shieldMaxHealth)

        val repeatedCast = session.dispatchResult(
            MatchCommand.CastSpell(
                CommandId("shield-repeat"),
                matchId,
                playerId,
                "FORTRESS_SHIELD",
            ),
        )
        assertEquals(
            CommandResult.Rejected(CommandId("shield-repeat"), MatchError.SPELL_ALREADY_ACTIVE),
            repeatedCast,
        )
        assertEquals(20, session.snapshot().resourcesByTeam[Team.BLUE]?.mana)
    }

    @Test
    fun spellCastRejectsUnknownUnavailableAndUnselectedSpells() {
        val playerId = PlayerId("spell-player")
        val matchId = MatchId("spell-rejections")
        val session = MatchSession(scenario = spellScenario(loadoutSpells = listOf("HASTE")))
        session.dispatch(MatchCommand.Start(CommandId("spell-rejections-start"), matchId, playerId))

        fun cast(id: String, spellType: String) = session.dispatchResult(
            MatchCommand.CastSpell(CommandId(id), matchId, playerId, spellType),
        )

        assertEquals(
            CommandResult.Rejected(CommandId("unknown-spell"), MatchError.UNKNOWN_SPELL),
            cast("unknown-spell", "UNKNOWN"),
        )
        assertEquals(
            CommandResult.Rejected(CommandId("unselected-spell"), MatchError.SPELL_NOT_IN_LOADOUT),
            cast("unselected-spell", "FORTRESS_SHIELD"),
        )
        assertEquals(
            CommandResult.Rejected(CommandId("unavailable-spell"), MatchError.SPELL_NOT_AVAILABLE),
            cast("unavailable-spell", "ICE_TRAP"),
        )
    }

    @Test
    fun specialSpellStockIsConsumedOnlyAfterAnAcceptedCast() {
        val bundledBalance = javaClass.getResourceAsStream("/balance/game-balance.json")!!
            .bufferedReader()
            .use { it.readText() }
        val balanceWithSpecialShield = bundledBalance.replace(
            "\"type\": \"FORTRESS_SHIELD\",\n" +
                "      \"goldCost\": 0,\n" +
                "      \"category\": \"DEFENSIVE\",\n" +
                "      \"targetCategory\": \"OWN_FORTRESS\",\n" +
                "      \"special\": false,",
            "\"type\": \"FORTRESS_SHIELD\",\n" +
                "      \"goldCost\": 0,\n" +
                "      \"category\": \"DEFENSIVE\",\n" +
                "      \"targetCategory\": \"OWN_FORTRESS\",\n" +
                "      \"special\": true,",
        )
        assertNotEquals(bundledBalance, balanceWithSpecialShield)
        val playerId = PlayerId("spell-player")
        val scenario = spellScenario().copy(
            playerInventory = PlayerInventoryConfig(
                playerId = playerId,
                ammunitionStocks = emptyMap(),
                specialSpellStocks = mapOf("FORTRESS_SHIELD" to 1),
            ),
        )
        val session = MatchSession(
            scenario = scenario,
            balanceRepository = GameBalanceRepository {
                ByteArrayInputStream(balanceWithSpecialShield.toByteArray())
            },
        )
        val matchId = MatchId("special-stock-match")
        session.dispatch(MatchCommand.Start(CommandId("special-stock-start"), matchId, playerId))

        assertEquals(
            1,
            session.snapshot().resourcesByTeam[Team.BLUE]
                ?.specialSpellStocks?.get("FORTRESS_SHIELD"),
        )
        assertEquals(
            1,
            session.snapshot().resourcesByTeam[Team.RED]
                ?.specialSpellStocks?.get("FORTRESS_SHIELD"),
        )
        assertIs<CommandResult.Accepted>(
            session.dispatchResult(
                MatchCommand.CastSpell(
                    CommandId("special-stock-cast"),
                    matchId,
                    playerId,
                    "FORTRESS_SHIELD",
                ),
            ),
        )
        assertEquals(
            0,
            session.snapshot().resourcesByTeam[Team.BLUE]
                ?.specialSpellStocks?.get("FORTRESS_SHIELD"),
        )
        assertEquals(
            CommandResult.Rejected(
                CommandId("special-stock-repeat"),
                MatchError.NO_SPELL_STOCK,
            ),
            session.dispatchResult(
                MatchCommand.CastSpell(
                    CommandId("special-stock-repeat"),
                    matchId,
                    playerId,
                    "FORTRESS_SHIELD",
                ),
            ),
        )
    }

    @Test
    fun fortressShieldRejectsAnExplicitEnemyFortressTarget() {
        val session = MatchSession(scenario = spellScenario())
        val matchId = MatchId("spell-invalid-target")
        val playerId = PlayerId("spell-player")
        session.dispatch(MatchCommand.Start(CommandId("spell-target-start"), matchId, playerId))

        val result = session.dispatchResult(
            MatchCommand.CastSpell(
                commandId = CommandId("spell-enemy-target"),
                matchId = matchId,
                playerId = playerId,
                spellType = "FORTRESS_SHIELD",
                target = EntityId("red-fortress"),
            ),
        )

        assertEquals(
            CommandResult.Rejected(
                CommandId("spell-enemy-target"),
                MatchError.INVALID_SPELL_TARGET,
            ),
            result,
        )
        assertEquals(100, session.snapshot().resourcesByTeam[Team.BLUE]?.mana)
    }

    @Test
    fun hasteActivationRefreshesItsTimerAndReplaysDeterministically() {
        val session = MatchSession(
            scenario = spellScenario(loadoutSpells = listOf("HASTE")),
            randomSeedProvider = { 79L },
        )
        val matchId = MatchId("haste-replay-match")
        val playerId = PlayerId("spell-player")
        session.dispatch(MatchCommand.Start(CommandId("haste-replay-start"), matchId, playerId))
        session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("haste-replay-deploy"),
                matchId,
                playerId,
                "DEFENDER",
            ),
        )
        session.dispatch(MatchCommand.SendWave(CommandId("haste-replay-wave"), matchId, playerId))

        val cast = session.dispatchResult(
            MatchCommand.CastSpell(
                CommandId("haste-replay-cast"),
                matchId,
                playerId,
                "HASTE",
            ),
        )
        val acceptedCast = assertIs<CommandResult.Accepted>(cast)
        val castEvent = assertIs<MatchEvent.SpellCast>(acceptedCast.events.single())
        assertEquals("HASTE", castEvent.spellType)
        assertEquals(50, castEvent.manaCost)
        assertEquals(null, castEvent.targetId)
        assertEquals(50, session.snapshot().resourcesByTeam[Team.BLUE]?.mana)

        session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("haste-replay-advance"),
                matchId,
                playerId,
                5_000L,
            ),
        )
        assertEquals(10_000L, session.snapshot().activeSpells.single().remainingMilliseconds)
        val refresh = session.dispatchResult(
            MatchCommand.CastSpell(
                CommandId("haste-replay-refresh"),
                matchId,
                playerId,
                "HASTE",
            ),
        )
        assertIs<CommandResult.Accepted>(refresh)
        assertEquals(15_000L, session.snapshot().activeSpells.single().remainingMilliseconds)

        session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("haste-replay-advance-after-refresh"),
                matchId,
                playerId,
                1_250L,
            ),
        )
        val expectedSnapshot = session.snapshot()
        val replay = session.replay() ?: error("match replay was not recorded")
        val replayResult = MatchReplayPlayer.play(replay)

        assertEquals(expectedSnapshot, replayResult.snapshot)
    }

    @Test
    fun rejectedSpellCommandsRemainDeterministicInReplay() {
        val session = MatchSession(scenario = spellScenario(), randomSeedProvider = { 31L })
        val matchId = MatchId("spell-replay")
        val playerId = PlayerId("spell-player")
        session.dispatch(MatchCommand.Start(CommandId("spell-replay-start"), matchId, playerId))
        session.consumeEvents()
        session.dispatchResult(
            MatchCommand.CastSpell(
                CommandId("spell-replay-rejected"),
                matchId,
                playerId,
                "UNKNOWN",
            ),
        )
        val accepted = session.dispatchResult(
            MatchCommand.CastSpell(
                CommandId("spell-replay-cast"),
                matchId,
                playerId,
                "FORTRESS_SHIELD",
            ),
        )
        val expectedSnapshot = session.snapshot()

        val replay = session.replay() ?: error("match replay was not recorded")
        val replayResult = MatchReplayPlayer.play(replay)

        assertIs<CommandResult.Accepted>(accepted)
        assertEquals(expectedSnapshot, replayResult.snapshot)
        assertTrue(replayResult.events.any { it is MatchEvent.SpellCast })
    }

    private fun spellScenario(
        loadoutSpells: List<String> = listOf("FORTRESS_SHIELD"),
    ) = MatchScenario(
        scenarioId = "spell-test",
        battlefield = BattlefieldConfig(width = 7_200, height = 4_800),
        players = listOf(
            PlayerConfig(PlayerId("spell-player"), Team.BLUE),
            PlayerConfig(PlayerId("spell-ai"), Team.RED),
        ),
        availableUnits = listOf("DEFENDER"),
        availableAmmunition = listOf("ROCK"),
        availableSpells = listOf("FORTRESS_SHIELD", "HASTE", "RUNIC_BASTION"),
        playerLoadout = PlayerLoadoutConfig(
            playerId = PlayerId("spell-player"),
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", null, null),
            spellTypes = loadoutSpells,
        ),
    )

    @Test
    fun replayCapturesSeedAndReproducesTheCommandTrace() {
        val session = MatchSession(randomSeedProvider = { 713L })
        val matchId = MatchId("replay-match")
        val playerId = PlayerId("replay-player")
        val commands = buildList {
            add(MatchCommand.Start(CommandId("replay-start"), matchId, playerId))
            add(MatchCommand.DeployUnit(CommandId("replay-deploy"), matchId, playerId, "DEFENDER"))
            add(MatchCommand.SendWave(CommandId("replay-wave"), matchId, playerId))
            repeat(30) { index ->
                add(
                    MatchCommand.AdvanceSimulation(
                        CommandId("replay-advance-$index"),
                        matchId,
                        playerId,
                        100,
                    ),
                )
            }
        }
        val events = mutableListOf<MatchEvent>()
        commands.forEach { command ->
            session.dispatch(command)
            events += session.consumeEvents()
        }
        session.pause()
        session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("replay-paused-advance"),
                matchId,
                playerId,
                500,
            ),
        )
        events += session.consumeEvents()
        session.resume()
        session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("replay-resumed-advance"),
                matchId,
                playerId,
                100,
            ),
        )
        events += session.consumeEvents()
        val snapshot = session.snapshot()

        val replay = session.replay() ?: error("match replay was not recorded")
        val replayResult = MatchReplayPlayer.play(replay)

        assertEquals(713L, replay.randomSeed)
        val replayStart = (replay.steps.first() as MatchReplayStep.Command).value as MatchCommand.Start
        assertEquals(713L, replayStart.randomSeed)
        assertTrue(MatchReplayStep.Pause in replay.steps)
        assertTrue(MatchReplayStep.Resume in replay.steps)
        assertEquals(snapshot, replayResult.snapshot)
        assertEquals(events, replayResult.events)
    }

    @Test
    fun replayCapturesStopTransitionAndReturnsStoppedSnapshot() {
        val session = MatchSession(randomSeedProvider = { 17L })
        session.dispatch(
            MatchCommand.Start(
                CommandId("stop-replay-start"),
                MatchId("stop-replay"),
                PlayerId("stop-replay-player"),
            ),
        )

        val stoppedSnapshot = session.stop()
        val replay = session.replay() ?: error("match replay was not recorded")
        val replayResult = MatchReplayPlayer.play(replay)

        assertTrue(MatchReplayStep.Stop in replay.steps)
        assertEquals(stoppedSnapshot, replayResult.snapshot)
    }

    @Test
    fun restartingMatchGeneratesAndRecordsANewRandomSeed() {
        val seeds = listOf(11L, 22L).iterator()
        val session = MatchSession(randomSeedProvider = { seeds.next() })
        val matchId = MatchId("seeded-restart")
        val playerId = PlayerId("seeded-restart-player")
        session.dispatch(MatchCommand.Start(CommandId("seeded-start"), matchId, playerId))
        val firstSeed = session.consumeEvents()
            .filterIsInstance<MatchEvent.MatchStarted>()
            .single()
            .randomSeed

        session.restart(matchId, playerId)
        val secondSeed = session.consumeEvents()
            .filterIsInstance<MatchEvent.MatchStarted>()
            .single()
            .randomSeed

        assertEquals(11L, firstSeed)
        assertEquals(22L, secondSeed)
        assertNotEquals(firstSeed, session.replay()?.randomSeed)
    }

    @Test
    fun startCommandProducesRunningSnapshot() {
        val snapshot = MatchSession().dispatch(
            MatchCommand.Start(
                commandId = CommandId("command-1"),
                matchId = MatchId("match-1"),
                playerId = PlayerId("player-1"),
            ),
        )

        assertEquals(MatchStatus.RUNNING, snapshot.status)
    }

    @Test
    fun configurableFortressHealthForBothTeamsIsExposedAndRecordedForReplay() {
        val session = MatchSession(
            blueFortressMaxHealth = 5_000,
            redFortressMaxHealth = 5,
        )
        val snapshot = session.dispatch(
            MatchCommand.Start(
                commandId = CommandId("start-demo-health"),
                matchId = MatchId("demo-health"),
                playerId = PlayerId("player-demo-health"),
            ),
        )

        assertEquals(
            5_000,
            snapshot.fortresses.first { it.team == com.urkaaaz.contracts.Team.BLUE }.maxHealth,
        )
        assertEquals(
            5,
            snapshot.fortresses.first { it.team == com.urkaaaz.contracts.Team.RED }.maxHealth,
        )
        assertEquals(snapshot, MatchReplayPlayer.play(requireNotNull(session.replay())).snapshot)
    }

    @Test
    fun demoPlayerFortressOverrideKeepsStandardRedFortressHealth() {
        val session = MatchSession(blueFortressMaxHealth = 5_000)
        val snapshot = session.dispatch(
            MatchCommand.Start(
                CommandId("demo-default-red-health-start"),
                MatchId("demo-default-red-health"),
                PlayerId("demo-default-red-health-player"),
            ),
        )

        assertEquals(5_000, snapshot.fortresses.single { it.team == Team.BLUE }.maxHealth)
        assertEquals(500, snapshot.fortresses.single { it.team == Team.RED }.maxHealth)
    }

    @Test
    fun campaignFortressHealthOverridesReachSimulationAndReplayIndependently() {
        val scenario = MatchScenario(
            scenarioId = "campaign-fortress-health",
            battlefield = BattlefieldConfig(1_000, 600),
            players = listOf(
                PlayerConfig(PlayerId("campaign-blue"), Team.BLUE),
                PlayerConfig(PlayerId("campaign-red"), Team.RED),
            ),
            fortressHealthOverrides = FortressHealthOverrides(
                blueMaximumHealth = 700,
                redMaximumHealth = 900,
            ),
        )
        val session = MatchSession(scenario = scenario)
        val matchId = MatchId("campaign-fortress-health")
        val snapshot = session.dispatch(
            MatchCommand.Start(
                CommandId("campaign-fortress-health-start"),
                matchId,
                PlayerId("campaign-blue"),
            ),
        )
        val replay = requireNotNull(session.replay())
        val replayResult = MatchReplayPlayer.play(replay)

        assertEquals(
            700,
            snapshot.fortresses.single { it.team == Team.BLUE }.maxHealth,
        )
        assertEquals(
            900,
            snapshot.fortresses.single { it.team == Team.RED }.maxHealth,
        )
        assertEquals(700, replay.configuration.blueFortressMaxHealth)
        assertEquals(900, replay.configuration.redFortressMaxHealth)
        assertEquals(snapshot, replayResult.snapshot)
    }

    @Test
    fun campaignFortressesInheritStandardHealthWhenLevelHasNoOverrides() {
        val scenario = MatchScenario(
            scenarioId = "campaign-standard-fortress-health",
            battlefield = BattlefieldConfig(1_000, 600),
            players = listOf(
                PlayerConfig(PlayerId("standard-blue"), Team.BLUE),
                PlayerConfig(PlayerId("standard-red"), Team.RED),
            ),
        )
        val snapshot = MatchSession(scenario = scenario).dispatch(
            MatchCommand.Start(
                CommandId("campaign-standard-fortress-health-start"),
                MatchId("campaign-standard-fortress-health"),
                PlayerId("standard-blue"),
            ),
        )

        assertEquals(
            500,
            snapshot.fortresses.single { it.team == Team.BLUE }.maxHealth,
        )
        assertEquals(
            500,
            snapshot.fortresses.single { it.team == Team.RED }.maxHealth,
        )
    }

    @Test
    fun explicitHostFortressHealthOverridesCampaignPerLevelOverrides() {
        val scenario = MatchScenario(
            scenarioId = "host-fortress-health-priority",
            battlefield = BattlefieldConfig(1_000, 600),
            players = listOf(
                PlayerConfig(PlayerId("host-blue"), Team.BLUE),
                PlayerConfig(PlayerId("host-red"), Team.RED),
            ),
            fortressHealthOverrides = FortressHealthOverrides(
                blueMaximumHealth = 700,
                redMaximumHealth = 900,
            ),
        )
        val session = MatchSession(
            scenario = scenario,
            blueFortressMaxHealth = 1_200,
        )
        val snapshot = session.dispatch(
            MatchCommand.Start(
                CommandId("host-fortress-health-start"),
                MatchId("host-fortress-health-priority"),
                PlayerId("host-blue"),
            ),
        )

        assertEquals(1_200, snapshot.fortresses.single { it.team == Team.BLUE }.maxHealth)
        assertEquals(900, snapshot.fortresses.single { it.team == Team.RED }.maxHealth)
    }

    @Test
    fun initialWindIsAppliedToTheSimulationAndSnapshot() {
        val session = MatchSession(
            initialWind = WindSnapshot(direction = -1f, strength = 0.5f),
        )
        val matchId = MatchId("match-wind")
        val playerId = PlayerId("player-wind")

        session.dispatch(MatchCommand.Start(CommandId("start-wind"), matchId, playerId))
        val snapshot = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("advance-wind"),
                matchId,
                playerId,
                33,
            ),
        )

        assertEquals(-1f, snapshot.wind.direction)
        assertEquals(0.5f, snapshot.wind.strength)
    }

    @Test
    fun advancingIntoMatchFinishReturnsTheFinishedSnapshotAndEvent() {
        val session = MatchSession(
            blueFortressMaxHealth = 1,
            playerTeams = mapOf(PlayerId("local-ai") to Team.RED),
            randomSeedProvider = { 17L },
        )
        val matchId = MatchId("finished-during-advance")
        val redPlayerId = PlayerId("local-ai")
        session.dispatch(MatchCommand.Start(CommandId("start-finish"), matchId, redPlayerId))
        session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("deploy-finisher"),
                matchId,
                redPlayerId,
                "DEFENDER",
            ),
        )
        session.dispatch(
            MatchCommand.SendWave(CommandId("send-finisher"), matchId, redPlayerId),
        )

        var snapshot = session.snapshot()
        var tick = 0
        while (snapshot.status == MatchStatus.RUNNING && tick < 600) {
            snapshot = session.dispatch(
                MatchCommand.AdvanceSimulation(
                    CommandId("finish-tick-$tick"),
                    matchId,
                    redPlayerId,
                    100,
                ),
            )
            tick++
        }

        assertEquals(MatchStatus.FINISHED, snapshot.status)
        assertTrue(session.consumeEvents().any { it is MatchEvent.MatchFinished })
    }

    @Test
    fun surrenderFinishesEvenWhenPausedAndReplaysTheDefeatOutcome() {
        val session = MatchSession(randomSeedProvider = { 17L })
        val matchId = MatchId("retreat-match")
        val playerId = PlayerId("player")
        session.dispatch(MatchCommand.Start(CommandId("retreat-start"), matchId, playerId))
        session.pause()

        val snapshot = session.dispatch(
            MatchCommand.Surrender(CommandId("retreat"), matchId, playerId),
        )
        val finished = session.consumeEvents()
            .filterIsInstance<MatchEvent.MatchFinished>()
            .single()

        assertEquals(MatchStatus.FINISHED, snapshot.status)
        assertEquals(MatchOutcome.SURRENDER, snapshot.outcome)
        assertEquals(MatchOutcome.SURRENDER, finished.outcome)

        val replay = MatchReplayPlayer.play(requireNotNull(session.replay()))
        assertEquals(MatchStatus.FINISHED, replay.snapshot.status)
        assertEquals(MatchOutcome.SURRENDER, replay.snapshot.outcome)
        assertEquals(
            MatchOutcome.SURRENDER,
            replay.events.filterIsInstance<MatchEvent.MatchFinished>().single().outcome,
        )
    }

    @Test
    fun selectedAmmunitionIsUsedByTheNextProjectile() {
        val session = MatchSession()
        val matchId = MatchId("match-ammo")
        val playerId = PlayerId("player-1")
        session.dispatch(MatchCommand.Start(CommandId("start"), matchId, playerId))
        session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("select"),
                matchId,
                playerId,
                "SIEGE_BOMB",
            ),
        )
        session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("wait-for-ammo-reload"),
                matchId,
                playerId,
                2_000,
            ),
        )

        val snapshot = session.dispatch(
            MatchCommand.Fire(
                CommandId("fire"),
                matchId,
                playerId,
                EntityId("blue-catapult"),
            ),
        )

        assertEquals("SIEGE_BOMB", snapshot.projectiles.single().ammunitionType)
        assertEquals(2f, snapshot.reloadRemainingSeconds[com.urkaaaz.contracts.Team.BLUE])
    }

    @Test
    fun changingAmmunitionRestartsItsReloadButReselectingDoesNot() {
        val session = MatchSession()
        val matchId = MatchId("match-ammo-selection-reload")
        val playerId = PlayerId("player-1")
        session.dispatch(MatchCommand.Start(CommandId("start-selection-reload"), matchId, playerId))

        var snapshot = session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("select-siege-first"),
                matchId,
                playerId,
                "SIEGE_BOMB",
            ),
        )
        assertEquals(
            2f,
            snapshot.reloadRemainingSecondsByAmmunition[Team.BLUE]?.get("SIEGE_BOMB"),
        )

        snapshot = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("advance-siege-reload"),
                matchId,
                playerId,
                1_000,
            ),
        )
        assertEquals(
            1f,
            snapshot.reloadRemainingSecondsByAmmunition[Team.BLUE]?.get("SIEGE_BOMB"),
        )

        snapshot = session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("reselect-siege"),
                matchId,
                playerId,
                "SIEGE_BOMB",
            ),
        )
        assertEquals(
            1f,
            snapshot.reloadRemainingSecondsByAmmunition[Team.BLUE]?.get("SIEGE_BOMB"),
        )

        session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("select-rock"),
                matchId,
                playerId,
                "ROCK",
            ),
        )
        snapshot = session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("select-siege-again"),
                matchId,
                playerId,
                "SIEGE_BOMB",
            ),
        )
        assertEquals(
            2f,
            snapshot.reloadRemainingSecondsByAmmunition[Team.BLUE]?.get("SIEGE_BOMB"),
        )
    }

    @Test
    fun eventsAreConsumedExactlyOnce() {
        val session = MatchSession()
        session.dispatch(
            MatchCommand.Start(
                commandId = CommandId("start-events"),
                matchId = MatchId("match-events"),
                playerId = PlayerId("player-events"),
            ),
        )

        val firstRead = session.consumeEvents()
        val secondRead = session.consumeEvents()

        assertTrue(firstRead.isNotEmpty())
        assertTrue(secondRead.isEmpty())
    }

    @Test
    fun deployingUnitConsumesGoldAndAddsUnitToSnapshot() {
        val session = MatchSession()
        val matchId = MatchId("match-units")
        val playerId = PlayerId("player-units")
        session.dispatch(MatchCommand.Start(CommandId("start-units"), matchId, playerId))

        val snapshot = session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("deploy-units"),
                matchId,
                playerId,
                "DEFENDER",
            ),
        )

        assertEquals(1, snapshot.units.size)
        assertEquals("DEFENDER", snapshot.units.single().unitType)
        assertEquals(15, snapshot.resourcesByTeam[com.urkaaaz.contracts.Team.BLUE]?.supply)
        assertTrue(session.consumeEvents().any { it is com.urkaaaz.contracts.MatchEvent.UnitDeployed })
    }

    @Test
    fun sendingWaveMovesDeployedUnitsAndMarksThemAsMoving() {
        val session = MatchSession()
        val matchId = MatchId("match-wave")
        val playerId = PlayerId("player-wave")
        session.dispatch(MatchCommand.Start(CommandId("start-wave"), matchId, playerId))
        session.dispatch(
            MatchCommand.DeployUnit(
                CommandId("deploy-wave"),
                matchId,
                playerId,
                "SAPPER",
            ),
        )
        val before = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("before-wave"),
                matchId,
                playerId,
                33,
            ),
        )
        val after = session.dispatch(
            MatchCommand.SendWave(CommandId("send-wave"), matchId, playerId),
        )
        val advanced = session.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("after-wave"),
                matchId,
                playerId,
                33,
            ),
        )

        assertTrue(after.units.single().moving)
        assertTrue(advanced.units.single().x > before.units.single().x)
    }

    @Test
    fun matchScenarioRejectsAvailableButUnselectedUnitWithoutSpendingSupply() {
        val playerId = PlayerId("player")
        val scenario = MatchScenario(
            scenarioId = "loadout-match",
            battlefield = BattlefieldConfig(100, 100),
            players = listOf(
                PlayerConfig(playerId, Team.BLUE),
                PlayerConfig(PlayerId("local-ai"), Team.RED),
            ),
            availableUnits = listOf("DEFENDER", "SAPPER"),
            availableAmmunition = listOf("ROCK", "SIEGE_BOMB"),
            playerLoadout = PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
        )
        val session = MatchSession(scenario = scenario)
        session.dispatch(MatchCommand.Start(CommandId("start-loadout"), MatchId("m"), playerId))
        val supplyBefore = session.snapshot().resourcesByTeam[Team.BLUE]?.supply

        assertFailsWith<LoadoutCommandRejectedException> {
            session.dispatch(
                MatchCommand.DeployUnit(
                    CommandId("deploy-unselected"),
                    MatchId("m"),
                    playerId,
                    "SAPPER",
                ),
            )
        }.also { assertEquals(LoadoutError.UNIT_UNAVAILABLE, it.error) }
        assertEquals(supplyBefore, session.snapshot().resourcesByTeam[Team.BLUE]?.supply)
        assertTrue(session.canDeployUnit(Team.BLUE, "DEFENDER"))
        assertTrue(!session.canDeployUnit(Team.BLUE, "SAPPER"))
    }

    @Test
    fun campaignAvailabilityAlsoRestrictsEnemyUnitDeployment() {
        val playerId = PlayerId("player")
        val enemyId = PlayerId("local-ai")
        val session = MatchSession(
            scenario = MatchScenario(
                scenarioId = "campaign-enemy-units",
                battlefield = BattlefieldConfig(100, 100),
                players = listOf(
                    PlayerConfig(playerId, Team.BLUE),
                    PlayerConfig(enemyId, Team.RED),
                ),
                availableUnits = listOf("DEFENDER"),
            ),
        )
        session.dispatch(MatchCommand.Start(CommandId("start-enemy-units"), MatchId("m"), playerId))

        assertTrue(session.canDeployUnit(Team.RED, "DEFENDER"))
        assertTrue(!session.canDeployUnit(Team.RED, "SAPPER"))
        assertFailsWith<LoadoutCommandRejectedException> {
            session.dispatch(
                MatchCommand.DeployUnit(
                    CommandId("deploy-enemy-sapper"),
                    MatchId("m"),
                    enemyId,
                    "SAPPER",
                ),
            )
        }.also { assertEquals(LoadoutError.UNIT_UNAVAILABLE, it.error) }
    }

    @Test
    fun campaignAvailabilityRestrictsEnemyAmmunitionAndInventory() {
        val playerId = PlayerId("player")
        val enemyId = PlayerId("local-ai")
        val matchId = MatchId("campaign-enemy-ammo")
        val session = MatchSession(
            scenario = MatchScenario(
                scenarioId = "campaign-enemy-ammo",
                battlefield = BattlefieldConfig(100, 100),
                players = listOf(
                    PlayerConfig(playerId, Team.BLUE),
                    PlayerConfig(enemyId, Team.RED),
                ),
                availableAmmunition = listOf("ROCK", "SIEGE_BOMB"),
            ),
        )
        val snapshot = session.dispatch(
            MatchCommand.Start(CommandId("start-enemy-ammo"), matchId, playerId),
        )

        assertTrue(
            snapshot.resourcesByTeam[Team.RED]?.ammunition
                ?.containsKey("POWDER_BARREL") == false,
        )
        assertFailsWith<LoadoutCommandRejectedException> {
            session.dispatch(
                MatchCommand.SelectAmmo(
                    CommandId("select-enemy-unavailable-ammo"),
                    matchId,
                    enemyId,
                    "POWDER_BARREL",
                ),
            )
        }.also { assertEquals(LoadoutError.AMMUNITION_UNAVAILABLE, it.error) }
    }

    @Test
    fun matchScenarioRejectsAvailableButUnselectedAmmunition() {
        val playerId = PlayerId("player")
        val scenario = MatchScenario(
            scenarioId = "loadout-ammo",
            battlefield = BattlefieldConfig(100, 100),
            players = listOf(
                PlayerConfig(playerId, Team.BLUE),
                PlayerConfig(PlayerId("local-ai"), Team.RED),
            ),
            availableUnits = listOf("DEFENDER"),
            availableAmmunition = listOf("ROCK", "SIEGE_BOMB", "POWDER_BARREL"),
            playerLoadout = PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
        )
        val session = MatchSession(scenario = scenario)
        session.dispatch(MatchCommand.Start(CommandId("start-loadout-ammo"), MatchId("m"), playerId))

        assertFailsWith<LoadoutCommandRejectedException> {
            session.dispatch(
                MatchCommand.SelectAmmo(
                    CommandId("select-unselected"),
                    MatchId("m"),
                    playerId,
                    "POWDER_BARREL",
                ),
            )
        }.also { assertEquals(LoadoutError.AMMUNITION_UNAVAILABLE, it.error) }
    }

    @Test
    fun restartRetainsTheCommittedLoadoutAndResetsSelectedAmmunition() {
        val playerId = PlayerId("player")
        val matchId = MatchId("restart-loadout")
        val scenario = MatchScenario(
            scenarioId = "restart-loadout",
            battlefield = BattlefieldConfig(100, 100),
            players = listOf(
                PlayerConfig(playerId, Team.BLUE),
                PlayerConfig(PlayerId("local-ai"), Team.RED),
            ),
            availableUnits = listOf("DEFENDER", "SAPPER"),
            availableAmmunition = listOf("ROCK", "SIEGE_BOMB"),
            playerLoadout = PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            ),
        )
        val session = MatchSession(scenario = scenario)
        session.dispatch(MatchCommand.Start(CommandId("start-restart-loadout"), matchId, playerId))
        session.dispatch(
            MatchCommand.SelectAmmo(
                CommandId("select-before-restart"),
                matchId,
                playerId,
                "SIEGE_BOMB",
            ),
        )

        session.restart(matchId, playerId)
        assertFailsWith<LoadoutCommandRejectedException> {
            session.dispatch(
                MatchCommand.DeployUnit(
                    CommandId("deploy-after-restart"),
                    matchId,
                    playerId,
                    "SAPPER",
                ),
            )
        }
        val fired = session.dispatch(
            MatchCommand.Fire(
                CommandId("fire-after-restart"),
                matchId,
                playerId,
                EntityId("blue-catapult"),
            ),
        )
        assertEquals("ROCK", fired.projectiles.single().ammunitionType)
    }
}
