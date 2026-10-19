package com.urkaaaz.ai

import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitSnapshot
import com.urkaaaz.domain.AmmunitionCatalog
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.SapperUnit
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

data class AiStrategyContext(
    val snapshot: MatchSnapshot,
    val playerId: PlayerId,
    val team: Team,
    val allowedUnits: Set<UnitDefinition>,
    val allowedAmmunition: Set<AmmunitionType>,
    val tuning: AiProfileTuning,
    val random: Random,
    private val commandIdFactory: (String) -> CommandId,
) {
    fun commandId(action: String) = commandIdFactory(action)
}

interface CatapultDecisionStrategy {
    val id: AiStrategyId

    fun decide(context: AiStrategyContext): List<MatchCommand>
}

interface UnitRecruitmentStrategy {
    val id: AiStrategyId

    fun chooseUnit(context: AiStrategyContext): UnitDefinition?
}

interface WaveDispatchStrategy {
    val id: AiStrategyId

    fun shouldSend(context: AiStrategyContext): Boolean
}

class AiStrategyRegistry(
    catapultStrategies: List<CatapultDecisionStrategy>,
    recruitmentStrategies: List<UnitRecruitmentStrategy>,
    waveStrategies: List<WaveDispatchStrategy>,
    val profiles: AiProfileCatalog = AiProfileCatalog.standard(),
) {
    private val catapultStrategiesById = catapultStrategies.associateBy(CatapultDecisionStrategy::id)
    private val recruitmentStrategiesById =
        recruitmentStrategies.associateBy(UnitRecruitmentStrategy::id)
    private val waveStrategiesById = waveStrategies.associateBy(WaveDispatchStrategy::id)

    init {
        require(catapultStrategiesById.size == catapultStrategies.size) {
            "catapult strategy IDs must be unique"
        }
        require(recruitmentStrategiesById.size == recruitmentStrategies.size) {
            "recruitment strategy IDs must be unique"
        }
        require(waveStrategiesById.size == waveStrategies.size) {
            "wave strategy IDs must be unique"
        }
    }

    fun catapult(id: AiStrategyId): CatapultDecisionStrategy =
        catapultStrategiesById[id] ?: error("unknown catapult strategy '${id.value}'")

    fun recruitment(id: AiStrategyId): UnitRecruitmentStrategy =
        recruitmentStrategiesById[id] ?: error("unknown recruitment strategy '${id.value}'")

    fun wave(id: AiStrategyId): WaveDispatchStrategy =
        waveStrategiesById[id] ?: error("unknown wave strategy '${id.value}'")

    companion object {
        fun standard() = AiStrategyRegistry(
            catapultStrategies = listOf(
                SimpleCatapultStrategy,
                SituationalCatapultStrategy,
                CounterplayCatapultStrategy,
            ),
            recruitmentStrategies = listOf(
                SimpleRecruitmentStrategy,
                SituationalRecruitmentStrategy,
                CounterplayRecruitmentStrategy,
            ),
            waveStrategies = listOf(
                SimpleWaveDispatchStrategy,
                SituationalWaveDispatchStrategy,
                CounterplayWaveDispatchStrategy,
            ),
        )
    }
}

private object SimpleCatapultStrategy : CatapultDecisionStrategy {
    override val id = AiStrategyId("simple")

    override fun decide(context: AiStrategyContext): List<MatchCommand> =
        fireAt(context, preferredTarget = { _, enemyCatapult -> enemyCatapult.toTargetPoint() }) { ammunition ->
            AmmunitionCatalog.definition(ammunition).damage.fortressDamage.toFloat()
        }
}

private object SituationalCatapultStrategy : CatapultDecisionStrategy {
    override val id = AiStrategyId("situational")

    override fun decide(context: AiStrategyContext): List<MatchCommand> =
        fireAt(context, preferredTarget = ::bestUnitCluster) { ammunition ->
            val damage = AmmunitionCatalog.definition(ammunition).damage
            damage.fortressDamage * 1.25f + damage.unitDamage +
                damage.fortressRadius * 0.1f + damage.unitRadius * 0.1f
        }
}

private object CounterplayCatapultStrategy : CatapultDecisionStrategy {
    override val id = AiStrategyId("counterplay")

    override fun decide(context: AiStrategyContext): List<MatchCommand> =
        fireAt(context, preferredTarget = ::highestThreatUnit) { ammunition ->
            val damage = AmmunitionCatalog.definition(ammunition).damage
            damage.unitDamage * 1.5f + damage.unitRadius +
                damage.fortressDamage * 0.5f + damage.fortressRadius * 0.1f
        }
}

