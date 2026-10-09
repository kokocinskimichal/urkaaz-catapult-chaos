package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.contracts.Team
import com.urkaaaz.simulation.DeterministicMatchSimulation
import com.urkaaaz.simulation.SimulationConfig

class MatchSession(
    private val terrainBiome: String = "GREEN_FRONTIER",
) {
    private var simulation: DeterministicMatchSimulation? = null
    private val selectedAmmunition = mutableMapOf(
        Team.BLUE to AmmunitionType.ROCK,
        Team.RED to AmmunitionType.ROCK,
    )
    private var paused = false
    private var latestSnapshot = MatchSnapshot()
    private var latestEvents = emptyList<com.urkaaaz.contracts.MatchEvent>()

    fun dispatch(command: MatchCommand): MatchSnapshot {
        latestSnapshot = when (command) {
            is MatchCommand.Start -> start(command)
            is MatchCommand.SelectAmmo -> {
                val team = teamForPlayer(command.playerId)
                selectedAmmunition[team] = runCatching {
                    AmmunitionType.valueOf(command.ammunitionType)
                }.getOrElse { throw IllegalArgumentException("unknown ammunition: ${command.ammunitionType}") }
                requireRunning().snapshot()
            }
            is MatchCommand.Aim -> {
                requireRunning().aim(command.catapultId, command.directionDegrees, command.power)
                requireRunning().snapshot()
            }
            is MatchCommand.Fire -> {
                val team = if (command.catapultId.value.startsWith("red")) Team.RED else Team.BLUE
                latestEvents = requireRunning().fire(
                    command.catapultId,
                    selectedAmmunition.getValue(team),
                    command.playerId,
                )
                requireRunning().snapshot()
            }
            is MatchCommand.AdvanceSimulation -> {
                latestEvents = if (!paused) {
                    requireRunning().advance(command.deltaMilliseconds)
                } else {
                    emptyList()
                }
                requireRunning().snapshot()
            }
            else -> throw UnsupportedOperationException(
                "M10 command is not implemented by the local session: ${command::class.simpleName}",
            )
        }
        return latestSnapshot
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun stop(): MatchSnapshot {
        simulation = null
        paused = false
        latestEvents = emptyList()
        return MatchSnapshot()
    }

    fun restart(matchId: com.urkaaaz.contracts.MatchId, playerId: com.urkaaaz.contracts.PlayerId): MatchSnapshot =
        start(
            MatchCommand.Start(
                commandId = com.urkaaaz.contracts.CommandId("restart"),
                matchId = matchId,
                playerId = playerId,
            ),
        )

    fun consumeEvents(): List<com.urkaaaz.contracts.MatchEvent> =
        latestEvents.also { latestEvents = emptyList() }

    private fun start(command: MatchCommand.Start): MatchSnapshot {
        simulation = DeterministicMatchSimulation(
            matchId = command.matchId,
            config = SimulationConfig(
                bounds = WorldBounds(width = 1_600f, height = 900f),
                terrainBiome = terrainBiome,
            ),
        )
        latestEvents = requireNotNull(simulation).start()
        paused = false
        return requireNotNull(simulation).snapshot()
    }

    private fun requireRunning(): DeterministicMatchSimulation =
        requireNotNull(simulation) { "match is not running" }.also {
            check(it.snapshot().status == MatchStatus.RUNNING) { "match is not running" }
        }

    private fun teamForPlayer(playerId: com.urkaaaz.contracts.PlayerId): Team =
        if (playerId.value.contains("ai", ignoreCase = true)) Team.RED else Team.BLUE
}
