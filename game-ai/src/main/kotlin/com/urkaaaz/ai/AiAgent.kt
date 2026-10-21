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
    val angleErrorDegrees: Float,
    val powerError: Float,
    val reactionIntervalMilliseconds: Long,
    val unitRecruitmentIntervalMilliseconds: Long = 0L,
    val progressionErrorReduction: Float = 0f,
    val progressionReactionReduction: Float = 0f,
) {
    init {
        require(angleErrorDegrees >= 0f) { "angle error must not be negative" }
        require(powerError >= 0f) { "power error must not be negative" }
        require(reactionIntervalMilliseconds > 0) {
            "reaction interval must be positive"
        }
        require(unitRecruitmentIntervalMilliseconds >= 0) {
            "unit recruitment interval must not be negative"
        }
        require(progressionErrorReduction in 0f..1f) {
            "profile progression error reduction must be between 0 and 1"
        }
        require(progressionReactionReduction in 0f..1f) {
            "profile progression reaction reduction must be between 0 and 1"
        }
    }
}

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

    fun tuning(selection: AiProfileSelection): AiProfileTuning {
        val definition = definitionsById[selection.profileId]
            ?: error("unknown AI profile '${selection.profileId.value}'")
        require(
            selection.progressionPercent == 0 ||
                definition.progressionErrorReduction > 0f ||
                definition.progressionReactionReduction > 0f,
        ) {
            "AI profile does not support campaign progression"
        }
        val progression = selection.progressionPercent / 100f
        val errorScale = 1f - progression * definition.progressionErrorReduction
        val reactionReduction =
            definition.reactionIntervalMilliseconds *
                definition.progressionReactionReduction *
                progression

        return AiProfileTuning(
            catapultStrategy = definition.catapultStrategy,
            recruitmentStrategy = definition.recruitmentStrategy,
            waveStrategy = definition.waveStrategy,
            angleErrorDegrees = definition.angleErrorDegrees * errorScale,
            powerError = definition.powerError * errorScale,
            unitRecruitmentIntervalMilliseconds =
                definition.unitRecruitmentIntervalMilliseconds,
            reactionIntervalMilliseconds = (
                definition.reactionIntervalMilliseconds - reactionReduction
                ).toLong().coerceAtLeast(
                    (definition.reactionIntervalMilliseconds *
                        (1f - definition.progressionReactionReduction))
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
                    angleErrorDegrees = 28f,
                    powerError = 45f,
                    reactionIntervalMilliseconds = 1_100L,
                    unitRecruitmentIntervalMilliseconds = 8_000L,
                ),
                AiProfileDefinition(
                    profileId = AiProfileId.VETERAN,
                    catapultStrategy = AiStrategyId("situational"),
                    recruitmentStrategy = AiStrategyId("situational"),
                    waveStrategy = AiStrategyId("situational"),
                    angleErrorDegrees = 3f,
                    powerError = 6f,
                    reactionIntervalMilliseconds = 700L,
                ),
                AiProfileDefinition(
                    profileId = AiProfileId.ELITE,
                    catapultStrategy = AiStrategyId("counterplay"),
                    recruitmentStrategy = AiStrategyId("counterplay"),
                    waveStrategy = AiStrategyId("counterplay"),
                    angleErrorDegrees = 1.25f,
                    powerError = 2.5f,
                    reactionIntervalMilliseconds = 400L,
                    progressionErrorReduction = 0.5f,
                    progressionReactionReduction = 0.5f,
                ),
            ),
        )
    }
}

data class AiConfiguration(
    val playerId: PlayerId,
    val team: Team,
    val profile: AiProfileSelection = AiProfileSelection(),
    val allowedUnits: Set<UnitDefinition> = UnitFactory.all(),
    val randomSeed: Long = 0L,
    val allowedAmmunition: Set<AmmunitionType> = AmmunitionType.entries.toSet(),
) {
    init {
        require(allowedUnits.isNotEmpty()) { "AI must have at least one allowed unit" }
        require(allowedAmmunition.isNotEmpty()) { "AI must have allowed ammunition" }
    }
}

class AiAgent(
    private var configuration: AiConfiguration,
    private val strategies: AiStrategyRegistry = AiStrategyRegistry.standard(),
) {
    private val random = Random(configuration.randomSeed)
    private var commandSequence = 0L
    private val tuning = strategies.profiles.tuning(configuration.profile)
    private val catapultStrategy = strategies.catapult(tuning.catapultStrategy)
    private val recruitmentStrategy = strategies.recruitment(tuning.recruitmentStrategy)
    private val waveStrategy = strategies.wave(tuning.waveStrategy)

    val reactionIntervalMilliseconds: Long
        get() = tuning.reactionIntervalMilliseconds

    val unitRecruitmentIntervalMilliseconds: Long
        get() = tuning.unitRecruitmentIntervalMilliseconds

    fun setAllowedUnits(allowedUnits: Set<UnitDefinition>) {
        require(allowedUnits.isNotEmpty()) { "AI must have at least one allowed unit" }
        configuration = configuration.copy(allowedUnits = allowedUnits)
    }

    fun setAllowedUnitIds(allowedUnitIds: Set<String>) {
        setAllowedUnits(allowedUnitIds.map(UnitFactory::byId).toSet())
    }

    fun allowedUnitIds(): Set<String> = configuration.allowedUnits.map { it.id }.toSet()

    fun setAllowedAmmunitionIds(allowedAmmunitionIds: Set<String>) {
        val allowedAmmunition = allowedAmmunitionIds.map { id ->
            AmmunitionType.entries.firstOrNull { it.name == id }
                ?: error("unknown AI ammunition identifier: $id")
        }.toSet()
        require(allowedAmmunition.isNotEmpty()) { "AI must have allowed ammunition" }
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
            tuning = tuning,
            random = random,
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
        }
    }
}
