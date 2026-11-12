package com.urkaaaz.ui

import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchSnapshot
import com.urkaaaz.contracts.BossSnapshot
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossPhase
import com.urkaaaz.contracts.CatapultPresentationContract
import com.urkaaaz.contracts.FortressSnapshot
import com.urkaaaz.contracts.InitialCameraAnchor
import com.urkaaaz.contracts.RunicBastionGeometry
import com.urkaaaz.contracts.Team
import com.urkaaaz.contracts.WorldDimensions

/** Immutable presentation model derived from the public match snapshot. */
data class RenderState(
    val statusLabel: String,
    val phaseLabel: String,
    val activeTeamLabel: String,
    val initialCameraAnchor: InitialCameraAnchor = InitialCameraAnchor.PLAYER_FORTRESS,
    val initialCameraZoom: Float = 1f,
    val projectileCount: Int,
    val terrainRevision: Long,
    val terrainBiome: String,
    val craters: List<CraterRenderState> = emptyList(),
    val terrainWorldWidth: Float = WorldDimensions.WIDTH,
    val terrainSampleSpacing: Float = 8f,
    val terrainHeightSamples: List<Float> = emptyList(),
    val projectiles: List<ProjectileRenderState>,
    val blueCatapultX: Float,
    val blueCatapultY: Float,
    val blueCatapultAnchorX: Float = blueCatapultX,
    val blueCatapultAnchorY: Float = blueCatapultY,
    val redCatapultX: Float,
    val redCatapultY: Float,
    val redCatapultAnchorX: Float = redCatapultX,
    val redCatapultAnchorY: Float = redCatapultY,
    val blueFortressX: Float = CatapultPresentationContract.FORTRESS_CENTER_MARGIN,
    val blueFortressFoundationY: Float = blueCatapultY,
    val redFortressX: Float = WorldDimensions.WIDTH -
        CatapultPresentationContract.FORTRESS_CENTER_MARGIN,
    val redFortressFoundationY: Float = redCatapultY,
    val fortressTeamsByEntityId: Map<String, Team> = emptyMap(),
    val playerAngleDegrees: Float,
    val playerPower: Float,
    val blueFortressHealth: Int,
    val blueFortressMaxHealth: Int,
    val blueFortressShieldHealth: Int? = null,
    val blueFortressShieldMaxHealth: Int? = null,
    val blueRunicBastionHealth: Int? = null,
    val blueRunicBastionMaxHealth: Int? = null,
    val redFortressHealth: Int,
    val redFortressMaxHealth: Int,
    val redFortressShieldHealth: Int? = null,
    val redFortressShieldMaxHealth: Int? = null,
    val windStrength: Float,
    val gravityAccelerationY: Float = 0f,
    val windAccelerationScale: Float = 0f,
    val projectileInitialSpeedMultiplier: Float = 0f,
    val projectileWindResponse: Float = 0f,
    val projectileGravityResponse: Float = 0f,
    val projectileDrag: Float = 0f,
    val gameplaySpeedMultiplier: Float = 1f,
    val impact: ImpactRenderState?,
    val outcomeLabel: String?,
    val playerReloadRemainingSeconds: Float,
    val enemyReloadRemainingSeconds: Float,
    val playerReloadByAmmunition: Map<String, Float> = emptyMap(),
    val enemyReloadByAmmunition: Map<String, Float> = emptyMap(),
    val playerProjectileActive: Boolean,
    val playerAmmunition: Map<String, Int>,
    val playerGold: Int,
    val playerSupply: Int,
    val units: List<UnitRenderState>,
    val runicBastions: List<RunicBastionRenderState> = emptyList(),
    val matchTimeRemainingMilliseconds: Long,
    val windDirection: Float,
    val combatFeedback: List<CombatFeedbackRenderState>,
    val mapObjects: List<MapObjectRenderState> = emptyList(),
    val matchId: String? = null,
    val matchStartEventId: String? = null,
    val isPaused: Boolean = false,
    val fireRainSplits: List<FireRainSplitRenderState> = emptyList(),
    val fireRainEffects: List<FireRainEffectRenderState> = emptyList(),
    val plagueCauldronEffects: List<PlagueCauldronEffectRenderState> = emptyList(),
    val matchGeneration: Long = 0L,
    val playerMana: Int = 0,
    val selectedAmmunitionType: String = "ROCK",
    val activeSpellTypes: Set<String> = emptySet(),
    val activeSpellRemainingMilliseconds: Map<String, Long> = emptyMap(),
    val spellFeedbackLabel: Int? = null,
    val fortressShieldHits: List<FortressShieldHitRenderState> = emptyList(),
    val boss: BossRenderState? = null,
    val bossAttack: BossAttackRenderState? = null,
    val tutorialLockedMechanicIds: Set<String> = emptySet(),
)

