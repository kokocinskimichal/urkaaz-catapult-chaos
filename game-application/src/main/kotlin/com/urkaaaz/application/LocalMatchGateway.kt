package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.simulation.CombatLogSink
import com.urkaaaz.simulation.NoOpCombatLogSink
import com.urkaaaz.simulation.AmmunitionLogSink
import com.urkaaaz.simulation.NoOpAmmunitionLogSink

/** Local in-memory application boundary for the Android host. */
class LocalMatchGateway(
    terrainBiome: String = "GREEN_FRONTIER",
    initialWind: WindSnapshot = WindSnapshot(),
    mapDefinition: MapDefinition? = null,
    scenario: MatchScenario? = null,
    blueFortressMaxHealth: Int = 500,
    combatLogSink: CombatLogSink = NoOpCombatLogSink,
    ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
    private val session: MatchSession = MatchSession(
        terrainBiome = terrainBiome,
        initialWind = initialWind,
        mapDefinition = mapDefinition,
        scenario = scenario,
        blueFortressMaxHealth = blueFortressMaxHealth,
        combatLogSink = combatLogSink,
        ammunitionLogSink = ammunitionLogSink,
    ),
    private val aiCoordinator: AiMatchCoordinator = AiMatchCoordinator(scenario),
) {
    fun dispatch(command: MatchCommand): MatchSnapshot {
        val snapshot = session.dispatch(command)
        if (command is MatchCommand.Start) {
            val seed = session.replay()?.randomSeed
                ?: error("started match is missing its recorded random seed")
            aiCoordinator.start(seed)
        }
        return snapshot
    }

    fun snapshot(): MatchSnapshot = session.snapshot()

    fun pause() {
        session.pause()
        aiCoordinator.pause()
    }

    fun resume() {
        session.resume()
        aiCoordinator.resume()
    }

    fun stop(): MatchSnapshot {
        aiCoordinator.stop()
        return session.stop()
    }

    fun restart(
        matchId: com.urkaaaz.contracts.MatchId,
        playerId: com.urkaaaz.contracts.PlayerId,
    ): MatchSnapshot {
        val snapshot = session.restart(matchId, playerId)
        val seed = session.replay()?.randomSeed
            ?: error("restarted match is missing its recorded random seed")
        aiCoordinator.start(seed)
        return snapshot
    }

    fun consumeEvents(): List<MatchEvent> = session.consumeEvents()

    fun replay(): MatchReplay? = session.replay()

    fun aiCommands(
        snapshot: MatchSnapshot,
        elapsedMilliseconds: Long,
    ): List<MatchCommand> = aiCoordinator.decide(snapshot, elapsedMilliseconds)

    fun setAiUnitAutomationEnabled(enabled: Boolean) =
        aiCoordinator.setUnitAutomationEnabled(enabled)

    fun setAiAllowedUnitIds(ids: Set<String>) = aiCoordinator.setAllowedUnitIds(ids)

    fun aiAllowedUnitIds(): Set<String> = aiCoordinator.allowedUnitIds()

    fun aiAllowedAmmunitionIds(): Set<String> = aiCoordinator.allowedAmmunitionIds()

    fun canDeployUnit(team: com.urkaaaz.contracts.Team, unitType: String): Boolean =
        session.canDeployUnit(team, unitType)
}
