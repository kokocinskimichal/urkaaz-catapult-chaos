package com.urkaaaz.application

import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.BattlefieldConfig
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerConfig
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalMatchGatewayTest {
    @Test
    fun localGatewayRunsAndAdvancesAnInMemoryMatch() {
        val gateway = LocalMatchGateway()
        val start = MatchCommand.Start(
            CommandId("start"),
            MatchId("match-1"),
            PlayerId("player-1"),
        )

        assertEquals(MatchStatus.RUNNING, gateway.dispatch(start).status)
        gateway.dispatch(
            MatchCommand.Fire(
                CommandId("fire"),
                MatchId("match-1"),
                PlayerId("player-1"),
                EntityId("blue-catapult"),
            ),
        )
        val advanced = gateway.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("advance"),
                MatchId("match-1"),
                PlayerId("player-1"),
                100,
            ),
        )

        assertTrue(advanced.projectiles.isNotEmpty())
    }

    @Test
    fun stoppingAndRestartingCreatesFreshState() {
        val gateway = LocalMatchGateway()
        gateway.dispatch(
            MatchCommand.Start(CommandId("start"), MatchId("match-1"), PlayerId("player-1")),
        )
        gateway.stop()

        val restarted = gateway.restart(MatchId("match-2"), PlayerId("player-1"))

        assertEquals(MatchStatus.RUNNING, restarted.status)
        assertEquals(MatchId("match-2"), restarted.matchId)
        assertTrue(restarted.projectiles.isEmpty())
    }

    @Test
    fun gatewayCreatesProfiledAiAndReplayReproducesItsCommands() {
        val redPlayer = PlayerId("red-ai")
        val scenario = MatchScenario(
            scenarioId = "gateway-ai",
            battlefield = BattlefieldConfig(width = 1_000, height = 600),
            players = listOf(
                PlayerConfig(PlayerId("blue-player"), Team.BLUE),
                PlayerConfig(redPlayer, Team.RED),
            ),
            availableUnits = listOf("DEFENDER"),
            availableAmmunition = listOf("ROCK"),
            aiProfile = AiProfileSelection(AiProfileId.CADET),
        )
        val session = MatchSession(scenario = scenario, randomSeedProvider = { 73L })
        val gateway = LocalMatchGateway(scenario = scenario, session = session)
        val matchId = MatchId("gateway-ai-match")
        gateway.dispatch(MatchCommand.Start(CommandId("start-ai"), matchId, PlayerId("blue-player")))

        val aiCommands = gateway.aiCommands(gateway.snapshot(), elapsedMilliseconds = 33)
        aiCommands.forEach(gateway::dispatch)
        gateway.dispatch(
            MatchCommand.AdvanceSimulation(
                CommandId("advance-ai"),
                matchId,
                PlayerId("blue-player"),
                100,
            ),
        )
        val replay = gateway.replay() ?: error("AI match replay was not recorded")
        val replayResult = MatchReplayPlayer.play(replay)

        assertTrue(aiCommands.any { it.playerId == redPlayer })
        assertEquals(gateway.snapshot(), replayResult.snapshot)
        assertTrue(
            replay.steps.filterIsInstance<MatchReplayStep.Command>()
                .any { it.value.playerId == redPlayer },
        )
    }
}
