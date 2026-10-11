package com.urkaaaz.ui

import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchSnapshot

/** Immutable presentation model derived from the public match snapshot. */
data class RenderState(
    val statusLabel: String,
    val phaseLabel: String,
    val activeTeamLabel: String,
    val projectileCount: Int,
    val terrainRevision: Long,
    val terrainBiome: String,
    val craters: List<CraterRenderState> = emptyList(),
    val terrainWorldWidth: Float = 1_600f,
    val terrainSampleSpacing: Float = 8f,
    val terrainHeightSamples: List<Float> = emptyList(),
    val projectiles: List<ProjectileRenderState>,
    val blueCatapultX: Float,
    val blueCatapultY: Float,
    val redCatapultX: Float,
    val redCatapultY: Float,
    val playerAngleDegrees: Float,
    val playerPower: Float,
    val blueFortressHealth: Int,
    val blueFortressMaxHealth: Int,
    val redFortressHealth: Int,
    val redFortressMaxHealth: Int,
    val windStrength: Float,
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
    val matchTimeRemainingMilliseconds: Long,
    val windDirection: Float,
    val combatFeedback: List<CombatFeedbackRenderState>,
)

data class ProjectileRenderState(
    val projectileId: String,
    val teamLabel: String,
    val x: Float,
    val y: Float,
    val ammunitionType: String,
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
)

data class CombatFeedbackRenderState(
    val eventId: String,
    val kind: Kind,
    val x: Float,
    val y: Float,
    val amount: Int = 0,
    val radius: Float = 0f,
    val label: String? = null,
) {
    enum class Kind {
        DAMAGE,
        HIT_FLASH,
        EXPLOSION,
        SYNERGY,
    }
}

data class UnitRenderState(
    val entityId: String,
    val teamLabel: String,
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
)

object RenderStateMapper {
    fun map(snapshot: MatchSnapshot, events: List<MatchEvent> = emptyList()): RenderState = RenderState(
        statusLabel = snapshot.status.name,
        phaseLabel = snapshot.phase.name,
        activeTeamLabel = snapshot.activeTeam?.name ?: "NONE",
        projectileCount = snapshot.projectiles.size,
        terrainRevision = snapshot.terrain.revision,
        terrainBiome = snapshot.terrain.biome,
        craters = snapshot.terrain.craters.map {
            CraterRenderState(it.centerX, it.centerY, it.radius, it.depth)
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
            )
        },
        blueCatapultX = snapshot.catapults.firstOrNull { it.team.name == "BLUE" }?.x ?: 120f,
        blueCatapultY = snapshot.catapults.firstOrNull { it.team.name == "BLUE" }?.y ?: 648f,
        redCatapultX = snapshot.catapults.firstOrNull { it.team.name == "RED" }?.x ?: 1480f,
        redCatapultY = snapshot.catapults.firstOrNull { it.team.name == "RED" }?.y ?: 648f,
        playerAngleDegrees = snapshot.catapults.firstOrNull { it.entityId.value == "blue-catapult" }
            ?.directionDegrees ?: 45f,
        playerPower = snapshot.catapults.firstOrNull { it.entityId.value == "blue-catapult" }
            ?.power ?: 50f,
        blueFortressHealth = snapshot.fortresses.firstOrNull { it.team.name == "BLUE" }?.health ?: 0,
        blueFortressMaxHealth = snapshot.fortresses.firstOrNull { it.team.name == "BLUE" }?.maxHealth ?: 0,
        redFortressHealth = snapshot.fortresses.firstOrNull { it.team.name == "RED" }?.health ?: 0,
        redFortressMaxHealth = snapshot.fortresses.firstOrNull { it.team.name == "RED" }?.maxHealth ?: 0,
        windStrength = snapshot.wind.strength,
        impact = events.lastOrNull { it is MatchEvent.TerrainDeformed || it is MatchEvent.ProjectileHitEntity }
            ?.let { event ->
                when (event) {
                    is MatchEvent.TerrainDeformed -> ImpactRenderState(
                        event.eventId.value,
                        event.centerX,
                        event.centerY,
                        event.ammunitionType,
                    )
                    is MatchEvent.ProjectileHitEntity -> ImpactRenderState(
                        event.eventId.value,
                        if (event.targetId.value.startsWith("blue")) 160f else 1440f,
                        600f,
                        event.ammunitionType,
                    )
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
        playerSupply = snapshot.resourcesByTeam[
            com.urkaaaz.contracts.Team.BLUE
        ]?.supply ?: snapshot.resources.supply,
        units = snapshot.units.map {
            UnitRenderState(
                entityId = it.entityId.value,
                teamLabel = it.team.name,
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
            )
        },
        matchTimeRemainingMilliseconds = snapshot.remainingMilliseconds,
        windDirection = snapshot.wind.direction,
        combatFeedback = events.mapNotNull { event ->
            when (event) {
                is MatchEvent.AttackHit -> {
                    val target = snapshot.units.firstOrNull { it.entityId == event.targetId }
                    CombatFeedbackRenderState(
                        eventId = event.eventId.value,
                        kind = CombatFeedbackRenderState.Kind.DAMAGE,
                        x = target?.x ?: fortressX(event.targetId.value),
                        y = target?.y ?: 470f,
                        amount = event.finalDamage,
                        label = if (event.synergyCount > 1) {
                            "x${"%.2f".format(event.synergyMultiplier)}"
                        } else {
                            null
                        },
                    )
                }
                is MatchEvent.ExplosionTriggered -> CombatFeedbackRenderState(
                    eventId = event.eventId.value,
                    kind = CombatFeedbackRenderState.Kind.EXPLOSION,
                    x = event.x,
                    y = event.y,
                    radius = event.radius,
                )
                is MatchEvent.DamageApplied -> {
                    val target = snapshot.units.firstOrNull {
                        it.entityId == event.targetId
                    }
                    CombatFeedbackRenderState(
                        eventId = event.eventId.value,
                        kind = CombatFeedbackRenderState.Kind.DAMAGE,
                        x = target?.x ?: fortressX(event.targetId.value),
                        y = target?.y ?: 470f,
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
    )

    private fun fortressX(id: String): Float =
        if (id.startsWith("blue")) 160f else 1440f
}
