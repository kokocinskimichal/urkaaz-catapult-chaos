package com.urkaaaz.ai

import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class AiDifficulty(
    val angleStep: Int,
    val powerStep: Int,
    val angleError: Float,
    val powerError: Float,
) {
    EASY(angleStep = 8, powerStep = 10, angleError = 7f, powerError = 12f),
    MEDIUM(angleStep = 4, powerStep = 5, angleError = 3f, powerError = 6f),
    HARD(angleStep = 2, powerStep = 2, angleError = 1.25f, powerError = 2.5f),
}

enum class AiTemperament {
    AGGRESSIVE,
    CAUTIOUS,
    CHAOTIC,
    DESPERATE,
}

data class AiConfiguration(
    val playerId: PlayerId,
    val team: Team,
    val difficulty: AiDifficulty = AiDifficulty.MEDIUM,
    val temperament: AiTemperament = AiTemperament.CAUTIOUS,
    val aimErrorScale: Float = 1f,
    val allowedUnits: Set<UnitDefinition> = UnitFactory.all(),
    val randomSeed: Int = 0,
) {
    init {
        require(aimErrorScale > 0f) { "aimErrorScale must be positive" }
    }
}

/**
 * Snapshot-only AI decision maker.
 *
 * The agent has no simulation reference and only returns commands for the
 * application layer to validate and execute.
 */
