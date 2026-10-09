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
    val playerProjectileActive: Boolean,
    val playerAmmunition: Map<String, Int>,
    val matchTimeRemainingMilliseconds: Long,
    val windDirection: Float,
)

data class ProjectileRenderState(
    val projectileId: String,
    val teamLabel: String,
    val x: Float,
    val y: Float,
    val ammunitionType: String,
)

data class ImpactRenderState(
    val eventId: String,
    val x: Float,
    val y: Float,
    val ammunitionType: String,
)

object RenderStateMapper {
    fun map(snapshot: MatchSnapshot, events: List<MatchEvent> = emptyList()): RenderState = RenderState(
        statusLabel = snapshot.status.name,
        phaseLabel = snapshot.phase.name,
        activeTeamLabel = snapshot.activeTeam?.name ?: "NONE",
        projectileCount = snapshot.projectiles.size,
        terrainRevision = snapshot.terrain.revision,
        terrainBiome = snapshot.terrain.biome,
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
        playerProjectileActive = com.urkaaaz.contracts.Team.BLUE in snapshot.activeProjectileTeams,
        playerAmmunition = snapshot.resourcesByTeam[
            com.urkaaaz.contracts.Team.BLUE
        ]?.ammunition ?: snapshot.resources.ammunition,
        matchTimeRemainingMilliseconds = snapshot.remainingMilliseconds,
        windDirection = snapshot.wind.direction,
    )
}
