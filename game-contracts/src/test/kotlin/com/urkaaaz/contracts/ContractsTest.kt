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
}
