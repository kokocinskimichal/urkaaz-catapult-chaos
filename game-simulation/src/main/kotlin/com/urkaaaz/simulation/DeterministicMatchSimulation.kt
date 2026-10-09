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
    private var sequence = 0L
    private var terrain = TerrainState(config.bounds.height * 0.78f)
    private var projectiles = emptyList<Projectile>()
    private var currentBlueFortress = blueFortress
    private var currentRedFortress = redFortress
    private var currentBlueCatapult = blueCatapult
    private var currentRedCatapult = redCatapult
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

    fun advance(deltaMilliseconds: Long): List<MatchEvent> {
        require(deltaMilliseconds >= 0) { "deltaMilliseconds must not be negative" }
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val deltaSeconds = deltaMilliseconds / 1_000f
        reloadRemainingSeconds.keys.forEach { team ->
            reloadRemainingSeconds[team] = (reloadRemainingSeconds.getValue(team) - deltaSeconds)
                .coerceAtLeast(0f)
        }
        simulationTimeMilliseconds += deltaMilliseconds
        val resolved = mutableListOf<Projectile>()
        projectiles.forEach { projectile ->
            val advanced = projectile.advance(
                deltaSeconds = deltaSeconds,
                windAccelerationX = config.windAccelerationX,
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
        terrain = TerrainSnapshot(terrain.revision),
        wind = WindSnapshot(direction = if (config.windAccelerationX < 0f) -1f else 1f, strength = kotlin.math.abs(config.windAccelerationX)),
        outcome = outcome(),
        resourcesByTeam = resources.mapValues { (_, wallet) ->
            ResourceSnapshot(
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
        if (position.distanceTo(currentBlueFortress.center) <= radius) {
            currentBlueFortress = currentBlueFortress.withDamage(damage)
            emitDamage(currentBlueFortress.id, damage)
        }
        if (position.distanceTo(currentRedFortress.center) <= radius) {
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
        return if (projectile.position.distanceTo(target.center) <= config.fortressCollisionRadius) {
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

    private fun emitDamage(targetId: EntityId, amount: Int) {
        emit {
            MatchEvent.DamageApplied(it, matchId, simulationTimeMilliseconds, targetId, amount)
        }
    }

    private fun initialWallet(): ResourceWallet = ResourceWallet(
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
