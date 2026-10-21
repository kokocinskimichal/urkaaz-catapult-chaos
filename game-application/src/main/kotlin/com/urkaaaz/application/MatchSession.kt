package com.urkaaaz.application

import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.CommandResult
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.SpellType
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.application.loadout.LoadoutCommandRejectedException
import com.urkaaaz.application.loadout.LoadoutError
import com.urkaaaz.simulation.DeterministicMatchSimulation
import com.urkaaaz.simulation.CombatLogSink
import com.urkaaaz.simulation.NoOpCombatLogSink
import com.urkaaaz.simulation.SimulationConfig
import com.urkaaaz.simulation.AmmunitionLogSink
import com.urkaaaz.simulation.NoOpAmmunitionLogSink
import kotlin.random.Random

class MatchSession(
    private val terrainBiome: String = "GREEN_FRONTIER",
    private val initialWind: WindSnapshot = WindSnapshot(),
    private val mapDefinition: MapDefinition? = null,
    private val scenario: MatchScenario? = null,
    private val blueFortressMaxHealth: Int = 500,
    private val redFortressMaxHealth: Int = 500,
    private val combatLogSink: CombatLogSink = NoOpCombatLogSink,
    private val ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
    private val randomSeedProvider: () -> Long = { Random.nextLong() },
) {
    private var simulation: DeterministicMatchSimulation? = null
    private val selectedAmmunition = mutableMapOf(
        Team.BLUE to AmmunitionType.ROCK,
        Team.RED to AmmunitionType.ROCK,
    )
    private var paused = false
    private var latestSnapshot = MatchSnapshot()
    private var latestEvents = emptyList<com.urkaaaz.contracts.MatchEvent>()
    private var replaySeed: Long? = null
    private val replaySteps = mutableListOf<MatchReplayStep>()

    private val replayConfiguration = MatchReplayConfiguration(
        terrainBiome = terrainBiome,
        initialWind = initialWind,
        mapDefinition = mapDefinition,
        scenario = scenario,
        blueFortressMaxHealth = blueFortressMaxHealth,
        redFortressMaxHealth = redFortressMaxHealth,
    )

    fun dispatch(command: MatchCommand): MatchSnapshot {
        if (command is MatchCommand.CastSpell) {
            return when (val result = dispatchSpell(command, includeEventsInResult = false)) {
                is CommandResult.Accepted -> latestSnapshot
                is CommandResult.Rejected -> throw MatchCommandRejectedException(result.error)
            }
        }
        latestEvents = emptyList()
        latestSnapshot = when (command) {
            is MatchCommand.Start -> start(command)
            is MatchCommand.SelectAmmo -> {
                val team = teamForPlayer(command.playerId)
                val ammunition = AmmunitionType.values().firstOrNull {
                    it.name == command.ammunitionType
                } ?: throw LoadoutCommandRejectedException(LoadoutError.UNKNOWN_AMMUNITION)
                enforceAmmunitionSelection(command.playerId, command.ammunitionType)
                selectedAmmunition[team] = ammunition
                requireRunning().snapshot()
            }
            is MatchCommand.DeployUnit -> {
                enforceUnitSelection(command.playerId, command.unitType)
                val unitType = UnitFactory.byId(command.unitType)
                requireRunning().deployUnit(unitType, teamForPlayer(command.playerId))
                latestEvents = requireRunning().drainEvents()
                requireRunning().snapshot()
            }
            is MatchCommand.SendWave -> {
                requireRunning().sendWave(teamForPlayer(command.playerId))
                latestEvents = requireRunning().drainEvents()
                requireRunning().snapshot()
            }
            is MatchCommand.Aim -> {
                requireRunning().aim(command.catapultId, command.directionDegrees, command.power)
                latestEvents = requireRunning().drainEvents()
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
                val runningSimulation = requireRunning()
                latestEvents = if (!paused) {
                    runningSimulation.advance(command.deltaMilliseconds)
                } else {
                    emptyList()
                }
                runningSimulation.snapshot()
            }
            else -> throw UnsupportedOperationException(
                "M10 command is not implemented by the local session: ${command::class.simpleName}",
            )
        }
        if (command !is MatchCommand.Start) {
            replaySteps += MatchReplayStep.Command(command)
        }
        return latestSnapshot
    }

    fun dispatchResult(command: MatchCommand): CommandResult {
        if (command is MatchCommand.CastSpell) {
            return dispatchSpell(command, includeEventsInResult = true)
        }
        dispatch(command)
        return CommandResult.Accepted(
            commandId = command.commandId,
            events = consumeEvents(),
        )
    }

    private fun dispatchSpell(
        command: MatchCommand.CastSpell,
        includeEventsInResult: Boolean,
    ): CommandResult {
        latestEvents = emptyList()
        fun rejected(error: MatchError): CommandResult.Rejected {
            if (replaySeed != null) replaySteps += MatchReplayStep.Command(command)
            return CommandResult.Rejected(command.commandId, error)
        }

        val runningSimulation = simulation
            ?: return rejected(MatchError.MATCH_NOT_RUNNING)
        when (runningSimulation.snapshot().status) {
            MatchStatus.RUNNING -> Unit
            MatchStatus.FINISHED -> return rejected(MatchError.ALREADY_FINISHED)
            else -> return rejected(MatchError.MATCH_NOT_RUNNING)
        }
        val spell = SpellCatalog.find(command.spellType)
            ?: return rejected(MatchError.UNKNOWN_SPELL)
        val matchScenario = scenario
            ?: return rejected(MatchError.SPELL_NOT_AVAILABLE)
        if (command.spellType !in matchScenario.availableSpells) {
            return rejected(MatchError.SPELL_NOT_AVAILABLE)
        }
        val team = teamForPlayer(command.playerId)
        if (team == Team.BLUE) {
            val loadout = matchScenario.playerLoadout
            if (loadout?.playerId != command.playerId ||
                command.spellType !in loadout.spellTypes
            ) {
                return rejected(MatchError.SPELL_NOT_IN_LOADOUT)
            }
        }
        if (!spell.isImplemented) return rejected(MatchError.SPELL_NOT_IMPLEMENTED)

        val error = when (spell.type) {
            SpellType.FORTRESS_SHIELD ->
                runningSimulation.castFortressShield(command.playerId, team, command.target)
            else -> MatchError.SPELL_NOT_IMPLEMENTED
        }
        if (error != null) return rejected(error)

        latestEvents = runningSimulation.drainEvents()
        latestSnapshot = runningSimulation.snapshot()
        if (replaySeed != null) replaySteps += MatchReplayStep.Command(command)
        val result = CommandResult.Accepted(command.commandId, latestEvents)
        if (includeEventsInResult) latestEvents = emptyList()
        return result
    }

    fun replay(): MatchReplay? {
        val seed = replaySeed ?: return null
        val startCommand = (replaySteps.firstOrNull() as? MatchReplayStep.Command)
            ?.value as? MatchCommand.Start
            ?: error("recorded match replay is missing its start command")
        return MatchReplay(
            formatVersion = MatchReplay.CURRENT_FORMAT_VERSION,
            matchId = startCommand.matchId,
            randomSeed = seed,
            configuration = replayConfiguration,
            steps = replaySteps.toList(),
        )
    }

    fun pause() {
        if (!paused) {
            paused = true
            if (replaySeed != null) replaySteps += MatchReplayStep.Pause
        }
    }

    fun resume() {
        if (paused) {
            paused = false
            if (replaySeed != null) replaySteps += MatchReplayStep.Resume
        }
    }

    fun stop(): MatchSnapshot {
        if (simulation != null && replaySeed != null) replaySteps += MatchReplayStep.Stop
        simulation = null
        paused = false
        latestEvents = emptyList()
        latestSnapshot = MatchSnapshot()
        return latestSnapshot
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

    fun snapshot(): MatchSnapshot = simulation?.snapshot() ?: latestSnapshot

    fun canDeployUnit(team: Team, unitType: String): Boolean {
        val matchScenario = scenario
        if (matchScenario != null) {
            if (matchScenario.availableUnits.isNotEmpty() &&
                unitType !in matchScenario.availableUnits
            ) return false
            if (team == Team.BLUE) {
                val loadout = matchScenario.playerLoadout
                if (loadout != null && unitType !in loadout.unitTypes) return false
            }
        }
        val definition = runCatching { UnitFactory.byId(unitType) }.getOrNull() ?: return false
        val resources = latestSnapshot.resourcesByTeam[team] ?: return false
        if (resources.supply < definition.supplyCost) return false
        return definition !is com.urkaaaz.domain.SapperUnit ||
            latestSnapshot.units.none {
                it.team == team &&
                    it.unitType == definition.id &&
                    it.health > 0
            }
    }

    private fun start(command: MatchCommand.Start): MatchSnapshot {
        selectedAmmunition[Team.BLUE] = AmmunitionType.ROCK
        selectedAmmunition[Team.RED] = AmmunitionType.ROCK
        val randomSeed = command.randomSeed ?: randomSeedProvider()
        simulation = DeterministicMatchSimulation(
            matchId = command.matchId,
            config = SimulationConfig(
                bounds = WorldBounds(
                    width = WorldDimensions.WIDTH,
                    height = WorldDimensions.HEIGHT,
                ),
                terrainBiome = terrainBiome,
                windAccelerationX = initialWind.direction * initialWind.strength,
                blueFortressMaxHealth = blueFortressMaxHealth,
                redFortressMaxHealth = redFortressMaxHealth,
                mapDefinition = mapDefinition,
                playerLoadout = scenario?.playerLoadout,
                availableAmmunitionTypes = scenario?.availableAmmunition?.toSet().orEmpty(),
                combatRandomSeed = randomSeed,
            ),
            combatLogSink = combatLogSink,
            ammunitionLogSink = ammunitionLogSink,
        )
        latestEvents = requireNotNull(simulation).start()
        replaySeed = randomSeed
        replaySteps.clear()
        replaySteps += MatchReplayStep.Command(command.copy(randomSeed = randomSeed))
        paused = false
        return requireNotNull(simulation).snapshot()
    }

    private fun requireRunning(): DeterministicMatchSimulation =
        requireNotNull(simulation) { "match is not running" }.also {
            check(it.snapshot().status == MatchStatus.RUNNING) { "match is not running" }
        }

    private fun teamForPlayer(playerId: com.urkaaaz.contracts.PlayerId): Team =
        scenario?.players?.firstOrNull { it.playerId == playerId }?.team
            ?: if (playerId.value.contains("ai", ignoreCase = true)) Team.RED else Team.BLUE

    private fun enforceUnitSelection(
        playerId: com.urkaaaz.contracts.PlayerId,
        unitType: String,
    ) {
        val matchScenario = scenario ?: return
        if (matchScenario.availableUnits.isNotEmpty() &&
            unitType !in matchScenario.availableUnits
        ) {
            throw LoadoutCommandRejectedException(LoadoutError.UNIT_UNAVAILABLE)
        }
        if (teamForPlayer(playerId) != Team.BLUE) return
        val loadout = matchScenario.playerLoadout
        if (loadout?.playerId == playerId && unitType !in loadout.unitTypes) {
            throw LoadoutCommandRejectedException(LoadoutError.UNIT_UNAVAILABLE)
        }
    }

    private fun enforceAmmunitionSelection(
        playerId: com.urkaaaz.contracts.PlayerId,
        ammunitionType: String,
    ) {
        val matchScenario = scenario ?: return
        if (matchScenario.availableAmmunition.isNotEmpty() &&
            ammunitionType !in matchScenario.availableAmmunition
        ) {
            throw LoadoutCommandRejectedException(LoadoutError.AMMUNITION_UNAVAILABLE)
        }
        if (teamForPlayer(playerId) != Team.BLUE) return
        val loadout = matchScenario.playerLoadout
        if (loadout?.playerId == playerId &&
            ammunitionType !in loadout.ammunitionSlots.filterNotNull()
        ) {
            throw LoadoutCommandRejectedException(LoadoutError.AMMUNITION_UNAVAILABLE)
        }
    }
}
