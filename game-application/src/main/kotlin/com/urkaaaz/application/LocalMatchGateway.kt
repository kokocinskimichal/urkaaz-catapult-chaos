package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.CommandResult
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.application.reward.MatchEventObserver
import com.urkaaaz.simulation.CombatLogSink
import com.urkaaaz.simulation.NoOpCombatLogSink
import com.urkaaaz.simulation.AmmunitionLogSink
import com.urkaaaz.simulation.NoOpAmmunitionLogSink

data class LocalMatchGatewayConfiguration(
    val terrainBiome: String,
    val initialWind: WindSnapshot,
    val mapDefinition: MapDefinition?,
    val scenario: MatchScenario?,
    val blueFortressMaxHealth: Int?,
    val playerTeams: Map<PlayerId, Team>,
    val eventObserver: MatchEventObserver,
)

/** Local in-memory application boundary for the Android host. */
class LocalMatchGateway(
    terrainBiome: String = "GREEN_FRONTIER",
    initialWind: WindSnapshot = WindSnapshot(),
    mapDefinition: MapDefinition? = null,
    scenario: MatchScenario? = null,
    blueFortressMaxHealth: Int? = null,
    redFortressMaxHealth: Int? = null,
    combatLogSink: CombatLogSink = NoOpCombatLogSink,
    ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
    eventObserver: MatchEventObserver = MatchEventObserver { },
    session: MatchSession? = null,
    private val aiCoordinator: AiMatchCoordinator = AiMatchCoordinator(scenario),
    playerTeams: Map<PlayerId, Team> = emptyMap(),
) {
    private val activeSession = session ?: MatchSession(
        terrainBiome = terrainBiome,
        initialWind = initialWind,
        mapDefinition = mapDefinition,
        scenario = scenario,
        playerTeams = playerTeams,
        blueFortressMaxHealth = blueFortressMaxHealth,
        redFortressMaxHealth = redFortressMaxHealth,
        combatLogSink = combatLogSink,
        ammunitionLogSink = ammunitionLogSink,
        eventObserver = eventObserver,
    )

    fun dispatch(command: MatchCommand): MatchSnapshot {
        val snapshot = activeSession.dispatch(command)
        startAiIfNeeded(command)
        return snapshot
    }

    fun dispatchResult(command: MatchCommand): CommandResult {
        val result = activeSession.dispatchResult(command)
        startAiIfNeeded(command)
        return result
    }

    private fun startAiIfNeeded(command: MatchCommand) {
        if (command is MatchCommand.Start) {
            val seed = activeSession.replay()?.randomSeed
                ?: error("started match is missing its recorded random seed")
            aiCoordinator.start(seed, activeSession.balanceConfig())
        }
    }

    fun snapshot(): MatchSnapshot = activeSession.snapshot()

    fun rewardResult() = activeSession.rewardResult()

    fun pause() {
        activeSession.pause()
        aiCoordinator.pause()
    }

    fun resume() {
        activeSession.resume()
        aiCoordinator.resume()
    }

    fun pauseSimulation() {
        activeSession.pause()
    }

    fun resumeSimulation() {
        activeSession.resume()
    }

    fun suspendAi() {
        aiCoordinator.pause()
    }

    fun resumeAi() {
        aiCoordinator.resume()
    }

    fun stop(): MatchSnapshot {
        aiCoordinator.stop()
        return activeSession.stop()
    }

    fun restart(
        matchId: com.urkaaaz.contracts.MatchId,
        playerId: com.urkaaaz.contracts.PlayerId,
        scenarioOverride: MatchScenario? = null,
    ): MatchSnapshot {
        val snapshot = activeSession.restart(matchId, playerId, scenarioOverride)
        val seed = activeSession.replay()?.randomSeed
            ?: error("restarted match is missing its recorded random seed")
        aiCoordinator.start(seed, activeSession.balanceConfig())
        return snapshot
    }

    fun consumeEvents(): List<MatchEvent> = activeSession.consumeEvents()

    fun replay(): MatchReplay? = activeSession.replay()

    fun aiCommands(
        snapshot: MatchSnapshot,
        elapsedMilliseconds: Long,
    ): List<MatchCommand> = aiCoordinator.decide(snapshot, elapsedMilliseconds)

    fun setAiUnitAutomationEnabled(enabled: Boolean) =
        aiCoordinator.setUnitAutomationEnabled(enabled)

    fun setAiAllowedUnitIds(ids: Set<String>) = aiCoordinator.setAllowedUnitIds(ids)

    fun setAiAllowedAmmunitionIds(ids: Set<String>) =
        aiCoordinator.setAllowedAmmunitionIds(ids)

    fun aiAllowedUnitIds(): Set<String> = aiCoordinator.allowedUnitIds()

    fun aiAllowedAmmunitionIds(): Set<String> = aiCoordinator.allowedAmmunitionIds()

    fun aiUnitAutomationEnabled(): Boolean = aiCoordinator.unitAutomationEnabled()

    fun canDeployUnit(team: com.urkaaaz.contracts.Team, unitType: String): Boolean =
        activeSession.canDeployUnit(team, unitType)
}
