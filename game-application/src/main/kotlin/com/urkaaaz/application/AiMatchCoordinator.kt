package com.urkaaaz.application

import com.urkaaaz.ai.AiAgent
import com.urkaaaz.ai.AiConfiguration
import com.urkaaaz.ai.AiStrategyRegistry
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.MatchScenario
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.UnitIds

class AiMatchCoordinator(
    private val scenario: MatchScenario? = null,
    private val registry: AiStrategyRegistry = AiStrategyRegistry.standard(),
) {
    private val aiPlayerId = scenario?.players
        ?.firstOrNull { it.team == Team.RED }
        ?.playerId
        ?: PlayerId("local-ai")
    private val profile = scenario?.aiProfile ?: AiProfileSelection()
    private val scenarioUnitIds = scenario?.availableUnits?.toSet()
    private val scenarioAmmunitionIds = scenario?.availableAmmunition
        ?.takeIf { it.isNotEmpty() }
        ?.toSet()
    private var allowedUnitIdsOverride: Set<String>? = null
    private var allowedAmmunitionIdsOverride: Set<String>? = null
    private var agent: AiAgent? = null
    private var unitAutomationEnabled = true
    private var paused = false
    private var initialDecisionPending = true
    private var elapsedSinceDecision = 0L
    private var recruitmentCooldownRemainingMilliseconds = 0L
    private var selectedAmmunition = AmmunitionType.ROCK

    fun start(randomSeed: Long, balance: GameBalanceConfig) {
        val allowedUnits = allowedUnitIdsOverride
            ?.map { UnitFactory.byId(it, balance.units) }
            ?.toSet()
            ?: scenarioUnitIds?.map { UnitFactory.byId(it, balance.units) }?.toSet()
            ?: UnitFactory.all(balance.units)
        val allowedAmmunition = allowedAmmunitionIdsOverride
            ?.map(::ammunitionById)
            ?.toSet()
            ?: scenarioAmmunitionIds?.map(::ammunitionById)?.toSet()
            ?: AmmunitionType.entries.toSet()
        val allowedSpells = scenario?.availableSpells?.map { identifier ->
            balance.spells.find(identifier)?.type
                ?: error("unknown AI spell identifier: $identifier")
        }?.toSet().orEmpty()
        agent = AiAgent(
            configuration = AiConfiguration(
                playerId = aiPlayerId,
                team = Team.RED,
                balance = balance,
                profile = profile,
                allowedUnits = allowedUnits,
                randomSeed = randomSeed,
                allowedAmmunition = allowedAmmunition,
                allowedSpells = allowedSpells,
            ),
            strategies = registry,
        )
        paused = false
        initialDecisionPending = true
        elapsedSinceDecision = 0L
        recruitmentCooldownRemainingMilliseconds = 0L
        selectedAmmunition = AmmunitionType.ROCK
    }

    fun decide(
        snapshot: MatchSnapshot,
        elapsedMilliseconds: Long,
    ): List<MatchCommand> {
        require(elapsedMilliseconds >= 0) { "elapsed time must not be negative" }
        val currentAgent = agent ?: return emptyList()
        if (paused || snapshot.status != MatchStatus.RUNNING) return emptyList()
        recruitmentCooldownRemainingMilliseconds =
            (recruitmentCooldownRemainingMilliseconds - elapsedMilliseconds).coerceAtLeast(0L)
        if (!initialDecisionPending) {
            elapsedSinceDecision += elapsedMilliseconds
            if (elapsedSinceDecision < currentAgent.reactionIntervalMilliseconds) {
                return emptyList()
            }
            elapsedSinceDecision %= currentAgent.reactionIntervalMilliseconds
        } else {
            initialDecisionPending = false
        }
        var commands = currentAgent.decide(
            snapshot = snapshot,
            includeUnitActions = unitAutomationEnabled,
        )
        val ammoSelection = commands.filterIsInstance<MatchCommand.SelectAmmo>().firstOrNull()
        if (ammoSelection != null) {
            val requestedAmmunition = ammunitionById(ammoSelection.ammunitionType)
            val changingAmmunition = requestedAmmunition != selectedAmmunition
            if (changingAmmunition) {
                selectedAmmunition = requestedAmmunition
                commands = commands.filterNot {
                    it is MatchCommand.Aim || it is MatchCommand.Fire
                }
            }
        }
        val reloadsByAmmunition = snapshot.reloadRemainingSecondsByAmmunition[Team.RED]
        val selectedReload = if (reloadsByAmmunition != null) {
            reloadsByAmmunition[selectedAmmunition.name] ?: 0f
        } else {
            snapshot.reloadRemainingSeconds[Team.RED] ?: 0f
        }
        if (selectedReload > 0f) {
            commands = commands.filterNot {
                it is MatchCommand.Aim || it is MatchCommand.Fire
            }
        }
        if (recruitmentCooldownRemainingMilliseconds > 0L) {
            return commands.filterNot { it is MatchCommand.DeployUnit }
        }
        if (commands.any { it is MatchCommand.DeployUnit }) {
            recruitmentCooldownRemainingMilliseconds =
                currentAgent.unitRecruitmentIntervalMilliseconds
        }
        return commands
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun stop() {
        agent = null
        paused = false
        initialDecisionPending = true
        elapsedSinceDecision = 0L
        recruitmentCooldownRemainingMilliseconds = 0L
    }

    fun setUnitAutomationEnabled(enabled: Boolean) {
        unitAutomationEnabled = enabled
    }

    fun unitAutomationEnabled(): Boolean = unitAutomationEnabled

    fun setAllowedUnitIds(ids: Set<String>) {
        require(ids.isNotEmpty()) { "AI must have at least one allowed unit" }
        require(ids.all { it in UnitIds.all }) { "AI unit IDs must be known" }
        allowedUnitIdsOverride = ids.toSet()
        agent?.setAllowedUnitIds(ids)
    }

    fun allowedUnitIds(): Set<String> =
        agent?.allowedUnitIds()
            ?: allowedUnitIdsOverride
            ?: scenarioUnitIds
            ?: UnitIds.all

    fun setAllowedAmmunitionIds(ids: Set<String>) {
        val ammunition = ids.map(::ammunitionById)
        allowedAmmunitionIdsOverride = ammunition.map(AmmunitionType::name).toSet()
        agent?.setAllowedAmmunitionIds(ids)
    }

    fun allowedAmmunitionIds(): Set<String> =
        agent?.allowedAmmunitionIds()
            ?: allowedAmmunitionIdsOverride
            ?: scenarioAmmunitionIds
            ?: AmmunitionType.entries.map(AmmunitionType::name).toSet()

    private fun ammunitionById(id: String): AmmunitionType =
        AmmunitionType.entries.firstOrNull { it.name == id }
            ?: error("unknown AI ammunition identifier: $id")
}
