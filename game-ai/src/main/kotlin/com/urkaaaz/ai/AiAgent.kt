package com.urkaaaz.ai

import com.urkaaaz.contracts.AiProfileId
import com.urkaaaz.contracts.AiProfileSelection
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.AiProfileBalance
import com.urkaaaz.domain.SpellType
import kotlin.random.Random

@JvmInline
value class AiStrategyId(val value: String) {
    init {
        require(value.isNotBlank()) { "AI strategy ID must not be blank" }
    }
}

data class AiProfileDefinition(
    val profileId: AiProfileId,
    val catapultStrategy: AiStrategyId,
    val recruitmentStrategy: AiStrategyId,
    val waveStrategy: AiStrategyId,
)

data class AiProfileTuning(
    val catapultStrategy: AiStrategyId,
    val recruitmentStrategy: AiStrategyId,
    val waveStrategy: AiStrategyId,
    val angleErrorDegrees: Float,
    val powerError: Float,
    val reactionIntervalMilliseconds: Long,
    val unitRecruitmentIntervalMilliseconds: Long,
)

class AiProfileCatalog(definitions: List<AiProfileDefinition>) {
    private val definitionsById = definitions.associateBy(AiProfileDefinition::profileId)

    init {
        require(definitions.isNotEmpty()) { "AI profile catalog must not be empty" }
        require(definitionsById.size == definitions.size) {
            "AI profile IDs must be unique"
        }
    }

    fun tuning(
        selection: AiProfileSelection,
        balanceProfiles: List<AiProfileBalance>,
    ): AiProfileTuning {
        val definition = definitionsById[selection.profileId]
            ?: error("unknown AI profile '${selection.profileId.value}'")
        val balance = balanceProfiles.singleOrNull { it.profileId == selection.profileId }
            ?: error("AI balance profile is missing: ${selection.profileId.value}")
        require(
            selection.progressionPercent == 0 ||
                balance.progressionErrorReduction > 0f ||
                balance.progressionReactionReduction > 0f,
        ) {
            "AI profile does not support campaign progression"
        }
        val progression = selection.progressionPercent / 100f
        val errorScale = 1f - progression * balance.progressionErrorReduction
        val reactionReduction =
            balance.reactionIntervalMilliseconds *
                balance.progressionReactionReduction *
                progression

        return AiProfileTuning(
            catapultStrategy = definition.catapultStrategy,
            recruitmentStrategy = definition.recruitmentStrategy,
            waveStrategy = definition.waveStrategy,
            angleErrorDegrees = balance.angleErrorDegrees * errorScale,
            powerError = balance.powerError * errorScale,
            unitRecruitmentIntervalMilliseconds = balance.unitRecruitmentIntervalMilliseconds,
            reactionIntervalMilliseconds = (
                balance.reactionIntervalMilliseconds - reactionReduction
                ).toLong().coerceAtLeast(
                    (balance.reactionIntervalMilliseconds *
                        (1f - balance.progressionReactionReduction))
                        .toLong(),
                ),
        )
    }

    companion object {
        fun standard(): AiProfileCatalog = AiProfileCatalog(
            listOf(
                AiProfileDefinition(
                    profileId = AiProfileId.CADET,
                    catapultStrategy = AiStrategyId("simple"),
                    recruitmentStrategy = AiStrategyId("simple"),
                    waveStrategy = AiStrategyId("simple"),
                ),
                AiProfileDefinition(
                    profileId = AiProfileId.VETERAN,
                    catapultStrategy = AiStrategyId("situational"),
                    recruitmentStrategy = AiStrategyId("situational"),
                    waveStrategy = AiStrategyId("situational"),
                ),
                AiProfileDefinition(
                    profileId = AiProfileId.ELITE,
                    catapultStrategy = AiStrategyId("counterplay"),
                    recruitmentStrategy = AiStrategyId("counterplay"),
                    waveStrategy = AiStrategyId("counterplay"),
                ),
            ),
        )
    }
}

/** Empty allowed units are valid for scenarios where neither team can deploy. */
data class AiConfiguration(
    val playerId: PlayerId,
    val team: Team,
    val balance: GameBalanceConfig,
    val profile: AiProfileSelection = AiProfileSelection(),
    val allowedUnits: Set<UnitDefinition> = UnitFactory.all(balance.units),
    val randomSeed: Long = 0L,
    val allowedAmmunition: Set<AmmunitionType> = AmmunitionType.entries.toSet(),
    val allowedSpells: Set<SpellType> = emptySet(),
)

