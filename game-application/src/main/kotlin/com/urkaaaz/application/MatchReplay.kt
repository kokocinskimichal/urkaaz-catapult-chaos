package com.urkaaaz.application

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.CommandResult
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.WindSnapshot

data class MatchReplay(
    val formatVersion: Int,
    val matchId: MatchId,
    val randomSeed: Long,
    val configuration: MatchReplayConfiguration,
    val steps: List<MatchReplayStep>,
) {
    init {
        require(formatVersion == CURRENT_FORMAT_VERSION) {
            "unsupported match replay format version: $formatVersion"
        }
        val startCommand = (steps.firstOrNull() as? MatchReplayStep.Command)
            ?.value as? MatchCommand.Start
        require(startCommand != null) {
            "match replay must start with a Start command"
        }
        require(steps.filterIsInstance<MatchReplayStep.Command>().all { it.value.matchId == matchId }) {
            "match replay commands must use the replay match ID"
        }
        require(startCommand.randomSeed == randomSeed) {
            "match replay start command must contain the replay seed"
        }
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

sealed interface MatchReplayStep {
    data class Command(val value: MatchCommand) : MatchReplayStep

    data object Pause : MatchReplayStep

    data object Resume : MatchReplayStep

    data object Stop : MatchReplayStep
}

data class MatchReplayConfiguration(
    val terrainBiome: String,
    val initialWind: WindSnapshot,
    val mapDefinition: MapDefinition?,
    val scenario: MatchScenario?,
    val blueFortressMaxHealth: Int,
    val redFortressMaxHealth: Int = 500,
)

data class MatchReplayResult(
    val snapshot: MatchSnapshot,
    val events: List<MatchEvent>,
)

object MatchReplayPlayer {
    fun play(replay: MatchReplay): MatchReplayResult {
        require(replay.formatVersion == MatchReplay.CURRENT_FORMAT_VERSION) {
            "unsupported match replay format version: ${replay.formatVersion}"
        }
        val session = MatchSession(
            terrainBiome = replay.configuration.terrainBiome,
            initialWind = replay.configuration.initialWind,
            mapDefinition = replay.configuration.mapDefinition,
            scenario = replay.configuration.scenario,
            blueFortressMaxHealth = replay.configuration.blueFortressMaxHealth,
            redFortressMaxHealth = replay.configuration.redFortressMaxHealth,
            randomSeedProvider = { replay.randomSeed },
        )
        val events = buildList {
            replay.steps.forEach { step ->
                when (step) {
                    is MatchReplayStep.Command -> {
                        when (val result = session.dispatchResult(step.value)) {
                            is CommandResult.Accepted -> addAll(result.events)
                            is CommandResult.Rejected -> Unit
                        }
                    }
                    MatchReplayStep.Pause -> session.pause()
                    MatchReplayStep.Resume -> session.resume()
                    MatchReplayStep.Stop -> session.stop()
                }
            }
        }
        return MatchReplayResult(session.snapshot(), events)
    }
}
