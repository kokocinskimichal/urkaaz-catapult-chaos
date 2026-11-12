package com.urkaaaz.ui

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.DemoLevelDefinition
import com.urkaaaz.campaign.DemoLevelDefinitions
import com.urkaaaz.campaign.MapDefinitionRepository
import com.urkaaaz.application.LocalMatchGateway
import com.urkaaaz.application.LocalMatchGatewayConfiguration
import com.urkaaaz.application.MatchReplay
import com.urkaaaz.application.campaign.BundledCampaignBalance
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.application.reward.CompositeMatchEventObserver
import com.urkaaaz.application.reward.MatchEventObserver
import com.urkaaaz.application.reward.RewardTracker
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.shop.MatchStockSettlementFailure
import com.urkaaaz.application.shop.MatchStockSettlementRejection
import com.urkaaaz.application.shop.MatchStockSettlementResult
import com.urkaaaz.application.shop.MatchStockSettlementUseCase
import com.urkaaaz.application.shop.MatchStockUsageTracker
import com.urkaaaz.application.tutorial.TutorialPlayerActionKind
import com.urkaaaz.application.tutorial.TutorialScenarioFacts
import com.urkaaaz.android.R
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.CommandResult
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.TerrainBiome
import java.util.Locale

/**
 * Presentation coordinator for one Android match screen.
 *
 * It translates UI intents into public commands and exposes only mapped
 * snapshot data to views.
 */