/** One-shot presentation trigger emitted when the boss starts a unit attack. */
data class BossAttackRenderState(
    val eventId: String,
    val bossId: String,
    val direction: BossAttackDirection,
)

data class BossRenderState(
    val entityId: String,
    val profileId: String,
    val presentationKey: String,
    val archetype: BossArchetype,
    val phase: BossPhase,
    val x: Float,
    val groundY: Float,
    val collisionWidth: Float,
    val collisionHeight: Float,
    val health: Int,
    val maxHealth: Int,
    val blueDamage: Int,
    val redDamage: Int,
)

data class RunicBastionRenderState(
    val entityId: String,
    val team: Team,
    val centerX: Float,
    val centerY: Float,
    val foundationY: Float,
    val health: Int,
    val maxHealth: Int,
) {
    val teamLabel: String get() = team.name
}

data class FortressShieldHitRenderState(
    val eventId: String,
    val teamLabel: String,
)

data class MapObjectRenderState(
    val id: String,
    val x: Float,
    val y: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val assetKey: String,
    val relicSize: String? = null,
    val relicRenderSize: Float? = null,
)

data class ProjectileRenderState(
    val projectileId: String,
    val teamLabel: String,
    val x: Float,
    val y: Float,
    val ammunitionType: String,
    val collisionRadius: Float = 0f,
)

data class CraterRenderState(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val depth: Float,
)

data class ImpactRenderState(
    val eventId: String,
    val x: Float,
    val y: Float,
    val ammunitionType: String,
    val projectileId: String? = null,
    val radius: Float = 0f,
)

data class CombatFeedbackRenderState(
    val eventId: String,
    val kind: Kind,
    val x: Float,
    val y: Float,
    val amount: Int = 0,
    val goldAmount: Long? = null,
    val radius: Float = 0f,
    val label: String? = null,
    val synergyMultiplier: Float? = null,
    val progress: Float = 0f,
    val sourceUnitType: String? = null,
    val sourceUnitId: String? = null,
    val animationFrameIndex: Int? = null,
) {
    enum class Kind {
        DAMAGE,
        HIT_FLASH,
        EXPLOSION,
        SYNERGY,
        GOLD_REWARD,
    }
}

data class UnitRenderState(
    val entityId: String,
    val team: Team,
    val unitType: String,
    val x: Float,
    val y: Float,
    val health: Int,
    val maxHealth: Int,
    val moving: Boolean,
    val attackType: String,
    val actionState: String,
    val targetId: String?,
    val attackGroupId: String?,
    val attackCycleId: Long,
    val attackProgress: Float,
    val facingDirection: Float,
    val sapperHasBomb: Boolean,
    val hasted: Boolean = false,
    val demolisherThrowFrameIndex: Int? = null,
) {
    val teamLabel: String get() = team.name
}

