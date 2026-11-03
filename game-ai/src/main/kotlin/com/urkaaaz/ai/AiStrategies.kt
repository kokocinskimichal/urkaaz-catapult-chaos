package com.urkaaaz.ai

import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.CommandId
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchCommand
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitSnapshot
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.AmmunitionHitLayer
import com.urkaaaz.domain.SapperUnit
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.UnitFactory
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.AiUnitScoringBalance
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.UnitIds
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.random.Random

data class AiStrategyContext(
    val snapshot: MatchSnapshot,
    val playerId: PlayerId,
    val team: Team,
    val allowedUnits: Set<UnitDefinition>,
    val allowedAmmunition: Set<AmmunitionType>,
    val balance: GameBalanceConfig,
    val tuning: AiProfileTuning,
    val random: Random,
    val allowedSpells: Set<SpellType>,
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
    spellUseStrategies: List<AiSpellUseStrategy> = standardAiSpellUseStrategies(),
) {
    private val catapultStrategiesById = catapultStrategies.associateBy(CatapultDecisionStrategy::id)
    private val recruitmentStrategiesById =
        recruitmentStrategies.associateBy(UnitRecruitmentStrategy::id)
    private val waveStrategiesById = waveStrategies.associateBy(WaveDispatchStrategy::id)
    private val spellUseStrategiesByType =
        spellUseStrategies.associateBy(AiSpellUseStrategy::spellType)

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
        require(spellUseStrategiesByType.size == spellUseStrategies.size) {
            "AI spell policies must have unique spell types"
        }
    }

    fun catapult(id: AiStrategyId): CatapultDecisionStrategy =
        catapultStrategiesById[id] ?: error("unknown catapult strategy '${id.value}'")

    fun recruitment(id: AiStrategyId): UnitRecruitmentStrategy =
        recruitmentStrategiesById[id] ?: error("unknown recruitment strategy '${id.value}'")

    fun wave(id: AiStrategyId): WaveDispatchStrategy =
        waveStrategiesById[id] ?: error("unknown wave strategy '${id.value}'")

    fun spellUseStrategies(): List<AiSpellUseStrategy> =
        spellUseStrategiesByType.values.toList()

    fun spellTypes(): Set<SpellType> = spellUseStrategiesByType.keys

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
            context.balance.ammunition.definition(ammunition).damage.fortressDamage.toFloat()
        }
}

private object SituationalCatapultStrategy : CatapultDecisionStrategy {
    override val id = AiStrategyId("situational")

    override fun decide(context: AiStrategyContext): List<MatchCommand> =
        fireAt(context, preferredTarget = ::bestUnitCluster) { ammunition ->
            val damage = context.balance.ammunition.definition(ammunition).damage
            val weights = context.balance.runtime.aiStrategy.ammunitionScoring
            damage.fortressDamage * weights.situationalFortressDamageWeight +
                damage.unitDamage * weights.situationalUnitDamageWeight +
                damage.fortressRadius * weights.situationalFortressRadiusWeight +
                damage.unitRadius * weights.situationalUnitRadiusWeight
        }
}

private object CounterplayCatapultStrategy : CatapultDecisionStrategy {
    override val id = AiStrategyId("counterplay")

