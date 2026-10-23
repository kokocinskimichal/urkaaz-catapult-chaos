package com.urkaaaz.ui

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.DemoLevelDefinition
import com.urkaaaz.campaign.DemoLevelDefinitions
import com.urkaaaz.campaign.MapDefinitionRepository
import com.urkaaaz.application.LocalMatchGateway
import com.urkaaaz.application.MatchReplay
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.CommandResult
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.TerrainBiome
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
    private val campaignLevel: Int = 1,
    private val demoMode: Boolean = false,
    playerLoadout: PlayerLoadoutConfig? = null,
    mapRepository: MapDefinitionRepository = MapDefinitionRepository.bundled,
    private val combatLogSink: CombatLogSink = NoOpCombatLogSink,
    private val ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
    private val matchId: MatchId = MatchId("local-match"),
    private val playerId: PlayerId = playerLoadout?.playerId ?: PlayerId("local-player"),
    private val onMatchFinished: (Int, MatchEvent.MatchFinished) -> Unit = { _, _ -> },
    private val gateway: LocalMatchGateway = LocalMatchGateway(
        loadSelectedDefinition(campaignLevel, demoMode, mapRepository).biome.name,
        loadSelectedDefinition(campaignLevel, demoMode, mapRepository).initialWind,
        mapDefinition = loadSelectedDefinition(campaignLevel, demoMode, mapRepository).mapDefinition,
        scenario = loadSelectedScenario(
            campaignLevel,
            demoMode,
            mapRepository,
            playerLoadout,
            playerId,
        ),
        blueFortressMaxHealth = if (demoMode) {
            DEMO_BLUE_FORTRESS_MAX_HEALTH
        } else {
            DEFAULT_FORTRESS_MAX_HEALTH
        },
        redFortressMaxHealth = DEFAULT_FORTRESS_MAX_HEALTH,
        combatLogSink = combatLogSink,
        ammunitionLogSink = ammunitionLogSink,
    ),
) {
    private val campaignScenario = loadSelectedScenario(
        campaignLevel,
        demoMode,
        mapRepository,
        playerLoadout,
        playerId,
    )

    val selectableAmmunitionTypes: List<String> = campaignScenario?.let { scenario ->
        val committedTypes = scenario.playerLoadout?.ammunitionSlots
            ?.filterNotNull()
            ?: scenario.availableAmmunition
        committedTypes
            .filter { it in scenario.availableAmmunition }
            .take(PlayerLoadoutConfig.AMMUNITION_SLOT_COUNT)
    } ?: listOf("ROCK", "SIEGE_BOMB", "POWDER_BARREL")

    val selectableUnitTypes: List<String> = campaignScenario?.let { scenario ->
        val committedTypes = scenario.playerLoadout?.unitTypes ?: scenario.availableUnits
        committedTypes
            .filter { it in scenario.availableUnits }
            .take(PlayerLoadoutConfig.MAX_UNITS)
    } ?: LoadoutCatalog.units
        .take(PlayerLoadoutConfig.MAX_UNITS)
        .map { it.id }

    val selectableSpellTypes: List<String> = campaignScenario?.let { scenario ->
        scenario.playerLoadout?.spellTypes.orEmpty()
            .filter { it in scenario.availableSpells }
            .take(PlayerLoadoutConfig.MAX_SPELLS)
    }.orEmpty()

    private var commandSequence = 0
    private var matchGeneration = 0L
    private var paused = false
    private var selectedAmmunition = "ROCK"
    private val forwardedCampaignCompletions = mutableSetOf<Triple<Long, MatchId, EventId>>()
    private var spellFeedbackLabel: String? = null
    var renderState: RenderState = RenderStateMapper.map(MatchSnapshot())
        private set

    fun setAiAllowedUnitIds(unitIds: Set<String>) {
        gateway.setAiAllowedUnitIds(unitIds)
    }

    fun aiAllowedUnitIds(): Set<String> =
        gateway.aiAllowedUnitIds()

    fun aiAllowedAmmunitionIds(): Set<String> =
        gateway.aiAllowedAmmunitionIds()

    fun recordedReplay(): MatchReplay? = gateway.replay()

    fun setAiUnitAutomationEnabled(enabled: Boolean) {
        gateway.setAiUnitAutomationEnabled(enabled)
    }

    fun debugRecruitEnemyUnit(unitId: String): RenderState {
        if (unitId !in gateway.aiAllowedUnitIds() ||
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
        return mapRenderState(snapshot, gateway.consumeEvents())
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
        return mapRenderState(snapshot, gateway.consumeEvents())
            .also { renderState = it }
    }

    fun dispatch(action: MatchUiAction): RenderState {
        if (action is MatchUiAction.CastSpell) {
            return dispatchSpell(action.spellType)
        }
        if (action == MatchUiAction.AdvanceSimulation) {
            val currentSnapshot = gateway.snapshot()
            if (currentSnapshot.status != com.urkaaaz.contracts.MatchStatus.RUNNING) {
                return mapRenderState(currentSnapshot, gateway.consumeEvents())
                    .also { renderState = it }
            }
        }
        if (action == MatchUiAction.FireRock && !selectedAmmunitionIsAvailable()) {
            return renderState
        }
        var snapshot = when (action) {
            MatchUiAction.StartMatch -> {
                paused = false
                matchGeneration += 1L
                spellFeedbackLabel = null
                gateway.dispatch(
                    MatchCommand.Start(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    ),
                )
            }
            MatchUiAction.RestartMatch -> {
                paused = false
                matchGeneration += 1L
                selectedAmmunition = "ROCK"
                spellFeedbackLabel = null
                gateway.restart(matchId, playerId)
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
                return renderState.copy(
                    statusLabel = if (paused) "PAUSED" else "RUNNING",
                    isPaused = paused,
                )
                    .also { renderState = it }
            }
            MatchUiAction.Retreat -> gateway.dispatch(
                MatchCommand.Surrender(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                ),
            )
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
            is MatchUiAction.SelectAmmo -> {
                val selected = gateway.dispatch(
                    MatchCommand.SelectAmmo(
                        commandId = CommandId("ui-${++commandSequence}"),
                        matchId = matchId,
                        playerId = playerId,
                        ammunitionType = action.ammunitionType,
                    ),
                )
                selectedAmmunition = action.ammunitionType
                selected
            }
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
            is MatchUiAction.CastSpell -> return dispatchSpell(action.spellType)
        }
        var presentationEvents = gateway.consumeEvents()
        if (action == MatchUiAction.AdvanceSimulation &&
            snapshot.status == com.urkaaaz.contracts.MatchStatus.RUNNING
        ) {
            gateway.aiCommands(snapshot, elapsedMilliseconds = 33L).forEach { command ->
                snapshot = gateway.dispatch(command)
                presentationEvents += gateway.consumeEvents()
            }
        }
        return mapRenderState(snapshot, presentationEvents).also { renderState = it }
    }

    private fun mapRenderState(
        snapshot: MatchSnapshot,
        events: List<MatchEvent> = emptyList(),
    ): RenderState {
        forwardCampaignCompletionEvents(events)
        return RenderStateMapper.map(snapshot, events).copy(
            matchGeneration = matchGeneration,
            selectedAmmunitionType = selectedAmmunition,
            spellFeedbackLabel = spellFeedbackLabel,
        )
    }

    private fun dispatchSpell(spellType: String): RenderState {
        spellFeedbackLabel = null
        val result = gateway.dispatchResult(
            MatchCommand.CastSpell(
                commandId = CommandId("ui-${++commandSequence}"),
                matchId = matchId,
                playerId = playerId,
                spellType = spellType,
            ),
        )
        val events = when (result) {
            is CommandResult.Accepted -> {
                spellFeedbackLabel = "SPELL CAST"
                result.events
            }
            is CommandResult.Rejected -> {
                spellFeedbackLabel = spellRejectionLabel(result.error)
                emptyList()
            }
        }
        return mapRenderState(gateway.snapshot(), events).also { renderState = it }
    }

    private fun spellRejectionLabel(error: MatchError): String = when (error) {
        MatchError.INSUFFICIENT_RESOURCE -> "NOT ENOUGH MANA"
        MatchError.SPELL_ALREADY_ACTIVE -> "SPELL ALREADY ACTIVE"
        MatchError.SPELL_NOT_IN_LOADOUT -> "SPELL NOT EQUIPPED"
        MatchError.SPELL_NOT_AVAILABLE -> "SPELL UNAVAILABLE"
        MatchError.MATCH_NOT_RUNNING,
        MatchError.ALREADY_FINISHED,
        -> "MATCH NOT RUNNING"
        else -> "CAST REJECTED"
    }

    internal fun forwardCampaignCompletionEvents(events: List<MatchEvent>) {
        if (demoMode) return
        events.filterIsInstance<MatchEvent.MatchFinished>().forEach { event ->
            val delivery = Triple(matchGeneration, event.matchId, event.eventId)
            if (forwardedCampaignCompletions.add(delivery)) {
                onMatchFinished(campaignLevel, event)
            }
        }
    }

    private fun selectedAmmunitionIsAvailable(): Boolean =
        renderState.playerAmmunition[selectedAmmunition]?.let { it > 0 } ?: true

    private companion object {
        const val DEFAULT_FORTRESS_MAX_HEALTH = 500
        const val DEMO_BLUE_FORTRESS_MAX_HEALTH = DEFAULT_FORTRESS_MAX_HEALTH * 10
    }
}

private fun loadSelectedDefinition(
    campaignLevel: Int,
    demoMode: Boolean,
    repository: MapDefinitionRepository,
): DemoLevelDefinition =
    if (demoMode) {
        DemoLevelDefinitions.fromRepository(repository)
    } else {
        val campaign = CampaignLevelDefinition.forLevel(campaignLevel, repository)
        DemoLevelDefinition(
            mapDefinition = campaign.mapDefinition,
            biome = TerrainBiome.fromMapId(campaign.mapDefinition.biome)
                ?: error("Campaign map has unsupported biome '${campaign.mapDefinition.biome}'"),
            initialWind = campaign.initialWind,
        )
    }

private fun loadSelectedScenario(
    campaignLevel: Int,
    demoMode: Boolean,
    repository: MapDefinitionRepository,
    playerLoadout: PlayerLoadoutConfig?,
    playerId: PlayerId,
): MatchScenario? {
    if (demoMode && playerLoadout == null) return null
    val definition = CampaignLevelDefinition.forLevel(
        if (demoMode) CampaignLevelDefinition.MAX_LEVELS else campaignLevel,
        repository,
    )
    val scenario = definition.toMatchScenario(
        playerId = playerId,
        opponentId = PlayerId("local-ai"),
        playerLoadout = playerLoadout,
    )
    if (!demoMode) return scenario

    val demo = DemoLevelDefinitions.fromRepository(repository)
    return scenario.copy(
        scenarioId = "demo",
        initialResources = scenario.initialResources.copy(
            ammunition = LoadoutCatalog.ammunition.associate {
                it.id to definition.startingAmmunition
            },
        ),
        availableUnits = LoadoutCatalog.units.map { it.id }.sorted(),
        availableAmmunition = LoadoutCatalog.ammunition.map { it.id }.sorted(),
        availableSpells = LoadoutCatalog.spells.map { it.id }.sorted(),
        mapDefinition = demo.mapDefinition,
        wind = demo.initialWind,
    )
}