private fun fireAt(
    context: AiStrategyContext,
    preferredTarget: (AiStrategyContext, CatapultSnapshot) -> TargetPoint,
    ammunitionScore: (AmmunitionType) -> Float,
): List<MatchCommand> {
    val team = context.team
    val catapult = context.snapshot.catapults.firstOrNull { it.team == team } ?: return emptyList()
    val enemyCatapult = context.snapshot.catapults.firstOrNull { it.team != team } ?: return emptyList()
    val ammunition = availableAmmunition(context)
        .maxByOrNull(ammunitionScore)
        ?: return emptyList()
    val reloadRemaining = context.snapshot
        .reloadRemainingSecondsByAmmunition[team]
        ?.get(ammunition.name)
        ?: context.snapshot.reloadRemainingSeconds[team]
        ?: 0f
    if (reloadRemaining > 0f || team in context.snapshot.activeProjectileTeams) return emptyList()

    val target = preferredTarget(context, enemyCatapult)
    val horizontalDistance = abs(target.x - catapult.x).coerceAtLeast(1f)
    val angleFromHorizontal = Math.toDegrees(
        atan2(abs(target.y - catapult.y).toDouble(), horizontalDistance.toDouble()),
    ).toFloat()
    val angle = (angleFromHorizontal + 42f + randomOffset(
        context.random,
        context.tuning.angleErrorDegrees,
    )).coerceIn(0f, 180f)
    val power = (horizontalDistance / 12f + randomOffset(
        context.random,
        context.tuning.powerError,
    )).coerceIn(0f, 100f)
    val matchId = requireNotNull(context.snapshot.matchId)

    return listOf(
        MatchCommand.SelectAmmo(
            commandId = context.commandId("select-ammo"),
            matchId = matchId,
            playerId = context.playerId,
            ammunitionType = ammunition.name,
        ),
        MatchCommand.Aim(
            commandId = context.commandId("aim"),
            matchId = matchId,
            playerId = context.playerId,
            catapultId = catapult.entityId,
            directionDegrees = angle,
            power = power,
        ),
        MatchCommand.Fire(
            commandId = context.commandId("fire"),
            matchId = matchId,
            playerId = context.playerId,
            catapultId = catapult.entityId,
        ),
    )
}

private fun availableAmmunition(context: AiStrategyContext): List<AmmunitionType> {
    val resources = context.snapshot.resourcesByTeam[context.team] ?: context.snapshot.resources
    return context.allowedAmmunition.filter { ammunition ->
        ammunition.unlimited || (resources.ammunition[ammunition.name] ?: 0) > 0
    }
}

private fun bestUnitCluster(
    context: AiStrategyContext,
    enemyCatapult: CatapultSnapshot,
): TargetPoint {
    val enemies = activeEnemies(context)
    if (enemies.isEmpty()) return enemyCatapult.toTargetPoint()
    val centerX = enemies.map(UnitSnapshot::x).average().toFloat()
    val centerY = enemies.map(UnitSnapshot::y).average().toFloat()
    return TargetPoint(centerX, centerY)
}

private fun highestThreatUnit(
    context: AiStrategyContext,
    enemyCatapult: CatapultSnapshot,
): TargetPoint {
    val target = activeEnemies(context).maxByOrNull { unit ->
        when {
            unit.unitType == UnitFactory.sapper().id -> 1_000f
            unit.unitType == "DEMOLISHER" -> 900f
            else -> definitionFor(unit)?.let {
                it.attackDamage * 2f + it.speed
            } ?: 0f
        }
    } ?: return enemyCatapult.toTargetPoint()
    val movementDirection = if (target.team == Team.BLUE) 1f else -1f
    val lead = if (target.moving) {
        (definitionFor(target)?.speed ?: 0f) *
            context.tuning.reactionIntervalMilliseconds / 1_000f *
            movementDirection
    } else {
        0f
    }
    return TargetPoint(
        x = (target.x + lead).coerceIn(0f, context.snapshot.terrain.worldWidth),
        y = target.y,
    )
}

private fun livingEnemies(context: AiStrategyContext): List<UnitSnapshot> =
    context.snapshot.units.filter { it.team != context.team && it.health > 0 }

private fun activeEnemies(context: AiStrategyContext): List<UnitSnapshot> =
    livingEnemies(context).filter { it.moving || it.actionState != "GARRISONED" }

private fun CatapultSnapshot.toTargetPoint() = TargetPoint(x, y)

private data class TargetPoint(val x: Float, val y: Float)

private object SimpleRecruitmentStrategy : UnitRecruitmentStrategy {
    override val id = AiStrategyId("simple")

