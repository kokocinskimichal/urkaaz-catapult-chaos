package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchSnapshot

/** Local in-memory application boundary for the Android host. */
class LocalMatchGateway(
    terrainBiome: String = "GREEN_FRONTIER",
    private val session: MatchSession = MatchSession(terrainBiome),
) {
    fun dispatch(command: MatchCommand): MatchSnapshot = session.dispatch(command)

    fun pause() = session.pause()

    fun resume() = session.resume()

    fun stop(): MatchSnapshot = session.stop()

    fun restart(
        matchId: com.urkaaaz.contracts.MatchId,
        playerId: com.urkaaaz.contracts.PlayerId,
    ): MatchSnapshot = session.restart(matchId, playerId)

    fun events(): List<MatchEvent> = session.events()
}
