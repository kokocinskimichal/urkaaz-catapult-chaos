package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.simulation.CombatLogSink
import com.urkaaaz.simulation.NoOpCombatLogSink

/** Local in-memory application boundary for the Android host. */
class LocalMatchGateway(
    terrainBiome: String = "GREEN_FRONTIER",
    initialWind: WindSnapshot = WindSnapshot(),
    combatLogSink: CombatLogSink = NoOpCombatLogSink,
    private val session: MatchSession = MatchSession(
        terrainBiome = terrainBiome,
        initialWind = initialWind,
        combatLogSink = combatLogSink,
    ),
) {
    fun dispatch(command: MatchCommand): MatchSnapshot = session.dispatch(command)

    fun pause() = session.pause()

    fun resume() = session.resume()

    fun stop(): MatchSnapshot = session.stop()

    fun restart(
        matchId: com.urkaaaz.contracts.MatchId,
        playerId: com.urkaaaz.contracts.PlayerId,
    ): MatchSnapshot = session.restart(matchId, playerId)

    fun consumeEvents(): List<MatchEvent> = session.consumeEvents()

    fun canDeployUnit(team: com.urkaaaz.contracts.Team, unitType: String): Boolean =
        session.canDeployUnit(team, unitType)
}