    override fun chooseUnit(context: AiStrategyContext): UnitDefinition? =
        affordableUnits(context).minWithOrNull(compareBy({ it.supplyCost }, { it.id }))
}

private object SituationalRecruitmentStrategy : UnitRecruitmentStrategy {
    override val id = AiStrategyId("situational")

    override fun chooseUnit(context: AiStrategyContext): UnitDefinition? {
        val affordable = affordableUnits(context)
        if (hasEnemySapper(context)) {
            affordable.firstOrNull { it.id == UnitFactory.defender().id }?.let { return it }
        }
        return affordable.maxByOrNull { it.attackDamage * 2f + it.speed }
    }
}

private object CounterplayRecruitmentStrategy : UnitRecruitmentStrategy {
    override val id = AiStrategyId("counterplay")

    override fun chooseUnit(context: AiStrategyContext): UnitDefinition? {
        val affordable = affordableUnits(context)
        val enemies = livingEnemies(context)
        if (enemies.any { it.unitType == UnitFactory.sapper().id }) {
            affordable.firstOrNull { it.id == UnitFactory.defender().id }?.let { return it }
        }
        if (enemies.any { it.unitType == "DEMOLISHER" }) {
            affordable.firstOrNull { it.id == "SLINGMASTER" }?.let { return it }
        }
        val threat = enemies.mapNotNull(::definitionFor)
            .maxByOrNull { it.attackDamage * 2f + it.speed }
        if (threat != null) {
            affordable.maxByOrNull { unit ->
                counterScore(unit, threat)
            }?.let { return it }
        }
        return affordable.maxByOrNull { it.attackDamage * 2f + it.speed }
    }
}

private fun affordableUnits(context: AiStrategyContext): List<UnitDefinition> {
    val supply = (context.snapshot.resourcesByTeam[context.team] ?: context.snapshot.resources).supply
    val hasSapper = context.snapshot.units.any {
        it.team == context.team &&
            it.unitType == UnitFactory.sapper().id &&
            it.health > 0
    }
    return context.allowedUnits.filter {
        it.supplyCost <= supply &&
            (it !is SapperUnit || !hasSapper)
    }
}

private fun hasEnemySapper(context: AiStrategyContext): Boolean =
    livingEnemies(context).any { it.unitType == UnitFactory.sapper().id }

private fun definitionFor(unit: UnitSnapshot): UnitDefinition? =
    UnitFactory.all().firstOrNull { it.id == unit.unitType }

private fun counterScore(unit: UnitDefinition, threat: UnitDefinition): Float {
    val roleBonus = when {
        threat.id == UnitFactory.sapper().id && unit.id == UnitFactory.defender().id -> 1_000f
        threat.attacksFromRange && unit.attacksFromRange -> 100f
        !threat.attacksFromRange && unit.attacksFromRange -> 50f
        else -> 0f
    }
    return roleBonus + unit.attackDamage * 2f + unit.speed - unit.supplyCost * 0.25f
}

private object SimpleWaveDispatchStrategy : WaveDispatchStrategy {
    override val id = AiStrategyId("simple")

    override fun shouldSend(context: AiStrategyContext): Boolean =
        readyUnits(context).isNotEmpty()
}

private object SituationalWaveDispatchStrategy : WaveDispatchStrategy {
    override val id = AiStrategyId("situational")

    override fun shouldSend(context: AiStrategyContext): Boolean =
        readyUnits(context).size >= 3
}

private object CounterplayWaveDispatchStrategy : WaveDispatchStrategy {
    override val id = AiStrategyId("counterplay")

    override fun shouldSend(context: AiStrategyContext): Boolean {
        val ready = readyUnits(context)
        if (ready.isEmpty()) return false
        val fortress = context.snapshot.fortresses.firstOrNull { it.team == context.team }
        if (fortress != null && fortress.health <= fortress.maxHealth * 0.35f) return true
        if (hasEnemySapper(context) &&
            ready.any { it.unitType == UnitFactory.defender().id }
        ) return true
        val enemyUnits = livingEnemies(context)
        return if (enemyUnits.any(UnitSnapshot::moving)) {
            ready.size >= 2
        } else {
            ready.size >= 3
        }
    }
}

private fun readyUnits(context: AiStrategyContext): List<UnitSnapshot> =
    context.snapshot.units.filter {
        it.team == context.team && it.health > 0 && !it.moving
    }

private fun randomOffset(random: Random, maximum: Float): Float =
    if (maximum == 0f) 0f else random.nextFloat() * maximum * 2f - maximum