object RenderStateMapper {
    fun map(snapshot: MatchSnapshot, events: List<MatchEvent> = emptyList()): RenderState {
        val blueFortressSnapshot = snapshot.fortresses.firstOrNull { it.team == Team.BLUE }
        val redFortressSnapshot = snapshot.fortresses.firstOrNull { it.team == Team.RED }
        val blueRunicBastion = snapshot.runicBastions.firstOrNull { it.team == Team.BLUE }
        val blueFortress = snapshot.mapDefinition?.fortresses?.firstOrNull {
            it.owner == Team.BLUE
        }
        val redFortress = snapshot.mapDefinition?.fortresses?.firstOrNull {
            it.owner == Team.RED
        }
        val blueFortressX = blueFortressSnapshot?.x ?: blueFortress?.x
            ?: CatapultPresentationContract.FORTRESS_CENTER_MARGIN
        val redFortressX = redFortressSnapshot?.x ?: redFortress?.x
            ?: snapshot.terrain.worldWidth - CatapultPresentationContract.FORTRESS_CENTER_MARGIN
        val blueFortressFoundationY = blueFortressSnapshot?.foundationY ?: blueFortress?.foundationHeight
            ?: snapshot.catapults.firstOrNull { it.team == Team.BLUE }?.y
            ?: 3648f
        val redFortressFoundationY = redFortressSnapshot?.foundationY ?: redFortress?.foundationHeight
            ?: snapshot.catapults.firstOrNull { it.team == Team.RED }?.y
            ?: 3648f

        return RenderState(
        statusLabel = snapshot.status.name,
        phaseLabel = snapshot.phase.name,
        activeTeamLabel = snapshot.activeTeam?.name ?: "NONE",
        matchId = snapshot.matchId?.value,
        matchStartEventId = events.lastOrNull { event ->
            event is MatchEvent.MatchStarted &&
                (snapshot.matchId == null || event.matchId == snapshot.matchId)
        }?.eventId?.value,
        projectileCount = snapshot.projectiles.size,
        terrainRevision = snapshot.terrain.revision,
        terrainBiome = snapshot.terrain.biome,
        initialCameraAnchor = snapshot.mapDefinition?.initialCameraAnchor
            ?: InitialCameraAnchor.PLAYER_FORTRESS,
        initialCameraZoom = snapshot.mapDefinition?.initialCameraZoom ?: 1f,
        craters = snapshot.terrain.craters.map {
            CraterRenderState(it.centerX, it.centerY, it.radius, it.depth)
        },
        runicBastions = snapshot.runicBastions.map {
            RunicBastionRenderState(
                entityId = it.entityId.value,
                team = it.team,
                centerX = it.centerX,
                centerY = RunicBastionGeometry.centerY(it.foundationY),
                foundationY = it.foundationY,
                health = it.health,
                maxHealth = it.maxHealth,
            )
        },
        terrainWorldWidth = snapshot.terrain.worldWidth,
        terrainSampleSpacing = snapshot.terrain.sampleSpacing,
        terrainHeightSamples = snapshot.terrain.heightSamples,
        projectiles = snapshot.projectiles.map {
            ProjectileRenderState(
                projectileId = it.entityId.value,
                teamLabel = it.firedBy.name,
                x = it.x,
                y = it.y,
                ammunitionType = it.ammunitionType,
                collisionRadius = it.collisionRadius,
            )
        },
        blueCatapultX = snapshot.catapults.firstOrNull { it.team == Team.BLUE }?.x
            ?: CatapultPresentationContract.catapultCenterX(
                Team.BLUE,
                snapshot.terrain.worldWidth,
            ),
        blueCatapultY = snapshot.catapults.firstOrNull { it.team == Team.BLUE }?.y ?: 3648f,
        blueCatapultAnchorX = snapshot.catapults.firstOrNull { it.team == Team.BLUE }?.anchorX
            ?: CatapultPresentationContract.projectileAnchorX(
                Team.BLUE,
                snapshot.terrain.worldWidth,
            ),
        blueCatapultAnchorY = snapshot.catapults.firstOrNull { it.team == Team.BLUE }?.anchorY
            ?: CatapultPresentationContract.anchorY(3648f, WorldDimensions.HEIGHT),
        redCatapultX = snapshot.catapults.firstOrNull { it.team == Team.RED }?.x
            ?: CatapultPresentationContract.catapultCenterX(
                Team.RED,
                snapshot.terrain.worldWidth,
            ),
        redCatapultY = snapshot.catapults.firstOrNull { it.team == Team.RED }?.y ?: 3648f,
        redCatapultAnchorX = snapshot.catapults.firstOrNull { it.team == Team.RED }?.anchorX
            ?: CatapultPresentationContract.projectileAnchorX(
                Team.RED,
                snapshot.terrain.worldWidth,
            ),
        redCatapultAnchorY = snapshot.catapults.firstOrNull { it.team == Team.RED }?.anchorY
            ?: CatapultPresentationContract.anchorY(3648f, WorldDimensions.HEIGHT),
        blueFortressX = blueFortressX,
        blueFortressFoundationY = blueFortressFoundationY,
        redFortressX = redFortressX,
        redFortressFoundationY = redFortressFoundationY,
        fortressTeamsByEntityId = snapshot.fortresses.associate {
            it.entityId.value to it.team
        },
        playerAngleDegrees = snapshot.catapults.firstOrNull { it.team == Team.BLUE }
            ?.directionDegrees ?: 0f,
        playerPower = snapshot.catapults.firstOrNull { it.team == Team.BLUE }
            ?.power ?: 0f,
        blueFortressHealth = blueFortressSnapshot?.health ?: 0,
        blueFortressMaxHealth = blueFortressSnapshot?.maxHealth ?: 0,
        blueFortressShieldHealth = blueFortressSnapshot?.shieldHealth,
        blueFortressShieldMaxHealth = blueFortressSnapshot?.shieldMaxHealth,
        blueRunicBastionHealth = blueRunicBastion?.health,
        blueRunicBastionMaxHealth = blueRunicBastion?.maxHealth,
        redFortressHealth = redFortressSnapshot?.health ?: 0,
        redFortressMaxHealth = redFortressSnapshot?.maxHealth ?: 0,
        redFortressShieldHealth = redFortressSnapshot?.shieldHealth,
        redFortressShieldMaxHealth = redFortressSnapshot?.shieldMaxHealth,
        windStrength = snapshot.wind.strength,
        gameplaySpeedMultiplier = snapshot.gameplaySpeedMultiplier,
        impact = events.lastOrNull {
            it is MatchEvent.TerrainDeformed ||
                it is MatchEvent.ProjectileHitEntity ||
                (
                    it is MatchEvent.ProjectileHitTerrain &&
                        it.ammunitionType == PLAGUE_CAULDRON_AMMUNITION
                    )
        }
            ?.let { event ->
                val projectileId = when (event) {
                    is MatchEvent.ProjectileHitEntity -> event.projectileId
                    is MatchEvent.ProjectileHitTerrain -> event.projectileId
                    else -> null
                }
                val plagueEffect = projectileId?.let { id ->
                    snapshot.effects.lastOrNull {
                        it.effectType == PLAGUE_CAULDRON_AMMUNITION &&
                            it.sourceProjectileId == id
                    }
                }
                when (event) {
                    is MatchEvent.TerrainDeformed -> ImpactRenderState(
                        event.eventId.value,
                        event.centerX,
                        event.centerY,
                        event.ammunitionType,
                    )
                    is MatchEvent.ProjectileHitEntity -> ImpactRenderState(
                        event.eventId.value,
                        event.x,
                        event.y,
                        event.ammunitionType,
                        projectileId = event.projectileId.value,
                        radius = plagueEffect?.radius ?: 0f,
                    )
                    is MatchEvent.ProjectileHitTerrain -> plagueEffect?.let { effect ->
                        ImpactRenderState(
                            eventId = event.eventId.value,
                            x = effect.x,
                            y = effect.y,
                            ammunitionType = event.ammunitionType,
                            projectileId = event.projectileId.value,
                            radius = effect.radius,
                        )
                    }
                    else -> null
                }
            },
        outcomeLabel = snapshot.outcome?.name,
        playerReloadRemainingSeconds = snapshot.reloadRemainingSeconds[
            com.urkaaaz.contracts.Team.BLUE
        ] ?: 0f,
        enemyReloadRemainingSeconds = snapshot.reloadRemainingSeconds[
            com.urkaaaz.contracts.Team.RED
        ] ?: 0f,
        playerReloadByAmmunition = snapshot.reloadRemainingSecondsByAmmunition[
            com.urkaaaz.contracts.Team.BLUE
        ] ?: emptyMap(),
        enemyReloadByAmmunition = snapshot.reloadRemainingSecondsByAmmunition[
            com.urkaaaz.contracts.Team.RED
        ] ?: emptyMap(),
        playerProjectileActive = com.urkaaaz.contracts.Team.BLUE in snapshot.activeProjectileTeams,
        playerAmmunition = snapshot.resourcesByTeam[
            com.urkaaaz.contracts.Team.BLUE
        ]?.ammunition ?: snapshot.resources.ammunition,
        playerGold = snapshot.resourcesByTeam[
            com.urkaaaz.contracts.Team.BLUE
        ]?.gold ?: snapshot.resources.gold,
        playerMana = snapshot.resourcesByTeam[
            com.urkaaaz.contracts.Team.BLUE
        ]?.mana ?: snapshot.resources.mana,
        activeSpellTypes = buildSet {
            if ((blueFortressSnapshot?.shieldHealth ?: 0) > 0) {
                add("FORTRESS_SHIELD")
            }
            if (blueRunicBastion != null) {
                add("RUNIC_BASTION")
            }
            snapshot.activeSpells
                .filter { it.team == com.urkaaaz.contracts.Team.BLUE }
                .forEach { add(it.spellType) }
        },
        activeSpellRemainingMilliseconds = snapshot.activeSpells
            .filter { it.team == com.urkaaaz.contracts.Team.BLUE }
            .associate { it.spellType to it.remainingMilliseconds },
        fortressShieldHits = events.filterIsInstance<MatchEvent.ShieldDamaged>()
            .filter { snapshot.matchId == null || it.matchId == snapshot.matchId }
            .mapNotNull { event ->
                snapshot.fortresses.firstOrNull { it.entityId == event.fortressId }?.let { fortress ->
                    FortressShieldHitRenderState(
                        eventId = event.eventId.value,
                        teamLabel = fortress.team.name,
                    )
                }
            },
        playerSupply = snapshot.resourcesByTeam[
            com.urkaaaz.contracts.Team.BLUE
        ]?.supply ?: snapshot.resources.supply,
        units = snapshot.units.map {
            UnitRenderState(
                entityId = it.entityId.value,
                team = it.team,
                unitType = it.unitType,
                x = it.x,
                y = it.y,
                health = it.health,
                maxHealth = it.maxHealth,
                moving = it.moving,
                attackType = it.attackType,
                actionState = it.actionState,
                targetId = it.targetId?.value,
                attackGroupId = it.attackGroupId,
                attackCycleId = it.attackCycleId,
                attackProgress = it.attackProgress,
                facingDirection = it.facingDirection,
                sapperHasBomb = it.sapperHasBomb,
                hasted = "HASTED" in it.statuses,
            )
        },
        matchTimeRemainingMilliseconds = snapshot.remainingMilliseconds,
        windDirection = snapshot.wind.direction,
        combatFeedback = events.mapNotNull { event ->
            when (event) {
                is MatchEvent.AttackHit -> {
                    val target = snapshot.units.firstOrNull { it.entityId == event.targetId }
                    val bastion = snapshot.runicBastions.firstOrNull {
                        it.entityId == event.targetId
                    }
                    val fortressX = snapshot.fortresses
                        .firstOrNull { it.entityId == event.targetId }
                        ?.let { fortressX(it, snapshot) }
                    CombatFeedbackRenderState(
                        eventId = event.eventId.value,
                        kind = CombatFeedbackRenderState.Kind.DAMAGE,
                        x = target?.x ?: bastion?.centerX ?: fortressX
                            ?: return@mapNotNull null,
                        y = target?.y ?: bastion?.let {
                            RunicBastionGeometry.centerY(it.foundationY)
                        } ?: 470f,
                        amount = event.finalDamage,
                        synergyMultiplier = event.synergyMultiplier
                            .takeIf { event.synergyCount > 1 },
                    )
                }
                is MatchEvent.ExplosionTriggered -> CombatFeedbackRenderState(
                    eventId = event.eventId.value,
                    kind = CombatFeedbackRenderState.Kind.EXPLOSION,
                    x = event.x,
                    y = event.y,
                    radius = event.radius,
                    sourceUnitType = snapshot.units
                        .firstOrNull { it.entityId == event.sourceId }
                        ?.unitType,
                    sourceUnitId = event.sourceId.value,
                )
                is MatchEvent.DamageApplied -> {
                    val target = snapshot.units.firstOrNull {
                        it.entityId == event.targetId
                    }
                    val bastion = snapshot.runicBastions.firstOrNull {
                        it.entityId == event.targetId
                    }
                    val targetFortressX = snapshot.fortresses
                        .firstOrNull { it.entityId == event.targetId }
                        ?.let { fortressX(it, snapshot) }
                    val x = target?.x ?: bastion?.centerX
                        ?: event.x.takeIf { it != 0f }
                        ?: targetFortressX
                        ?: return@mapNotNull null
                    CombatFeedbackRenderState(
                        eventId = event.eventId.value,
                        kind = CombatFeedbackRenderState.Kind.DAMAGE,
                        x = x,
                        y = target?.y ?: bastion?.let {
                                RunicBastionGeometry.centerY(it.foundationY)
                            }
                            ?: event.y.takeIf { it != 0f }
                            ?: 470f,
                        amount = event.amount,
                    )
                }
                is MatchEvent.AttackGroupJoined -> {
                    val unit = snapshot.units.firstOrNull { it.entityId == event.unitId }
                    CombatFeedbackRenderState(
                        eventId = event.eventId.value,
                        kind = CombatFeedbackRenderState.Kind.SYNERGY,
                        x = unit?.x ?: 0f,
                        y = unit?.y ?: 470f,
                        label = event.groupId,
                    )
                }
                else -> null
            }
        },
        mapObjects = snapshot.mapDefinition?.decorations?.map {
            MapObjectRenderState(
                id = it.id,
                x = it.x,
                y = it.y,
                scale = it.scale,
                rotationDegrees = it.rotationDegrees,
                assetKey = it.assetKey,
                relicSize = it.relicSize?.name,
                relicRenderSize = it.relicSize?.renderSize,
            )
        } ?: emptyList(),
        fireRainSplits = events.mapNotNull { event ->
            (event as? MatchEvent.ProjectileSplit)
                ?.takeIf {
                    it.ammunitionType == FIRE_RAIN_AMMUNITION &&
                        (snapshot.matchId == null || it.matchId == snapshot.matchId)
                }
                ?.let {
                    FireRainSplitRenderState(
                        eventId = it.eventId.value,
                        projectileId = it.projectileId.value,
                        fragmentProjectileIds = it.fragmentProjectileIds.map { id -> id.value },
                        x = it.x,
                        y = it.y,
                    )
                }
        },
        fireRainEffects = snapshot.effects.mapNotNull { effect ->
            if (effect.effectType != FIRE_RAIN_AMMUNITION) {
                null
            } else {
                FireRainEffectRenderState(
                    effectId = requireNotNull(effect.effectId) {
                        "FIRE_RAIN effect snapshot is missing its effectId"
                    }.value,
                    x = effect.x,
                    y = effect.y,
                    radius = effect.radius,
                    remainingMilliseconds = effect.remainingMilliseconds,
                )
            }
        },
        plagueCauldronEffects = snapshot.effects.mapNotNull { effect ->
            if (effect.effectType != PLAGUE_CAULDRON_AMMUNITION) {
                null
            } else {
                PlagueCauldronEffectRenderState(
                    effectId = requireNotNull(effect.effectId) {
                        "PLAGUE_CAULDRON effect snapshot is missing its effectId"
                    }.value,
                    sourceProjectileId = requireNotNull(effect.sourceProjectileId) {
                        "PLAGUE_CAULDRON effect snapshot is missing its sourceProjectileId"
                    }.value,
                    x = effect.x,
                    y = effect.y,
                    radius = effect.radius,
                    remainingMilliseconds = effect.remainingMilliseconds,
                )
            }
        },
        boss = snapshot.boss?.let { it.toRenderState() },
        bossAttack = events.lastOrNull { it is MatchEvent.BossUnitAttackStarted }?.let {
            (it as MatchEvent.BossUnitAttackStarted).toRenderState()
        },
        )
    }