class MatchViewModel(
    private val campaignLevel: Int = 1,
    private val demoMode: Boolean = false,
    private val testMode: Boolean = false,
    playerLoadout: PlayerLoadoutConfig? = null,
    playerInventory: PlayerInventoryConfig? = null,
    private val stockSettlementUseCase: MatchStockSettlementUseCase? = null,
    private val loadoutCatalog: LoadoutCatalog = LoadoutCatalog.bundled(),
    mapRepository: MapDefinitionRepository = MapDefinitionRepository.bundled,
    private val matchId: MatchId = MatchId("local-match"),
    private val playerId: PlayerId = playerLoadout?.playerId ?: PlayerId("local-player"),
    private val onMatchFinished: (Int, MatchEvent.MatchFinished) -> Unit = { _, _ -> },
    private val onMatchFinishedWithRewards: (
        Int,
        MatchEvent.MatchFinished,
        LevelRewardResult?,
    ) -> Unit = { level, event, _ -> onMatchFinished(level, event) },
    private val onStockSettlementRequested: () -> Unit = {},
    private val rewardTracker: RewardTracker? =
        if (demoMode || testMode) null else RewardTracker(campaignLevel),
    private val stockUsageTracker: MatchStockUsageTracker? =
        if (demoMode || testMode) null else playerInventory?.let(::MatchStockUsageTracker),
    private val gatewayFactory: (LocalMatchGatewayConfiguration) -> LocalMatchGateway = { configuration ->
        LocalMatchGateway(
            terrainBiome = configuration.terrainBiome,
            initialWind = configuration.initialWind,
            mapDefinition = configuration.mapDefinition,
            scenario = configuration.scenario,
            blueFortressMaxHealth = configuration.blueFortressMaxHealth,
            playerTeams = configuration.playerTeams,
            eventObserver = configuration.eventObserver,
        )
    },
    private val gateway: LocalMatchGateway = gatewayFactory(
        LocalMatchGatewayConfiguration(
            terrainBiome = loadSelectedDefinition(
                campaignLevel,
                demoMode,
                mapRepository,
            ).biome.name,
            initialWind = loadSelectedDefinition(
                campaignLevel,
                demoMode,
                mapRepository,
            ).initialWind,
            mapDefinition = loadSelectedDefinition(
                campaignLevel,
                demoMode,
                mapRepository,
            ).mapDefinition,
            scenario = loadSelectedScenario(
                campaignLevel,
                demoMode,
                mapRepository,
                playerLoadout,
                playerId,
                loadoutCatalog,
                playerInventory,
            ),
            blueFortressMaxHealth =
                loadoutCatalog.demoBlueFortressMaximumHealth.takeIf { demoMode },
            playerTeams = mapOf(
                playerId to Team.BLUE,
                PlayerId("local-ai") to Team.RED,
            ),
            eventObserver = CompositeMatchEventObserver(
                listOfNotNull(rewardTracker, stockUsageTracker)
                    .ifEmpty { listOf(MatchEventObserver { }) },
            ),
        ),
    ),
) {
    private var currentPlayerInventory = playerInventory
    private var campaignScenario = loadSelectedScenario(
        campaignLevel,
        demoMode,
        mapRepository,
        playerLoadout,
        playerId,
        loadoutCatalog,
        playerInventory,
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
    } ?: loadoutCatalog.units
        .take(PlayerLoadoutConfig.MAX_UNITS)
        .map { it.id }

    val unitAnimationPreloadTypesByTeam: Map<Team, Set<String>>
        get() = mapOf(
            Team.BLUE to selectableUnitTypes.toSet(),
            Team.RED to aiAllowedUnitIds(),
        ).filterValues { it.isNotEmpty() }

    val selectableSpellTypes: List<String> = campaignScenario?.let { scenario ->
        scenario.playerLoadout?.spellTypes.orEmpty()
            .filter { it in scenario.availableSpells }
            .take(PlayerLoadoutConfig.MAX_SPELLS)
    }.orEmpty()

    private var commandSequence = 0
    private var matchGeneration = 0L
    private val pauseReasons = mutableSetOf<MatchPauseReason>()
    private val aiPauseReasons = mutableSetOf<MatchPauseReason>()
    private var tutorialLockedMechanicIds = emptySet<String>()
    private var selectedAmmunition = "ROCK"
    private val forwardedCampaignCompletions = mutableSetOf<Triple<Long, MatchId, EventId>>()
    private val pendingTutorialEvents = mutableListOf<MatchEvent>()
    private val pendingTutorialPlayerActions = mutableListOf<TutorialPlayerActionKind>()
    private var spellFeedbackLabel: Int? = null
    var renderState: RenderState = withProjectileBalance(RenderStateMapper.map(MatchSnapshot()))
        private set

    fun setAiAllowedUnitIds(unitIds: Set<String>) {
        gateway.setAiAllowedUnitIds(unitIds)
    }

    fun setAiAllowedAmmunitionIds(ammunitionIds: Set<String>) {
        gateway.setAiAllowedAmmunitionIds(ammunitionIds)
    }

    fun aiUnitAutomationEnabled(): Boolean = gateway.aiUnitAutomationEnabled()

    fun setTutorialSimulationPaused(paused: Boolean) {
        val changed = if (paused) {
            pauseReasons.add(MatchPauseReason.TUTORIAL)
        } else {
            pauseReasons.remove(MatchPauseReason.TUTORIAL)
        }
        if (changed) renderState = withPausePresentation(renderState)
    }

    fun setTutorialAiSuspended(suspended: Boolean) {
        val changed = if (suspended) {
            aiPauseReasons.add(MatchPauseReason.TUTORIAL)
        } else {
            aiPauseReasons.remove(MatchPauseReason.TUTORIAL)
        }
        if (!changed) return
        if (suspended) {
            gateway.suspendAi()
        } else if (aiPauseReasons.isEmpty()) {
            gateway.resumeAi()
        }
    }

    fun setTutorialLockedMechanics(mechanicIds: Set<String>) {
        tutorialLockedMechanicIds = mechanicIds.toSet()
        renderState = renderState.copy(tutorialLockedMechanicIds = tutorialLockedMechanicIds)
    }

    fun tutorialScenarioFacts(): TutorialScenarioFacts {
        val scenario = campaignScenario
        val availableUnits = scenario?.let { current ->
            current.playerLoadout?.unitTypes
                ?.filter { it in current.availableUnits }
                ?: current.availableUnits
        }.orEmpty().toSet()
        val availableAmmunition = scenario?.let { current ->
            current.playerLoadout?.ammunitionSlots
                ?.filterNotNull()
                ?.filter { it in current.availableAmmunition }
                ?: current.availableAmmunition
        }.orEmpty().toSet()
        val availableSpells = scenario?.let { current ->
            current.playerLoadout?.spellTypes
                ?.filter { it in current.availableSpells }
                ?: current.availableSpells
        }.orEmpty().toSet()
        return TutorialScenarioFacts(
            campaignLevel = campaignLevel.takeUnless { demoMode || testMode },
            availableMechanicIds = buildSet {
                availableUnits.forEach { add("unit.${it.lowercase(Locale.ROOT)}") }
                if (availableUnits.isNotEmpty()) add("unit.send_wave")
                availableAmmunition
                    .forEach { add("ammunition.${it.lowercase(Locale.ROOT)}") }
                availableSpells.forEach { add("spell.${it.lowercase(Locale.ROOT)}") }
                scenario?.bossEncounter?.let {
                    add("boss.${it.profileId.lowercase(Locale.ROOT)}")
                }
            },
            availableUnits = availableUnits,
            availableAmmunition = availableAmmunition,
            availableSpells = availableSpells,
            bossPresent = scenario?.bossEncounter != null,
        )
    }

    fun tutorialMatchGeneration(): Long = matchGeneration

    fun tutorialMatchId(): String? = gateway.snapshot().matchId?.value

    fun consumeTutorialEvents(): List<MatchEvent> =
        pendingTutorialEvents.toList().also { pendingTutorialEvents.clear() }

    fun consumeTutorialPlayerActions(): List<TutorialPlayerActionKind> =
        pendingTutorialPlayerActions.toList().also { pendingTutorialPlayerActions.clear() }

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
            if (pauseReasons.isNotEmpty()) return withPausePresentation(renderState)
        }
        if (action == MatchUiAction.FireRock && !selectedAmmunitionIsAvailable()) {
            return renderState
        }
        var acceptedTutorialPlayerAction: TutorialPlayerActionKind? = null
        var acceptedCommandEvents = emptyList<MatchEvent>()
        var snapshot = when (action) {
            MatchUiAction.StartMatch -> {
                pauseReasons.clear()
                aiPauseReasons.clear()
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
            MatchUiAction.FireRock -> {
                val result = gateway.dispatchResult(
                    MatchCommand.Fire(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    catapultId = com.urkaaaz.contracts.EntityId("blue-catapult"),
                    ),
                )
                if (result is CommandResult.Accepted) {
                    acceptedTutorialPlayerAction = TutorialPlayerActionKind.FIRE
                }
                gateway.snapshot()
            }
            MatchUiAction.AdvanceSimulation -> gateway.dispatch(
                MatchCommand.AdvanceSimulation(
                    commandId = CommandId("ui-${++commandSequence}"),
                    matchId = matchId,
                    playerId = playerId,
                    deltaMilliseconds = 33,
                ),
            )
            MatchUiAction.TogglePause -> {
                if (MatchPauseReason.USER in pauseReasons) {
                    pauseReasons.remove(MatchPauseReason.USER)
                    aiPauseReasons.remove(MatchPauseReason.USER)
                    gateway.resumeSimulation()
                    if (aiPauseReasons.isEmpty()) gateway.resumeAi()
                } else {
                    pauseReasons.add(MatchPauseReason.USER)
                    aiPauseReasons.add(MatchPauseReason.USER)
                    gateway.pause()
                }
                return withPausePresentation(renderState)
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
                val result = gateway.dispatchResult(
                    MatchCommand.SelectAmmo(
                        commandId = CommandId("ui-${++commandSequence}"),
                        matchId = matchId,
                        playerId = playerId,
                        ammunitionType = action.ammunitionType,
                    ),
                )
                if (result is CommandResult.Accepted) {
                    selectedAmmunition = action.ammunitionType
                    acceptedTutorialPlayerAction = TutorialPlayerActionKind.SELECT_AMMUNITION
                    acceptedCommandEvents = result.events
                }
                gateway.snapshot()
            }
            is MatchUiAction.DeployUnit -> {
                if (!gateway.canDeployUnit(com.urkaaaz.contracts.Team.BLUE, action.unitType)) {
                    return renderState
                }
                val result = gateway.dispatchResult(
                    MatchCommand.DeployUnit(
                        commandId = CommandId("ui-${++commandSequence}"),
                        matchId = matchId,
                        playerId = playerId,
                        unitType = action.unitType,
                    ),
                )
                if (result is CommandResult.Accepted) {
                    acceptedTutorialPlayerAction = TutorialPlayerActionKind.DEPLOY_UNIT
                    acceptedCommandEvents = result.events
                }
                gateway.snapshot()
            }
            MatchUiAction.SendWave -> {
                val result = gateway.dispatchResult(
                    MatchCommand.SendWave(
                        commandId = CommandId("ui-${++commandSequence}"),
                        matchId = matchId,
                        playerId = playerId,
                    ),
                )
                if (result is CommandResult.Accepted) {
                    acceptedTutorialPlayerAction = TutorialPlayerActionKind.SEND_WAVE
                    acceptedCommandEvents = result.events
                }
                gateway.snapshot()
            }
            is MatchUiAction.CastSpell -> return dispatchSpell(action.spellType)
        }
        var presentationEvents = acceptedCommandEvents + gateway.consumeEvents()
        if (action == MatchUiAction.AdvanceSimulation &&
            snapshot.status == com.urkaaaz.contracts.MatchStatus.RUNNING
        ) {
            gateway.aiCommands(snapshot, elapsedMilliseconds = 33L).forEach { command ->
                snapshot = gateway.dispatch(command)
                presentationEvents += gateway.consumeEvents()
            }
        }
        val mapped = mapRenderState(snapshot, presentationEvents)
        acceptedTutorialPlayerAction?.let(pendingTutorialPlayerActions::add)
        renderState = mapped
        return mapped
    }

    private fun mapRenderState(
        snapshot: MatchSnapshot,
        events: List<MatchEvent> = emptyList(),
    ): RenderState {
        forwardCampaignCompletionEvents(events)
        if (events.isNotEmpty()) {
            pendingTutorialEvents += events
        }
        val mapped = RenderStateMapper.map(snapshot, events)
        val rewardFeedback = rewardTracker?.consumeNotifications().orEmpty().map { notification ->
            CombatFeedbackRenderState(
                eventId = notification.id,
                kind = CombatFeedbackRenderState.Kind.GOLD_REWARD,
                x = notification.x,
                y = notification.y,
                goldAmount = notification.gold,
            )
        }
        return withPausePresentation(withProjectileBalance(mapped.copy(
            matchGeneration = matchGeneration,
            selectedAmmunitionType = selectedAmmunition,
            spellFeedbackLabel = spellFeedbackLabel,
            combatFeedback = mapped.combatFeedback + rewardFeedback,
        )))
    }

    private fun withPausePresentation(state: RenderState): RenderState {
        val paused = pauseReasons.isNotEmpty() &&
            state.statusLabel != com.urkaaaz.contracts.MatchStatus.FINISHED.name
        return state.copy(
            statusLabel = if (paused) "PAUSED" else {
                if (state.statusLabel == "PAUSED") "RUNNING" else state.statusLabel
            },
            isPaused = paused,
        )
    }

    private fun withProjectileBalance(state: RenderState): RenderState {
        val ammunition = loadoutCatalog.ammunition.single { it.id == selectedAmmunition }
        return state.copy(
            playerAngleDegrees = if (state.matchId == null) {
                loadoutCatalog.initialAimDirectionDegrees
            } else {
                state.playerAngleDegrees
            },
            playerPower = if (state.matchId == null) {
                loadoutCatalog.initialAimPower
            } else {
                state.playerPower
            },
            gravityAccelerationY = loadoutCatalog.gravityAccelerationY,
            windAccelerationScale = loadoutCatalog.windAccelerationScale,
            projectileInitialSpeedMultiplier = ammunition.initialSpeedMultiplier,
            projectileWindResponse = ammunition.windResponse,
            projectileGravityResponse = ammunition.gravityResponse,
            projectileDrag = ammunition.drag,
            tutorialLockedMechanicIds = tutorialLockedMechanicIds,
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
                spellFeedbackLabel = R.string.match_spell_feedback_cast
                pendingTutorialPlayerActions += TutorialPlayerActionKind.CAST_SPELL
                result.events
            }
            is CommandResult.Rejected -> {
                spellFeedbackLabel = spellRejectionLabel(result.error)
                emptyList()
            }
        }
        return mapRenderState(gateway.snapshot(), events).also { renderState = it }
    }

    suspend fun settleStockUsage(): MatchStockSettlementResult? {
        val tracker = stockUsageTracker ?: return null
        val request = tracker.settlementRequest()
            ?: return if (tracker.hasUsageOverflowed()) {
                MatchStockSettlementResult.Rejected(
                    MatchStockSettlementRejection.INVALID_USAGE,
                )
            } else {
                null
            }
        val useCase = stockSettlementUseCase
            ?: return MatchStockSettlementResult.Failure(
                MatchStockSettlementFailure.STORAGE_UNAVAILABLE,
            )
        val result = useCase.settle(request.id, request.usage)
        val profile = when (result) {
            is MatchStockSettlementResult.Settled -> result.profile
            is MatchStockSettlementResult.AlreadySettled -> result.profile
            is MatchStockSettlementResult.Rejected,
            is MatchStockSettlementResult.Failure,
            -> return result
        }
        val currentInventory = requireNotNull(currentPlayerInventory) {
            "stock tracker requires a player inventory snapshot"
        }
        val nextInventory = PlayerInventoryConfig(
            playerId = currentInventory.playerId,
            ammunitionStocks = profile.ammunitionStocks,
            specialSpellStocks = profile.specialSpellStocks,
        )
        currentPlayerInventory = nextInventory
        tracker.updateInventoryForNextGeneration(nextInventory)
        campaignScenario = campaignScenario?.copy(playerInventory = nextInventory)
        return result
    }

    suspend fun restartMatch(): MatchStockSettlementResult? {
        val settlement = settleStockUsage()
        if (settlement != null && !settlement.isSuccessful()) return settlement

        pauseReasons.clear()
        aiPauseReasons.clear()
        matchGeneration += 1L
        selectedAmmunition = "ROCK"
        spellFeedbackLabel = null
        val nextScenario = campaignScenario?.let { scenario ->
            currentPlayerInventory?.let { scenario.copy(playerInventory = it) } ?: scenario
        }
        campaignScenario = nextScenario
        val snapshot = gateway.restart(matchId, playerId, nextScenario)
        mapRenderState(snapshot, gateway.consumeEvents()).also { renderState = it }
        return settlement
    }

    suspend fun stopMatch(): MatchStockSettlementResult? {
        val settlement = settleStockUsage()
        if (settlement != null && !settlement.isSuccessful()) return settlement
        gateway.stop()
        return settlement
    }

    private fun spellRejectionLabel(error: MatchError): Int = when (error) {
        MatchError.INSUFFICIENT_RESOURCE -> R.string.match_spell_feedback_not_enough_mana
        MatchError.SPELL_ALREADY_ACTIVE -> R.string.match_spell_feedback_already_active
        MatchError.SPELL_NOT_IN_LOADOUT -> R.string.match_spell_feedback_not_equipped
        MatchError.SPELL_NOT_AVAILABLE -> R.string.match_spell_feedback_unavailable
        MatchError.MATCH_NOT_RUNNING,
        MatchError.ALREADY_FINISHED,
        -> R.string.match_spell_feedback_match_not_running
        else -> R.string.match_spell_feedback_rejected
    }

    internal fun forwardCampaignCompletionEvents(events: List<MatchEvent>) {
        if (demoMode || testMode) return
        events.filterIsInstance<MatchEvent.MatchFinished>().forEach { event ->
            val delivery = Triple(matchGeneration, event.matchId, event.eventId)
            if (forwardedCampaignCompletions.add(delivery)) {
                onStockSettlementRequested()
                onMatchFinishedWithRewards(campaignLevel, event, gateway.rewardResult())
            }
        }
    }

    private fun selectedAmmunitionIsAvailable(): Boolean =
        renderState.playerAmmunition[selectedAmmunition]?.let { it > 0 } ?: true

}

