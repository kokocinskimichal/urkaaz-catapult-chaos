package com.urkaaaz.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ContractsTest {
    private val matchId = MatchId("match-1")
    private val playerId = PlayerId("player-1")
    private val commandId = CommandId("command-1")

    @Test
    fun commandsCarryIntentWithoutRuntimeObjects() {
        val command = MatchCommand.Fire(
            commandId = commandId,
            matchId = matchId,
            playerId = playerId,
            catapultId = EntityId("catapult-1"),
        )

        assertEquals(matchId, command.matchId)
        assertEquals(playerId, command.playerId)
    }

    @Test
    fun negativeSimulationTimeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            MatchCommand.AdvanceSimulation(
                commandId = commandId,
                matchId = matchId,
                playerId = playerId,
                deltaMilliseconds = -1,
            )
        }
    }

    @Test
    fun rejectedCommandContainsExplicitError() {
        val result: CommandResult = CommandResult.Rejected(
            commandId = commandId,
            error = MatchError.NOT_PLAYERS_TURN,
        )

        assertIs<CommandResult.Rejected>(result)
        assertEquals(MatchError.NOT_PLAYERS_TURN, result.error)
    }

    @Test
    fun scenarioRejectsEmptyPlayerList() {
        assertFailsWith<IllegalArgumentException> {
            MatchScenario(
                scenarioId = "empty",
                battlefield = BattlefieldConfig(width = 100, height = 100),
                players = emptyList(),
            )
        }
    }

    @Test
    fun finishedSnapshotRequiresOutcome() {
        assertFailsWith<IllegalArgumentException> {
            MatchSnapshot(status = MatchStatus.FINISHED)
        }
    }

    @Test
    fun eventDescribesCompletedFact() {
        val event = MatchEvent.MatchStarted(
            eventId = EventId("event-1"),
            matchId = matchId,
            simulationTimeMilliseconds = 0,
        )

        assertEquals(matchId, event.matchId)
        assertEquals(0, event.simulationTimeMilliseconds)
    }

    @Test
    fun mapDefinitionCarriesImmutableTypedAuthoringData() {
        val definition = MapDefinition(
            levelId = "test-map",
            schemaVersion = 1,
            worldWidth = 80f,
            worldHeight = 100f,
            terrain = TerrainProfile(
                samples = listOf(
                    TerrainSample(0f, 50f),
                    TerrainSample(40f, 55f),
                    TerrainSample(80f, 60f),
                ),
            ),
            protectedTerrainZones = emptyList(),
            fortresses = emptyList(),
            catapults = emptyList(),
            objects = emptyList(),
            initialCameraAnchor = InitialCameraAnchor.CENTER,
            generationMetadata = MapGenerationMetadata(
                seed = 1L,
                generatorVersion = "test",
                authoringVersion = "test",
                source = "contracts-test",
            ),
        )

        assertEquals(InitialCameraAnchor.CENTER, definition.initialCameraAnchor)
        assertEquals(40f, definition.terrain.sampleStep)
        assertEquals(1L, definition.generationMetadata.seed)
    }

    @Test
    fun sharedMatchEntityScaleUsesCurrentGlobalRules() {
        assertEquals(0.70f, WorldScale.FORTRESS_SCALE)
        assertEquals(137.2f, WorldScale.FORTRESS_HALF_WIDTH)
        assertEquals(3.0f, WorldScale.UNIT_SCALE)
    }
}
