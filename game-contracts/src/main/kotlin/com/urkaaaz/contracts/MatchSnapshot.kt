package com.urkaaaz.contracts

data class MatchSnapshot(
    val status: MatchStatus = MatchStatus.NOT_STARTED,
    val matchId: MatchId? = null,
    val phase: MatchPhase = MatchPhase.CREATED,
    val activeTeam: Team? = null,
    val catapults: List<CatapultSnapshot> = emptyList(),
    val fortresses: List<FortressSnapshot> = emptyList(),
    val units: List<UnitSnapshot> = emptyList(),
    val projectiles: List<ProjectileSnapshot> = emptyList(),
    val terrain: TerrainSnapshot = TerrainSnapshot(),
    val resources: ResourceSnapshot = ResourceSnapshot(),
    val resourcesByTeam: Map<Team, ResourceSnapshot> = emptyMap(),
    val effects: List<EffectSnapshot> = emptyList(),
    val wind: WindSnapshot = WindSnapshot(),
    val outcome: MatchOutcome? = null,
    val reloadRemainingSeconds: Map<Team, Float> = emptyMap(),
    val activeProjectileTeams: Set<Team> = emptySet(),
    val remainingMilliseconds: Long = 300_000L,
) {
    init {
        require(status != MatchStatus.FINISHED || outcome != null) {
            "finished snapshot must contain an outcome"
        }
    }
}

data class CatapultSnapshot(
    val entityId: EntityId,
    val team: Team,
    val x: Float,
    val y: Float,
    val directionDegrees: Float = 0f,
    val power: Float = 0f,
)

data class FortressSnapshot(
    val entityId: EntityId,
    val team: Team,
    val health: Int,
    val maxHealth: Int,
)

data class UnitSnapshot(
    val entityId: EntityId,
    val team: Team,
    val unitType: String,
    val x: Float,
    val y: Float,
    val health: Int,
    val maxHealth: Int = 0,
    val moving: Boolean = false,
    val attackType: String = "MELEE",
    val actionState: String = "GARRISONED",
    val targetId: EntityId? = null,
    val attackGroupId: String? = null,
    val attackCycleId: Long = 0L,
    val attackProgress: Float = 0f,
    val facingDirection: Float = 1f,
    val sapperHasBomb: Boolean = true,
)

data class ProjectileSnapshot(
    val entityId: EntityId,
    val firedBy: Team,
    val ammunitionType: String,
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
)

data class TerrainSnapshot(
    val revision: Long = 0L,
    val biome: String = "GREEN_FRONTIER",
)

data class ResourceSnapshot(
    val gold: Int = 0,
    val supply: Int = 0,
    val mana: Int = 0,
    val ammunition: Map<String, Int> = emptyMap(),
)

data class EffectSnapshot(
    val effectType: String,
    val remainingMilliseconds: Long,
)

data class WindSnapshot(
    val direction: Float = 0f,
    val strength: Float = 0f,
)