class AiAgent(
    private var configuration: AiConfiguration,
    private val strategies: AiStrategyRegistry = AiStrategyRegistry.standard(),
) {
    private val random = Random(configuration.randomSeed)
    private var commandSequence = 0L
    private val tuning = strategies.profiles.tuning(
        configuration.profile,
        configuration.balance.runtime.aiProfiles,
    )
    private val catapultStrategy = strategies.catapult(tuning.catapultStrategy)
    private val recruitmentStrategy = strategies.recruitment(tuning.recruitmentStrategy)
    private val waveStrategy = strategies.wave(tuning.waveStrategy)
    private val missingSpellPolicies = configuration.balance.spells.all
        .filter { it.isImplemented }
        .map { it.type }
        .toSet() - strategies.spellTypes()

    init {
        require(missingSpellPolicies.isEmpty()) {
            "AI spell policies are missing for implemented spells: " +
                missingSpellPolicies.joinToString { it.name }
        }
    }

    val reactionIntervalMilliseconds: Long
        get() = tuning.reactionIntervalMilliseconds

    val unitRecruitmentIntervalMilliseconds: Long
        get() = tuning.unitRecruitmentIntervalMilliseconds

    fun setAllowedUnits(allowedUnits: Set<UnitDefinition>) {
        require(allowedUnits.isNotEmpty()) { "AI must have at least one allowed unit" }
        configuration = configuration.copy(allowedUnits = allowedUnits)
    }

    fun setAllowedUnitIds(allowedUnitIds: Set<String>) {
        setAllowedUnits(allowedUnitIds.map { UnitFactory.byId(it, configuration.balance.units) }.toSet())
    }

    fun allowedUnitIds(): Set<String> = configuration.allowedUnits.map { it.id }.toSet()

    fun setAllowedAmmunitionIds(allowedAmmunitionIds: Set<String>) {
        val allowedAmmunition = allowedAmmunitionIds.map { id ->
            AmmunitionType.entries.firstOrNull { it.name == id }
                ?: error("unknown AI ammunition identifier: $id")
        }.toSet()
        configuration = configuration.copy(allowedAmmunition = allowedAmmunition)
    }

    fun allowedAmmunitionIds(): Set<String> =
        configuration.allowedAmmunition.map { it.name }.toSet()

    fun decide(
        snapshot: MatchSnapshot,
        includeUnitActions: Boolean = true,
    ): List<MatchCommand> {
        val matchId = snapshot.matchId ?: return emptyList()
        if (snapshot.status != MatchStatus.RUNNING || snapshot.phase == MatchPhase.FINISHED) {
            return emptyList()
        }
        val context = AiStrategyContext(
            snapshot = snapshot,
            playerId = configuration.playerId,
            team = configuration.team,
            allowedUnits = configuration.allowedUnits,
            allowedAmmunition = configuration.allowedAmmunition,
            balance = configuration.balance,
            tuning = tuning,
            random = random,
            allowedSpells = configuration.allowedSpells,
            commandIdFactory = { action ->
                CommandId("ai-${matchId.value}-$action-${++commandSequence}")
            },
        )
        return buildList {
            addAll(catapultStrategy.decide(context))
            if (includeUnitActions) {
                recruitmentStrategy.chooseUnit(context)?.let { unit ->
                    add(
                        MatchCommand.DeployUnit(
                            commandId = context.commandId("deploy"),
                            matchId = matchId,
                            playerId = configuration.playerId,
                            unitType = unit.id,
                        ),
                    )
                }
                if (waveStrategy.shouldSend(context)) {
                    add(
                        MatchCommand.SendWave(
                            commandId = context.commandId("send-wave"),
                            matchId = matchId,
                            playerId = configuration.playerId,
                        ),
                    )
                }
            }
            chooseSpell(context)?.let { candidate ->
                add(
                    MatchCommand.CastSpell(
                        commandId = context.commandId("cast-${candidate.spellType.name.lowercase()}"),
                        matchId = matchId,
                        playerId = configuration.playerId,
                        spellType = candidate.spellType.name,
                        target = candidate.target,
                    ),
                )
            }
        }
    }

    private fun chooseSpell(context: AiStrategyContext): AiSpellCandidate? {
        val resources = context.snapshot.resourcesByTeam[configuration.team] ?: return null
        return strategies.spellUseStrategies()
            .asSequence()
            .filter { it.spellType in configuration.allowedSpells }
            .mapNotNull { strategy -> strategy.decide(context) }
            .filter { candidate ->
                val definition = configuration.balance.spells.definition(candidate.spellType)
                definition.isImplemented &&
                    resources.mana >= definition.manaCost &&
                    (!definition.special ||
                        (resources.specialSpellStocks[candidate.spellType.name] ?: 0) > 0)
            }
            .sortedWith(
                compareByDescending<AiSpellCandidate>(AiSpellCandidate::utilityScore)
                    .thenBy { it.spellType.name },
            )
            .firstOrNull()
    }
}