private enum class MatchPauseReason {
    USER,
    TUTORIAL,
}

private fun loadSelectedDefinition(
    campaignLevel: Int,
    demoMode: Boolean,
    repository: MapDefinitionRepository,
): DemoLevelDefinition =
    if (demoMode) {
        BundledCampaignBalance.demoLevelDefinition(repository)
    } else {
        val campaign = BundledCampaignBalance.levelDefinition(campaignLevel, repository)
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
    loadoutCatalog: LoadoutCatalog,
    playerInventory: PlayerInventoryConfig?,
): MatchScenario? {
    if (demoMode && playerLoadout == null) return null
    val definition = BundledCampaignBalance.levelDefinition(
        if (demoMode) CampaignLevelDefinition.MAX_LEVELS else campaignLevel,
        repository,
    )
    val scenario = definition.toMatchScenario(
        playerId = playerId,
        opponentId = PlayerId("local-ai"),
        playerLoadout = playerLoadout,
        playerInventory = playerInventory,
    )
    if (!demoMode) return scenario

    val demo = BundledCampaignBalance.demoLevelDefinition(repository)
    return scenario.copy(
        scenarioId = "demo",
        availableUnits = loadoutCatalog.units.map { it.id }.sorted(),
        availableAmmunition = loadoutCatalog.ammunition.map { it.id }.sorted(),
        availableSpells = loadoutCatalog.spells.map { it.id }.sorted(),
        mapDefinition = demo.mapDefinition,
        wind = demo.initialWind,
    )
}

private fun MatchStockSettlementResult.isSuccessful(): Boolean =
    this is MatchStockSettlementResult.Settled ||
        this is MatchStockSettlementResult.AlreadySettled
