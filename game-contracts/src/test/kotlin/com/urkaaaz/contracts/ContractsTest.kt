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
    fun playerLoadoutIsValidatedAndSeparateFromScenarioAvailability() {
        val loadout = PlayerLoadoutConfig(
            playerId = playerId,
            unitTypes = listOf("DEFENDER"),
            ammunitionSlots = listOf("ROCK", "SIEGE_BOMB", null),
            spellTypes = listOf("FORTRESS_SHIELD"),
        )
        val scenario = MatchScenario(
            scenarioId = "loadout",
            battlefield = BattlefieldConfig(width = 100, height = 100),
            players = listOf(PlayerConfig(playerId, Team.BLUE)),
            availableUnits = listOf("DEFENDER", "SAPPER"),
            availableAmmunition = listOf("ROCK", "SIEGE_BOMB"),
            availableSpells = listOf("FORTRESS_SHIELD", "HASTE"),
            playerLoadout = loadout,
        )

        assertEquals(listOf("DEFENDER", "SAPPER"), scenario.availableUnits)
        assertEquals(listOf("DEFENDER"), scenario.playerLoadout?.unitTypes)
        assertEquals(listOf("FORTRESS_SHIELD"), scenario.playerLoadout?.spellTypes)
    }

    @Test
    fun playerLoadoutRejectsInvalidSlotsAndDuplicateSelections() {
        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = listOf("DEFENDER", "DEFENDER"),
                ammunitionSlots = listOf("SIEGE_BOMB", "ROCK", null),
                spellTypes = listOf("FORTRESS_SHIELD"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PlayerLoadoutConfig(
                playerId = playerId,
                unitTypes = listOf("DEFENDER"),
                ammunitionSlots = listOf("ROCK", "SIEGE_BOMB"),
                spellTypes = listOf("FORTRESS_SHIELD"),
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
            randomSeed = 7L,
        )

        assertEquals(matchId, event.matchId)
        assertEquals(0, event.simulationTimeMilliseconds)
        assertEquals(7L, event.randomSeed)
    }

    @Test
    fun mapDefinitionCarriesTheSharedJsonContract() {
        val definition = MapDefinition(
            schemaVersion = 1,
            mapId = "test-map",
            biome = "green_frontier",
            world = MapWorldDimensions(80, 100),
            terrain = TerrainProfile(
                sampleStep = 40f,
                samples = listOf(
                    TerrainSample(0f, 50f),
                    TerrainSample(40f, 55f),
                    TerrainSample(80f, 60f),
                ),
            ),
            protectedTerrainZones = emptyList(),
            fortresses = emptyList(),
            decorations = emptyList(),
            initialCameraAnchor = InitialCameraAnchor.ENEMY_FORTRESS,
            initialCameraZoom = 1.3f,
            generationMetadata = MapGenerationMetadata(
                seed = 1L,
                generatorVersion = "test",
                authoringVersion = "test-authoring",
            ),
        )

        assertEquals("test-map", definition.mapId)
        assertEquals("green_frontier", definition.biome)
        assertEquals(40f, definition.terrain.sampleStep)
        assertEquals(InitialCameraAnchor.ENEMY_FORTRESS, definition.initialCameraAnchor)
        assertEquals(1.3f, definition.initialCameraZoom)
        assertEquals(1L, definition.generationMetadata?.seed)
        assertEquals("test-authoring", definition.generationMetadata?.authoringVersion)
    }

    @Test
    fun sharedMatchEntityScaleUsesCurrentGlobalRules() {
        assertEquals(2.4f, WorldScale.FORTRESS_SCALE)
        assertEquals(672f, WorldScale.FORTRESS_WIDTH, 0.001f)
        assertEquals(504f, WorldScale.FORTRESS_HEIGHT, 0.001f)
        assertEquals(470.4f, WorldScale.FORTRESS_HALF_WIDTH, 0.001f)
        assertEquals(0.8f, WorldScale.CATAPULT_SIZE_MULTIPLIER)
        assertEquals(182.4f, WorldScale.CATAPULT_RENDER_WIDTH, 0.001f)
        assertEquals(3.0f, WorldScale.UNIT_SCALE)
    }

    @Test
    fun fortressAlphaProfileUsesInsetSilhouetteRectangle() {
        val rectangle = FortressHitboxProfile.rectangles.single()

        assertEquals(-253.96875f, rectangle.left, 0.001f)
        assertEquals(-191.953125f, rectangle.top, 0.001f)
        assertEquals(253.96875f, rectangle.right, 0.001f)
        assertEquals(191.953125f, rectangle.bottom, 0.001f)
    }
}
