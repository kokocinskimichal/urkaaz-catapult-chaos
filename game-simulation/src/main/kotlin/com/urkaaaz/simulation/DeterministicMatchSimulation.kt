package com.urkaaaz.simulation

import com.urkaaaz.contracts.CatapultSnapshot
import com.urkaaaz.contracts.CatapultPresentationContract
import com.urkaaaz.contracts.ActiveSpellSnapshot
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.FortressHitboxProfile
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.FortressShieldGeometry
import com.urkaaaz.contracts.RunicBastionGeometry
import com.urkaaaz.contracts.RunicBastionAnimationTiming
import com.urkaaaz.contracts.RunicBastionSnapshot
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchError
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchPhase
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerId
import com.urkaaaz.contracts.ProjectileSnapshot
import com.urkaaaz.contracts.ProjectileResolution
import com.urkaaaz.contracts.ProjectileTargetKind
import com.urkaaaz.contracts.ResourceSnapshot
import com.urkaaaz.contracts.EffectSnapshot
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.UnitHitboxProfile
import com.urkaaaz.contracts.WorldScale
import com.urkaaaz.contracts.TerrainSnapshot
import com.urkaaaz.contracts.WindSnapshot
import com.urkaaaz.domain.Aim
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.AmmunitionCatalog
import com.urkaaaz.domain.AmmunitionImpactBehavior
import com.urkaaaz.domain.Catapult
import com.urkaaaz.domain.Fortress
import com.urkaaaz.domain.RunicBastion
import com.urkaaaz.domain.Projectile
import com.urkaaaz.domain.Velocity
import com.urkaaaz.domain.WorldBounds
import com.urkaaaz.domain.WorldPosition
import com.urkaaaz.domain.ResourceWallet
import com.urkaaaz.domain.SpellCatalog
import com.urkaaaz.domain.SpellEffectDefinition
import com.urkaaaz.domain.SpellType
import com.urkaaaz.domain.SupplyConfiguration
import com.urkaaaz.domain.Unit
import com.urkaaaz.domain.UnitConfigurations
import com.urkaaaz.domain.DemolisherUnit
import com.urkaaaz.domain.SapperUnit
import com.urkaaaz.domain.UnitDefinition
import com.urkaaaz.domain.AttackType
import com.urkaaaz.domain.AttackGroupId
import com.urkaaaz.domain.UnitActionState
import com.urkaaaz.domain.UnitStatus
import com.urkaaaz.domain.FriendlyFirePolicy
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private data class RunicBastionDestructionBlocker(
    val bastion: RunicBastion,
    val remainingMilliseconds: Long,
    val heldUnitPositions: Map<EntityId, Float>,
)

/**
 * Deterministic projectile-and-impact simulation.
 *
 * It accepts no Android or rendering objects. Every state transition is driven
 * by an explicit time step and produces inspectable events and a snapshot.
 */
