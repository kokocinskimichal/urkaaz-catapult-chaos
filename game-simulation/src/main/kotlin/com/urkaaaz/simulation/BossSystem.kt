package com.urkaaaz.simulation

import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossEncounterConfig
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.BossSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitHitboxProfile
import com.urkaaaz.domain.BossAttackBalance
import com.urkaaaz.domain.BossAttackTarget
import com.urkaaaz.domain.BossBalanceProfile
import com.urkaaaz.domain.SapperUnit
import com.urkaaaz.domain.SlingmasterUnit
import com.urkaaaz.domain.Unit
import com.urkaaaz.domain.UnitActionState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

data class BossDamageResult(
    val amount: Int,
    val remainingHealth: Int,
    val phaseChanged: Boolean,
    val defeated: Boolean,
    val winner: MatchOutcome?,
    val x: Float,
    val y: Float,
)

data class BossTickResult(
    val unitDamage: Map<EntityId, Int> = emptyMap(),
    val fortressDamage: Int = 0,
    val phaseChanged: Boolean = false,
    val unitAttackStarted: Team? = null,
)

class BossSystem(
    val profile: BossBalanceProfile,
    encounter: BossEncounterConfig,
    private val worldWidth: Float,
    private val groundHeightAt: (Float) -> Float,
) {
    val entityId = EntityId("boss-${profile.id}")

    private var x = worldWidth * encounter.spawnXRatio
    private var groundY = groundHeightAt(x)
    private var health = profile.maxHealth
    private var phase = if (profile.archetype == BossArchetype.CENTRAL_OBJECTIVE) {
        BossPhase.IDLE
    } else {
        BossPhase.MARCHING
    }
    private var lastDamageSourceTeam: Team? = null
    private val damageByTeam = Team.entries.associateWith { 0 }.toMutableMap()
    private val remainingAttackCooldowns = profile.attacks.associate {
        it.target to it.initialCooldownSeconds
    }.toMutableMap()
    private var pendingUnitAttack: PendingUnitAttack? = null

    private data class PendingUnitAttack(
        val attack: BossAttackBalance,
        val targetTeam: Team,
        var remainingWindupSeconds: Float,
    )

    val isAlive: Boolean
        get() = health > 0

    fun snapshot(): BossSnapshot = BossSnapshot(
        entityId = entityId,
        profileId = profile.id,
        presentationKey = profile.presentationKey,
        archetype = profile.archetype,
        phase = phase,
        x = x,
        groundY = groundY,
        collisionWidth = profile.collision.width,
        collisionHeight = profile.collision.height,
        health = health,
        maxHealth = profile.maxHealth,
        blueDamage = damageByTeam.getValue(Team.BLUE),
        redDamage = damageByTeam.getValue(Team.RED),
    )

    fun approachX(unit: Unit, movingTeams: Set<Team>): Float? {
        if (!canUnitTargetBoss(unit, movingTeams)) return null
        val direction = if (unit.team == Team.BLUE) 1f else -1f
        if ((x - unit.position.x) * direction < 0f) return null
        val attackRange = unitAttackRange(unit)
        return (x - direction * (profile.collision.width / 2f + attackRange))
            .coerceIn(0f, worldWidth)
    }

    fun formationRank(
        unit: Unit,
        units: List<Unit>,
        movingTeams: Set<Team>,
    ): Int {
        if (!canUnitTargetBoss(unit, movingTeams)) return 0
        val direction = if (unit.team == Team.BLUE) 1f else -1f
        val peers = units.asSequence()
            .filter { peer ->
                peer.team == unit.team &&
                    canUnitTargetBoss(peer, movingTeams) &&
                    (peer.id == unit.id ||
                        peer.combatState.targetId == null ||
                        peer.combatState.targetId == entityId) &&
                    (x - peer.position.x) * direction >= 0f
            }
            .sortedWith(compareBy<Unit>({ abs(x - it.position.x) }, { it.id.value }))
            .toList()
        return peers.indexOfFirst { it.id == unit.id }.coerceAtLeast(0)
    }

    fun canUnitAttack(
        unit: Unit,
        movingTeams: Set<Team>,
        formationOffset: Float = 0f,
    ): Boolean {
        if (!canUnitTargetBoss(unit, movingTeams)) return false
        val attackRange = unitAttackRange(unit) + formationOffset.coerceAtLeast(0f)
        return abs(unit.position.x - x) <= profile.collision.width / 2f + attackRange
    }

    fun applyDamage(
        amount: Int,
        sourceTeam: Team,
        ammunitionType: String? = null,
    ): BossDamageResult? {
        if (!isAlive || amount <= 0 || ammunitionType in profile.immuneAmmunitionTypes) {
            return null
        }
        val previousPhase = phase
        val applied = amount.coerceAtMost(health)
        health -= applied
        if (health == 0) pendingUnitAttack = null
        damageByTeam[sourceTeam] = damageByTeam.getValue(sourceTeam) + applied
        lastDamageSourceTeam = sourceTeam
        phase = when {
            health == 0 -> BossPhase.DEFEATED
            profile.archetype == BossArchetype.MOBILE_SIEGE_BOSS && isEnraged() ->
                BossPhase.ENRAGED
            else -> phase
        }
        val winner = if (health == 0) {
            if (profile.archetype == BossArchetype.CENTRAL_OBJECTIVE) {
                if (damageByTeam.getValue(Team.BLUE) >= damageByTeam.getValue(Team.RED)) {
                    MatchOutcome.BLUE_WIN
                } else {
                    MatchOutcome.RED_WIN
                }
            } else {
                when (lastDamageSourceTeam) {
                    Team.BLUE -> MatchOutcome.BLUE_WIN
                    Team.RED -> MatchOutcome.RED_WIN
                    null -> MatchOutcome.DRAW
                }
            }
        } else {
            null
        }
        return BossDamageResult(
            amount = applied,
            remainingHealth = health,
            phaseChanged = phase != previousPhase,
            defeated = health == 0,
            winner = winner,
            x = x,
            y = groundY - profile.collision.height / 2f,
        )
    }

    fun applyDamageAt(
        positionX: Float,
        positionY: Float,
        radius: Float,
        amount: Int,
        sourceTeam: Team,
        ammunitionType: String,
    ): BossDamageResult? {
        if (
            !isAlive ||
            amount <= 0 ||
            radius < 0f ||
            ammunitionType in profile.immuneAmmunitionTypes
        ) return null
        val effectiveRadiusX = profile.collision.width / 2f + radius
        val effectiveRadiusY = profile.collision.height / 2f + radius
        val normalizedDistance = hypot(
            (positionX - x) / effectiveRadiusX,
            (positionY - (groundY - profile.collision.height / 2f)) / effectiveRadiusY,
        )
        if (normalizedDistance > 1f) return null
        val falloff = (1f - normalizedDistance).coerceIn(0f, 1f)
        val damage = (amount * falloff).toInt().coerceAtLeast(1)
        return applyDamage(damage, sourceTeam, ammunitionType)
    }

    fun advance(
        deltaSeconds: Float,
        units: List<Unit>,
        movingTeams: Set<Team>,
        blueGateX: Float,
        random: Random,
    ): BossTickResult {
        require(deltaSeconds >= 0f) { "deltaSeconds must not be negative" }
        if (!isAlive || deltaSeconds == 0f) return BossTickResult()
        val previousPhase = phase

        pendingUnitAttack?.let { pending ->
            elapseCooldown(pending.attack.target, deltaSeconds)
            pending.remainingWindupSeconds -= deltaSeconds
            if (pending.remainingWindupSeconds > 0f) {
                return BossTickResult(phaseChanged = previousPhase != phase)
            }
            pendingUnitAttack = null
            val impactTargets = unitsInRange(
                BossAttackTarget.UNIT,
                units,
                movingTeams,
            ).filter { it.team == pending.targetTeam }
            return BossTickResult(
                unitDamage = attackTargets(pending.attack, impactTargets, pending.targetTeam),
                phaseChanged = previousPhase != phase,
            )
        }

        if (profile.archetype == BossArchetype.CENTRAL_OBJECTIVE) {
            val targets = unitsInRange(BossAttackTarget.UNIT, units, movingTeams)
            if (targets.isEmpty()) {
                remainingAttackCooldowns[BossAttackTarget.UNIT] = 1f
                return BossTickResult(phaseChanged = previousPhase != phase)
            }
            elapseCooldown(BossAttackTarget.UNIT, deltaSeconds)
            if (remainingAttackCooldowns.getValue(BossAttackTarget.UNIT) > 0f) {
                return BossTickResult(phaseChanged = previousPhase != phase)
            }
            val attack = attack(BossAttackTarget.UNIT)
            val targetTeam = selectAttackTeam(targets, random)
            remainingAttackCooldowns[attack.target] = attack.cooldownSeconds
            if (attack.windupSeconds > 0f) {
                pendingUnitAttack = PendingUnitAttack(
                    attack = attack,
                    targetTeam = targetTeam,
                    remainingWindupSeconds = attack.windupSeconds,
                )
                return BossTickResult(
                    phaseChanged = previousPhase != phase,
                    unitAttackStarted = targetTeam,
                )
            }
            return BossTickResult(
                unitDamage = attackTargets(attack, targets, targetTeam),
                phaseChanged = previousPhase != phase,
                unitAttackStarted = targetTeam,
            )
        }

        val gateStopX = blueGateX + profile.collision.width * 0.34f
        val enraged = isEnraged()
        if (x <= gateStopX) {
            phase = BossPhase.AT_GATE
            val attack = attack(BossAttackTarget.FORTRESS_GATE)
            elapseCooldown(attack.target, deltaSeconds)
            if (remainingAttackCooldowns.getValue(attack.target) > 0f) {
                return BossTickResult(phaseChanged = previousPhase != phase)
            }
            remainingAttackCooldowns[attack.target] = attack.cooldownSeconds
            return BossTickResult(
                fortressDamage = attack.damage,
                phaseChanged = previousPhase != phase,
            )
        }

        val targets = unitsInRange(BossAttackTarget.UNIT, units, movingTeams)
        if (targets.isEmpty()) {
            val speedMultiplier = if (enraged) profile.enrage()?.speedMultiplier ?: 1f else 1f
            x = (x - profile.movement.speedWorldUnitsPerSecond * speedMultiplier * deltaSeconds)
                .coerceAtLeast(gateStopX)
            groundY = groundHeightAt(x)
            phase = if (x <= gateStopX) BossPhase.AT_GATE else {
                if (enraged) BossPhase.ENRAGED else BossPhase.MARCHING
            }
            return BossTickResult(phaseChanged = previousPhase != phase)
        }

        phase = if (enraged) BossPhase.ENRAGED else BossPhase.ENGAGED
        val attack = attack(BossAttackTarget.UNIT)
        elapseCooldown(attack.target, deltaSeconds)
        if (remainingAttackCooldowns.getValue(attack.target) > 0f) {
            return BossTickResult(phaseChanged = previousPhase != phase)
        }
        val cooldownMultiplier =
            if (enraged) profile.enrage()?.attackCooldownMultiplier ?: 1f else 1f
        val targetTeam = selectAttackTeam(targets, random)
        remainingAttackCooldowns[attack.target] = attack.cooldownSeconds * cooldownMultiplier
        if (attack.windupSeconds > 0f) {
            pendingUnitAttack = PendingUnitAttack(
                attack = attack,
                targetTeam = targetTeam,
                remainingWindupSeconds = attack.windupSeconds,
            )
            return BossTickResult(
                phaseChanged = previousPhase != phase,
                unitAttackStarted = targetTeam,
            )
        }
        return BossTickResult(
            unitDamage = attackTargets(attack, targets, targetTeam),
            phaseChanged = previousPhase != phase,
            unitAttackStarted = targetTeam,
        )
    }

    private fun canUnitTargetBoss(unit: Unit, movingTeams: Set<Team>): Boolean =
        isActiveUnit(unit, movingTeams) &&
            unit.type !is SapperUnit &&
            unit.type !is SlingmasterUnit

    private fun isActiveUnit(unit: Unit, movingTeams: Set<Team>): Boolean =
        unit.isAlive &&
            unit.team in movingTeams &&
            !unit.inGarrison &&
            !unit.returningToGarrison

    private fun unitAttackRange(unit: Unit): Float = when {
        unit.type.detonatesOnContact ->
            UnitHitboxProfile.forUnitType(unit.type.id).halfWidth
        unit.type.attacksFromRange -> unit.type.attackRange
        else -> 82f
    }

    private fun elapseCooldown(target: BossAttackTarget, deltaSeconds: Float) {
        remainingAttackCooldowns[target] =
            (remainingAttackCooldowns.getValue(target) - deltaSeconds).coerceAtLeast(0f)
    }

    private fun unitsInRange(
        target: BossAttackTarget,
        units: List<Unit>,
        movingTeams: Set<Team>,
    ): List<Unit> {
        val attack = profile.attacks.firstOrNull { it.target == target } ?: return emptyList()
        return units.asSequence()
            .filter { isActiveUnit(it, movingTeams) }
            .filter {
                profile.archetype != BossArchetype.CENTRAL_OBJECTIVE ||
                    it.isEngagedInCombat()
            }
            .filter { abs(it.position.x - x) <= attack.rangeWorldUnits }
            .sortedBy { it.id.value }
            .toList()
    }

    private fun Unit.isEngagedInCombat(): Boolean =
        combatState.targetId != null && when (combatState.action) {
            UnitActionState.SEEKING_TARGET,
            UnitActionState.WINDING_UP,
            UnitActionState.ATTACKING,
            UnitActionState.RECOVERING,
            UnitActionState.THROWING,
            -> true
            else -> false
        }

    private fun selectAttackTeam(targets: List<Unit>, random: Random): Team {
        val left = targets.filter { it.team == Team.BLUE }
        val right = targets.filter { it.team == Team.RED }
        return when {
            left.isEmpty() -> Team.RED
            right.isEmpty() -> Team.BLUE
            left.size == right.size -> if (random.nextBoolean()) Team.BLUE else Team.RED
            else -> {
                val larger = if (left.size > right.size) Team.BLUE else Team.RED
                if (random.nextFloat() < 0.72f) {
                    larger
                } else if (larger == Team.BLUE) {
                    Team.RED
                } else {
                    Team.BLUE
                }
            }
        }
    }

    private fun attackTargets(
        attack: BossAttackBalance,
        targets: List<Unit>,
        targetTeam: Team,
    ): Map<EntityId, Int> {
        val selected = targets.filter { it.team == targetTeam }
        if (selected.isEmpty()) return emptyMap()
        val maximumDistance = selected.maxOf { abs(it.position.x - x) }.coerceAtLeast(1f)
        return selected.associate { unit ->
            val distanceRatio = (abs(unit.position.x - x) / maximumDistance).coerceIn(0f, 1f)
            unit.id to (attack.damage * (1f - distanceRatio * 0.70f))
                .toInt()
                .coerceAtLeast(attack.minimumDamage)
        }
    }

    private fun isEnraged(): Boolean {
        val ability = profile.enrage() ?: return false
        return health.toFloat() / profile.maxHealth <= ability.healthFractionThreshold
    }

    private fun attack(target: BossAttackTarget): BossAttackBalance =
        profile.attacks.firstOrNull { it.target == target }
            ?: error("boss profile '${profile.id}' is missing attack for $target")
}
