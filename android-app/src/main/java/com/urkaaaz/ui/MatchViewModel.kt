package com.urkaaaz.ui

import com.urkaaaz.application.LocalMatchGateway
import com.urkaaaz.ai.AiAgent
import com.urkaaaz.ai.AiConfiguration
import com.urkaaaz.ai.AiDifficulty
import com.urkaaaz.ai.AiTemperament
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.PlayerId

/**
 * Presentation coordinator for one Android match screen.
 *
 * It translates UI intents into public commands and exposes only mapped
 * snapshot data to views.
 */
class MatchViewModel(
    private val gateway: LocalMatchGateway = LocalMatchGateway(),
    private val matchId: MatchId = MatchId("local-match"),
    private val playerId: PlayerId = PlayerId("local-player"),
) {
    private var commandSequence = 0
    private var paused = false
    private val aiAgent = AiAgent(
        AiConfiguration(
            playerId = PlayerId("local-ai"),
            team = com.urkaaaz.contracts.Team.RED,
            difficulty = AiDifficulty.EASY,
            temperament = AiTemperament.CAUTIOUS,
            randomSeed = 7,
        ),
    )

    var renderState: RenderState = RenderStateMapper.map(MatchSnapshot())
        private set

    fun dispatch(action: MatchUiAction): RenderState {
        var snapshot = when (action) {
            MatchUiAction.StartMatch -> {
                paused = false
                gateway.dispatch(
                    MatchCommand.Start(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    ),
                )
            }
            MatchUiAction.FireRock -> gateway.dispatch(
                MatchCommand.Fire(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    catapultId = com.urkaaaz.contracts.EntityId("blue-catapult"),
                ),
            )
            MatchUiAction.AdvanceSimulation -> gateway.dispatch(
                MatchCommand.AdvanceSimulation(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    deltaMilliseconds = 33,
                ),
            )
            MatchUiAction.TogglePause -> {
                paused = !paused
                if (paused) gateway.pause() else gateway.resume()
                return renderState.copy(statusLabel = if (paused) "PAUSED" else "RUNNING")
                    .also { renderState = it }
            }
            is MatchUiAction.Aim -> gateway.dispatch(
                MatchCommand.Aim(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    catapultId = com.urkaaaz.contracts.EntityId("blue-catapult"),
                    directionDegrees = action.directionDegrees,
                    power = action.power,
                ),
            )
            is MatchUiAction.SelectAmmo -> gateway.dispatch(
                MatchCommand.SelectAmmo(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    ammunitionType = action.ammunitionType,
                ),
            )
        }
        var presentationEvents = gateway.events()
        if (action == MatchUiAction.AdvanceSimulation &&
            snapshot.status == com.urkaaaz.contracts.MatchStatus.RUNNING
        ) {
            aiAgent.decide(snapshot).forEach { command ->
                snapshot = gateway.dispatch(command)
            }
            presentationEvents += gateway.events()
        }
        return RenderStateMapper.map(snapshot, presentationEvents).also { renderState = it }
    }
}