class AiAgent(
    private var configuration: AiConfiguration,
) {
    private val random = Random(configuration.randomSeed)
    private val matchTemperament = if (configuration.temperament == AiTemperament.CAUTIOUS) {
        AiTemperament.entries[random.nextInt(AiTemperament.entries.size)]
    } else {
        configuration.temperament
    }

    fun setAllowedUnits(allowedUnits: Set<UnitDefinition>) {
        require(allowedUnits.isNotEmpty()) { "AI must have at least one allowed unit" }
        configuration = configuration.copy(allowedUnits = allowedUnits)
    }

    fun setAllowedUnitIds(allowedUnitIds: Set<String>) {
        setAllowedUnits(allowedUnitIds.map(UnitFactory::byId).toSet())
    }

    fun allowedUnitIds(): Set<String> = configuration.allowedUnits.map { it.id }.toSet()

    fun waveThreshold(): Int = when (matchTemperament) {
        AiTemperament.AGGRESSIVE,
        AiTemperament.DESPERATE -> 1
        AiTemperament.CAUTIOUS -> 3
        AiTemperament.CHAOTIC -> 2
    }

    fun decide(snapshot: MatchSnapshot): List<MatchCommand> {
        val matchId = snapshot.matchId ?: return emptyList()
        if (snapshot.status != MatchStatus.RUNNING || snapshot.phase == MatchPhase.FINISHED) {
            return emptyList()
        }
        if ((snapshot.reloadRemainingSeconds[configuration.team] ?: 0f) > 0f ||
            configuration.team in snapshot.activeProjectileTeams
        ) {
            return emptyList()
        }

        val catapult = snapshot.catapults.firstOrNull { it.team == configuration.team }
            ?: return emptyList()
        val ammunition = chooseAmmunition(snapshot)
        val target = snapshot.catapults.firstOrNull { it.team != configuration.team }
            ?: return emptyList()
        val (angle, power) = aimAt(catapult.x, catapult.y, target.x, target.y, snapshot)
        val commandPrefix = "ai-${matchId.value}-${snapshot.terrain.revision}"

        return listOf(
            MatchCommand.SelectAmmo(
                commandId = CommandId("$commandPrefix-select"),
                matchId = matchId,
                playerId = configuration.playerId,
                ammunitionType = ammunition,
            ),
            MatchCommand.Aim(
                commandId = CommandId("$commandPrefix-aim"),
                matchId = matchId,
                playerId = configuration.playerId,
                catapultId = catapult.entityId,
                directionDegrees = angle,
                power = power,
            ),
            MatchCommand.Fire(
                commandId = CommandId("$commandPrefix-fire"),
                matchId = matchId,
                playerId = configuration.playerId,
                catapultId = catapult.entityId,
            ),
        )
    }

    fun chooseDeployment(snapshot: MatchSnapshot): MatchCommand.DeployUnit? {
        val matchId = snapshot.matchId ?: return null
        if (snapshot.status != MatchStatus.RUNNING ||
            (snapshot.resourcesByTeam[configuration.team]?.supply
                ?: snapshot.resources.supply) < minimumUnitCost()
        ) {
            return null
        }
        val unit = chooseUnit(snapshot) ?: return null
        return MatchCommand.DeployUnit(
            commandId = CommandId("ai-${matchId.value}-deploy-${snapshot.units.size}"),
            matchId = matchId,
            playerId = configuration.playerId,
            unitType = unit.id,
        )
    }

    private fun chooseAmmunition(snapshot: MatchSnapshot): String {
        val available = (snapshot.resourcesByTeam[configuration.team]?.ammunition
            ?: snapshot.resources.ammunition)
            .filter { (_, count) -> count > 0 }
            .keys
            .mapNotNull { runCatching { AmmunitionType.valueOf(it) }.getOrNull() }
            .ifEmpty { listOf(AmmunitionType.ROCK) }

        return when (effectiveTemperament(snapshot)) {
            AiTemperament.AGGRESSIVE,
            AiTemperament.DESPERATE -> available.maxBy {
                it.damage + it.damageRadius.toInt()
            }.name
            AiTemperament.CAUTIOUS -> available.minBy { it.damageRadius }.name
            AiTemperament.CHAOTIC -> available[random.nextInt(available.size)].name
        }
    }

    private fun chooseUnit(snapshot: MatchSnapshot): UnitDefinition? {
        val availableSupply = snapshot.resourcesByTeam[configuration.team]?.supply
            ?: snapshot.resources.supply
        val affordable = configuration.allowedUnits.filter {
            it.supplyCost <= availableSupply
        }
        if (affordable.isEmpty()) return null
        val enemyHasSapper = snapshot.units.any {
            it.team != configuration.team && it.unitType == UnitFactory.sapper().id
        }
        val defender = affordable.firstOrNull { it.id == UnitFactory.defender().id }
        if (enemyHasSapper && defender != null) {
            return defender
        }
        return when (effectiveTemperament(snapshot)) {
            AiTemperament.AGGRESSIVE,
            AiTemperament.DESPERATE -> affordable.maxBy {
                it.attackDamage + it.speed.roundToInt()
            }
            AiTemperament.CAUTIOUS -> affordable.minBy { it.supplyCost }
            AiTemperament.CHAOTIC -> affordable[random.nextInt(affordable.size)]
        }
    }

    private fun aimAt(
        ownX: Float,
        ownY: Float,
        targetX: Float,
        targetY: Float,
        snapshot: MatchSnapshot,
    ): Pair<Float, Float> {
        val horizontalDistance = abs(targetX - ownX).coerceAtLeast(1f)
        val angleFromHorizontal = Math.toDegrees(
            atan2(abs(targetY - ownY).toDouble(), horizontalDistance.toDouble()),
        ).toFloat()
        // The simulation applies the team direction separately. The AI therefore
        // supplies the local launch angle for both teams instead of mirroring
        // the red team's angle a second time.
        val baseAngle = (angleFromHorizontal + 42f).coerceIn(28f, 68f)
        val basePower = (horizontalDistance / 12f).coerceIn(65f, 96f)
        val desperateBonus = if (effectiveTemperament(snapshot) == AiTemperament.DESPERATE) 10f else 0f
        val cautiousPenalty = if (effectiveTemperament(snapshot) == AiTemperament.CAUTIOUS) 7f else 0f
        return (
            baseAngle + randomOffset(configuration.difficulty.angleError * configuration.aimErrorScale)
        ).coerceIn(0f, 180f) to (
            basePower + desperateBonus - cautiousPenalty +
                randomOffset(configuration.difficulty.powerError * configuration.aimErrorScale)
        ).coerceIn(0f, 100f)
    }

    private fun effectiveTemperament(snapshot: MatchSnapshot): AiTemperament {
        val fortress = snapshot.fortresses.firstOrNull { it.team == configuration.team }
        return if (fortress != null && fortress.health <= fortress.maxHealth * 0.35f) {
            AiTemperament.DESPERATE
        } else {
            matchTemperament
        }
    }

    private fun minimumUnitCost(): Int =
        configuration.allowedUnits.minOfOrNull { it.supplyCost } ?: Int.MAX_VALUE

    private fun randomOffset(maximum: Float): Float =
        if (maximum == 0f) 0f else random.nextFloat() * maximum * 2f - maximum
}