class DeterministicMatchSimulation(
    private val matchId: MatchId,
    private val config: SimulationConfig,
    blueCatapult: Catapult? = null,
    redCatapult: Catapult? = null,
    private val blueFortress: Fortress = defaultFortress(Team.BLUE, config.bounds, config),
    private val redFortress: Fortress = defaultFortress(Team.RED, config.bounds, config),
    private val combatLogSink: CombatLogSink = NoOpCombatLogSink,
    private val ammunitionLogSink: AmmunitionLogSink = NoOpAmmunitionLogSink,
) {
    private val fortressCenterMargin = fortressMarginFor(config.bounds.width)
    private val worldPhysicsScale =
        (config.bounds.width / BASE_PHYSICS_WORLD_WIDTH).coerceAtLeast(1f)
    private var status = MatchStatus.NOT_STARTED
    private var phase = MatchPhase.CREATED
    private var activeTeam = Team.BLUE
    private var finalOutcome: MatchOutcome? = null
    private var simulationTimeMilliseconds = 0L
    private var remainingMatchTimeMilliseconds = if (config.bossEncounter != null) {
        config.bossEncounterMatchDurationMilliseconds
    } else {
        config.matchDurationMilliseconds
    }
    private var sequence = 0L
    private var terrain = (config.mapDefinition?.let(TerrainState::fromMapDefinition)
        ?: TerrainState.legacyProfile(
            baselineHeight = config.bounds.height * 0.78f,
            worldWidth = config.bounds.width,
            worldHeight = config.bounds.height,
        ).flattenFoundation(fortressCenterMargin, FORTRESS_FLAT_HALF_WIDTH)
            .flattenFoundation(
                config.bounds.width - fortressCenterMargin,
                FORTRESS_FLAT_HALF_WIDTH,
            )

            .protectRange(fortressCenterMargin, FORTRESS_FLAT_HALF_WIDTH)
            .protectRange(
                config.bounds.width - fortressCenterMargin,
                FORTRESS_FLAT_HALF_WIDTH,
            )).let { battlefieldTerrain ->
        val encounter = config.bossEncounter
        val profile = config.bossProfile
        if (
            encounter != null &&
            profile?.archetype == BossArchetype.CENTRAL_OBJECTIVE
        ) {
            val centerX = config.bounds.width * encounter.spawnXRatio
            val halfWidth = profile.collision.width * 0.58f
            val foundationY = battlefieldTerrain.heightAt(centerX)
            battlefieldTerrain
                .flattenFoundation(centerX, halfWidth, foundationY)
                .protectRange(centerX, halfWidth)
        } else {
            battlefieldTerrain
        }
    }
    private val bossSystem = config.bossEncounter?.let { encounter ->
        BossSystem(
            profile = requireNotNull(config.bossProfile),
            encounter = encounter,
            worldWidth = config.bounds.width,
            groundHeightAt = terrain::heightAt,
        )
    }
    private var projectiles = emptyList<Projectile>()
    private var currentBlueFortress = blueFortress
    private var currentRedFortress = redFortress
    private var currentBlueRunicBastion: RunicBastion? = null
    private var currentRedRunicBastion: RunicBastion? = null
    private val runicBastionDestructionBlockers =
        linkedMapOf<EntityId, RunicBastionDestructionBlocker>()
    private var currentBlueCatapult = blueCatapult
        ?: defaultCatapult(Team.BLUE, config.bounds, config, terrain)
    private var currentRedCatapult = redCatapult
        ?: defaultCatapult(Team.RED, config.bounds, config, terrain)
    private var units = emptyList<Unit>()
    private var defeatedUnits = emptyList<DefeatedUnit>()
    private val movingTeams = mutableSetOf<Team>()
    private val hasteRemainingMillisecondsByTeam = mutableMapOf<Team, Long>()
    private val supplyRegenProgressSeconds = mutableMapOf(
        Team.BLUE to 0f,
        Team.RED to 0f,
    )
    private val manaRegenProgressMilliseconds = mutableMapOf(
        Team.BLUE to 0L,
        Team.RED to 0L,
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
    private val combatRandom = Random(config.combatRandomSeed)
    private val combatLuckByUnit = mutableMapOf<EntityId, Float>()

    fun start(): List<MatchEvent> {
        check(status == MatchStatus.NOT_STARTED) { "match has already started" }
        status = MatchStatus.RUNNING
        phase = MatchPhase.PLAYER_TURN
        emit {
            MatchEvent.MatchStarted(
                it,
                matchId,
                simulationTimeMilliseconds,
                config.combatRandomSeed,
                config.balanceRevision,
                config.balanceContentHash,
            )
        }
        bossSystem?.snapshot()?.let { boss ->
            emit {
                MatchEvent.BossSpawned(
                    eventId = it,
                    matchId = matchId,
                    simulationTimeMilliseconds = simulationTimeMilliseconds,
                    bossId = boss.entityId,
                    profileId = boss.profileId,
                    archetype = boss.archetype,
                    x = boss.x,
                    groundY = boss.groundY,
                )
            }
        }
        emit { MatchEvent.TurnChanged(it, matchId, simulationTimeMilliseconds, activeTeam) }
        logCombatState("match-started")
        return drainEvents()
    }

    fun surrender(surrenderingTeam: Team): List<MatchEvent> {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        finish(
            if (surrenderingTeam == Team.BLUE) {
                MatchOutcome.SURRENDER
            } else {
                MatchOutcome.BLUE_WIN
            },
        )
        return drainEvents()
    }

    private fun shieldImpactBy(
        start: WorldPosition,
        end: WorldPosition,
        projectileTeam: Team,
    ): ShieldImpact? {
        val targetFortress = fortress(opposingTeam(projectileTeam))
        if (targetFortress.shield == null) return null
        val center = targetFortress.center
        val radiusX = FortressShieldGeometry.horizontalRadius(WorldScale.FORTRESS_WIDTH)
        val radiusY = FortressShieldGeometry.verticalRadius(WorldScale.FORTRESS_HEIGHT)
        val fraction = segmentCircleIntersectionFraction(
            start = WorldPosition(
                x = (start.x - center.x) / radiusX,
                y = (start.y - center.y) / radiusY,
            ),
            end = WorldPosition(
                x = (end.x - center.x) / radiusX,
                y = (end.y - center.y) / radiusY,
            ),
            center = WorldPosition(0f, 0f),
            radius = 1f,
        ) ?: return null
        return ShieldImpact(
            fortress = targetFortress,
            position = WorldPosition(
                x = start.x + (end.x - start.x) * fraction,
                y = start.y + (end.y - start.y) * fraction,
            ),
        )
    }

    private fun segmentCircleIntersectionFraction(
        start: WorldPosition,
        end: WorldPosition,
        center: WorldPosition,
        radius: Float,
    ): Float? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val offsetX = start.x - center.x
        val offsetY = start.y - center.y
        val a = dx * dx + dy * dy
        val c = offsetX * offsetX + offsetY * offsetY - radius * radius
        if (c <= 0f) return 0f
        if (a == 0f) return null
        val b = 2f * (offsetX * dx + offsetY * dy)
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null
        val root = sqrt(discriminant)
        val first = (-b - root) / (2f * a)
        val second = (-b + root) / (2f * a)
        return when {
            first in 0f..1f -> first
            second in 0f..1f -> second
            else -> null
        }
    }

    private fun resolveShieldImpact(projectile: Projectile, target: Fortress) {
        phase = MatchPhase.RESOLVING_IMPACT
        val damage = AmmunitionCatalog.definition(projectile.ammunition).damage.fortressDamage
        val remainingHealth = (requireNotNull(target.shield).health.current - damage)
            .coerceAtLeast(0)
        setFortress(target.withShieldDamage(damage))
        emit {
            MatchEvent.ProjectileHitEntity(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                projectileId = projectile.id,
                targetId = target.id,
                ammunitionType = projectile.ammunition.name,
                targetLayer = "FORTRESS_SHIELD",
                x = projectile.position.x,
                y = projectile.position.y,
                targetTeam = target.team,
                targetKind = ProjectileTargetKind.FORTRESS_SHIELD,
            )
        }
        emit {
            MatchEvent.ShieldDamaged(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                fortressId = target.id,
                projectileId = projectile.id,
                amount = damage,
                remainingHealth = remainingHealth,
                x = projectile.position.x,
                y = projectile.position.y,
            )
        }
        logAmmunition(
            reason = "projectile-hit-fortress-shield",
            projectile = projectile,
            targetId = target.id,
        )
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
        val speed =
            catapult.aim.power *
                ammunitionDefinition.flight.initialSpeedMultiplier *
                worldPhysicsScale
        val direction = if (catapult.team == Team.BLUE) 1f else -1f
        val projectile = Projectile(
            id = EntityId("projectile-${sequence + 1}"),
            ammunition = ammunition,
            firedBy = catapult.team,
            position = catapult.anchorPosition,
            velocity = Velocity(
                x = kotlin.math.cos(radians).toFloat() * speed * direction,
                y = -kotlin.math.sin(radians).toFloat() * speed,
            ),
            mass = ammunitionDefinition.flight.mass,
            windResponse = ammunitionDefinition.flight.windResponse,
            gravityResponse = ammunitionDefinition.flight.gravityResponse,
            drag = ammunitionDefinition.flight.drag,
            apexFragmentation = ammunitionDefinition.flight.apexFragmentation,
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
                shooterTeam = catapult.team,
            )
        }
        logAmmunition(
            reason = "projectile-fired",
            projectile = projectile,
            remainingReloadSeconds = ammunitionDefinition.reloadSeconds,
        )
        return drainEvents()
    }

    fun beginAmmunitionReload(team: Team, ammunition: AmmunitionType) {
        check(status == MatchStatus.RUNNING) { "match is not running" }
        reloadRemainingSecondsByAmmunition
            .getValue(team)[ammunition] = AmmunitionCatalog.definition(ammunition).reloadSeconds
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
        val firstGarrisonSlot = garrisonFirstSlotFromScreenEdge(config.bounds.width)
        val spawnX = if (team == Team.BLUE) {
            firstGarrisonSlot
        } else {
            config.bounds.width - firstGarrisonSlot
        }
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
                team = team,
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

    fun castFortressShield(
        playerId: PlayerId,
        team: Team,
        targetId: EntityId?,
    ): MatchError? {
        when (status) {
            MatchStatus.RUNNING -> Unit
            MatchStatus.FINISHED -> return MatchError.ALREADY_FINISHED
            else -> return MatchError.MATCH_NOT_RUNNING
        }
        val definition = SpellCatalog.definition(SpellType.FORTRESS_SHIELD)
        val effect = definition.effect as? SpellEffectDefinition.FortressShield
            ?: return MatchError.SPELL_NOT_IMPLEMENTED
        val targetFortress = fortress(team)
        if (targetId != null && targetId != targetFortress.id) {
            return MatchError.INVALID_SPELL_TARGET
        }
        if (targetFortress.shield != null) return MatchError.SPELL_ALREADY_ACTIVE
        val wallet = resources.getValue(team)
        val manaCost = definition.manaCost
        if (!wallet.canSpendMana(manaCost)) return MatchError.INSUFFICIENT_RESOURCE

        resources[team] = wallet.spendMana(manaCost)
        setFortress(targetFortress.withShield(effect.hitPoints))
        emit {
            MatchEvent.SpellCast(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                playerId = playerId,
                spellType = definition.type.name,
                targetId = targetFortress.id,
                manaCost = manaCost,
            )
        }
        return null
    }

    fun castRunicBastion(
        playerId: PlayerId,
        team: Team,
        targetId: EntityId?,
    ): MatchError? {
        when (status) {
            MatchStatus.RUNNING -> Unit
            MatchStatus.FINISHED -> return MatchError.ALREADY_FINISHED
            else -> return MatchError.MATCH_NOT_RUNNING
        }
        val definition = SpellCatalog.definition(SpellType.RUNIC_BASTION)
        val effect = definition.effect as? SpellEffectDefinition.RunicBastion
            ?: return MatchError.SPELL_NOT_IMPLEMENTED
        if (targetId != null) return MatchError.INVALID_SPELL_TARGET
        if (bastion(team) != null) return MatchError.SPELL_ALREADY_ACTIVE
        val wallet = resources.getValue(team)
        if (!wallet.canSpendMana(definition.manaCost)) {
            return MatchError.INSUFFICIENT_RESOURCE
        }

        val frontDirection = if (team == Team.BLUE) 1f else -1f
        val x = fortressAttackX(team) +
            frontDirection * RunicBastionGeometry.FRONT_OFFSET_FROM_GATE
        val foundationY = terrain.heightAt(x)
        val deployed = RunicBastion(
            id = EntityId("${team.name.lowercase()}-runic-bastion-${sequence + 1}"),
            team = team,
            center = WorldPosition(x, RunicBastionGeometry.centerY(foundationY)),
            foundationY = foundationY,
            maxHealth = effect.hitPoints,
        )
        resources[team] = wallet.spendMana(definition.manaCost)
        setBastion(deployed)
        emit {
            MatchEvent.SpellCast(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                playerId = playerId,
                spellType = definition.type.name,
                targetId = deployed.id,
                manaCost = definition.manaCost,
            )
        }
        return null
    }

    fun castHaste(
        playerId: PlayerId,
        team: Team,
        targetId: EntityId?,
    ): MatchError? {
        when (status) {
            MatchStatus.RUNNING -> Unit
            MatchStatus.FINISHED -> return MatchError.ALREADY_FINISHED
            else -> return MatchError.MATCH_NOT_RUNNING
        }
        val definition = SpellCatalog.definition(SpellType.HASTE)
        val effect = definition.effect as? SpellEffectDefinition.Haste
            ?: return MatchError.SPELL_NOT_IMPLEMENTED
        if (targetId != null) return MatchError.INVALID_SPELL_TARGET
        val wallet = resources.getValue(team)
        if (!wallet.canSpendMana(definition.manaCost)) {
            return MatchError.INSUFFICIENT_RESOURCE
        }

        resources[team] = wallet.spendMana(definition.manaCost)
        hasteRemainingMillisecondsByTeam[team] = effect.durationMilliseconds
        emit {
            MatchEvent.SpellCast(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                playerId = playerId,
                spellType = definition.type.name,
                manaCost = definition.manaCost,
            )
        }
        return null
    }

    fun advance(deltaMilliseconds: Long): List<MatchEvent> {
        require(deltaMilliseconds >= 0) { "deltaMilliseconds must not be negative" }
        check(status == MatchStatus.RUNNING) { "match is not running" }
        val deltaSeconds = deltaMilliseconds / 1_000f
        wind.update(deltaSeconds)
        replenishSupply(deltaSeconds)
        replenishMana(deltaMilliseconds)
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
        val bossTick = advanceBoss(deltaSeconds)
        advanceUnits(deltaSeconds, deltaMilliseconds)
        // Keep each blocker active for the whole movement step; deaths in the
        // interaction and projectile phases below start with the full duration.
        advanceRunicBastionDestructionBlockers(deltaMilliseconds)
        advanceHasteTimers(deltaMilliseconds)
        resolveUnitInteractions(deltaSeconds)
        applyBossTick(bossTick)
        finishIfFortressDestroyed()
        val resolved = mutableListOf<Projectile>()
        projectiles.forEach { projectile ->
            val apexTime = timeToApex(projectile, deltaSeconds)
            if (apexTime == null) {
                fragmentAtApex(projectile).forEach { fragment ->
                    advanceProjectile(fragment, deltaSeconds)?.let(resolved::add)
                }
            } else {
                val atApex = advanceProjectile(projectile, apexTime)
                if (atApex != null) {
                    val splitProjectile = atApex.copy(
                        velocity = atApex.velocity.copy(y = 0f),
                    )
                    val remainingSeconds = deltaSeconds - apexTime
                    fragmentAtApex(splitProjectile).forEach { fragment ->
                        advanceProjectile(fragment, remainingSeconds)?.let(resolved::add)
                    }
                }
            }
        }
        projectiles = resolved
        advanceLingeringEffects(deltaSeconds)
        logCombatState("tick")
        if (projectiles.isEmpty() && status == MatchStatus.RUNNING) {
            phase = MatchPhase.PLAYER_TURN
        }
        return drainEvents()
    }

    private fun timeToApex(projectile: Projectile, deltaSeconds: Float): Float? {
        if (
            projectile.apexFragmentation == null ||
            projectile.hasFragmented ||
            projectile.velocity.y >= 0f ||
            deltaSeconds <= 0f
        ) {
            return null
        }

        val gravityAcceleration = config.gravityAccelerationY *
            worldPhysicsScale *
            projectile.gravityResponse
        if (gravityAcceleration <= 0f) return null

        fun verticalVelocityAfter(seconds: Float): Float = projectile.advance(
            deltaSeconds = seconds,
            windAccelerationX = wind.accelerationX(),
            gravityAccelerationY = config.gravityAccelerationY * worldPhysicsScale,
            windResponse = projectile.windResponse,
            gravityResponse = projectile.gravityResponse,
            drag = projectile.drag,
        ).velocity.y

        if (verticalVelocityAfter(deltaSeconds) < 0f) return null

        var ascendingSeconds = 0f
        var descendingSeconds = deltaSeconds
        repeat(APEX_TIME_SEARCH_ITERATIONS) {
            val midpoint = (ascendingSeconds + descendingSeconds) * 0.5f
            if (verticalVelocityAfter(midpoint) >= 0f) {
                descendingSeconds = midpoint
            } else {
                ascendingSeconds = midpoint
            }
        }
        return descendingSeconds
    }

    private fun advanceProjectile(
        projectile: Projectile,
        deltaSeconds: Float,
    ): Projectile? {
        val advanced = projectile.advance(
            deltaSeconds = deltaSeconds,
            windAccelerationX = wind.accelerationX(),
            gravityAccelerationY = config.gravityAccelerationY * worldPhysicsScale,
            windResponse = projectile.windResponse,
            gravityResponse = projectile.gravityResponse,
            drag = projectile.drag,
        )
        val terrainImpact = terrainImpactPosition(
            previous = projectile,
            deltaSeconds = deltaSeconds,
            windAccelerationX = wind.accelerationX() * projectile.windResponse,
            gravityAccelerationY = config.gravityAccelerationY *
                worldPhysicsScale *
                projectile.gravityResponse,
        )
        val impactProjectile = terrainImpact?.let {
            advanced.copy(position = it)
        } ?: advanced
        val shieldImpact = shieldImpactBy(projectile.position, impactProjectile.position, projectile.firedBy)
        if (shieldImpact != null) {
            resolveShieldImpact(
                impactProjectile.copy(position = shieldImpact.position),
                shieldImpact.fortress,
            )
            emitProjectileResolved(projectile.id, ProjectileResolution.SHIELD_IMPACT)
            return null
        }
        val target = targetHitBy(impactProjectile)
        return when {
            target != null -> {
                resolveEntityImpact(impactProjectile, target)
                if (target.stopsProjectile) {
                    emitProjectileResolved(projectile.id, ProjectileResolution.ENTITY_IMPACT)
                    null
                } else {
                    impactProjectile
                }
            }
            terrainImpact != null -> {
                resolveTerrainImpact(impactProjectile)
                emitProjectileResolved(projectile.id, ProjectileResolution.TERRAIN_IMPACT)
                null
            }
            outsideBounds(advanced.position) -> {
                emitProjectileResolved(projectile.id, ProjectileResolution.OUT_OF_BOUNDS)
                null
            }
            advanced.ageSeconds >= AmmunitionCatalog
                .definition(advanced.ammunition)
                .flight
                .maximumLifetimeSeconds -> {
                emitProjectileResolved(projectile.id, ProjectileResolution.LIFETIME_EXPIRED)
                null
            }
            else -> advanced
        }
    }

    private fun fragmentAtApex(projectile: Projectile): List<Projectile> {
        val profile = projectile.apexFragmentation
            ?: return listOf(projectile)
        if (projectile.hasFragmented || projectile.velocity.y < 0f) {
            return listOf(projectile)
        }

        val referenceSpeed = kotlin.math.abs(projectile.velocity.x)
            .coerceAtLeast(profile.minimumReferenceSpeed)
        val parent = projectile.copy(
            velocity = projectile.velocity.copy(
                y = referenceSpeed * profile.initialDownwardSpeed,
            ),
            hasFragmented = true,
        )
        val fragments = (-profile.fragmentsPerSide..profile.fragmentsPerSide)
            .filter { it != 0 }
            .mapIndexed { index, offset ->
                projectile.copy(
                    id = EntityId("${projectile.id.value}-fragment-${index + 1}"),
                    velocity = Velocity(
                        x = projectile.velocity.x +
                            offset * referenceSpeed * profile.horizontalSpreadPerStep,
                        y = referenceSpeed * (
                            profile.initialDownwardSpeed +
                                kotlin.math.abs(offset) * profile.downwardSpreadPerStep
                            ),
                    ),
                    ageSeconds = 0f,
                    hasFragmented = true,
                )
            }

        emit {
            MatchEvent.ProjectileSplit(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                projectileId = projectile.id,
                fragmentProjectileIds = fragments.map { fragment -> fragment.id },
                ammunitionType = projectile.ammunition.name,
                x = projectile.position.x,
                y = projectile.position.y,
            )
        }
        return listOf(parent) + fragments
    }

    /**
     * Finds the first point where the projectile crosses the terrain during
     * this simulation step. Checking only the advanced position allows a fast
     * projectile to tunnel through the sampled surface.
     */
    private fun terrainImpactPosition(
        previous: Projectile,
        deltaSeconds: Float,
        windAccelerationX: Float,
        gravityAccelerationY: Float,
    ): WorldPosition? {
        val previousPosition = previous.position
        if (previousPosition.x !in 0f..terrain.worldWidth) {
            return null
        }

        fun clearance(position: WorldPosition): Float =
            position.y - terrain.heightAt(position.x)

        fun positionAt(timeSeconds: Float): WorldPosition = WorldPosition(
            x = previousPosition.x +
                (previous.velocity.x + windAccelerationX * timeSeconds) * timeSeconds,
            y = previousPosition.y +
                (previous.velocity.y + gravityAccelerationY * timeSeconds) * timeSeconds,
        )

        var intervalStart = previousPosition
        var intervalStartClearance = clearance(intervalStart)
        var hasBeenAboveTerrain = intervalStartClearance < 0f
        if (!hasBeenAboveTerrain && previous.ageSeconds > 0f && previous.velocity.y >= 0f) {
            return previousPosition
        }

        val sampleCount = maxOf(
            1,
            kotlin.math.ceil(deltaSeconds / TERRAIN_COLLISION_SAMPLE_SECONDS).toInt(),
        )
        for (sampleIndex in 1..sampleCount) {
            val time = deltaSeconds * sampleIndex / sampleCount
            val intervalEnd = positionAt(time)
            if (intervalEnd.x !in 0f..terrain.worldWidth) break
            val intervalEndClearance = clearance(intervalEnd)
            if (!hasBeenAboveTerrain) {
                hasBeenAboveTerrain = intervalEndClearance < 0f
                intervalStart = intervalEnd
                intervalStartClearance = intervalEndClearance
                continue
            }
            if (intervalStartClearance < 0f && intervalEndClearance >= 0f) {
                var above = intervalStart
                var below = intervalEnd
                repeat(TERRAIN_COLLISION_SEARCH_ITERATIONS) {
                    val midpoint = WorldPosition(
                        x = (above.x + below.x) * 0.5f,
                        y = (above.y + below.y) * 0.5f,
                    )
                    if (clearance(midpoint) < 0f) {
                        above = midpoint
                    } else {
                        below = midpoint
                    }
                }
                return below
            }
            intervalStart = intervalEnd
            intervalStartClearance = intervalEndClearance
        }
        return null
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
                anchorX = it.anchorPosition.x,
                anchorY = it.anchorPosition.y,
                directionDegrees = it.aim.directionDegrees,
                power = it.aim.power,
            )
        },
        fortresses = listOf(currentBlueFortress, currentRedFortress).map {
            FortressSnapshot(
                entityId = it.id,
                team = it.team,
                health = it.health.current,
                maxHealth = it.maxHealth,
                x = it.center.x,
                foundationY = it.center.y + WorldScale.FORTRESS_HEIGHT / 2f,
                shieldHealth = it.shield?.health?.current,
                shieldMaxHealth = it.shield?.health?.maximum,
            )
        },
        runicBastions = listOfNotNull(
            currentBlueRunicBastion,
            currentRedRunicBastion,
        ).filter { it.isAlive }.map {
            RunicBastionSnapshot(
                entityId = it.id,
                team = it.team,
                centerX = it.center.x,
                foundationY = it.foundationY,
                health = it.health.current,
                maxHealth = it.maxHealth,
            )
        },
        units = (units + defeatedUnits.map { it.unit }).map {
            val hasteRemaining = hasteRemainingMillisecondsByTeam[it.team]
                ?.takeIf { remaining -> remaining > 0L && it.isAlive && !it.inGarrison }
            val statuses = it.statuses.mapKeys { (status, _) -> status.name }
                .mapValues { (_, state) -> state.remainingSeconds }
                .let { unitStatuses ->
                    if (hasteRemaining != null) {
                        unitStatuses + (UnitStatus.HASTED.name to hasteRemaining / 1_000f)
                    } else {
                        unitStatuses
                    }
                }
            val statusDetails = it.statuses.mapKeys { (status, _) -> status.name }
                .mapValues { (_, state) ->
                    com.urkaaaz.contracts.StatusSnapshot(
                        remainingMilliseconds = (state.remainingSeconds * 1_000f).toLong(),
                        strength = state.strength,
                    )
                }
                .let { unitStatusDetails ->
                    if (hasteRemaining != null) {
                        val effect = SpellCatalog.definition(SpellType.HASTE).effect
                            as SpellEffectDefinition.Haste
                        unitStatusDetails + (
                            UnitStatus.HASTED.name to
                                com.urkaaaz.contracts.StatusSnapshot(
                                    remainingMilliseconds = hasteRemaining,
                                    strength = effect.movementSpeedMultiplier,
                                )
                            )
                    } else {
                        unitStatusDetails
                    }
                }
            com.urkaaaz.contracts.UnitSnapshot(
                entityId = it.id,
                team = it.team,
                unitType = it.type.id,
                x = it.position.x,
                y = it.position.y,
                health = it.health.current,
                maxHealth = it.type.maxHealth,
                moving = it.moving &&
                    (!it.isAttacking() || it.combatState.action == UnitActionState.THROWING),
                attackType = it.type.attackType.name,
                actionState = it.combatState.action.name,
                targetId = it.combatState.targetId,
                attackGroupId = it.combatState.attackGroupId?.value,
                attackCycleId = it.combatState.attackCycleId,
                attackProgress = attackProgress(it),
                facingDirection = if (it.team == Team.BLUE) 1f else -1f,
                sapperHasBomb = it.sapperHasBomb,
                statuses = statuses,
                statusDetails = statusDetails,
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
                mass = it.mass,
                windResponse = it.windResponse,
                gravityResponse = it.gravityResponse,
                drag = it.drag,
                collisionRadius = AmmunitionCatalog.definition(it.ammunition).collision.hitRadius,
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
        mapDefinition = config.mapDefinition,
        wind = wind.snapshot(),
        outcome = outcome(),
        resources = ResourceSnapshot(
            gold = resources.getValue(Team.BLUE).gold,
            mana = resources.getValue(Team.BLUE).mana,
        ),
        resourcesByTeam = resources.mapValues { (_, wallet) ->
            ResourceSnapshot(
                gold = wallet.gold,
                supply = wallet.supply,
                mana = wallet.mana,
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
        activeSpells = Team.entries.mapNotNull { team ->
            hasteRemainingMillisecondsByTeam[team]?.let { remaining ->
                ActiveSpellSnapshot(
                    spellType = SpellType.HASTE.name,
                    team = team,
                    remainingMilliseconds = remaining,
                )
            }
        },
        boss = bossSystem?.snapshot(),
        balanceRevision = config.balanceRevision,
        balanceContentHash = config.balanceContentHash,
    )

    private fun advanceBoss(deltaSeconds: Float): BossTickResult {
        val boss = bossSystem ?: return BossTickResult()
        val result = boss.advance(
            deltaSeconds = deltaSeconds,
            units = units,
            movingTeams = movingTeams,
            blueGateX = currentBlueFortress.center.x,
            random = combatRandom,
        )
        if (result.phaseChanged) {
            emit {
                MatchEvent.BossPhaseChanged(
                    eventId = it,
                    matchId = matchId,
                    simulationTimeMilliseconds = simulationTimeMilliseconds,
                    bossId = boss.entityId,
                    phase = boss.snapshot().phase,
                )
            }
        }
        return result
    }

    private fun canUnitAttackBoss(unit: Unit, candidates: List<Unit>): Boolean {
        val boss = bossSystem ?: return false
        val formationOffset =
            boss.formationRank(unit, candidates, movingTeams) * ATTACK_FORMATION_SPACING
        return boss.canUnitAttack(unit, movingTeams, formationOffset.toFloat())
    }

    private fun applyBossTick(result: BossTickResult) {
        if (status != MatchStatus.RUNNING) return
        val boss = bossSystem ?: return
        result.unitAttackStarted?.let { targetTeam ->
            emit {
                MatchEvent.BossUnitAttackStarted(
                    eventId = it,
                    matchId = matchId,
                    simulationTimeMilliseconds = simulationTimeMilliseconds,
                    bossId = boss.entityId,
                    targetTeam = targetTeam,
                )
            }
        }
        val current = units.toMutableList()
        result.unitDamage.entries.sortedBy { it.key.value }.forEach { (unitId, damage) ->
            val target = current.firstOrNull { it.id == unitId && it.isAlive }
                ?: return@forEach
            replaceUnit(current, unitId, target.withDamage(damage))
            emit {
                MatchEvent.BossAttackHit(
                    eventId = it,
                    matchId = matchId,
                    simulationTimeMilliseconds = simulationTimeMilliseconds,
                    bossId = boss.entityId,
                    targetId = unitId,
                    amount = damage,
                )
            }
            emitDamage(
                targetId = unitId,
                amount = damage,
                position = target.position,
                targetLayer = com.urkaaaz.domain.AmmunitionHitLayer.UNIT,
            )
        }
        defeatedUnits += current.filterNot { it.isAlive }.map { unit ->
            DefeatedUnit(
                unit = unit.copy(
                    combatState = unit.combatState.copy(
                        action = UnitActionState.DEAD,
                        targetId = null,
                    ),
                ),
                remainingSeconds = DEATH_ANIMATION_SECONDS,
            )
        }
        units = current.filter { it.isAlive }

        if (result.fortressDamage > 0 && currentBlueFortress.isAlive) {
            currentBlueFortress = currentBlueFortress.withDamage(result.fortressDamage)
            emitDamage(
                targetId = currentBlueFortress.id,
                amount = result.fortressDamage,
                position = currentBlueFortress.center,
                targetLayer = com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS,
            )
        }
        finishIfFortressDestroyed()
    }

    private fun applyBossDamage(
        amount: Int,
        sourceTeam: Team,
        sourceId: EntityId?,
        sourceAmmunition: String? = null,
    ) {
        val boss = bossSystem ?: return
        val result = boss.applyDamage(
            amount = amount,
            sourceTeam = sourceTeam,
            ammunitionType = sourceAmmunition,
        ) ?: return
        emitBossDamage(result, sourceTeam, sourceId)
    }

    private fun emitBossDamage(
        result: BossDamageResult,
        sourceTeam: Team,
        sourceId: EntityId?,
    ) {
        val boss = requireNotNull(bossSystem)
        val hitPosition = WorldPosition(result.x, result.y)
        emitDamage(
            targetId = boss.entityId,
            amount = result.amount,
            position = hitPosition,
            targetLayer = com.urkaaaz.domain.AmmunitionHitLayer.BOSS,
        )
        emit {
            MatchEvent.BossDamaged(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                bossId = boss.entityId,
                sourceId = sourceId,
                sourceTeam = sourceTeam,
                amount = result.amount,
                remainingHealth = result.remainingHealth,
                x = result.x,
                y = result.y,
            )
        }
        if (result.phaseChanged) {
            emit {
                MatchEvent.BossPhaseChanged(
                    eventId = it,
                    matchId = matchId,
                    simulationTimeMilliseconds = simulationTimeMilliseconds,
                    bossId = boss.entityId,
                    phase = boss.snapshot().phase,
                )
            }
        }
        if (result.defeated) {
            val winner = requireNotNull(result.winner)
            emit {
                MatchEvent.BossDefeated(
                    eventId = it,
                    matchId = matchId,
                    simulationTimeMilliseconds = simulationTimeMilliseconds,
                    bossId = boss.entityId,
                    winner = winner,
                )
            }
            finish(winner)
        }
    }

    private fun attackProgress(unit: Unit): Float {
        val cooldown = unit.combatState.attackCooldownRemainingSeconds
        val duration = if (unit.isDemolisherThrowing()) {
            DEMOLISHER_THROW_DURATION_SECONDS
        } else {
            unit.type.attackCooldownSeconds
        }
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

    private fun advanceUnits(deltaSeconds: Float, deltaMilliseconds: Long) {
        if (deltaSeconds <= 0f) return
        val heldUnitPositions = runicBastionDestructionBlockers.values
            .flatMap { it.heldUnitPositions.entries }
            .associate { it.key to it.value }
        val obstacleCentersByTeam = Team.entries.associateWith { team ->
            buildList {
                bastion(team)?.let { add(it.center.x) }
                runicBastionDestructionBlockers.values
                    .filter { it.bastion.team == team }
                    .forEach { add(it.bastion.center.x) }
            }
        }
        units = units.map { unit ->
            val statusAdvanced = unit.advanceStatuses(deltaSeconds)
            val heldX = heldUnitPositions[unit.id]
            if (heldX != null) {
                return@map statusAdvanced.copy(
                    position = WorldPosition(heldX, terrain.heightAt(heldX)),
                    moving = false,
                )
            }
            if (unit.isDemolisherThrowing()) {
                val duration = DEMOLISHER_THROW_DURATION_SECONDS
                val remaining = unit.combatState.attackCooldownRemainingSeconds
                    .coerceIn(0f, duration)
                val elapsed = duration - remaining
                val nextElapsed = minOf(elapsed + deltaSeconds, duration)
                val movementFraction = (nextElapsed - elapsed) / duration
                val direction = if (unit.team == Team.BLUE) 1f else -1f
                val nextX = unit.position.x +
                    direction * DEMOLISHER_THROW_FORWARD_DISTANCE * movementFraction
                return@map statusAdvanced.copy(
                    position = WorldPosition(nextX, terrain.heightAt(nextX)),
                    moving = movementFraction > 0f,
                )
            }
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
            val sapperFortressStopX =
                if (waveMoving && !unit.returningToGarrison && unit.type is SapperUnit) {
                    sapperFortressStopX(unit.team)
                } else {
                    null
                }
            val demolisherFortressStopX =
                if (waveMoving && !unit.returningToGarrison && unit.type.detonatesOnContact) {
                    demolisherFortressStopX(unit.team)
                } else {
                    null
                }
            val direction = kotlin.math.sign(targetX - unit.position.x).let {
                if (it == 0f) {
                    if (unit.team == Team.BLUE) 1f else -1f
                } else {
                    it
                }
            }
            val obstacle = if (waveMoving && !unit.returningToGarrison) {
                obstacleCentersByTeam.getValue(opposingTeam(unit.team))
                    .filter {
                        (it - unit.position.x) * direction >=
                            -RunicBastionGeometry.WIDTH / 2f
                    }
                    .minByOrNull { kotlin.math.abs(it - unit.position.x) }
            } else {
                null
            }
            val bastionStopX = obstacle?.let {
                it -
                    direction * (
                        RunicBastionGeometry.WIDTH / 2f +
                            UnitHitboxProfile.forUnitType(unit.type.id).halfWidth
                        )
            }
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
                    unit.type.detonatesOnContact ->
                        target.position.x -
                            direction * unitContactDistance(unit, target)
                    unit.type.attacksFromRange ->
                        target.position.x -
                            direction * (unit.type.attackRange + formationOffset)
                    else ->
                        target.position.x -
                            direction * (MELEE_ATTACK_STANDOFF_DISTANCE + formationOffset)
                }
            }
            val bossStopX = if (waveMoving && combatTarget == null) {
                bossSystem?.approachX(unit, movingTeams)
            } else {
                null
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
            val movementTargets = listOfNotNull(
                targetX,
                combatStopX,
                alliedStopX,
                bastionStopX,
                bossStopX,
                sapperFortressStopX,
                demolisherFortressStopX,
            )
            val movementTargetX = if (direction >= 0f) {
                movementTargets.minOrNull() ?: targetX
            } else {
                movementTargets.maxOrNull() ?: targetX
            }
            val hasteMultiplier = hasteMovementMultiplier(unit, deltaMilliseconds)
            val step = unit.type.speed * UnitMovementSpeed.statusMultiplier(
                statuses = statusAdvanced.statuses,
                hasteMultiplier = hasteMultiplier,
            ) * if (waveMoving) {
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
                        unitHitboxesIntersect(unit, other)
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

    private fun hasteMovementMultiplier(unit: Unit, deltaMilliseconds: Long): Float {
        if (unit.inGarrison || deltaMilliseconds <= 0L) return 1f
        val remainingMilliseconds = hasteRemainingMillisecondsByTeam[unit.team] ?: return 1f
        if (remainingMilliseconds <= 0L) return 1f
        val effect = SpellCatalog.definition(SpellType.HASTE).effect
            as? SpellEffectDefinition.Haste
            ?: error("active HASTE is missing its effect definition")
        val activeFraction = minOf(remainingMilliseconds, deltaMilliseconds).toFloat() /
            deltaMilliseconds.toFloat()
        return 1f + (effect.movementSpeedMultiplier - 1f) * activeFraction
    }

    private fun advanceHasteTimers(deltaMilliseconds: Long) {
        if (deltaMilliseconds <= 0L) return
        hasteRemainingMillisecondsByTeam.entries.toList().forEach { (team, remaining) ->
            val next = (remaining - deltaMilliseconds).coerceAtLeast(0L)
            if (next == 0L) {
                hasteRemainingMillisecondsByTeam.remove(team)
            } else {
                hasteRemainingMillisecondsByTeam[team] = next
            }
        }
    }

    private fun advanceRunicBastionDestructionBlockers(deltaMilliseconds: Long) {
        if (deltaMilliseconds <= 0L) return
        val remainingBlockers = runicBastionDestructionBlockers.mapNotNull {
                (entityId, blocker) ->
            val remaining = blocker.remainingMilliseconds - deltaMilliseconds
            if (remaining > 0L) {
                entityId to blocker.copy(remainingMilliseconds = remaining)
            } else {
                null
            }
        }.toMap()
        runicBastionDestructionBlockers.clear()
        runicBastionDestructionBlockers.putAll(remainingBlockers)
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
        val completedDemolisherThrows = mutableListOf<EntityId>()

        current.indices.forEach { index ->
            val attacker = current[index]
            if (!attacker.isAlive) return@forEach
            if (attacker.isDemolisherThrowing()) {
                val progress = 1f -
                    attacker.combatState.attackCooldownRemainingSeconds /
                    DEMOLISHER_THROW_DURATION_SECONDS
                current[index] = attacker.copy(
                    moving = attacker.combatState.attackCooldownRemainingSeconds > 0f,
                    combatState = attacker.combatState.copy(
                        attackProgress = progress.coerceIn(0f, 1f),
                    ),
                )
                if (attacker.combatState.attackCooldownRemainingSeconds <= 0f) {
                    completedDemolisherThrows += attacker.id
                }
                return@forEach
            }
            if (!attacker.isAttacking() || attacker.statuses.containsKey(UnitStatus.STUNNED)) {
                return@forEach
            }
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
                        if (attacker.type.detonatesOnContact) {
                            AttackType.CONTACT_EXPLOSIVE
                        } else {
                            AttackType.SIEGE_MISSION
                        }
                    } else if (bastionById(targetId) != null) {
                        if (attacker.type.detonatesOnContact) {
                            AttackType.CONTACT_EXPLOSIVE
                        } else {
                            AttackType.SIEGE_MISSION
                        }
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
            val attackerDirection = if (attacker.team == Team.BLUE) 1f else -1f
            val enemyBastion = bastion(opposingTeam(attacker.team))
            val bastionAhead = enemyBastion?.takeIf {
                !attacker.returningToGarrison &&
                    (it.center.x - attacker.position.x) * attackerDirection >=
                    -RunicBastionGeometry.WIDTH / 2f
            }
            val unitCandidates = if (bastionAhead == null) {
                current
            } else {
                current.filter { candidate ->
                    candidate.team != attacker.team &&
                        (candidate.position.x - attacker.position.x) * attackerDirection >= 0f &&
                        (candidate.position.x - attacker.position.x) * attackerDirection <=
                            (
                                (bastionAhead.center.x - attacker.position.x) * attackerDirection -
                                    RunicBastionGeometry.WIDTH / 2f
                                )
                }
            }
            val target = lockedOrAcquireTarget(attacker, unitCandidates)
            val attackIntent: AttackIntent?
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
                attackIntent = when {
                    attacker.type is SapperUnit -> null
                    attacker.type.detonatesOnContact && contacted(attacker, target) ->
                        AttackIntent(attacker.id, target.id, AttackType.CONTACT_EXPLOSIVE)
                    attacker.type.attacksFromRange &&
                        kotlin.math.abs(target.position.x - attacker.position.x) <= attacker.type.attackRange ->
                        AttackIntent(attacker.id, target.id, AttackType.RANGED)
                    !attacker.type.attacksFromRange && canMeleeAttack(attacker, target, current) ->
                        AttackIntent(attacker.id, target.id, AttackType.MELEE)
                    else -> null
                }
            } else if (bastionAhead != null) {
                if (attacker.combatState.targetId != bastionAhead.id) {
                    emit {
                        MatchEvent.TargetAcquired(
                            it,
                            matchId,
                            simulationTimeMilliseconds,
                            attacker.id,
                            bastionAhead.id,
                        )
                    }
                }
                current[index] = attacker.copy(
                    combatState = attacker.combatState.copy(
                        action = UnitActionState.SEEKING_TARGET,
                        targetId = bastionAhead.id,
                        attackGroupId = null,
                    ),
                )
                attackIntent = when {
                    !canAttackBastion(attacker, bastionAhead, current) -> null
                    attacker.type.detonatesOnContact &&
                        contactedBastion(attacker, bastionAhead) ->
                        AttackIntent(attacker.id, bastionAhead.id, AttackType.CONTACT_EXPLOSIVE)
                    else ->
                        AttackIntent(attacker.id, bastionAhead.id, AttackType.SIEGE_MISSION)
                }
            } else if (canUnitAttackBoss(attacker, current)) {
                val boss = requireNotNull(bossSystem)
                if (attacker.combatState.targetId != boss.entityId) {
                    emit {
                        MatchEvent.TargetAcquired(
                            it,
                            matchId,
                            simulationTimeMilliseconds,
                            attacker.id,
                            boss.entityId,
                        )
                    }
                }
                val groupId = if (
                    attacker.type.participatesInMeleeSynergy &&
                    !attacker.type.attacksFromRange
                ) {
                    AttackGroupId("melee-${boss.entityId.value}")
                } else {
                    null
                }
                current[index] = attacker.copy(
                    combatState = attacker.combatState.copy(
                        action = UnitActionState.SEEKING_TARGET,
                        targetId = boss.entityId,
                        attackGroupId = groupId,
                    ),
                )
                attackIntent = when {
                    attacker.type.detonatesOnContact ->
                        AttackIntent(attacker.id, boss.entityId, AttackType.CONTACT_EXPLOSIVE)
                    attacker.type.attacksFromRange ->
                        AttackIntent(attacker.id, boss.entityId, AttackType.RANGED)
                    else ->
                        AttackIntent(attacker.id, boss.entityId, AttackType.MELEE)
                }
            } else {
                val enemyFortress = fortress(opposingTeam(attacker.team))
                val contactedEnemyFortress =
                    attacker.type.detonatesOnContact &&
                        contactedFortress(attacker, enemyFortress)
                if (contactedEnemyFortress) {
                    current[index] = attacker.copy(
                        combatState = attacker.combatState.copy(
                            action = UnitActionState.SEEKING_TARGET,
                            targetId = enemyFortress.id,
                            attackGroupId = null,
                        ),
                    )
                }
                attackIntent = when {
                    contactedEnemyFortress ->
                        AttackIntent(attacker.id, enemyFortress.id, AttackType.CONTACT_EXPLOSIVE)
                    canAttackFortress(attacker, enemyFortress, current) ->
                        AttackIntent(attacker.id, enemyFortress.id, AttackType.SIEGE_MISSION)
                    else -> null
                }
            }
            attackIntent?.let { intent ->
                queueAttackAfterInitialDelay(
                    current = current,
                    unitIndex = index,
                    deltaSeconds = deltaSeconds,
                    intent = intent,
                    startIntents = startIntents,
                )
            }
        }

        resolveAttackHits(current, hitIntents)
        startAttackIntents(current, startIntents)
        completedDemolisherThrows.forEach { unitId ->
            val index = current.indexOfFirst { it.id == unitId }
            if (index >= 0) {
                val attacker = current[index]
                if (attacker.isAlive && attacker.isDemolisherThrowing()) {
                    detonateDemolisher(current, index, attacker)
                }
            }
        }
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

    private fun queueAttackAfterInitialDelay(
        current: MutableList<Unit>,
        unitIndex: Int,
        deltaSeconds: Float,
        intent: AttackIntent,
        startIntents: MutableList<AttackIntent>,
    ) {
        if (intent.type == AttackType.CONTACT_EXPLOSIVE) {
            startIntents += intent
            return
        }
        val attacker = current[unitIndex]
        val remainingDelay = attacker.combatState.firstAttackDelayRemainingSeconds
        if (remainingDelay == null) {
            val delay = combatRandom.nextFloat() *
                attacker.type.attackCooldownSeconds *
                MAX_INITIAL_ATTACK_DELAY_RATIO
            current[unitIndex] = attacker.copy(
                combatState = attacker.combatState.copy(
                    firstAttackDelayRemainingSeconds = delay,
                ),
            )
            if (delay <= 0f) startIntents += intent
            return
        }
        if (remainingDelay <= 0f) {
            startIntents += intent
            return
        }
        val updatedDelay = (remainingDelay - deltaSeconds).coerceAtLeast(0f)
        current[unitIndex] = attacker.copy(
            combatState = attacker.combatState.copy(
                firstAttackDelayRemainingSeconds = updatedDelay,
            ),
        )
        if (updatedDelay <= 0f) startIntents += intent
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
                attackMission(current, attackerIndex, attacker, intent.targetId)
                return@forEach
            }
            val bastionTarget = bastionById(intent.targetId)
            if (intent.type == AttackType.CONTACT_EXPLOSIVE && bastionTarget != null) {
                if (bastionTarget.isAlive &&
                    bastionTarget.team != attacker.team &&
                    contactedBastion(attacker, bastionTarget)
                ) {
                    startDemolisherThrow(current, attackerIndex, attacker, intent.targetId)
                } else {
                    current[attackerIndex] = attacker.copy(
                        combatState = attacker.combatState.copy(
                            action = UnitActionState.SEEKING_TARGET,
                            targetId = null,
                            attackProgress = 0f,
                        ),
                    )
                }
                return@forEach
            }
            val fortressTarget = listOf(currentBlueFortress, currentRedFortress)
                .firstOrNull { it.id == intent.targetId }
            if (intent.type == AttackType.CONTACT_EXPLOSIVE && fortressTarget != null) {
                if (fortressTarget.isAlive &&
                    fortressTarget.team != attacker.team &&
                    contactedFortress(attacker, fortressTarget)
                ) {
                    startDemolisherThrow(current, attackerIndex, attacker, intent.targetId)
                } else {
                    current[attackerIndex] = attacker.copy(
                        combatState = attacker.combatState.copy(
                            action = UnitActionState.SEEKING_TARGET,
                            targetId = null,
                            attackProgress = 0f,
                        ),
                    )
                }
                return@forEach
            }
            val bossTarget = bossSystem?.takeIf { it.entityId == intent.targetId }
            if (bossTarget != null) {
                if (intent.type == AttackType.CONTACT_EXPLOSIVE) {
                    if (bossTarget.isAlive && canUnitAttackBoss(attacker, current)) {
                        startDemolisherThrow(current, attackerIndex, attacker, intent.targetId)
                    } else {
                        current[attackerIndex] = attacker.copy(
                            combatState = attacker.combatState.copy(
                                action = UnitActionState.SEEKING_TARGET,
                                targetId = null,
                                attackProgress = 0f,
                            ),
                        )
                    }
                    return@forEach
                }
                if (!bossTarget.isAlive ||
                    !canUnitAttackBoss(attacker, current) ||
                    intent.type !in setOf(AttackType.MELEE, AttackType.RANGED)
                ) {
                    current[attackerIndex] = attacker.copy(
                        combatState = attacker.combatState.copy(
                            action = UnitActionState.SEEKING_TARGET,
                            targetId = null,
                            attackProgress = 0f,
                        ),
                    )
                    return@forEach
                }
                val roll = rollUnitDamage(attacker, attacker.type.attackDamage)
                val members = if (intent.type == AttackType.MELEE) {
                    current.filter { member ->
                        member.isAlive &&
                            member.team == attacker.team &&
                            member.type.participatesInMeleeSynergy &&
                            member.combatState.targetId == bossTarget.entityId &&
                            canUnitAttackBoss(member, current)
                    }
                } else {
                    emptyList()
                }
                val synergy = if (intent.type == AttackType.MELEE) {
                    min(1f + (members.size - 1).coerceAtLeast(0) * 0.35f, 2f)
                } else {
                    1f
                }
                val finalDamage = (roll * synergy).toInt()
                    .coerceAtMost(bossTarget.snapshot().health)
                emit {
                    MatchEvent.AttackHit(
                        it,
                        matchId,
                        simulationTimeMilliseconds,
                        attacker.id,
                        bossTarget.entityId,
                        intent.type.name,
                        attacker.combatState.attackCycleId,
                        attacker.type.attackDamage,
                        roll,
                        members.size,
                        synergy,
                        finalDamage,
                    )
                }
                applyBossDamage(
                    amount = (roll * synergy).toInt(),
                    sourceTeam = attacker.team,
                    sourceId = attacker.id,
                )
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
                    val roll = rollUnitDamage(attacker, attacker.type.attackDamage)
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
            if (intent.type == AttackType.CONTACT_EXPLOSIVE) {
                val cycle = attacker.combatState.attackCycleId + 1
                current[index] = attacker.copy(
                    combatState = attacker.combatState.copy(
                        action = UnitActionState.SEEKING_TARGET,
                        targetId = intent.targetId,
                        attackCycleId = cycle,
                        attackProgress = 0f,
                        attackCooldownRemainingSeconds = 0f,
                        firstAttackDelayRemainingSeconds = null,
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
                resolveAttackHits(current, listOf(intent))
                return@forEach
            }
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
            MELEE_ATTACK_STANDOFF_DISTANCE + formationRank * ATTACK_FORMATION_SPACING
        return kotlin.math.abs(target.position.x - attacker.position.x) <= formationReach
    }

    private fun canAttackFortress(
        attacker: Unit,
        target: Fortress,
        current: List<Unit>,
    ): Boolean {
        if (attacker.type is SapperUnit) {
            val direction = if (attacker.team == Team.BLUE) 1f else -1f
            val faceOffset = if (direction > 0f) {
                FortressHitboxProfile.rectangles.minOf { it.left }
            } else {
                FortressHitboxProfile.rectangles.maxOf { it.right }
            }
            val distanceToFront = (
                target.center.x + faceOffset - attacker.position.x
                ) * direction
            return distanceToFront <= SAPPER_FORTRESS_STANDOFF_DISTANCE
        }
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

    private fun canAttackBastion(
        attacker: Unit,
        target: RunicBastion,
        current: List<Unit>,
    ): Boolean {
        val direction = if (attacker.team == Team.BLUE) 1f else -1f
        val centerDistance = (target.center.x - attacker.position.x) * direction
        if (centerDistance < -RunicBastionGeometry.WIDTH / 2f) return false
        val formationRank = current
            .filter {
                it.team == attacker.team &&
                    it.isAlive &&
                    !it.inGarrison &&
                    !it.returningToGarrison &&
                    it.type !is SapperUnit &&
                    (it.id == attacker.id || it.combatState.targetId == target.id)
            }
            .sortedBy { it.id.value }
            .indexOfFirst { it.id == attacker.id }
            .coerceAtLeast(0)
        val formationReach = FORTRESS_ATTACK_RANGE + formationRank * ATTACK_FORMATION_SPACING
        val distanceToFace = (
            kotlin.math.abs(target.center.x - attacker.position.x) -
                RunicBastionGeometry.WIDTH / 2f
            ).coerceAtLeast(0f)
        return distanceToFace <= formationReach
    }

    private fun contactedBastion(attacker: Unit, target: RunicBastion): Boolean =
        unitIntersectsRectangle(
            unit = attacker,
            left = target.center.x - RunicBastionGeometry.WIDTH / 2f,
            top = target.center.y - RunicBastionGeometry.HEIGHT / 2f,
            right = target.center.x + RunicBastionGeometry.WIDTH / 2f,
            bottom = target.center.y + RunicBastionGeometry.HEIGHT / 2f,
        )

    private fun Unit.isAttacking(): Boolean =
        combatState.action == UnitActionState.WINDING_UP ||
            combatState.action == UnitActionState.ATTACKING ||
            combatState.action == UnitActionState.RECOVERING ||
            combatState.action == UnitActionState.THROWING

    private fun Unit.isDemolisherThrowing(): Boolean =
        type is DemolisherUnit && combatState.action == UnitActionState.THROWING

    private fun startDemolisherThrow(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
        targetId: EntityId,
    ) {
        if (attacker.type !is DemolisherUnit) {
            detonateDemolisher(current, attackerIndex, attacker)
            return
        }
        current[attackerIndex] = attacker.copy(
            moving = true,
            combatState = attacker.combatState.copy(
                action = UnitActionState.THROWING,
                targetId = targetId,
                attackProgress = 0f,
                attackCooldownRemainingSeconds = DEMOLISHER_THROW_DURATION_SECONDS,
                attackGroupId = null,
            ),
        )
    }

    private fun attackMission(
        current: MutableList<Unit>,
        index: Int,
        attacker: Unit,
        targetId: EntityId,
    ) {
        val behavior = attacker.type
        if (behavior !is SapperUnit) {
            val damage = rollUnitDamage(attacker, behavior.fortressDamage)
            val targetBastion = bastionById(targetId)
            if (targetBastion != null) {
                if (targetBastion.isAlive && targetBastion.team != attacker.team) {
                    damageBastion(
                        targetBastion,
                        damage,
                        attackingUnits = current,
                        sourceUnitId = attacker.id,
                    )
                }
            } else {
                damageFortress(fortress(opposingTeam(attacker.team)), damage)
            }
            return
        }
        if (!attacker.sapperHasBomb) {
            current[index] = attacker.copy(returningToGarrison = true, inGarrison = false)
            return
        }
        val damage = rollUnitDamage(attacker, behavior.fortressDamage)
        val targetBastion = bastionById(targetId)
        if (targetBastion != null) {
            if (targetBastion.isAlive && targetBastion.team != attacker.team) {
                damageBastion(
                    targetBastion,
                    damage,
                    attackingUnits = current,
                    sourceUnitId = attacker.id,
                )
            }
        } else {
            damageFortress(fortress(opposingTeam(attacker.team)), damage)
        }
        current[index] = attacker.copy(
            sapperHasBomb = false,
            returningToGarrison = true,
            inGarrison = false,
        )
    }

    private fun rollUnitDamage(
        attacker: Unit,
        baseDamage: Int,
        teamLuckBias: Float = 0f,
    ): Int {
        val unitBias = combatLuckByUnit.getOrPut(attacker.id) {
            (combatRandom.nextFloat() * 0.08f) - 0.04f
        }
        return applyDamageVariance(baseDamage, damageVarianceFactor(unitBias + teamLuckBias))
    }

    private data class AttackIntent(val attackerId: EntityId, val targetId: EntityId, val type: AttackType)

    private fun detonateDemolisher(
        current: MutableList<Unit>,
        attackerIndex: Int,
        attacker: Unit,
    ) {
        val explosionDamage = rollUnitDamage(attacker, DEMOLISHER_DAMAGE)
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
        val enemyBastion = bastion(opposingTeam(attacker.team))
        if (enemyBastion != null &&
            enemyBastion.intersects(attacker.position, DEMOLISHER_BLAST_RADIUS)
        ) {
            val distance = attacker.position.distanceTo(enemyBastion.center)
            val falloff = (1f - distance / DEMOLISHER_BLAST_RADIUS).coerceAtLeast(0f)
            damageBastion(
                enemyBastion,
                (explosionDamage * falloff).toInt().coerceAtLeast(1),
                attackingUnits = current,
                sourceUnitId = attacker.id,
            )
        }
        if (enemyFortress.intersects(attacker.position, DEMOLISHER_BLAST_RADIUS)) {
            val distance = FortressHitboxProfile.rectangles.minOf {
                it.distanceTo(
                    attacker.position.x - enemyFortress.center.x,
                    attacker.position.y - enemyFortress.center.y,
                )
            }
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

    private fun damageVarianceFactor(teamLuckBias: Float = 0f): Float =
        (MIN_DAMAGE_VARIANCE_FACTOR +
            combatRandom.nextFloat() *
            (MAX_DAMAGE_VARIANCE_FACTOR - MIN_DAMAGE_VARIANCE_FACTOR) +
            teamLuckBias)
            .coerceIn(MIN_DAMAGE_VARIANCE_FACTOR, MAX_DAMAGE_VARIANCE_FACTOR)

    private fun applyDamageVariance(baseDamage: Int, factor: Float): Int =
        if (baseDamage <= 0) 0 else (baseDamage * factor).toInt().coerceAtLeast(1)

    private fun contacted(first: Unit, second: Unit): Boolean =
        unitHitboxesIntersect(first, second)

    private fun unitContactDistance(first: Unit, second: Unit): Float =
        UnitHitboxProfile.forUnitType(first.type.id).halfWidth +
            UnitHitboxProfile.forUnitType(second.type.id).halfWidth

    private fun unitHitboxesIntersect(first: Unit, second: Unit): Boolean {
        val firstHitbox = UnitHitboxProfile.forUnitType(first.type.id)
        val secondHitbox = UnitHitboxProfile.forUnitType(second.type.id)
        return kotlin.math.abs(first.position.x - second.position.x) <=
            firstHitbox.halfWidth + secondHitbox.halfWidth &&
            first.position.y - firstHitbox.height <= second.position.y &&
            second.position.y - secondHitbox.height <= first.position.y
    }

    private fun unitIntersectsRectangle(
        unit: Unit,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        val hitbox = UnitHitboxProfile.forUnitType(unit.type.id)
        return unit.position.x - hitbox.halfWidth <= right &&
            unit.position.x + hitbox.halfWidth >= left &&
            unit.position.y - hitbox.height <= bottom &&
            unit.position.y >= top
    }

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

    private fun bastion(team: Team): RunicBastion? =
        (if (team == Team.BLUE) currentBlueRunicBastion else currentRedRunicBastion)
            ?.takeIf { it.isAlive }

    private fun bastionById(id: EntityId): RunicBastion? =
        listOfNotNull(currentBlueRunicBastion, currentRedRunicBastion)
            .firstOrNull { it.id == id }

    private fun setBastion(bastion: RunicBastion) {
        if (bastion.team == Team.BLUE) {
            currentBlueRunicBastion = bastion
        } else {
            currentRedRunicBastion = bastion
        }
    }

    private fun damageBastion(
        target: RunicBastion,
        damage: Int,
        position: WorldPosition = target.center,
        sourceProjectileId: EntityId? = null,
        attackingUnits: List<Unit> = units,
        sourceUnitId: EntityId? = null,
    ) {
        if (!target.isAlive || damage <= 0) return
        val updated = target.withDamage(damage)
        setBastion(updated)
        if (!updated.isAlive) {
            val heldUnitPositions = attackingUnits
                .filter {
                    it.isAlive &&
                        it.team != target.team &&
                        it.team in movingTeams &&
                        !it.inGarrison &&
                        !it.returningToGarrison &&
                        (
                            it.combatState.targetId == target.id ||
                                it.id == sourceUnitId
                            )
                }
                .associate { it.id to it.position.x }
            runicBastionDestructionBlockers[target.id] =
                RunicBastionDestructionBlocker(
                    bastion = updated,
                    remainingMilliseconds =
                        RunicBastionAnimationTiming.DESTRUCTION_DURATION_MILLISECONDS,
                    heldUnitPositions = heldUnitPositions,
                )
        }
        emitDamage(
            targetId = target.id,
            amount = damage,
            position = position,
            sourceProjectileId = sourceProjectileId,
        )
    }

    private fun setFortress(fortress: Fortress) {
        if (fortress.team == Team.BLUE) {
            currentBlueFortress = fortress
        } else {
            currentRedFortress = fortress
        }
    }

    private fun fortressAttackX(team: Team): Float {
        val fortress = fortress(team)
        return fortress.center.x + if (team == Team.BLUE) 60f else -60f
    }

    private fun sapperFortressStopX(team: Team): Float {
        val direction = if (team == Team.BLUE) 1f else -1f
        val frontFaceOffset = fortressFrontFaceOffset(team)
        return fortress(opposingTeam(team)).center.x + frontFaceOffset -
            direction * SAPPER_FORTRESS_STANDOFF_DISTANCE
    }

    private fun demolisherFortressStopX(team: Team): Float {
        val direction = if (team == Team.BLUE) 1f else -1f
        val frontFaceOffset = fortressFrontFaceOffset(team)
        return fortress(opposingTeam(team)).center.x + frontFaceOffset -
            direction * UnitHitboxProfile.forUnitType(UnitConfigurations.DEMOLISHER.id).halfWidth
    }

    private fun contactedFortress(attacker: Unit, target: Fortress): Boolean {
        return FortressHitboxProfile.rectangles.any { rectangle ->
            unitIntersectsRectangle(
                unit = attacker,
                left = target.center.x + rectangle.left,
                top = target.center.y + rectangle.top,
                right = target.center.x + rectangle.right,
                bottom = target.center.y + rectangle.bottom,
            )
        }
    }

    private fun fortressFrontFaceOffset(attackingTeam: Team): Float =
        if (attackingTeam == Team.BLUE) {
            FortressHitboxProfile.rectangles.minOf { it.left }
        } else {
            FortressHitboxProfile.rectangles.maxOf { it.right }
        }

    private fun opposingTeam(team: Team): Team =
        if (team == Team.BLUE) Team.RED else Team.BLUE

    private fun replenishSupply(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        Team.entries.forEach { team ->
            val wallet = resources.getValue(team)
            if (wallet.supply >= SupplyConfiguration.MAXIMUM_SUPPLY) {
                supplyRegenProgressSeconds[team] = 0f
                return@forEach
            }
            val progress = supplyRegenProgressSeconds.getValue(team) + deltaSeconds
            val replenished = (progress / SUPPLY_REGEN_INTERVAL_SECONDS).toInt()
            supplyRegenProgressSeconds[team] =
                progress - replenished * SUPPLY_REGEN_INTERVAL_SECONDS
            if (replenished > 0) {
                resources[team] = wallet.copy(
                    supply = (wallet.supply + replenished)
                        .coerceAtMost(SupplyConfiguration.MAXIMUM_SUPPLY),
                )
            }
        }
    }

    private fun replenishMana(deltaMilliseconds: Long) {
        if (deltaMilliseconds <= 0L) return
        Team.entries.forEach { team ->
            val wallet = resources.getValue(team)
            if (wallet.mana >= config.maximumMana) {
                manaRegenProgressMilliseconds[team] = 0L
                return@forEach
            }
            val progress = manaRegenProgressMilliseconds.getValue(team) + deltaMilliseconds
            val intervals = progress / config.manaRegenerationIntervalMilliseconds
            var remainingProgress = progress % config.manaRegenerationIntervalMilliseconds
            if (intervals > 0L) {
                val manaMissing = config.maximumMana - wallet.mana
                val intervalsToFull =
                    (manaMissing.toLong() + config.manaRegenerationAmount - 1) /
                        config.manaRegenerationAmount
                val restored = if (intervals >= intervalsToFull) {
                    manaMissing
                } else {
                    (intervals * config.manaRegenerationAmount).toInt()
                }
                val updatedWallet = wallet.restoreMana(restored, config.maximumMana)
                resources[team] = updatedWallet
                if (updatedWallet.mana >= config.maximumMana) remainingProgress = 0L
            }
            manaRegenProgressMilliseconds[team] = remainingProgress
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
                targetTeam = target.team,
                targetKind = target.kind,
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
        val damageFactor = damageVarianceFactor(teamLuckBias = 0f)
        when (val behavior = definition.behavior) {
            is com.urkaaaz.domain.AmmunitionImpactBehavior.LingeringArea -> {
                val fortressDamagePerTick =
                    applyDamageVariance(behavior.fortressDamagePerTick, damageFactor)
                val unitDamagePerTick =
                    applyDamageVariance(behavior.damagePerTick, damageFactor)
                val effectCenter = if (behavior.followsTerrain) {
                    projectile.position.copy(
                        y = terrain.heightAt(projectile.position.x),
                    )
                } else {
                    projectile.position
                }
                applyDamageAt(
                    position = effectCenter,
                    radius = definition.damage.fortressRadius,
                    damage = applyDamageVariance(definition.damage.fortressDamage, damageFactor),
                    sourceTeam = projectile.firedBy,
                    sourceAmmunitionType = projectile.ammunition.name,
                    unitDamage = unitDamagePerTick,
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
                    center = effectCenter,
                    radius = behavior.radius,
                    remainingSeconds = behavior.durationSeconds,
                    tickIntervalSeconds = behavior.tickIntervalSeconds,
                    untilNextTickSeconds = behavior.tickIntervalSeconds,
                    status = behavior.status,
                    driftsWithWind = behavior.driftsWithWind,
                    fortressDamagePerTick = fortressDamagePerTick,
                    unitDamagePerTick = unitDamagePerTick,
                )
            }
            is com.urkaaaz.domain.AmmunitionImpactBehavior.Explosion -> {
                val fortressDamage =
                    applyDamageVariance(definition.damage.fortressDamage, damageFactor)
                emit {
                    MatchEvent.ExplosionTriggered(
                        it,
                        matchId,
                        simulationTimeMilliseconds,
                        projectile.id,
                        projectile.position.x,
                        projectile.position.y,
                        behavior.radius,
                        fortressDamage,
                    )
                }
                applyDamageAt(
                    position = projectile.position,
                    radius = behavior.radius,
                    damage = fortressDamage,
                    sourceTeam = projectile.firedBy,
                    sourceAmmunitionType = projectile.ammunition.name,
                    unitDamage = applyDamageVariance(definition.damage.unitDamage, damageFactor),
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
                    damage = applyDamageVariance(definition.damage.fortressDamage, damageFactor),
                    sourceTeam = projectile.firedBy,
                    sourceAmmunitionType = projectile.ammunition.name,
                    unitDamage = applyDamageVariance(definition.damage.unitDamage, damageFactor),
                    hitLayer = hitLayer,
                    sourceProjectileId = projectile.id,
                    minimumDamage = definition.damage.minimumDamage,
                    friendlyFire = definition.collision.friendlyFire,
                )
            }
        }
        finishIfFortressDestroyed()
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
        sourceAmmunitionType: String? = null,
        minimumDamage: Int = 0,
        friendlyFire: FriendlyFirePolicy = FriendlyFirePolicy.ENEMY_ONLY,
        unitFriendlyFire: FriendlyFirePolicy? = null,
    ) {
        if (damage > 0 &&
            sourceTeam != null &&
            hitLayer != com.urkaaaz.domain.AmmunitionHitLayer.UNIT
        ) {
            val bossResult = bossSystem?.applyDamageAt(
                positionX = position.x,
                positionY = position.y,
                radius = radius,
                amount = damage,
                sourceTeam = sourceTeam,
                ammunitionType = sourceAmmunitionType ?: "UNKNOWN",
            )
            if (bossResult != null) {
                emitBossDamage(
                    result = bossResult,
                    sourceTeam = sourceTeam,
                    sourceId = sourceProjectileId,
                )
            }
        }
        if (damage > 0 &&
            currentBlueFortress.intersects(position, radius) &&
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
        if (damage > 0 &&
            currentRedFortress.intersects(position, radius) &&
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
        listOfNotNull(bastion(Team.BLUE), bastion(Team.RED)).forEach { target ->
            if (damage > 0 &&
                target.intersects(position, radius) &&
                canHitFortress(sourceTeam, target.team, friendlyFire)
            ) {
                damageBastion(
                    target = target,
                    damage = damage,
                    position = position,
                    sourceProjectileId = sourceProjectileId,
                )
            }
        }
        if (unitDamage > 0) {
            units = units.map { unit ->
                val distance = kotlin.math.hypot(
                    unit.position.x - position.x,
                    unit.position.y - position.y,
                )
                if (unit.isAlive &&
                    distance <= radius &&
                    canHitUnit(sourceTeam, unit.team, unitFriendlyFire ?: friendlyFire)
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
            val definition = AmmunitionCatalog.definition(effect.ammunition)
            val behavior = definition.behavior as? AmmunitionImpactBehavior.LingeringArea
                ?: error("lingering effect has a non-lingering ammunition definition")
            var remaining = effect.remainingSeconds - deltaSeconds
            var untilNextTick = effect.untilNextTickSeconds - deltaSeconds
            while (remaining > 0f && untilNextTick <= 0f) {
                applyDamageAt(
                    position = effect.currentCenter,
                    radius = effect.radius,
                    damage = effect.fortressDamagePerTick,
                    sourceTeam = effect.sourceTeam,
                    sourceAmmunitionType = effect.ammunition.name,
                    unitDamage = effect.unitDamagePerTick,
                    status = behavior.status,
                    statusDurationSeconds = behavior.durationSeconds,
                    sourceProjectileId = effect.sourceProjectileId,
                    friendlyFire = definition.collision.friendlyFire,
                    unitFriendlyFire = behavior.unitFriendlyFire,
                    minimumDamage = definition.damage.minimumDamage,
                )
                untilNextTick += effect.tickIntervalSeconds
            }
            if (remaining > 0f) {
                val nextX = if (effect.driftsWithWind) {
                    (
                        effect.currentCenter.x +
                            wind.accelerationX() *
                            behavior.windDriftMultiplier *
                            deltaSeconds
                        ).coerceIn(0f, terrain.worldWidth)
                } else {
                    effect.currentCenter.x
                }
                val nextCenter = effect.currentCenter.copy(
                    x = nextX,
                    y = if (behavior.followsTerrain) {
                        terrain.heightAt(nextX)
                    } else {
                        effect.currentCenter.y
                    },
                )
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
            finish(outcome() ?: MatchOutcome.DRAW)
        }
    }

    private fun finish(outcome: MatchOutcome) {
        if (status == MatchStatus.FINISHED) return
        status = MatchStatus.FINISHED
        phase = MatchPhase.FINISHED
        finalOutcome = outcome
        emit {
            MatchEvent.MatchFinished(
                it,
                matchId,
                simulationTimeMilliseconds,
                outcome,
            )
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
                team = targetUnit.team,
                kind = ProjectileTargetKind.UNIT,
            )
        }
        if (com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS !in definition.collision.hitLayers) {
            return null
        }
        val bossTarget = bossSystem?.takeIf { boss ->
            val snapshot = boss.snapshot()
            val radiusX = snapshot.collisionWidth / 2f + definition.collision.hitRadius
            val radiusY = snapshot.collisionHeight / 2f + definition.collision.hitRadius
            boss.isAlive &&
                kotlin.math.hypot(
                    (snapshot.x - projectile.position.x) / radiusX,
                    (
                        snapshot.groundY - snapshot.collisionHeight / 2f -
                            projectile.position.y
                        ) / radiusY,
                ) <= 1f
        }
        if (bossTarget != null) {
            return EntityTarget(
                id = bossTarget.entityId,
                layer = com.urkaaaz.domain.AmmunitionHitLayer.BOSS,
                stopsProjectile = true,
                team = null,
                kind = ProjectileTargetKind.BOSS,
            )
        }
        val bastionTarget = listOfNotNull(bastion(Team.BLUE), bastion(Team.RED))
            .filter { target ->
                canHitFortress(
                    sourceTeam = projectile.firedBy,
                    targetTeam = target.team,
                    friendlyFire = definition.collision.friendlyFire,
                ) && target.intersects(
                    projectile.position,
                    maxOf(config.fortressCollisionRadius, definition.collision.hitRadius),
                )
            }
            .minByOrNull { target ->
                projectile.position.distanceTo(target.center)
            }
        if (bastionTarget != null) {
            return EntityTarget(
                bastionTarget.id,
                com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS,
                stopsProjectile = true,
                team = bastionTarget.team,
                kind = ProjectileTargetKind.BASTION,
            )
        }
        val fortressCandidates = listOf(currentBlueFortress, currentRedFortress)
            .filter { fortress ->
                canHitFortress(
                    sourceTeam = projectile.firedBy,
                    targetTeam = fortress.team,
                    friendlyFire = definition.collision.friendlyFire,
                ) && !runicBastionBlocksProjectile(
                    projectile = projectile,
                    targetTeam = fortress.team,
                    collisionRadius = maxOf(
                        config.fortressCollisionRadius,
                        definition.collision.hitRadius,
                    ),
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
                team = it.team,
                kind = ProjectileTargetKind.FORTRESS,
            )
        }
    }

    private fun runicBastionBlocksProjectile(
        projectile: Projectile,
        targetTeam: Team,
        collisionRadius: Float,
    ): Boolean {
        val targetBastion = bastion(targetTeam) ?: return false
        val direction = if (targetTeam == Team.RED) 1f else -1f
        val forwardDistance = (targetBastion.center.x - projectile.position.x) * direction
        if (forwardDistance <= -RunicBastionGeometry.WIDTH / 2f) return false
        val velocityTowardBastion = projectile.velocity.x * direction
        if (velocityTowardBastion <= 0f) return false
        val timeToBastion = forwardDistance.coerceAtLeast(0f) / velocityTowardBastion
        val predicted = projectile.advance(
            deltaSeconds = timeToBastion,
            windAccelerationX = wind.accelerationX(),
            gravityAccelerationY = config.gravityAccelerationY * worldPhysicsScale,
            windResponse = projectile.windResponse,
            gravityResponse = projectile.gravityResponse,
            drag = projectile.drag,
        )
        return kotlin.math.abs(predicted.position.y - targetBastion.center.y) <=
            RunicBastionGeometry.HEIGHT / 2f + collisionRadius
    }

    private fun catapult(id: EntityId): Catapult =
        listOf(currentBlueCatapult, currentRedCatapult).firstOrNull { it.id == id }
            ?: error("unknown catapult: ${id.value}")

    private fun outsideBounds(position: WorldPosition): Boolean =
        position.x < 0f || position.x > config.bounds.width || position.y < -config.bounds.height

    private fun outcome(): MatchOutcome? = finalOutcome ?: when {
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
        targetLayer: com.urkaaaz.domain.AmmunitionHitLayer? = null,
    ) {
        val (targetTeam, targetKind) = eventTargetDetails(targetId)
        val resolvedTargetLayer = targetLayer ?: if (targetKind == ProjectileTargetKind.UNIT) {
            com.urkaaaz.domain.AmmunitionHitLayer.UNIT
        } else {
            com.urkaaaz.domain.AmmunitionHitLayer.FORTRESS
        }
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
                targetLayer = resolvedTargetLayer.name,
                sourceProjectileId = sourceProjectileId,
                targetTeam = targetTeam,
                targetKind = targetKind,
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

    private fun eventTargetDetails(targetId: EntityId): Pair<Team?, ProjectileTargetKind?> {
        listOf(currentBlueFortress, currentRedFortress)
            .firstOrNull { it.id == targetId }
            ?.let { return it.team to ProjectileTargetKind.FORTRESS }
        listOfNotNull(currentBlueRunicBastion, currentRedRunicBastion)
            .firstOrNull { it.id == targetId }
            ?.let { return it.team to ProjectileTargetKind.BASTION }
        units.firstOrNull { it.id == targetId }
            ?.let { return it.team to ProjectileTargetKind.UNIT }
        bossSystem?.takeIf { it.entityId == targetId }
            ?.let { return null to ProjectileTargetKind.BOSS }
        return null to null
    }

    private fun emitProjectileResolved(
        projectileId: EntityId,
        reason: ProjectileResolution,
    ) {
        emit {
            MatchEvent.ProjectileResolved(
                eventId = it,
                matchId = matchId,
                simulationTimeMilliseconds = simulationTimeMilliseconds,
                projectileId = projectileId,
                reason = reason,
            )
        }
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
        supply = SupplyConfiguration.MAXIMUM_SUPPLY,
        mana = config.startingMana,
        ammunition = AmmunitionType.entries
            .filter {
                !it.unlimited &&
                    (config.availableAmmunitionTypes.isEmpty() ||
                        it.name in config.availableAmmunitionTypes)
            }
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
        val fortressDamagePerTick: Int,
        val unitDamagePerTick: Int,
    )

    private data class DefeatedUnit(
        val unit: Unit,
        val remainingSeconds: Float,
    )

    private data class EntityTarget(
        val id: EntityId,
        val layer: com.urkaaaz.domain.AmmunitionHitLayer,
        val stopsProjectile: Boolean,
        val team: Team?,
        val kind: ProjectileTargetKind,
    )

    private data class ShieldImpact(
        val fortress: Fortress,
        val position: WorldPosition,
    )

    companion object {
        private const val SUPPLY_REGEN_INTERVAL_SECONDS = 3f
        private const val MIN_DAMAGE_VARIANCE_FACTOR = 0.80f
        private const val MAX_DAMAGE_VARIANCE_FACTOR = 1.20f
        private const val MAX_INITIAL_ATTACK_DELAY_RATIO = 0.5f
        private const val UNIT_MOVEMENT_SPEED_MULTIPLIER = 2f
        private const val UNIT_MOVEMENT_SPEED_SCALE = 0.45f * UNIT_MOVEMENT_SPEED_MULTIPLIER
        private const val GARRISON_MOVEMENT_SPEED_SCALE = 0.22f * UNIT_MOVEMENT_SPEED_MULTIPLIER
        private const val GARRISON_FIRST_SLOT_FROM_SCREEN_EDGE = 690f
        private const val GARRISON_SPACING = 70f
        private const val NEW_UNIT_REAR_OFFSET = 20f
        private const val UNIT_INTERACTION_RANGE = 260f
        private const val FORTRESS_ATTACK_RANGE = 82f
        internal const val SAPPER_FORTRESS_STANDOFF_DISTANCE = 200f
        private const val DEMOLISHER_THROW_DURATION_SECONDS = 0.5f
        private const val DEMOLISHER_THROW_FORWARD_DISTANCE = 90f
        private const val DEMOLISHER_BLAST_RADIUS = 260f
        private const val DEMOLISHER_DAMAGE = 300
        private const val DEATH_ANIMATION_SECONDS = 0.8f
        private const val SAPPER_CONTACT_SPEED_MULTIPLIER = 0.45f
        private const val ALLIED_UNIT_MIN_SEPARATION = 16f
        private const val MELEE_ATTACK_STANDOFF_DISTANCE = 150f
        private const val ATTACK_FORMATION_SPACING = 55f
        private const val FORTRESS_CENTER_MARGIN = 490f
        private const val CATAPULT_CENTER_MARGIN = 450f
        // Keep default foundations wide enough for the scaled fortress footprint.
        private val FORTRESS_FLAT_HALF_WIDTH = WorldScale.FORTRESS_HALF_WIDTH
        private const val BASE_PHYSICS_WORLD_WIDTH = 1_600f
        private const val APEX_TIME_SEARCH_ITERATIONS = 16
        private const val TERRAIN_COLLISION_SEARCH_ITERATIONS = 12
        private const val TERRAIN_COLLISION_SAMPLE_SECONDS = 1f / 60f

        private fun fortressMarginFor(worldWidth: Float): Float =
            if (worldWidth < 2_000f) 160f else FORTRESS_CENTER_MARGIN

        private fun catapultMarginFor(worldWidth: Float): Float =
            if (worldWidth < 2_000f) 120f else CATAPULT_CENTER_MARGIN

        private fun garrisonFirstSlotFromScreenEdge(worldWidth: Float): Float =
            if (worldWidth < 2_000f) {
                minOf(GARRISON_FIRST_SLOT_FROM_SCREEN_EDGE, worldWidth / 2f - 60f)
            } else {
                GARRISON_FIRST_SLOT_FROM_SCREEN_EDGE
            }

        private fun defaultCatapult(
            team: Team,
            bounds: WorldBounds,
            config: SimulationConfig,
            terrain: TerrainState,
        ): Catapult {
            val fortress = config.mapDefinition?.fortresses?.firstOrNull { it.owner == team }
            val foundationHeight = (fortress?.foundationHeight
                ?: bounds.height * config.defaultCatapultHeightRatio) +
                CatapultPresentationContract.FORTRESS_VERTICAL_OFFSET +
                CatapultPresentationContract.CATAPULT_VERTICAL_OFFSET
            val anchorFoundationHeight = (fortress?.foundationHeight
                ?: bounds.height * config.defaultCatapultHeightRatio) +
                CatapultPresentationContract.FORTRESS_VERTICAL_OFFSET
            val catapultX = fortress?.let {
                it.x + if (team == Team.BLUE) {
                    -CatapultPresentationContract.CATAPULT_CENTER_OFFSET_FROM_FORTRESS
                } else {
                    CatapultPresentationContract.CATAPULT_CENTER_OFFSET_FROM_FORTRESS
                }
            } ?: CatapultPresentationContract.catapultCenterX(team, bounds.width)
            val anchorX = fortress?.let {
                it.x + if (team == Team.BLUE) {
                    -CatapultPresentationContract.PROJECTILE_ANCHOR_OFFSET_FROM_FORTRESS
                } else {
                    CatapultPresentationContract.PROJECTILE_ANCHOR_OFFSET_FROM_FORTRESS
                }
            } ?: CatapultPresentationContract.projectileAnchorX(team, bounds.width)
            val anchorY = minOf(
                CatapultPresentationContract.anchorY(
                    foundationHeight = anchorFoundationHeight,
                    worldHeight = bounds.height,
                ),
                terrain.heightAt(anchorX) - 1f,
            )
            return Catapult(
                id = EntityId("${team.name.lowercase()}-catapult"),
                team = team,
                position = WorldPosition(
                    x = catapultX,
                    y = foundationHeight,
                ),
                anchorPosition = WorldPosition(
                    x = anchorX,
                    y = anchorY,
                ),
                aim = Aim(directionDegrees = 45f, power = 50f),
            )
        }

        private fun defaultFortress(
            team: Team,
            bounds: WorldBounds,
            config: SimulationConfig,
        ): Fortress =
            Fortress(
                id = EntityId("${team.name.lowercase()}-fortress"),
                team = team,
                center = WorldPosition(
                    x = config.mapDefinition?.fortresses
                        ?.firstOrNull { it.owner == team }?.x
                        ?: if (team == Team.BLUE) fortressMarginFor(bounds.width) else {
                            bounds.width - fortressMarginFor(bounds.width)
                        },
                    y = (config.mapDefinition?.fortresses
                        ?.firstOrNull { it.owner == team }?.foundationHeight
                        ?: bounds.height * config.defaultCatapultHeightRatio) +
                        CatapultPresentationContract.FORTRESS_VERTICAL_OFFSET -
                        WorldScale.FORTRESS_HEIGHT / 2f,
                ),
                maxHealth = if (team == Team.BLUE) {
                        config.blueFortressMaxHealth
                } else {
                        config.redFortressMaxHealth
                },
            )
    }

}

private fun WorldPosition.distanceTo(other: WorldPosition): Float {
    val dx = x - other.x
    val dy = y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
