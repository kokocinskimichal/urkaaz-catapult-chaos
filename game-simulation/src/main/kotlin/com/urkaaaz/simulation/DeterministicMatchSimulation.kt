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
import com.urkaaaz.domain.AmmunitionCatalog
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
import com.urkaaaz.domain.AttackType
import com.urkaaaz.domain.AttackGroupId
import com.urkaaaz.domain.UnitActionState
import com.urkaaaz.domain.UnitStatus
import com.urkaaaz.domain.FriendlyFirePolicy
import kotlin.math.min
import kotlin.random.Random

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
    private val combatLogSink: CombatLogSink = NoOpCombatLogSink,
    private val ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
) {
    private var status = MatchStatus.NOT_STARTED
    private var phase = MatchPhase.CREATED
    private var activeTeam = Team.BLUE
    private var simulationTimeMilliseconds = 0L
    private var remainingMatchTimeMilliseconds = config.matchDurationMilliseconds
    private var sequence = 0L
    private var terrain = TerrainState.legacyProfile(
        baselineHeight = config.bounds.height * 0.78f,
        worldWidth = config.bounds.width,
        worldHeight = config.bounds.height,
    ).flattenFoundation(160f, 150f)
        .flattenFoundation(config.bounds.width - 160f, 150f)
        .protectRange(160f, 150f)
        .protectRange(config.bounds.width - 160f, 150f)
    private var projectiles = emptyList<Projectile>()
    private val pendingProjectiles = mutableListOf<Projectile>()
    private var currentBlueFortress = blueFortress
    private var currentRedFortress = redFortress
    private var currentBlueCatapult = blueCatapult
    private var currentRedCatapult = redCatapult
    private var units = emptyList<Unit>()
    private var defeatedUnits = emptyList<DefeatedUnit>()
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
    private val reloadRemainingSecondsByAmmunition =
        mutableMapOf<Team, MutableMap<AmmunitionType, Float>>().apply {
            Team.entries.forEach { team ->
                this[team] = AmmunitionCatalog.all.associate { it.type to 0f }.toMutableMap()
            }
        }
    private val pendingEvents = mutableListOf<MatchEvent>()
    private var lingeringEffects = emptyList<LingeringEffect>()
    private val combatRandom = Random(matchId.value.hashCode().toLong())
    private val combatLuckByUnit = mutableMapOf<EntityId, Float>()

    fun start(): List<MatchEvent> {
        check(status == MatchStatus.NOT_STARTED) { "match has already started" }
        status = MatchStatus.RUNNING
        phase = MatchPhase.PLAYER_TURN
        emit { MatchEvent.MatchStarted(it, matchId, simulationTimeMilliseconds) }
        emit { MatchEvent.TurnChanged(it, matchId, simulationTimeMilliseconds, activeTeam) }
        logCombatState("match-started")
        return drainEvents()
    }

    fun fire(
        catapultId: EntityId,
        ammunition: AmmunitionType = AmmunitionType.ROCK,
        playerId: PlayerId = PlayerId("simulation"),
    ): List<MatchEvent> {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val catapult = catapult(catapultId)
        val ammunitionDefinition = AmmunitionCatalog.definition(ammunition)
        check(
            reloadRemainingSecondsByAmmunition
                .getValue(catapult.team)
                .getValue(ammunition) <= 0f,
        ) {
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
        val speed = catapult.aim.power * ammunitionDefinition.flight.initialSpeedMultiplier
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
            mass = ammunitionDefinition.flight.mass,
            windResponse = ammunitionDefinition.flight.windResponse,
            gravityResponse = ammunitionDefinition.flight.gravityResponse,
            drag = ammunitionDefinition.flight.drag,
        )
        projectiles = projectiles + projectile
        resources[catapult.team] = availableResources.consume(ammunition)
        logAmmunition(
            reason = "inventory-consumed",
            projectile = projectile,
        )
        reloadRemainingSecondsByAmmunition
            .getValue(catapult.team)[ammunition] = ammunitionDefinition.reloadSeconds
        logAmmunition(
            reason = "reload-started",
            projectile = projectile,
            remainingReloadSeconds = ammunitionDefinition.reloadSeconds,
        )
        phase = MatchPhase.PROJECTILE_IN_FLIGHT
        emit {
            MatchEvent.ProjectileFired(
                it,
                matchId,
                simulationTimeMilliseconds,
                projectile.id,
                catapult.id,
                ammunition.name,
                projectile.position.x,
                projectile.position.y,
            )
        }
        logAmmunition(
            reason = "projectile-fired",
            projectile = projectile,
            remainingReloadSeconds = ammunitionDefinition.reloadSeconds,
        )
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
        check(
            type !is SapperUnit ||
                units.none { it.team == team && it.type is SapperUnit && it.isAlive },
        ) {
            "only one active Sapper is allowed per team"
        }
        resources[team] = wallet.spendSupply(type.supplyCost)
        val spawnX = if (team == Team.BLUE) 250f else config.bounds.width - 250f
        val direction = if (team == Team.BLUE) 1f else -1f
        val existingTeamUnits = units.filter { it.team == team && it.inGarrison }
        units = units.map { existing ->
            if (existing.team == team && existing.inGarrison) {
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
                x = spawnX - direction * NEW_UNIT_REAR_OFFSET,
                y = terrain.heightAt(spawnX - direction * NEW_UNIT_REAR_OFFSET),
            ),
            moving = true,
            garrisonTargetX = spawnX,
            inGarrison = true,
        )
        units += unit
        logCombatState("unit-deployed:${unit.id.value}")
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
        units = units.map { unit ->
            if (unit.team == team && unit.isAlive && !unit.returningToGarrison) {
                unit.copy(inGarrison = false)
            } else {
                unit
            }
        }
        movingTeams += team
    }

    fun advance(deltaMilliseconds: Long): List<MatchEvent> {
        require(deltaMilliseconds >= 0) { "deltaMilliseconds must not be negative" }
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val deltaSeconds = deltaMilliseconds / 1_000f
        wind.update(deltaSeconds)
        replenishSupply(deltaSeconds)
        reloadRemainingSecondsByAmmunition.forEach { (team, reloads) ->
            reloads.keys.forEach { ammunition ->
                val previous = reloads.getValue(ammunition)
                val next = (previous - deltaSeconds)
                    .coerceAtLeast(0f)
                reloads[ammunition] = next
                if (previous > 0f && next == 0f) {
                    logAmmunition(
                        reason = "reload-finished",
                        ammunitionType = ammunition.name,
                        team = team,
                    )
                }
            }
        }
        simulationTimeMilliseconds += deltaMilliseconds
        remainingMatchTimeMilliseconds =
            (remainingMatchTimeMilliseconds - deltaMilliseconds).coerceAtLeast(0L)
        defeatedUnits = defeatedUnits.mapNotNull { defeated ->
            defeated.copy(remainingSeconds = defeated.remainingSeconds - deltaSeconds)
                .takeIf { it.remainingSeconds > 0f }
        }
        advanceUnits(deltaSeconds)
        resolveUnitInteractions(deltaSeconds)
        finishIfFortressDestroyed()
        val resolved = mutableListOf<Projectile>()
        projectiles.forEach { projectile ->
            val advanced = projectile.advance(
                deltaSeconds = deltaSeconds,
                windAccelerationX = wind.accelerationX(),
                gravityAccelerationY = config.gravityAccelerationY,
                windResponse = projectile.windResponse,
                gravityResponse = projectile.gravityResponse,
                drag = projectile.drag,
            )
            val target = targetHitBy(advanced)
            when {
                target != null -> {
                    resolveEntityImpact(advanced, target)
                    if (!target.stopsProjectile) resolved += advanced
                }
                terrain.contains(advanced.position) -> resolveTerrainImpact(advanced)
                outsideBounds(advanced.position) ||
                    advanced.ageSeconds >= AmmunitionCatalog
                        .definition(advanced.ammunition)
                        .flight
                        .maximumLifetimeSeconds -> Unit
                else -> resolved += advanced
            }
        }
        projectiles = resolved + pendingProjectiles
        pendingProjectiles.clear()
        advanceLingeringEffects(deltaSeconds)
        logCombatState("tick")
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
        units = (units + defeatedUnits.map { it.unit }).map {
            com.urkaaaz.contracts.UnitSnapshot(
                entityId = it.id,
                team = it.team,
                unitType = it.type.id,
                x = it.position.x,
                y = it.position.y,
                health = it.health.current,
                maxHealth = it.type.maxHealth,
                moving = it.moving && !it.isAttacking(),
                attackType = it.type.attackType.name,
                actionState = it.combatState.action.name,
                targetId = it.combatState.targetId,
                attackGroupId = it.combatState.attackGroupId?.value,
                attackCycleId = it.combatState.attackCycleId,
                attackProgress = attackProgress(it),
                facingDirection = if (it.team == Team.BLUE) 1f else -1f,
                sapperHasBomb = it.sapperHasBomb,
                statuses = it.statuses.mapKeys { (status, _) -> status.name }
                    .mapValues { (_, state) -> state.remainingSeconds },
                statusDetails = it.statuses.mapKeys { (status, _) -> status.name }
                    .mapValues { (_, state) ->
                        com.urkaaaz.contracts.StatusSnapshot(
                            remainingMilliseconds = (state.remainingSeconds * 1_000f).toLong(),
                            strength = state.strength,
                        )
                    },
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
                ageMilliseconds = (it.ageSeconds * 1_000f).toLong(),
                parentProjectileId = it.parentProjectileId,
                generation = it.generation,
                mass = it.mass,
                windResponse = it.windResponse,
                gravityResponse = it.gravityResponse,
                drag = it.drag,
            )
        },
        terrain = TerrainSnapshot(
            revision = terrain.revision,
            biome = config.terrainBiome,
            craters = terrain.craters.map {
                com.urkaaaz.contracts.CraterSnapshot(
                    centerX = it.center.x,
                    centerY = it.center.y,
                    radius = it.radius,
                    depth = it.depth,
                )
            },
            worldWidth = terrain.worldWidth,
            sampleSpacing = terrain.sampleSpacing,
            heightSamples = terrain.heightSamples.ifEmpty {
                List((terrain.worldWidth / terrain.sampleSpacing).toInt() + 2) {
                    terrain.baselineHeight
                }
            },
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
                effectId = it.id,
                sourceProjectileId = it.sourceProjectileId,
                x = it.currentCenter.x,
                y = it.currentCenter.y,
                radius = it.radius,
                status = it.status?.name,
            )
        },
        reloadRemainingSeconds = reloadRemainingSecondsByAmmunition.mapValues { (_, reloads) ->
            reloads.values.maxOrNull() ?: 0f
        },
        reloadRemainingSecondsByAmmunition = reloadRemainingSecondsByAmmunition.mapValues { (_, reloads) ->
            reloads.mapKeys { (type, _) -> type.name }
        },
        activeProjectileTeams = projectiles.map { it.firedBy }.toSet(),
        remainingMilliseconds = remainingMatchTimeMilliseconds,
    )

    private fun attackProgress(unit: Unit): Float {
        val cooldown = unit.combatState.attackCooldownRemainingSeconds
        val duration = unit.type.attackCooldownSeconds
        return if (duration <= 0f) 0f else (1f - cooldown / duration).coerceIn(0f, 1f)
    }

    private fun logCombatState(reason: String) {
        if (units.isEmpty()) return
        val states = units.map { unit ->
            val target = unit.combatState.targetId?.let { targetId ->
                units.firstOrNull { it.id == targetId && it.isAlive }
            }
            CombatLogUnit(
                id = unit.id,
                type = unit.type.id,
                team = unit.team,
                health = unit.health.current,
                maxHealth = unit.type.maxHealth,
                x = unit.position.x,
                y = unit.position.y,
                moving = unit.moving,
                action = unit.combatState.action.name,
                targetId = unit.combatState.targetId,
                attackGroupId = unit.combatState.attackGroupId?.value,
                attackCycleId = unit.combatState.attackCycleId,
                attackProgress = unit.combatState.attackProgress,
                distanceToTarget = target?.let {
                    kotlin.math.abs(it.position.x - unit.position.x)
                },
                sapperHasBomb = unit.sapperHasBomb,
            )
        }
        combatLogSink.log(
            CombatLogRecord(
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                reason = reason,
                units = states,
            ),
        )
    }

    fun drainEvents(): List<MatchEvent> = pendingEvents.toList().also { pendingEvents.clear() }

    private fun resolveTerrainImpact(projectile: Projectile) {
        phase = MatchPhase.RESOLVING_IMPACT
        val definition = AmmunitionCatalog.definition(projectile.ammunition)
        if (definition.terrain.deformsTerrain) {
            terrain = terrain.deform(
                projectile.position,
                definition.terrain.craterRadius,
                definition.terrain.craterDepth,
            )
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
                definition.terrain.craterRadius,
                projectile.ammunition.name,
            )
        }
        logAmmunition("projectile-hit-terrain", projectile)
        resolveAmmunitionEffect(projectile)
    }

    private fun advanceUnits(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        units = units.map { unit ->
            val statusAdvanced = unit.advanceStatuses(deltaSeconds)
            if (statusAdvanced.statuses.containsKey(UnitStatus.STUNNED)) {
                return@map statusAdvanced.copy(
                    position = statusAdvanced.position.copy(
                        y = terrain.heightAt(statusAdvanced.position.x),
                    ),
                    moving = false,
                )
            }
            if (unit.isAttacking()) {
                return@map statusAdvanced.copy(
                    position = statusAdvanced.position.copy(
                        y = terrain.heightAt(statusAdvanced.position.x),
                    ),
                    moving = false,
                )
            }
            val waveMoving = unit.team in movingTeams && !unit.inGarrison
            val targetX = if (waveMoving && !unit.returningToGarrison) {
                fortressAttackX(opposingTeam(unit.team))
            } else if (unit.returningToGarrison) {
                fortressAttackX(unit.team)
            } else {
                unit.garrisonTargetX ?: return@map statusAdvanced.copy(
                    position = statusAdvanced.position.copy(
                        y = terrain.heightAt(statusAdvanced.position.x),
                    ),
                )
            }
            val direction = kotlin.math.sign(targetX - unit.position.x)
            val combatTarget = if (waveMoving) {
                val locked = unit.combatState.targetId?.let { id ->
                    units.firstOrNull { it.id == id && it.isAlive && it.team != unit.team }
                }
                val groupTarget = units
                    .filter {
                        it.team == unit.team &&
                            it.isAlive &&
                            !it.returningToGarrison &&
                            it.combatState.targetId != null &&
                            it.combatState.targetId != unit.combatState.targetId
                    }
                    .sortedWith(compareBy<Unit>({ kotlin.math.abs(it.position.x - unit.position.x) }, { it.id.value }))
                    .asSequence()
                    .mapNotNull { ally ->
                        ally.combatState.targetId?.let { targetId ->
                            units.firstOrNull { target ->
                                target.id == targetId &&
                                    target.team != unit.team &&
                                    target.isAlive &&
                                    !target.returningToGarrison
                            }
                        }
                    }
                    .firstOrNull()
                locked ?: groupTarget ?: units
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
                val sameTargetAttackers = (units
                    .filter {
                        it.team == unit.team &&
                            it.isAlive &&
                            !it.returningToGarrison &&
                            (it.id == unit.id || it.combatState.targetId == target.id) &&
                            it.type !is SapperUnit
                    }
                    .sortedBy { it.id.value })
                val formationRank = sameTargetAttackers
                    .indexOfFirst { it.id == unit.id }
                    .coerceAtLeast(0)
                val formationOffset = formationRank * ATTACK_FORMATION_SPACING
                when {
                    unit.type is SapperUnit -> null
                    target.type is SapperUnit && !unit.type.attacksFromRange -> null
                    unit.type.attacksFromRange ->
                        target.position.x -
                            direction * (unit.type.attackRange + formationOffset)
                    else ->
                        target.position.x -
                            direction * (config.unitCollisionRadius + formationOffset)
                }
            }
            val formationRank = combatTarget?.let { target ->
                units
                    .filter {
                        it.team == unit.team &&
                            it.isAlive &&
                            !it.returningToGarrison &&
                            it.type !is SapperUnit &&
                            (
                                it.id == unit.id ||
                                    it.combatState.targetId == target.id
                                )
                    }
                    .sortedBy { it.id.value }
                    .indexOfFirst { it.id == unit.id }
                    .coerceAtLeast(0)
            } ?: 0
            val alliedStopX = if (waveMoving && !unit.returningToGarrison) {
                units
                    .filter {
                        it.team == unit.team &&
                            it.id != unit.id &&
                            it.isAlive &&
                            !it.returningToGarrison &&
                            it.type.speed >= unit.type.speed &&
                            (
                                (it.position.x - unit.position.x) * direction > 0f ||
                                    (
                                        kotlin.math.abs(it.position.x - unit.position.x) < 0.01f &&
                                            combatTarget != null &&
                                            it.combatState.targetId == combatTarget.id &&
                                            units
                                                .filter { member ->
                                                    member.team == unit.team &&
                                                        member.isAlive &&
                                                        !member.returningToGarrison &&
                                                        member.type !is SapperUnit &&
                                                        (
                                                            member.id == it.id ||
                                                                member.combatState.targetId == combatTarget.id
                                                            )
                                                }
                                                .sortedBy { member -> member.id.value }
                                                .indexOfFirst { member -> member.id == it.id } < formationRank
                                    )
                                )
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
            val slowMultiplier = statusAdvanced.statuses[UnitStatus.SLOWED]
                ?.strength
                ?.let { (1f - it).coerceIn(0.05f, 1f) }
                ?: 1f
            val step = unit.type.speed * slowMultiplier * if (waveMoving) {
                UNIT_MOVEMENT_SPEED_SCALE
            } else {
                GARRISON_MOVEMENT_SPEED_SCALE
            } * if (
                waveMoving &&
                unit.type is SapperUnit &&
                units.any { other ->
                    other.team != unit.team &&
                        other.isAlive &&
                        !other.returningToGarrison &&
                        !other.type.attacksFromRange &&
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
            statusAdvanced.copy(
                position = WorldPosition(
                    x = nextX,
                    y = terrain.heightAt(nextX),
                ),
                moving = !reachedTarget ||
                    (
                        waveMoving &&
                            !unit.returningToGarrison &&
                            combatStopX == null &&
                            unit.type !is SapperUnit
                        ),
                garrisonTargetX = if (reachedTarget && !unit.returningToGarrison && !unit.inGarrison) {
                    null
                } else {
                    unit.garrisonTargetX
                },
                combatState = unit.combatState.copy(
                    action = when {
                        unit.isAttacking() -> unit.combatState.action
                        unit.returningToGarrison -> UnitActionState.RETURNING
                        combatTarget != null && reachedTarget -> UnitActionState.SEEKING_TARGET
                        unit.moving || !reachedTarget -> UnitActionState.MOVING
                        else -> unit.combatState.action
                    },
                ),
            )
        }
    }

    private fun resolveUnitInteractions(deltaSeconds: Float) {
        if (units.isEmpty()) return
        val current = units.map {
            it.copy(
                combatState = it.combatState.copy(
                    attackCooldownRemainingSeconds =
                        (it.combatState.attackCooldownRemainingSeconds - deltaSeconds)
                            .coerceAtLeast(0f),
                ),
            )
        }.toMutableList()
        val hitIntents = mutableListOf<AttackIntent>()
        val startIntents = mutableListOf<AttackIntent>()

        current.indices.forEach { index ->
            val attacker = current[index]
            if (!attacker.isAlive ||
                !attacker.isAttacking() ||
                attacker.statuses.containsKey(UnitStatus.STUNNED)
            ) return@forEach
            val duration = attacker.type.attackCooldownSeconds
            val previousProgress = attacker.combatState.attackProgress
            val progress = if (duration <= 0f) {
                1f
            } else {
                (1f - attacker.combatState.attackCooldownRemainingSeconds / duration)
                    .coerceIn(0f, 1f)
            }
            current[index] = attacker.copy(
                combatState = attacker.combatState.copy(
                    action = if (progress < 1f) {
                        UnitActionState.ATTACKING
                    } else {
                        UnitActionState.RECOVERING
                    },
                    attackProgress = progress,
                ),
            )
            if (previousProgress < 0.5f && progress >= 0.5f) {
                val targetId = attacker.combatState.targetId
                    ?: return@forEach
                hitIntents += AttackIntent(
                    attackerId = attacker.id,
                    targetId = targetId,
                    type = if (
                        targetId == currentBlueFortress.id ||
                        targetId == currentRedFortress.id
                    ) {
                        AttackType.SIEGE_MISSION
                    } else {
                        attacker.type.attackType
                    },
                )
            }
            if (progress >= 1f) {
                current[index] = current[index].copy(
                    combatState = current[index].combatState.copy(
                        action = UnitActionState.SEEKING_TARGET,
                        attackProgress = 0f,
                    ),
                )
            }
        }

        current.indices.forEach { index ->
            val attacker = current[index]
            if (!attacker.isAlive ||
                attacker.team !in movingTeams ||
                attacker.isAttacking() ||
                attacker.combatState.attackCooldownRemainingSeconds > 0f
            ) return@forEach
            if (attacker.returningToGarrison) {
                if (kotlin.math.abs(attacker.position.x - fortressAttackX(attacker.team)) <= 1f) {
                    current[index] = attacker.copy(
                        moving = false,
                        returningToGarrison = false,
                        inGarrison = true,
                        sapperHasBomb = true,
                        health = attacker.health.copy(current = attacker.health.maximum),
                        combatState = attacker.combatState.copy(
                            action = UnitActionState.GARRISONED,
                            targetId = null,
                            attackCooldownRemainingSeconds = 0f,
                        ),
                    )
                }
                return@forEach
            }
            val target = lockedOrAcquireTarget(attacker, current)
            if (target != null) {
                if (attacker.combatState.targetId != target.id) {
                    emit { MatchEvent.TargetAcquired(it, matchId, simulationTimeMilliseconds, attacker.id, target.id) }
                }
            val groupId = if (attacker.type.attackType == AttackType.MELEE) {
                AttackGroupId("melee-${target.id.value}")
            } else {
                null
            }
            val joinedGroup = groupId != null && attacker.combatState.attackGroupId != groupId
            current[index] = attacker.copy(
                combatState = attacker.combatState.copy(
                    action = UnitActionState.SEEKING_TARGET,
                    targetId = target.id,
                    attackGroupId = groupId ?: attacker.combatState.attackGroupId,
                ),
            )
            if (joinedGroup) {
                emit {
                    MatchEvent.AttackGroupJoined(
                        it,
                        matchId,
                        simulationTimeMilliseconds,
                        groupId!!.value,
                        attacker.id,
                        target.id,
                    )
                }
            }
            when {
                    attacker.type is SapperUnit -> Unit
                    attacker.type.detonatesOnContact && contacted(attacker, target) ->
                        startIntents += AttackIntent(attacker.id, target.id, AttackType.CONTACT_EXPLOSIVE)
                    attacker.type.attacksFromRange &&
                        kotlin.math.abs(target.position.x - attacker.position.x) <= attacker.type.attackRange ->
                        startIntents += AttackIntent(attacker.id, target.id, AttackType.RANGED)
                    !attacker.type.attacksFromRange && canMeleeAttack(attacker, target, current) ->
                        startIntents += AttackIntent(attacker.id, target.id, AttackType.MELEE)
                }
            } else {
                val enemyFortress = fortress(opposingTeam(attacker.team))
                if (canAttackFortress(attacker, enemyFortress, current)) {
                    startIntents += AttackIntent(attacker.id, enemyFortress.id, AttackType.SIEGE_MISSION)
                }
            }
        }

        resolveAttackHits(current, hitIntents)
        startAttackIntents(current, startIntents)
        defeatedUnits += current.filter { !it.isAlive }.map {
            DefeatedUnit(
                unit = it.copy(
                    combatState = it.combatState.copy(
                        action = UnitActionState.DEAD,
                        targetId = null,
                        attackProgress = 1f,
                    ),
                ),
                remainingSeconds = DEATH_ANIMATION_SECONDS,
            )
        }
        units = current.filter { it.isAlive }
    }

    private fun lockedOrAcquireTarget(attacker: Unit, candidates: List<Unit>): Unit? {
        if (attacker.type is SapperUnit) return null
        val locked = attacker.combatState.targetId?.let { id ->
            candidates.firstOrNull {
                it.id == id && it.isAlive && it.team != attacker.team &&
                    !it.returningToGarrison &&
                    (
                        attacker.combatState.attackGroupId != null ||
                            kotlin.math.abs(it.position.x - attacker.position.x) <= UNIT_INTERACTION_RANGE
                        )
            }
        }
        return locked ?: candidates.filter {
            it.team != attacker.team && it.isAlive && !it.returningToGarrison &&
                kotlin.math.abs(it.position.x - attacker.position.x) <= UNIT_INTERACTION_RANGE
        }.minWithOrNull(compareBy({ kotlin.math.abs(it.position.x - attacker.position.x) }, { it.id.value }))
    }

    private fun resolveAttackHits(current: MutableList<Unit>, intents: List<AttackIntent>) {
        val damageByTarget = mutableMapOf<EntityId, Int>()
        val attackSnapshot = current.toList()
        intents.forEach { intent ->
            val attackerIndex = current.indexOfFirst { it.id == intent.attackerId }
            if (attackerIndex < 0) return@forEach
            val attacker = current[attackerIndex]
            if (!attacker.isAlive) return@forEach
            if (intent.type == AttackType.SIEGE_MISSION) {
                attackMission(current, attackerIndex, attacker)
                return@forEach
            }
            val target = attackSnapshot.firstOrNull { it.id == intent.targetId }
            if (target == null || !target.isAlive || !isValidAttackTarget(attacker, target, attackSnapshot)) {
                current[attackerIndex] = attacker.copy(
                    combatState = attacker.combatState.copy(
                        action = UnitActionState.SEEKING_TARGET,
                        targetId = null,
                        attackProgress = 0f,
                    ),
                )
                return@forEach
            }
            when (intent.type) {
                AttackType.CONTACT_EXPLOSIVE -> detonateDemolisher(current, attackerIndex, attacker)
                AttackType.MELEE, AttackType.RANGED -> {
                    val roll = rollDamage(attacker)
                    val members = meleeMembersForTarget(current, target.id)
                    val synergy = if (intent.type == AttackType.MELEE) {
                        min(1f + (members.size - 1) * 0.35f, 2f)
                    } else {
                        1f
                    }
                    val rolled = (roll * synergy).toInt()
                    val finalDamage = if (intent.type == AttackType.MELEE) {
                        target.type.incomingDamage(rolled)
                    } else {
                        rolled
                    }
                    damageByTarget[target.id] = (damageByTarget[target.id] ?: 0) + finalDamage
                    emit {
                        MatchEvent.AttackHit(
                            it,
                            matchId,
                            simulationTimeMilliseconds,
                            attacker.id,
                            target.id,
                            intent.type.name,
                            attacker.combatState.attackCycleId,
                            attacker.type.attackDamage,
                            roll,
                            members.size,
                            synergy,
                            finalDamage,
                        )
                    }
                }
            }
        }
        damageByTarget.forEach { (targetId, damage) ->
            val target = current.firstOrNull { it.id == targetId } ?: return@forEach
            emitDamage(targetId, damage)
            replaceUnit(current, targetId, target.withDamage(damage))
        }
    }

    private fun startAttackIntents(current: MutableList<Unit>, intents: List<AttackIntent>) {
        intents.forEach { intent ->
            val index = current.indexOfFirst { it.id == intent.attackerId }
            if (index < 0) return@forEach
            val attacker = current[index]
            if (!attacker.isAlive || attacker.isAttacking()) return@forEach
            val cycle = attacker.combatState.attackCycleId + 1
            val groupId = if (intent.type == AttackType.MELEE) {
                attacker.combatState.attackGroupId?.value ?: "melee-${intent.targetId.value}"
            } else {
                null
            }
            current[index] = attacker.copy(
                combatState = attacker.combatState.copy(
                    action = UnitActionState.WINDING_UP,
                    targetId = intent.targetId,
                    attackCycleId = cycle,
                    attackProgress = 0f,
                    attackCooldownRemainingSeconds = attacker.type.attackCooldownSeconds,
                    attackGroupId = groupId?.let(::AttackGroupId),
                ),
            )
            emit {
                MatchEvent.AttackStarted(
                    it,
                    matchId,
                    simulationTimeMilliseconds,
                    attacker.id,
                    intent.targetId,
                    intent.type.name,
                    cycle,
                )
            }
            if (groupId != null && attacker.combatState.attackGroupId?.value != groupId) {
                emit {
                    MatchEvent.AttackGroupJoined(
                        it,
                        matchId,
                        simulationTimeMilliseconds,
                        groupId,
                        attacker.id,
                        intent.targetId,
                    )
                }
            }
        }
    }

    private fun meleeMembersForTarget(current: List<Unit>, targetId: EntityId): List<Unit> =
        current.filter { attacker ->
            val target = current.firstOrNull { it.id == targetId }
            target != null &&
                attacker.isAlive &&
                attacker.type.participatesInMeleeSynergy &&
                attacker.combatState.targetId == targetId &&
                canMeleeAttack(attacker, target, current)
        }

    private fun isValidAttackTarget(
        attacker: Unit,
        target: Unit,
        current: List<Unit>,
    ): Boolean =
        target.team != attacker.team &&
            target.isAlive &&
            when (attacker.type.attackType) {
                AttackType.MELEE, AttackType.CONTACT_EXPLOSIVE ->
                    canMeleeAttack(attacker, target, current)
                AttackType.RANGED ->
                    kotlin.math.abs(target.position.x - attacker.position.x) <= attacker.type.attackRange
                AttackType.SIEGE_MISSION -> false
            }

    private fun canMeleeAttack(
        attacker: Unit,
        target: Unit,
        current: List<Unit>,
    ): Boolean {
        if (contacted(attacker, target)) return true
        if (attacker.combatState.attackGroupId?.value != "melee-${target.id.value}") {
            return false
        }
        val sameTargetMembers = current
            .filter {
                it.team == attacker.team &&
                    it.isAlive &&
                    !it.returningToGarrison &&
                    it.type.participatesInMeleeSynergy &&
                    (
                        it.id == attacker.id ||
                            it.combatState.targetId == target.id
                        )
            }
            .sortedBy { it.id.value }
        val formationRank = sameTargetMembers.indexOfFirst { it.id == attacker.id }
        if (formationRank < 0) return false
        val formationReach =
            config.unitCollisionRadius + formationRank * ATTACK_FORMATION_SPACING
        return kotlin.math.abs(target.position.x - attacker.position.x) <= formationReach
    }

    private fun canAttackFortress(
        attacker: Unit,
        target: Fortress,
        current: List<Unit>,
    ): Boolean {
        val formationRank = current
            .filter {
                it.team == attacker.team &&
                    it.isAlive &&
                    !it.inGarrison &&
                    !it.returningToGarrison &&
                    it.type !is SapperUnit
            }
            .sortedBy { it.id.value }
            .indexOfFirst { it.id == attacker.id }
            .coerceAtLeast(0)
        val formationReach = FORTRESS_ATTACK_RANGE + formationRank * ATTACK_FORMATION_SPACING
        return kotlin.math.abs(target.center.x - attacker.position.x) <= formationReach
    }

    private fun Unit.isAttacking(): Boolean =
        combatState.action == UnitActionState.WINDING_UP ||
            combatState.action == UnitActionState.ATTACKING ||
            combatState.action == UnitActionState.RECOVERING

    private fun attackMission(current: MutableList<Unit>, index: Int, attacker: Unit) {
        val behavior = attacker.type
        if (behavior !is SapperUnit) {
            damageFortress(fortress(opposingTeam(attacker.team)), behavior.fortressDamage)
            return
        }
        if (!attacker.sapperHasBomb) {
            current[index] = attacker.copy(returningToGarrison = true, inGarrison = false)
            return
        }
        damageFortress(fortress(opposingTeam(attacker.team)), behavior.fortressDamage)
        current[index] = attacker.copy(
            sapperHasBomb = false,
            returningToGarrison = true,
            inGarrison = false,
        )
    }

    private fun rollDamage(attacker: Unit): Int {
        val luckBias = combatLuckByUnit.getOrPut(attacker.id) {
            (combatRandom.nextFloat() * 0.08f) - 0.04f
        }
        val factor = (
            0.90f +
                combatRandom.nextFloat() * 0.20f +
                luckBias
            ).coerceIn(0.90f, 1.10f)
        return (attacker.type.attackDamage * factor).toInt().coerceAtLeast(1)
    }

    private data class AttackIntent(val attackerId: EntityId, val targetId: EntityId, val type: AttackType)

    private fun detonateDemolisher(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
    ) {
        val explosionDamage = rollExplosionDamage()
        emit {
            MatchEvent.ExplosionTriggered(
                it,
                matchId,
                simulationTimeMilliseconds,
                attacker.id,
                attacker.position.x,
                attacker.position.y,
                DEMOLISHER_BLAST_RADIUS,
                explosionDamage,
            )
        }
        current.indices.forEach { index ->
            val target = current[index]
            if (target.isAlive) {
                val distance = target.position.distanceTo(attacker.position)
                if (distance <= DEMOLISHER_BLAST_RADIUS) {
                    val falloff = 1f - distance / DEMOLISHER_BLAST_RADIUS
                    val damage = (explosionDamage * falloff).toInt().coerceAtLeast(1)
                    emitDamage(target.id, damage)
                    replaceUnit(
                        current,
                        target.id,
                        target.withDamage(damage),
                        triggerDeathExplosion = false,
                    )
                }
            }
        }
        val enemyFortress = fortress(opposingTeam(attacker.team))
        if (enemyFortress.intersects(attacker.position, DEMOLISHER_BLAST_RADIUS)) {
            val distance = attacker.position.distanceTo(enemyFortress.center)
            val falloff = (1f - distance / DEMOLISHER_BLAST_RADIUS).coerceAtLeast(0f)
            damageFortress(enemyFortress, (explosionDamage * falloff).toInt().coerceAtLeast(1))
        }
        replaceUnit(
            current,
            attacker.id,
            attacker.withDamage(attacker.health.current),
            triggerDeathExplosion = false,
        )
    }

    private fun rollExplosionDamage(): Int =
        (DEMOLISHER_DAMAGE * (0.90f + combatRandom.nextFloat() * 0.20f))
            .toInt()
            .coerceAtLeast(1)

    private fun contacted(first: Unit, second: Unit): Boolean =
        kotlin.math.abs(first.position.x - second.position.x) <= config.unitCollisionRadius

    private fun replaceUnit(
        current: MutableList<Unit>,
        id: EntityId,
        replacement: Unit,
        triggerDeathExplosion: Boolean = true,
    ) {
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            if (current[index].isAlive && !replacement.isAlive) {
                if (triggerDeathExplosion && current[index].type is DemolisherUnit) {
                    detonateDemolisher(current, index, current[index])
                }
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
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                projectileId = projectile.id,
                targetId = target.id,
                ammunitionType = projectile.ammunition.name,
                targetLayer = target.layer.name,
                x = projectile.position.x,
                y = projectile.position.y,
            )
        }
        logAmmunition("projectile-hit-${target.layer.name.lowercase()}", projectile, targetId = target.id)
        resolveAmmunitionEffect(projectile, target.layer)
    }

    private fun resolveAmmunitionEffect(
        projectile: Projectile,
        hitLayer: com.urkaaaz.domain.AmmunitionHitLayer? = null,
    ) {
        val definition = AmmunitionCatalog.definition(projectile.ammunition)
        when (val behavior = definition.behavior) {
            is com.urkaaaz.domain.AmmunitionImpactBehavior.Fragmentation -> {
                if (projectile.generation == 0) {
                    spawnSubprojectiles(projectile, behavior)
                } else {
                    applyDamageAt(
                        position = projectile.position,
                        radius = definition.damage.fortressRadius,
                        damage = behavior.fragmentDamage,
                        sourceTeam = projectile.firedBy,
                        unitDamage = behavior.fragmentDamage,
                        hitLayer = hitLayer,
                        sourceProjectileId = projectile.id,
                        minimumDamage = definition.damage.minimumDamage,
                        friendlyFire = definition.collision.friendlyFire,
                    )
                }
            }
            is com.urkaaaz.domain.AmmunitionImpactBehavior.LingeringArea -> {
                applyDamageAt(
                    position = projectile.position,
                    radius = definition.damage.fortressRadius,
                    damage = definition.damage.fortressDamage,
                    sourceTeam = projectile.firedBy,
                    unitDamage = behavior.damagePerTick,
                    status = behavior.status,
                    statusDurationSeconds = behavior.durationSeconds,
                    hitLayer = hitLayer,
                    sourceProjectileId = projectile.id,
                    minimumDamage = definition.damage.minimumDamage,
                    friendlyFire = definition.collision.friendlyFire,
                )
                lingeringEffects += LingeringEffect(
                    id = EntityId("effect-${sequence + 1}"),
                    sourceProjectileId = projectile.id,
                    ammunition = projectile.ammunition,
                    sourceTeam = projectile.firedBy,
                    center = projectile.position,
                    radius = behavior.radius,
                    remainingSeconds = behavior.durationSeconds,
                    tickIntervalSeconds = behavior.tickIntervalSeconds,
                    untilNextTickSeconds = behavior.tickIntervalSeconds,
                    status = behavior.status,
                    driftsWithWind = behavior.driftsWithWind,
                )
            }
            is com.urkaaaz.domain.AmmunitionImpactBehavior.Explosion -> {
                emit {
                    MatchEvent.ExplosionTriggered(
                        it,
                        matchId,
                        simulationTimeMilliseconds,
                        projectile.id,
                        projectile.position.x,
                        projectile.position.y,
                        behavior.radius,
                        definition.damage.fortressDamage,
                    )
                }
                applyDamageAt(
                    position = projectile.position,
                    radius = behavior.radius,
                    damage = definition.damage.fortressDamage,
                    sourceTeam = projectile.firedBy,
                    unitDamage = definition.damage.unitDamage,
                    hitLayer = hitLayer,
                    sourceProjectileId = projectile.id,
                    minimumDamage = definition.damage.minimumDamage,
                    friendlyFire = definition.collision.friendlyFire,
                )
            }
            com.urkaaaz.domain.AmmunitionImpactBehavior.DirectImpact -> {
                applyDamageAt(
                    position = projectile.position,
                    radius = definition.damage.fortressRadius,
                    damage = definition.damage.fortressDamage,
                    sourceTeam = projectile.firedBy,
                    unitDamage = definition.damage.unitDamage,
                    hitLayer = hitLayer,
                    sourceProjectileId = projectile.id,
                    minimumDamage = definition.damage.minimumDamage,
                    friendlyFire = definition.collision.friendlyFire,
                )
            }
        }
        finishIfFortressDestroyed()
    }

    private fun spawnSubprojectiles(
        parent: Projectile,
        behavior: com.urkaaaz.domain.AmmunitionImpactBehavior.Fragmentation,
    ) {
        val centerIndex = (behavior.fragmentCount - 1) / 2f
        repeat(behavior.fragmentCount) { index ->
            val offset = (index - centerIndex) * behavior.spread
            pendingProjectiles += parent.copy(
                id = EntityId("${parent.id.value}-fragment-$index"),
                position = parent.position.copy(x = parent.position.x + offset),
                velocity = Velocity(
                    x = parent.velocity.x * 0.35f + offset * 1.8f,
                    y = parent.velocity.y * 0.35f - kotlin.math.abs(offset) * 0.4f,
                ),
                ageSeconds = 0f,
                parentProjectileId = parent.id,
                generation = parent.generation + 1,
                mass = parent.mass,
                windResponse = parent.windResponse,
                gravityResponse = parent.gravityResponse,
                drag = parent.drag,
            )
            logAmmunition(
                reason = "subprojectile-spawned",
                projectile = pendingProjectiles.last(),
            )
        }
    }

    private fun applyDamageAt(
        position: WorldPosition,
        radius: Float,
        damage: Int,
        sourceTeam: Team? = null,
        unitDamage: Int = damage,
        status: UnitStatus? = null,
        statusDurationSeconds: Float = 3f,
        hitLayer: com.urkaaaz.domain.AmmunitionHitLayer? = null,
        sourceProjectileId: EntityId? = null,
        minimumDamage: Int = 0,
        friendlyFire: FriendlyFirePolicy = FriendlyFirePolicy.ENEMY_ONLY,
    ) {
        if (currentBlueFortress.intersects(position, radius) &&
            canHitFortress(sourceTeam, Team.BLUE, friendlyFire)
        ) {
            currentBlueFortress = currentBlueFortress.withDamage(damage)
            emitDamage(
                targetId = currentBlueFortress.id,
                amount = damage,
                position = position,
                sourceProjectileId = sourceProjectileId,
                targetLayer = com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS,
            )
        }
        if (currentRedFortress.intersects(position, radius) &&
            canHitFortress(sourceTeam, Team.RED, friendlyFire)
        ) {
            currentRedFortress = currentRedFortress.withDamage(damage)
            emitDamage(
                targetId = currentRedFortress.id,
                amount = damage,
                position = position,
                sourceProjectileId = sourceProjectileId,
                targetLayer = com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS,
            )
        }
        if (unitDamage > 0) {
            units = units.map { unit ->
                val distance = kotlin.math.hypot(
                    unit.position.x - position.x,
                    unit.position.y - position.y,
                )
                if (unit.isAlive &&
                    distance <= radius &&
                    canHitUnit(sourceTeam, unit.team, friendlyFire)
                ) {
                    val distanceFactor = if (radius <= 0f) 1f else {
                        (1f - distance / radius).coerceIn(0f, 1f)
                    }
                    val scaledDamage = if (distanceFactor >= 1f) {
                        unitDamage
                    } else {
                        maxOf(
                            minimumDamage,
                            kotlin.math.round(unitDamage * distanceFactor).toInt(),
                        )
                    }
                    unit.withDamage(scaledDamage).let {
                        if (status == null ||
                            status in unit.type.statusImmunities ||
                            (unit.type.statusResistance[status] ?: 1f) <= 0f
                        ) {
                            it
                        } else {
                            val resistance = unit.type.statusResistance[status] ?: 1f
                            it.withStatus(
                                status = status,
                                durationSeconds = statusDurationSeconds * resistance,
                                strength = (1f * resistance).coerceIn(0f, 1f),
                            )
                        }
                    }.also {
                        emitDamage(
                            targetId = it.id,
                            amount = scaledDamage,
                            position = position,
                            sourceProjectileId = sourceProjectileId,
                            targetLayer = com.urkaaaz.domain.AmmunitionHitLayer.UNIT,
                        )
                    }
                } else {
                    unit
                }
            }
        }
        logAmmunition(
            reason = "damage-area",
            ammunitionType = null,
            x = position.x,
            y = position.y,
            amount = damage,
            radius = radius,
        )
    }

    private fun advanceLingeringEffects(deltaSeconds: Float) {
        if (lingeringEffects.isEmpty()) return
        val updated = mutableListOf<LingeringEffect>()
        lingeringEffects.forEach { effect ->
            var remaining = effect.remainingSeconds - deltaSeconds
            var untilNextTick = effect.untilNextTickSeconds - deltaSeconds
            while (remaining > 0f && untilNextTick <= 0f) {
                applyDamageAt(
                    position = effect.currentCenter,
                    radius = effect.radius,
                    damage = AmmunitionCatalog
                        .definition(effect.ammunition)
                        .damage
                        .fortressDamage / 2,
                    sourceTeam = effect.sourceTeam,
                    unitDamage = AmmunitionCatalog
                        .definition(effect.ammunition)
                        .behavior
                        .let { behavior ->
                            (behavior as? com.urkaaaz.domain.AmmunitionImpactBehavior.LingeringArea)
                                ?.damagePerTick
                                ?: 0
                        },
                    status = (
                        AmmunitionCatalog.definition(effect.ammunition).behavior as?
                            com.urkaaaz.domain.AmmunitionImpactBehavior.LingeringArea
                    )?.status,
                    statusDurationSeconds = (
                        AmmunitionCatalog.definition(effect.ammunition).behavior as?
                            com.urkaaaz.domain.AmmunitionImpactBehavior.LingeringArea
                    )?.durationSeconds ?: 3f,
                    sourceProjectileId = effect.sourceProjectileId,
                    friendlyFire = AmmunitionCatalog
                        .definition(effect.ammunition)
                        .collision
                        .friendlyFire,
                    minimumDamage = AmmunitionCatalog
                        .definition(effect.ammunition)
                        .damage
                        .minimumDamage,
                )
                untilNextTick += effect.tickIntervalSeconds
            }
            if (remaining > 0f) {
                val nextCenter = if (effect.driftsWithWind) {
                    effect.currentCenter.copy(
                        x = effect.currentCenter.x +
                            wind.accelerationX() * deltaSeconds,
                    )
                } else {
                    effect.currentCenter
                }
                updated += effect.copy(
                    currentCenter = nextCenter,
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

    private fun canHitFortress(
        sourceTeam: Team?,
        targetTeam: Team,
        friendlyFire: FriendlyFirePolicy,
    ): Boolean = sourceTeam == null ||
        friendlyFire == FriendlyFirePolicy.ALL_TARGETS ||
        targetTeam != sourceTeam

    private fun canHitUnit(
        sourceTeam: Team?,
        targetTeam: Team,
        friendlyFire: FriendlyFirePolicy,
    ): Boolean = sourceTeam == null ||
        friendlyFire != FriendlyFirePolicy.ENEMY_ONLY ||
        targetTeam != sourceTeam

    private fun targetHitBy(projectile: Projectile): EntityTarget? {
        val definition = AmmunitionCatalog.definition(projectile.ammunition)
        val targetUnit = if (com.urkaaaz.domain.AmmunitionHitLayer.UNIT in definition.collision.hitLayers) {
            if (definition.collision.mode ==
                com.urkaaaz.domain.AmmunitionCollisionMode.IGNORE_UNITS
            ) {
                null
            } else {
            units.asSequence()
                .filter { unit ->
                    unit.isAlive &&
                        canHitUnit(
                            sourceTeam = projectile.firedBy,
                            targetTeam = unit.team,
                            friendlyFire = definition.collision.friendlyFire,
                        ) &&
                        kotlin.math.hypot(
                            unit.position.x - projectile.position.x,
                            unit.position.y - projectile.position.y,
                        ) <= definition.collision.hitRadius
                }
                .minByOrNull { unit ->
                    kotlin.math.hypot(
                        unit.position.x - projectile.position.x,
                        unit.position.y - projectile.position.y,
                    )
                }
            }
        } else {
            null
        }
        if (targetUnit != null) {
            return EntityTarget(
                id = targetUnit.id,
                layer = com.urkaaaz.domain.AmmunitionHitLayer.UNIT,
                stopsProjectile = definition.collision.mode !=
                    com.urkaaaz.domain.AmmunitionCollisionMode.PIERCE_UNITS,
            )
        }
        if (com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS !in definition.collision.hitLayers) {
            return null
        }
        val fortressCandidates = listOf(currentBlueFortress, currentRedFortress)
            .filter { fortress ->
                canHitFortress(
                    sourceTeam = projectile.firedBy,
                    targetTeam = fortress.team,
                    friendlyFire = definition.collision.friendlyFire,
                )
            }
        val target = fortressCandidates.firstOrNull { fortress ->
            fortress.intersects(
                projectile.position,
                maxOf(config.fortressCollisionRadius, definition.collision.hitRadius),
            )
        }
        return target?.let {
            EntityTarget(
                it.id,
                com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS,
                stopsProjectile = true,
            )
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

    private fun emitDamage(
        targetId: EntityId,
        amount: Int,
        position: WorldPosition? = null,
        sourceProjectileId: EntityId? = null,
        targetLayer: com.urkaaaz.domain.AmmunitionHitLayer =
            com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS,
    ) {
        emit {
            MatchEvent.DamageApplied(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                targetId = targetId,
                amount = amount,
                ammunitionType = "AMMUNITION",
                x = position?.x ?: 0f,
                y = position?.y ?: 0f,
                targetLayer = targetLayer.name,
                sourceProjectileId = sourceProjectileId,
            )
        }
        logAmmunition(
            reason = "damage-applied",
            targetId = targetId,
            x = position?.x,
            y = position?.y,
            amount = amount,
        )
    }

    private fun logAmmunition(
        reason: String,
        projectile: Projectile? = null,
        ammunitionType: String? = projectile?.ammunition?.name,
        team: Team? = projectile?.firedBy,
        x: Float? = projectile?.position?.x,
        y: Float? = projectile?.position?.y,
        targetId: EntityId? = null,
        amount: Int? = null,
        radius: Float? = null,
        remainingReloadSeconds: Float? = null,
    ) {
        ammunitionLogSink.log(
            AmmunitionLogRecord(
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                reason = reason,
                projectileId = projectile?.id,
                ammunitionType = ammunitionType,
                team = team,
                x = x,
                y = y,
                targetId = targetId,
                amount = amount,
                radius = radius,
                remainingReloadSeconds = remainingReloadSeconds,
            ),
        )
    }

    private fun initialWallet(): ResourceWallet = ResourceWallet(
        gold = 100,
        supply = MAX_SUPPLY,
        ammunition = AmmunitionType.entries
            .filter { !it.unlimited }
            .associateWith { config.startingLimitedAmmunition },
    )

    private data class LingeringEffect(
        val id: EntityId,
        val sourceProjectileId: EntityId,
        val ammunition: AmmunitionType,
        val sourceTeam: Team,
        val center: WorldPosition,
        val currentCenter: WorldPosition = center,
        val radius: Float,
        val remainingSeconds: Float,
        val tickIntervalSeconds: Float,
        val untilNextTickSeconds: Float,
        val status: UnitStatus?,
        val driftsWithWind: Boolean,
    )

    private data class DefeatedUnit(
        val unit: Unit,
        val remainingSeconds: Float,
    )

    private data class EntityTarget(
        val id: EntityId,
        val layer: com.urkaaaz.domain.AmmunitionHitLayer,
        val stopsProjectile: Boolean,
    )

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
        private const val DEATH_ANIMATION_SECONDS = 0.8f
        private const val SAPPER_CONTACT_SPEED_MULTIPLIER = 0.45f
        private const val ALLIED_UNIT_MIN_SEPARATION = 16f
        private const val ATTACK_FORMATION_SPACING = 42f

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
