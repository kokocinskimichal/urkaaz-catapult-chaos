package com.urkaaaz.simulation

import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ProjectileSnapshot
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.EffectSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.TerrainSnapshot
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.domain.Aim
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.Catapult
import com.urkaaaz.domain.Fortress
import com.urkaaaz.domain.Projectile
import com.urkaaaz.domain.Velocity
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.domain.WorldPosition
import com.urkaaaz.domain.ResourceWallet
import com.urkaaaz.domain.Unit
import com.urkaaaz.domain.DemolisherUnit
import com.urkaaaz.domain.SapperUnit
import com.urkaaaz.domain.UnitDefinition

/**
 * Deterministic projectile-and-impact simulation.
 *
 * It accepts no Android or rendering objects. Every state transition is driven
 * by an explicit time step and produces inspectable events and a snapshot.
 */
class DeterministicMatchSimulation(
    private val matchId: MatchId,
    private val config: SimulationConfig,
    private val blueCatapult: Catapult = defaultCatapult(Team.BLUE, config.bounds, config),
    private val redCatapult: Catapult = defaultCatapult(Team.RED, config.bounds, config),
    private val blueFortress: Fortress = defaultFortress(Team.BLUE, config.bounds),
    private val redFortress: Fortress = defaultFortress(Team.RED, config.bounds),
) {
    private var status = MatchStatus.NOT_STARTED
    private var phase = MatchPhase.CREATED
    private var activeTeam = Team.BLUE
    private var simulationTimeMilliseconds = 0L
    private var remainingMatchTimeMilliseconds = config.matchDurationMilliseconds
    private var sequence = 0L
    private var terrain = TerrainState(config.bounds.height * 0.78f)
    private var projectiles = emptyList<Projectile>()
    private var currentBlueFortress = blueFortress
    private var currentRedFortress = redFortress
    private var currentBlueCatapult = blueCatapult
    private var currentRedCatapult = redCatapult
    private var units = emptyList<Unit>()
    private val movingTeams = mutableSetOf<Team>()
    private val supplyRegenProgressSeconds = mutableMapOf(
        Team.BLUE to 0f,
        Team.RED to 0f,
    )
    private val wind = WindSystem(
        initialAccelerationX = config.windAccelerationX,
        accelerationScale = config.windAccelerationScale,
    )
    private val resources = mutableMapOf(
        Team.BLUE to initialWallet(),
        Team.RED to initialWallet(),
    )
    private val reloadRemainingSeconds = mutableMapOf(
        Team.BLUE to 0f,
        Team.RED to 0f,
    )
    private val pendingEvents = mutableListOf<MatchEvent>()
    private var lingeringEffects = emptyList<LingeringEffect>()

    fun start(): List<MatchEvent> {
        check(status == MatchStatus.NOT_STARTED) { "match has already started" }
        status = MatchStatus.RUNNING
        phase = MatchPhase.PLAYER_TURN
        emit { MatchEvent.MatchStarted(it, matchId, simulationTimeMilliseconds) }
        emit { MatchEvent.TurnChanged(it, matchId, simulationTimeMilliseconds, activeTeam) }
        return drainEvents()
    }

    fun fire(
        catapultId: EntityId,
        ammunition: AmmunitionType = AmmunitionType.ROCK,
        playerId: PlayerId = PlayerId("simulation"),
    ): List<MatchEvent> {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val catapult = catapult(catapultId)
        check(reloadRemainingSeconds.getValue(catapult.team) <= 0f) {
            "catapult is reloading"
        }
        val availableResources = resources.getValue(catapult.team)
        check(availableResources.canUse(ammunition)) {
            "ammunition is unavailable: ${ammunition.name}"
        }
        check(projectiles.none { it.firedBy == catapult.team }) {
            "catapult already has an active projectile"
        }
        val radians = Math.toRadians(catapult.aim.directionDegrees.toDouble())
        val speed = catapult.aim.power * 8f
        val direction = if (catapult.team == Team.BLUE) 1f else -1f
        val projectile = Projectile(
            id = EntityId("projectile-${sequence + 1}"),
            ammunition = ammunition,
            firedBy = catapult.team,
            position = catapult.muzzlePosition(),
            velocity = Velocity(
                x = kotlin.math.cos(radians).toFloat() * speed * direction,
                y = -kotlin.math.sin(radians).toFloat() * speed,
            ),
        )
        projectiles = projectiles + projectile
        resources[catapult.team] = availableResources.consume(ammunition)
        reloadRemainingSeconds[catapult.team] = ammunition.reloadSeconds
        phase = MatchPhase.PROJECTILE_IN_FLIGHT
        emit {
            MatchEvent.ProjectileFired(
                it,
                matchId,
                simulationTimeMilliseconds,
                projectile.id,
                catapult.id,
            )
        }
        return drainEvents()
    }

    fun aim(catapultId: EntityId, directionDegrees: Float, power: Float) {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val catapult = catapult(catapultId)
        val aimed = catapult.withAim(Aim(directionDegrees, power))
        if (aimed.team == Team.BLUE) {
            currentBlueCatapult = aimed
        } else {
            currentRedCatapult = aimed
        }
    }

    fun deployUnit(type: UnitDefinition, team: Team): MatchEvent.UnitDeployed {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val wallet = resources.getValue(team)
        check(wallet.canSpendSupply(type.supplyCost)) {
            "insufficient supply for ${type.id}"
        }
        resources[team] = wallet.spendSupply(type.supplyCost)
        val spawnX = if (team == Team.BLUE) 250f else config.bounds.width - 250f
        val direction = if (team == Team.BLUE) 1f else -1f
        val existingTeamUnits = units.filter { it.team == team && team !in movingTeams }
        units = units.map { existing ->
            if (existing.team == team && team !in movingTeams) {
                val index = existingTeamUnits.indexOf(existing)
                existing.copy(
                    moving = true,
                    garrisonTargetX = spawnX +
                        direction * (existingTeamUnits.size - index) * GARRISON_SPACING,
                )
            } else {
                existing
            }
        }
        val unit = Unit(
            id = EntityId("${team.name.lowercase()}-${type.id.lowercase()}-${sequence + 1}"),
            type = type,
            team = team,
            position = WorldPosition(
                x = spawnX -
                    direction * NEW_UNIT_REAR_OFFSET,
                y = config.bounds.height * 0.78f,
            ),
            moving = true,
            garrisonTargetX = spawnX,
        )
        units += unit
        return emitAndReturn {
            MatchEvent.UnitDeployed(
                it,
                matchId,
                simulationTimeMilliseconds,
                unit.id,
                type.id,
            )
        }
    }

    fun sendWave(team: Team) {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        check(units.any { it.team == team }) { "no units available to send" }
        movingTeams += team
    }

    fun advance(deltaMilliseconds: Long): List<MatchEvent> {
        require(deltaMilliseconds >= 0) { "deltaMilliseconds must not be negative" }
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val deltaSeconds = deltaMilliseconds / 1_000f
        wind.update(deltaSeconds)
        replenishSupply(deltaSeconds)
        reloadRemainingSeconds.keys.forEach { team ->
            reloadRemainingSeconds[team] = (reloadRemainingSeconds.getValue(team) - deltaSeconds)
                .coerceAtLeast(0f)
        }
        simulationTimeMilliseconds += deltaMilliseconds
        remainingMatchTimeMilliseconds =
            (remainingMatchTimeMilliseconds - deltaMilliseconds).coerceAtLeast(0L)
        advanceUnits(deltaSeconds)
        resolveUnitInteractions(deltaSeconds)
        finishIfFortressDestroyed()
        val resolved = mutableListOf<Projectile>()
        projectiles.forEach { projectile ->
            val advanced = projectile.advance(
                deltaSeconds = deltaSeconds,
                windAccelerationX = wind.accelerationX(),
                gravityAccelerationY = config.gravityAccelerationY,
            )
            val target = targetHitBy(advanced)
            when {
                target != null -> resolveEntityImpact(advanced, target)
                terrain.contains(advanced.position) -> resolveTerrainImpact(advanced)
                outsideBounds(advanced.position) -> Unit
                else -> resolved += advanced
            }
        }
        projectiles = resolved
        advanceLingeringEffects(deltaSeconds)
        if (projectiles.isEmpty() && status == MatchStatus.RUNNING) {
            phase = MatchPhase.PLAYER_TURN
        }
        return drainEvents()
    }

    fun snapshot(): MatchSnapshot = MatchSnapshot(
        status = status,
        matchId = matchId,
        phase = phase,
        activeTeam = activeTeam,
        catapults = listOf(currentBlueCatapult, currentRedCatapult).map {
            CatapultSnapshot(
                entityId = it.id,
                team = it.team,
                x = it.position.x,
                y = it.position.y,
                directionDegrees = it.aim.directionDegrees,
                power = it.aim.power,
            )
        },
        fortresses = listOf(currentBlueFortress, currentRedFortress).map {
            FortressSnapshot(it.id, it.team, it.health.current, it.maxHealth)
        },
        units = units.map {
            com.urkaaaz.contracts.UnitSnapshot(
                entityId = it.id,
                team = it.team,
                unitType = it.type.id,
                x = it.position.x,
                y = it.position.y,
                health = it.health.current,
                maxHealth = it.type.maxHealth,
                moving = it.moving || it.team in movingTeams,
            )
        },
        projectiles = projectiles.map {
            ProjectileSnapshot(
                entityId = it.id,
                firedBy = it.firedBy,
                ammunitionType = it.ammunition.name,
                x = it.position.x,
                y = it.position.y,
                velocityX = it.velocity.x,
                velocityY = it.velocity.y,
            )
        },
        terrain = TerrainSnapshot(
            revision = terrain.revision,
            biome = config.terrainBiome,
        ),
        wind = wind.snapshot(),
        outcome = outcome(),
        resources = ResourceSnapshot(
            gold = resources[Team.BLUE]?.gold ?: 0,
        ),
        resourcesByTeam = resources.mapValues { (_, wallet) ->
            ResourceSnapshot(
                gold = wallet.gold,
                supply = wallet.supply,
                ammunition = wallet.ammunition.mapKeys { (type, _) -> type.name },
            )
        },
        effects = lingeringEffects.map {
            EffectSnapshot(
                effectType = it.ammunition.name,
                remainingMilliseconds = (it.remainingSeconds * 1_000f).toLong(),
            )
        },
        reloadRemainingSeconds = reloadRemainingSeconds.toMap(),
        activeProjectileTeams = projectiles.map { it.firedBy }.toSet(),
        remainingMilliseconds = remainingMatchTimeMilliseconds,
    )

    fun drainEvents(): List<MatchEvent> = pendingEvents.toList().also { pendingEvents.clear() }

    private fun resolveTerrainImpact(projectile: Projectile) {
        phase = MatchPhase.RESOLVING_IMPACT
        val effect = projectile.ammunition
        if (effect.craterRadius > 0f) {
            terrain = terrain.deform(projectile.position, effect.craterRadius, effect.craterDepth)
        }

        emit {
            MatchEvent.ProjectileHitTerrain(
                it,
                matchId,
                simulationTimeMilliseconds,
                projectile.id,
                projectile.ammunition.name,
            )
        }
        emit {
            MatchEvent.TerrainDeformed(
                it,
                matchId,
                simulationTimeMilliseconds,
                projectile.position.x,
                projectile.position.y,
                effect.craterRadius,
                projectile.ammunition.name,
            )
        }
        resolveAmmunitionEffect(projectile)
    }

    private fun advanceUnits(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        units = units.map { unit ->
            val waveMoving = unit.team in movingTeams
            val targetX = if (waveMoving && !unit.returningToGarrison) {
                fortressAttackX(opposingTeam(unit.team))
            } else if (unit.returningToGarrison) {
                fortressAttackX(unit.team)
            } else {
                unit.garrisonTargetX ?: return@map unit
            }
            val direction = kotlin.math.sign(targetX - unit.position.x)
            val combatTarget = if (waveMoving) {
                units
                    .filter {
                        it.team != unit.team &&
                            it.isAlive &&
                            !it.returningToGarrison &&
                            (it.position.x - unit.position.x) * direction >= 0f &&
                            kotlin.math.abs(it.position.x - unit.position.x) <=
                                UNIT_INTERACTION_RANGE
                    }
                    .minByOrNull { kotlin.math.abs(it.position.x - unit.position.x) }
            } else {
                null
            }
            val combatStopX = combatTarget?.let { target ->
                when {
                    unit.type is SapperUnit -> null
                    target.type is SapperUnit && !unit.type.attacksFromRange -> null
                    unit.type.attacksFromRange ->
                        target.position.x - direction * unit.type.attackRange
                    else ->
                        target.position.x - direction * config.unitCollisionRadius
                }
            }
            val alliedStopX = if (waveMoving && !unit.returningToGarrison) {
                units
                    .filter {
                        it.team == unit.team &&
                            it.id != unit.id &&
                            it.isAlive &&
                            !it.returningToGarrison &&
                            (it.position.x - unit.position.x) * direction > 0f
                    }
                    .minByOrNull { kotlin.math.abs(it.position.x - unit.position.x) }
                    ?.let { ally ->
                        if (direction >= 0f) {
                            maxOf(unit.position.x, ally.position.x - ALLIED_UNIT_MIN_SEPARATION)
                        } else {
                            minOf(unit.position.x, ally.position.x + ALLIED_UNIT_MIN_SEPARATION)
                        }
                    }
            } else {
                null
            }
            val movementTargetX = when {
                combatStopX == null -> alliedStopX ?: targetX
                alliedStopX == null -> combatStopX
                direction >= 0f -> minOf(combatStopX, alliedStopX)
                else -> maxOf(combatStopX, alliedStopX)
            }
            val step = unit.type.speed * if (waveMoving) {
                UNIT_MOVEMENT_SPEED_SCALE
            } else {
                GARRISON_MOVEMENT_SPEED_SCALE
            } * if (
                waveMoving &&
                unit.type is SapperUnit &&
                units.any { other ->
                    other.team != unit.team &&
                        other.isAlive &&
                        other.type is SapperUnit &&
                        kotlin.math.abs(other.position.x - unit.position.x) <=
                            config.unitCollisionRadius
                }
            ) {
                SAPPER_CONTACT_SPEED_MULTIPLIER
            } else {
                1f
            } * deltaSeconds
            val nextX = if (direction >= 0f) {
                minOf(unit.position.x + step, movementTargetX)
            } else {
                maxOf(unit.position.x - step, movementTargetX)
            }
            val reachedTarget = nextX == movementTargetX
            unit.copy(
                position = unit.position.copy(x = nextX),
                moving = !reachedTarget ||
                    (waveMoving && !unit.returningToGarrison && combatStopX == null),
                garrisonTargetX = if (reachedTarget && !unit.returningToGarrison) {
                    null
                } else {
                    unit.garrisonTargetX
                },
            )
        }
    }

    private fun resolveUnitInteractions(deltaSeconds: Float) {
        if (units.isEmpty()) return
        val updated = units.toMutableList()
        updated.indices.forEach { index ->
            val attacker = updated[index]
            if (!attacker.isAlive || attacker.team !in movingTeams) return@forEach
            if (attacker.returningToGarrison) {
                if (kotlin.math.abs(attacker.position.x - fortressAttackX(attacker.team)) <= 1f) {
                    updated[index] = attacker.copy(
                        moving = false,
                        returningToGarrison = false,
                        sapperHasBomb = true,
                        attackCooldownRemainingSeconds = 0f,
                    )
                }
                return@forEach
            }
            val enemyTeam = opposingTeam(attacker.team)
            val behavior = attacker.type
            val enemies = updated.filter {
                it.team == enemyTeam &&
                    it.isAlive &&
                    !it.returningToGarrison &&
                    kotlin.math.abs(it.position.x - attacker.position.x) <= UNIT_INTERACTION_RANGE
            }
            val nearestEnemy = enemies.minWithOrNull(
                compareBy<Unit>(
                    {                     if (behavior.attacksFromRange) {
                        when (it.type) {
                            is DemolisherUnit -> 0
                            is SapperUnit -> 1
                            else -> 2
                        }
                    } else {
                        0
                    } },
                    { kotlin.math.abs(it.position.x - attacker.position.x) },
                ),
            )
            val nextAttacker = attacker.copy(
                attackCooldownRemainingSeconds =
                    (attacker.attackCooldownRemainingSeconds - deltaSeconds).coerceAtLeast(0f),
            )
            if (nearestEnemy != null) {
                when {
                    behavior.attacksFromRange -> {
                        if (kotlin.math.abs(nearestEnemy.position.x - attacker.position.x) <=
                            behavior.attackRange
                        ) {
                            updated[index] = rangedAttack(
                                updated,
                                index,
                                nextAttacker,
                                nearestEnemy,
                            )
                        }
                    }
                    behavior.detonatesOnContact -> {
                        if (contacted(nextAttacker, nearestEnemy)) {
                            detonateDemolisher(updated, index, nextAttacker)
                        }
                    }
                    behavior is SapperUnit -> {
                        if (contacted(nextAttacker, nearestEnemy)) {
                            updated[index] = nextAttacker.copy(
                                moving = true,
                            )
                        }
                    }
                    else -> {
                        if (contacted(nextAttacker, nearestEnemy)) {
                            updated[index] = meleeAttack(
                                updated,
                                index,
                                nextAttacker,
                                nearestEnemy,
                            )
                        }
                    }
                }
                return@forEach
            }

            val enemyFortress = fortress(opposingTeam(attacker.team))
            val fortressDistance =
                kotlin.math.abs(enemyFortress.center.x - attacker.position.x)
            if (fortressDistance <= FORTRESS_ATTACK_RANGE) {
                updated[index] = attackEnemyFortress(
                    updated,
                    index,
                    nextAttacker,
                    enemyFortress,
                )
            } else {
                updated[index] = nextAttacker
            }
        }
        units = updated.filter { it.isAlive }
    }

    private fun meleeAttack(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
        target: Unit,
    ): Unit {
        if (attacker.attackCooldownRemainingSeconds > 0f) return attacker
        val damage = target.type.incomingDamage(attacker.type.attackDamage)
        replaceUnit(current, target.id, target.withDamage(damage))
        return attacker.copy(
            attackCooldownRemainingSeconds = attacker.type.attackCooldownSeconds,
        )
    }

    private fun rangedAttack(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
        target: Unit,
    ): Unit {
        if (attacker.attackCooldownRemainingSeconds > 0f) return attacker
        replaceUnit(
            current,
            target.id,
            target.withDamage(
                target.type.incomingDamage(attacker.type.attackDamage),
            ),
        )
        return attacker.copy(
            attackCooldownRemainingSeconds = attacker.type.attackCooldownSeconds,
        )
    }

    private fun attackEnemyFortress(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
        target: Fortress,
    ): Unit {
        if (attacker.attackCooldownRemainingSeconds > 0f) return attacker
        when (val behavior = attacker.type) {
            is SapperUnit -> {
                if (!attacker.sapperHasBomb) {
                    return attacker.copy(returningToGarrison = true)
                }
                damageFortress(target, behavior.fortressDamage)
                return attacker.copy(
                    sapperHasBomb = false,
                    returningToGarrison = true,
                    attackCooldownRemainingSeconds = attacker.type.attackCooldownSeconds,
                )
            }
            is DemolisherUnit -> {
                detonateDemolisher(current, attackerIndex, attacker)
                return attacker.copy(health = attacker.health.damage(attacker.health.current))
            }
            else -> {
                damageFortress(target, behavior.fortressDamage)
                return attacker.copy(
                    attackCooldownRemainingSeconds = attacker.type.attackCooldownSeconds,
                )
            }
        }
    }

    private fun detonateDemolisher(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
    ) {
        current.indices.forEach { index ->
            val target = current[index]
            if (target.isAlive &&
                kotlin.math.abs(target.position.x - attacker.position.x) <= DEMOLISHER_BLAST_RADIUS
            ) {
                replaceUnit(current, target.id, target.withDamage(DEMOLISHER_DAMAGE))
            }
        }
        damageFortress(fortress(opposingTeam(attacker.team)), DEMOLISHER_DAMAGE)
        replaceUnit(current, attacker.id, attacker.withDamage(attacker.health.current))
    }

    private fun contacted(first: Unit, second: Unit): Boolean =
        kotlin.math.abs(first.position.x - second.position.x) <= config.unitCollisionRadius

    private fun replaceUnit(current: MutableList<Unit>, id: EntityId, replacement: Unit) {
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            if (current[index].isAlive && !replacement.isAlive) {
                emit {
                    MatchEvent.UnitDefeated(
                        it,
                        matchId,
                        simulationTimeMilliseconds,
                        replacement.id,
                    )
                }
            }
            current[index] = replacement
        }
    }

    private fun damageFortress(target: Fortress, damage: Int) {
        if (target.team == Team.BLUE) {
            currentBlueFortress = currentBlueFortress.withDamage(damage)
            emitDamage(currentBlueFortress.id, damage)
        } else {
            currentRedFortress = currentRedFortress.withDamage(damage)
            emitDamage(currentRedFortress.id, damage)
        }
    }

    private fun fortress(team: Team): Fortress =
        if (team == Team.BLUE) currentBlueFortress else currentRedFortress

    private fun fortressAttackX(team: Team): Float {
        val fortress = fortress(team)
        return fortress.center.x + if (team == Team.BLUE) 60f else -60f
    }

    private fun opposingTeam(team: Team): Team =
        if (team == Team.BLUE) Team.RED else Team.BLUE

    private fun replenishSupply(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        Team.entries.forEach { team ->
            val wallet = resources.getValue(team)
            if (wallet.supply >= MAX_SUPPLY) {
                supplyRegenProgressSeconds[team] = 0f
                return@forEach
            }
            val progress = supplyRegenProgressSeconds.getValue(team) + deltaSeconds
            val replenished = (progress / SUPPLY_REGEN_INTERVAL_SECONDS).toInt()
            supplyRegenProgressSeconds[team] =
                progress - replenished * SUPPLY_REGEN_INTERVAL_SECONDS
            if (replenished > 0) {
                resources[team] = wallet.copy(
                    supply = (wallet.supply + replenished).coerceAtMost(MAX_SUPPLY),
                )
            }
        }
    }

    private fun resolveEntityImpact(projectile: Projectile, target: EntityTarget) {
        phase = MatchPhase.RESOLVING_IMPACT
        emit {
            MatchEvent.ProjectileHitEntity(
                it,
                matchId,
                simulationTimeMilliseconds,
                projectile.id,
                target.id,
                projectile.ammunition.name,
            )
        }
        resolveAmmunitionEffect(projectile)
    }

    private fun resolveAmmunitionEffect(projectile: Projectile) {
        val ammunition = projectile.ammunition
        when (ammunition.effect) {
            com.urkaaaz.domain.AmmunitionEffect.CLUSTER -> {
                listOf(-32f, 0f, 32f).forEach { offset ->
                    applyDamageAt(
                        position = projectile.position.copy(x = projectile.position.x + offset),
                        radius = ammunition.damageRadius,
                        damage = ammunition.damage / 2,
                    )
                }
            }
            com.urkaaaz.domain.AmmunitionEffect.INCENDIARY -> {
                applyDamageAt(projectile.position, ammunition.damageRadius, ammunition.damage)
                lingeringEffects += LingeringEffect(
                    ammunition = ammunition,
                    center = projectile.position,
                    remainingSeconds = 3f,
                    tickIntervalSeconds = 0.5f,
                    untilNextTickSeconds = 0.5f,
                )
            }
            else -> applyDamageAt(projectile.position, ammunition.damageRadius, ammunition.damage)
        }
        finishIfFortressDestroyed()
    }

    private fun applyDamageAt(position: WorldPosition, radius: Float, damage: Int) {
        if (currentBlueFortress.intersects(position, radius)) {
            currentBlueFortress = currentBlueFortress.withDamage(damage)
            emitDamage(currentBlueFortress.id, damage)
        }
        if (currentRedFortress.intersects(position, radius)) {
            currentRedFortress = currentRedFortress.withDamage(damage)
            emitDamage(currentRedFortress.id, damage)
        }
    }

    private fun advanceLingeringEffects(deltaSeconds: Float) {
        if (lingeringEffects.isEmpty()) return
        val updated = mutableListOf<LingeringEffect>()
        lingeringEffects.forEach { effect ->
            var remaining = effect.remainingSeconds - deltaSeconds
            var untilNextTick = effect.untilNextTickSeconds - deltaSeconds
            while (remaining > 0f && untilNextTick <= 0f) {
                applyDamageAt(
                    position = effect.center,
                    radius = effect.ammunition.damageRadius,
                    damage = effect.ammunition.damage / 2,
                )
                untilNextTick += effect.tickIntervalSeconds
            }
            if (remaining > 0f) {
                updated += effect.copy(
                    remainingSeconds = remaining,
                    untilNextTickSeconds = untilNextTick,
                )
            }
        }
        lingeringEffects = updated
        finishIfFortressDestroyed()
    }

    private fun finishIfFortressDestroyed() {
        if (!currentBlueFortress.isAlive || !currentRedFortress.isAlive) {
            status = MatchStatus.FINISHED
            phase = MatchPhase.FINISHED
            emit {
                MatchEvent.MatchFinished(
                    it,
                    matchId,
                    simulationTimeMilliseconds,
                    outcome() ?: MatchOutcome.DRAW,
                )
            }
        }
    }

    private fun targetHitBy(projectile: Projectile): EntityTarget? {
        val target = if (projectile.firedBy == Team.BLUE) currentRedFortress else currentBlueFortress
        return if (target.intersects(projectile.position, config.fortressCollisionRadius)) {
            EntityTarget(target.id)
        } else {
            null
        }
    }

    private fun catapult(id: EntityId): Catapult =
        listOf(currentBlueCatapult, currentRedCatapult).firstOrNull { it.id == id }
            ?: error("unknown catapult: ${id.value}")

    private fun outsideBounds(position: WorldPosition): Boolean =
        position.x < 0f || position.x > config.bounds.width || position.y < -config.bounds.height

    private fun outcome(): MatchOutcome? = when {
        currentBlueFortress.isAlive && currentRedFortress.isAlive -> null
        currentBlueFortress.isAlive -> MatchOutcome.BLUE_WIN
        currentRedFortress.isAlive -> MatchOutcome.RED_WIN
        else -> MatchOutcome.DRAW
    }

    private fun emit(factory: (EventId) -> MatchEvent) {
        sequence += 1
        pendingEvents += factory(EventId("event-$sequence"))
    }

    private fun <T : MatchEvent> emitAndReturn(factory: (EventId) -> T): T {
        sequence += 1
        return factory(EventId("event-$sequence")).also { pendingEvents += it }
    }

    private fun emitDamage(targetId: EntityId, amount: Int) {
        emit {
            MatchEvent.DamageApplied(it, matchId, simulationTimeMilliseconds, targetId, amount)
        }
    }

    private fun initialWallet(): ResourceWallet = ResourceWallet(
        gold = 100,
        supply = MAX_SUPPLY,
        ammunition = AmmunitionType.entries
            .filter { !it.unlimited }
            .associateWith { config.startingLimitedAmmunition },
    )

    private data class LingeringEffect(
        val ammunition: AmmunitionType,
        val center: WorldPosition,
        val remainingSeconds: Float,
        val tickIntervalSeconds: Float,
        val untilNextTickSeconds: Float,
    )

    private data class EntityTarget(val id: EntityId)

    companion object {
        private const val MAX_SUPPLY = 20
        private const val SUPPLY_REGEN_INTERVAL_SECONDS = 3f
        private const val UNIT_MOVEMENT_SPEED_SCALE = 0.45f
        private const val GARRISON_MOVEMENT_SPEED_SCALE = 0.22f
        private const val GARRISON_SPACING = 40f
        private const val NEW_UNIT_REAR_OFFSET = 20f
        private const val UNIT_INTERACTION_RANGE = 260f
        private const val FORTRESS_ATTACK_RANGE = 82f
        private const val DEMOLISHER_BLAST_RADIUS = 260f
        private const val DEMOLISHER_DAMAGE = 300
        private const val SAPPER_CONTACT_SPEED_MULTIPLIER = 0.65f
        private const val ALLIED_UNIT_MIN_SEPARATION = 16f

        private fun defaultCatapult(team: Team, bounds: WorldBounds, config: SimulationConfig): Catapult =
            Catapult(
                id = EntityId("${team.name.lowercase()}-catapult"),
                team = team,
                position = WorldPosition(
                    x = if (team == Team.BLUE) 120f else bounds.width - 120f,
                    y = bounds.height * config.defaultCatapultHeightRatio,
                ),
                aim = Aim(directionDegrees = 45f, power = 50f),
            )

        private fun defaultFortress(team: Team, bounds: WorldBounds): Fortress =
            Fortress(
                id = EntityId("${team.name.lowercase()}-fortress"),
                team = team,
                center = WorldPosition(
                    x = if (team == Team.BLUE) 160f else bounds.width - 160f,
                    y = bounds.height * 0.68f,
                ),
            )
    }
}

private fun WorldPosition.distanceTo(other: WorldPosition): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