    override fun decide(context: AiStrategyContext): List<MatchCommand> =
        fireAt(context, preferredTarget = ::highestThreatUnit) { ammunition ->
            val damage = context.balance.ammunition.definition(ammunition).damage
            val weights = context.balance.runtime.aiStrategy.ammunitionScoring
            damage.unitDamage * weights.counterplayUnitDamageWeight +
                damage.unitRadius * weights.counterplayUnitRadiusWeight +
                damage.fortressDamage * weights.counterplayFortressDamageWeight +
                damage.fortressRadius * weights.counterplayFortressRadiusWeight
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
    if (team in context.snapshot.activeProjectileTeams) return emptyList()

    val boss = context.snapshot.boss
    val centralObjective = boss?.takeIf {
        it.archetype == BossArchetype.CENTRAL_OBJECTIVE && it.health > 0
    }
    val selectedAmmunitionAndAim = if (centralObjective != null) {
        val bossProfile = context.balance.bossProfile(centralObjective.profileId)
        availableAmmunition(context)
            .filter { ammunition ->
                val definition = context.balance.ammunition.definition(ammunition)
                AmmunitionHitLayer.FORTRESS in definition.collision.hitLayers &&
                    definition.damage.fortressDamage > 0 &&
                    ammunition.name !in bossProfile.immuneAmmunitionTypes &&
                    definition.flight.apexFragmentation == null &&
                    !isAmmunitionReloading(context, ammunition)
            }
            .sortedByDescending {
                context.balance.ammunition.definition(it).damage.fortressDamage
            }
            .firstNotNullOfOrNull { ammunition ->
                CentralObjectiveAimSolver.solve(context, catapult, ammunition, centralObjective)
                    ?.let { ammunition to it }
            }
            ?: return emptyList()
    } else {
        val ammunition = availableAmmunition(context).maxByOrNull(ammunitionScore)
            ?: return emptyList()
        if (isAmmunitionReloading(context, ammunition)) return emptyList()
        ammunition to null
    }
    val selectedAmmunition = selectedAmmunitionAndAim.first
    val predictedAim = selectedAmmunitionAndAim.second
    val target = if (centralObjective != null) {
        TargetPoint(
            x = centralObjective.x,
            y = centralObjective.groundY - centralObjective.collisionHeight / 2f,
        )
    } else {
        preferredTarget(context, enemyCatapult)
    }
    val horizontalDistance = abs(target.x - catapult.x).coerceAtLeast(1f)
    val angleFromHorizontal = Math.toDegrees(
        atan2(abs(target.y - catapult.y).toDouble(), horizontalDistance.toDouble()),
    ).toFloat()
    val strategyBalance = context.balance.runtime.aiStrategy
    val angle = predictedAim?.let {
        (
            it.directionDegrees + randomOffset(
                context.random,
                context.tuning.angleErrorDegrees,
            )
        ).coerceIn(0f, 180f)
    }
        ?: (angleFromHorizontal + strategyBalance.aimAngleOffsetDegrees + randomOffset(
            context.random,
            context.tuning.angleErrorDegrees,
        )).coerceIn(0f, 180f)
    val power = predictedAim?.let {
        (
            it.power + randomOffset(
                context.random,
                context.tuning.powerError,
            )
        ).coerceIn(0f, 100f)
    } ?: (
        horizontalDistance / strategyBalance.horizontalDistancePerPowerPercent +
            randomOffset(
                context.random,
                context.tuning.powerError,
            )
        ).coerceIn(0f, 100f)
    val matchId = requireNotNull(context.snapshot.matchId)

    return listOf(
        MatchCommand.SelectAmmo(
            commandId = context.commandId("select-ammo"),
            matchId = matchId,
            playerId = context.playerId,
            ammunitionType = selectedAmmunition.name,
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

private fun isAmmunitionReloading(
    context: AiStrategyContext,
    ammunition: AmmunitionType,
): Boolean {
    val reloadsByAmmunition =
        context.snapshot.reloadRemainingSecondsByAmmunition[context.team]
    val remaining = if (reloadsByAmmunition != null) {
        reloadsByAmmunition[ammunition.name] ?: 0f
    } else {
        context.snapshot.reloadRemainingSeconds[context.team] ?: 0f
    }
    return remaining > 0f
}

private fun availableAmmunition(context: AiStrategyContext): List<AmmunitionType> {
    val resources = context.snapshot.resourcesByTeam[context.team] ?: context.snapshot.resources
    return context.allowedAmmunition.filter { ammunition ->
        context.balance.ammunition.definition(ammunition).unlimited ||
            (resources.ammunition[ammunition.name] ?: 0) > 0
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
    val scoring = context.balance.runtime.aiStrategy.unitScoring
    val target = activeEnemies(context).maxByOrNull { unit ->
        when {
            unit.unitType == UnitIds.SAPPER -> scoring.sapperPriorityScore
            unit.unitType == UnitIds.DEMOLISHER -> scoring.demolisherPriorityScore
            else -> definitionFor(unit, context)?.let { unitPriorityScore(it, scoring) } ?: 0f
        }
    } ?: return enemyCatapult.toTargetPoint()
    val movementDirection = if (target.team == Team.BLUE) 1f else -1f
    val lead = if (target.moving) {
        (definitionFor(target, context)?.speed ?: 0f) *
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
            affordable.firstOrNull { it.id == UnitIds.DEFENDER }?.let { return it }
        }
        val scoring = context.balance.runtime.aiStrategy.unitScoring
        return affordable.maxByOrNull { unitPriorityScore(it, scoring) }
    }
}

private object CounterplayRecruitmentStrategy : UnitRecruitmentStrategy {
    override val id = AiStrategyId("counterplay")

    override fun chooseUnit(context: AiStrategyContext): UnitDefinition? {
        val affordable = affordableUnits(context)
        val enemies = livingEnemies(context)
        if (enemies.any { it.unitType == UnitIds.SAPPER }) {
            affordable.firstOrNull { it.id == UnitIds.DEFENDER }?.let { return it }
        }
        if (enemies.any { it.unitType == UnitIds.DEMOLISHER }) {
            affordable.firstOrNull { it.id == UnitIds.SLINGMASTER }?.let { return it }
        }
        val scoring = context.balance.runtime.aiStrategy.unitScoring
        val threat = enemies.mapNotNull { definitionFor(it, context) }
            .maxByOrNull { unitPriorityScore(it, scoring) }
        if (threat != null) {
            affordable.maxByOrNull { unit ->
                counterScore(unit, threat, scoring)
            }?.let { return it }
        }
        return affordable.maxByOrNull { unitPriorityScore(it, scoring) }
    }
}

private fun affordableUnits(context: AiStrategyContext): List<UnitDefinition> {
    val supply = (context.snapshot.resourcesByTeam[context.team] ?: context.snapshot.resources).supply
    val hasSapper = context.snapshot.units.any {
        it.team == context.team &&
            it.unitType == UnitIds.SAPPER &&
            it.health > 0
    }
    return context.allowedUnits.filter {
        it.supplyCost <= supply &&
            (it !is SapperUnit || !hasSapper)
    }
}

private fun hasEnemySapper(context: AiStrategyContext): Boolean =
    livingEnemies(context).any { it.unitType == UnitIds.SAPPER }

private fun definitionFor(unit: UnitSnapshot, context: AiStrategyContext): UnitDefinition? =
    context.balance.units.find(unit.unitType)?.let(UnitFactory::create)

private fun counterScore(
    unit: UnitDefinition,
    threat: UnitDefinition,
    scoring: AiUnitScoringBalance,
): Float {
    val roleBonus = when {
        threat.id == UnitIds.SAPPER && unit.id == UnitIds.DEFENDER ->
            scoring.defenderVsSapperRoleBonus
        threat.attacksFromRange && unit.attacksFromRange ->
            scoring.rangedVsRangedRoleBonus
        !threat.attacksFromRange && unit.attacksFromRange ->
            scoring.rangedVsMeleeRoleBonus
        else -> 0f
    }
    return unitPriorityScore(unit, scoring) + roleBonus -
        unit.supplyCost * scoring.supplyCostPenaltyWeight
}

private fun unitPriorityScore(
    unit: UnitDefinition,
    scoring: AiUnitScoringBalance,
): Float =
    unit.attackDamage * scoring.attackDamageWeight + unit.speed * scoring.speedWeight

private object SimpleWaveDispatchStrategy : WaveDispatchStrategy {
    override val id = AiStrategyId("simple")

    override fun shouldSend(context: AiStrategyContext): Boolean =
        readyUnits(context).isNotEmpty()
}

private object SituationalWaveDispatchStrategy : WaveDispatchStrategy {
    override val id = AiStrategyId("situational")

    override fun shouldSend(context: AiStrategyContext): Boolean =
        readyUnits(context).size >=
            context.balance.runtime.aiStrategy.waveDispatch.situationalMinimumReadyUnits
}

private object CounterplayWaveDispatchStrategy : WaveDispatchStrategy {
    override val id = AiStrategyId("counterplay")

    override fun shouldSend(context: AiStrategyContext): Boolean {
        val ready = readyUnits(context)
        if (ready.isEmpty()) return false
        val fortress = context.snapshot.fortresses.firstOrNull { it.team == context.team }
        val waveBalance = context.balance.runtime.aiStrategy.waveDispatch
        if (
            fortress != null &&
            fortress.health <= fortress.maxHealth * waveBalance.lowFortressHealthFraction
        ) return true
        if (hasEnemySapper(context) &&
            ready.any { it.unitType == UnitIds.DEFENDER }
        ) return true
        val enemyUnits = livingEnemies(context)
        return if (enemyUnits.any(UnitSnapshot::moving)) {
            ready.size >= waveBalance.counterplayMinimumReadyUnitsAgainstMovingEnemies
        } else {
            ready.size >= waveBalance.counterplayMinimumReadyUnits
        }
    }
}

private fun readyUnits(context: AiStrategyContext): List<UnitSnapshot> =
    context.snapshot.units.filter {
        it.team == context.team && it.health > 0 && !it.moving
    }

private fun randomOffset(random: Random, maximum: Float): Float =
    if (maximum == 0f) 0f else random.nextFloat() * maximum * 2f - maximum
