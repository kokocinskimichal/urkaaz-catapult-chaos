package com.urkaaaz.ui

import com.urkaaaz.campaign.CampaignLevelDefinition
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
import com.urkaaaz.simulation.CombatLogSink
import com.urkaaaz.simulation.NoOpCombatLogSink
import com.urkaaaz.simulation.AmmunitionLogSink
import com.urkaaaz.simulation.NoOpAmmunitionLogSink

/**
 * Presentation coordinator for one Android match screen.
 *
 * It translates UI intents into public commands and exposes only mapped
 * snapshot data to views.
 */
class MatchViewModel(
    campaignLevel: Int = 1,
    private val combatLogSink: CombatLogSink = NoOpCombatLogSink,
    private val ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
    private val gateway: LocalMatchGateway = LocalMatchGateway(
        CampaignLevelDefinition.forLevel(campaignLevel).terrain.biome.name,
        CampaignLevelDefinition.forLevel(campaignLevel).initialWind,
        combatLogSink = combatLogSink,
        ammunitionLogSink = ammunitionLogSink,
    ),
    private val matchId: MatchId = MatchId("local-match"),
    private val playerId: PlayerId = PlayerId("local-player"),
) {
    private var commandSequence = 0
    private var paused = false
    private var aiUnitAutomationEnabled = true
    private var aiUnitDecisionElapsedMilliseconds = 0L
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

    fun setAiAllowedUnitIds(unitIds: Set<String>) {
        aiAgent.setAllowedUnitIds(unitIds)
    }

    fun aiAllowedUnitIds(): Set<String> =
        aiAgent.allowedUnitIds()

    fun setAiUnitAutomationEnabled(enabled: Boolean) {
        aiUnitAutomationEnabled = enabled
    }

    fun debugRecruitEnemyUnit(unitId: String): RenderState {
        if (unitId !in aiAgent.allowedUnitIds() ||
            !gateway.canDeployUnit(com.urkaaaz.contracts.Team.RED, unitId)
        ) {
            return renderState
        }
        val snapshot = gateway.dispatch(
            MatchCommand.DeployUnit(
                commandId = CommandId("debug-ai-${++commandSequence}-deploy"),
                matchId = matchId,
                playerId = PlayerId("local-ai"),
                unitType = unitId,
            ),
        )
        return RenderStateMapper.map(snapshot, gateway.consumeEvents())
            .also { renderState = it }
    }

    fun debugSendEnemyWave(): RenderState {
        val snapshot = gateway.dispatch(
            MatchCommand.SendWave(
                commandId = CommandId("debug-ai-${++commandSequence}-send-wave"),
                matchId = matchId,
                playerId = PlayerId("local-ai"),
            ),
        )
        return RenderStateMapper.map(snapshot, gateway.consumeEvents())
            .also { renderState = it }
    }

    fun dispatch(action: MatchUiAction): RenderState {
        var snapshot = when (action) {
            MatchUiAction.StartMatch -> {
                paused = false
                aiUnitDecisionElapsedMilliseconds = 0L
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
            is MatchUiAction.DeployUnit -> {
                if (!gateway.canDeployUnit(com.urkaaaz.contracts.Team.BLUE, action.unitType)) {
                    return renderState
                }
                gateway.dispatch(
                    MatchCommand.DeployUnit(
                        commandId = CommandId("ui-${++commandSequence}"),
                        matchId = matchId,
                        playerId = playerId,
                        unitType = action.unitType,
                    ),
                )
            }
            MatchUiAction.SendWave -> gateway.dispatch(
                MatchCommand.SendWave(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                ),
            )
        }
        var presentationEvents = gateway.consumeEvents()
        if (action == MatchUiAction.AdvanceSimulation &&
            snapshot.status == com.urkaaaz.contracts.MatchStatus.RUNNING
        ) {
            aiAgent.decide(snapshot).forEach { command ->
                snapshot = gateway.dispatch(command)
            }
            presentationEvents += gateway.consumeEvents()
            if (aiUnitAutomationEnabled) {
                aiUnitDecisionElapsedMilliseconds += 33L
            }
            if (aiUnitAutomationEnabled &&
                aiUnitDecisionElapsedMilliseconds >= AI_UNIT_DECISION_INTERVAL_MILLISECONDS
            ) {
                aiUnitDecisionElapsedMilliseconds = 0L
                aiAgent.chooseDeployment(snapshot)?.let { command ->
                    snapshot = gateway.dispatch(command)
                    presentationEvents += gateway.consumeEvents()
                }
                val redUnits = snapshot.units.filter {
                    it.team == com.urkaaaz.contracts.Team.RED
                }
                if (redUnits.size >= aiAgent.waveThreshold() &&
                    redUnits.none { it.moving }
                ) {
                    snapshot = gateway.dispatch(
                        MatchCommand.SendWave(
                            commandId = CommandId("ai-${matchId.value}-send-wave-${snapshot.units.size}"),
                            matchId = matchId,
                            playerId = PlayerId("local-ai"),
                        ),
                    )
                    presentationEvents += gateway.consumeEvents()
                }
            }
        }
        return RenderStateMapper.map(snapshot, presentationEvents).also { renderState = it }
    }

    private companion object {
        const val AI_UNIT_DECISION_INTERVAL_MILLISECONDS = 1_000L
    }
}