    private fun MatchEvent.BossUnitAttackStarted.toRenderState() = BossAttackRenderState(
        eventId = eventId.value,
        bossId = bossId.value,
        direction = when (targetTeam) {
            Team.BLUE -> BossAttackDirection.LEFT
            Team.RED -> BossAttackDirection.RIGHT
        },
    )

    private fun BossSnapshot.toRenderState() = BossRenderState(
        entityId = entityId.value,
        profileId = profileId,
        presentationKey = presentationKey,
        archetype = archetype,
        phase = phase,
        x = x,
        groundY = groundY,
        collisionWidth = collisionWidth,
        collisionHeight = collisionHeight,
        health = health,
        maxHealth = maxHealth,
        blueDamage = blueDamage,
        redDamage = redDamage,
    )

    private fun fortressX(
        fortress: FortressSnapshot,
        snapshot: MatchSnapshot,
    ): Float {
        val mapFortress = snapshot.mapDefinition?.fortresses?.firstOrNull {
            it.owner == fortress.team
        }
        return fortress.x ?: mapFortress?.x ?: when (fortress.team) {
            Team.BLUE -> CatapultPresentationContract.FORTRESS_CENTER_MARGIN
            Team.RED -> snapshot.terrain.worldWidth -
                CatapultPresentationContract.FORTRESS_CENTER_MARGIN
        }
    }

    private const val FIRE_RAIN_AMMUNITION = "FIRE_RAIN"
    private const val PLAGUE_CAULDRON_AMMUNITION = "PLAGUE_CAULDRON"
}
