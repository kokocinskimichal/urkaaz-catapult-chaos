package com.urkaaaz.contracts

sealed interface MatchEvent {
    val eventId: EventId
    val matchId: MatchId
    val simulationTimeMilliseconds: Long

    data class MatchStarted(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val randomSeed: Long,
        val balanceRevision: String? = null,
        val balanceContentHash: String? = null,
    ) : MatchEvent

    data class BossSpawned(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val bossId: EntityId,
        val profileId: String,
        val archetype: BossArchetype,
        val x: Float,
        val groundY: Float,
    ) : MatchEvent

    data class BossDamaged(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val bossId: EntityId,
        val sourceId: EntityId?,
        val sourceTeam: Team,
        val amount: Int,
        val remainingHealth: Int,
        val x: Float,
        val y: Float,
    ) : MatchEvent

    data class BossAttackHit(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val bossId: EntityId,
        val targetId: EntityId,
        val amount: Int,
    ) : MatchEvent

    data class BossPhaseChanged(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val bossId: EntityId,
        val phase: BossPhase,
    ) : MatchEvent

    data class BossDefeated(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val bossId: EntityId,
        val winner: MatchOutcome,
    ) : MatchEvent

    data class TurnChanged(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val activeTeam: Team,
    ) : MatchEvent

    data class AmmunitionSelected(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val playerId: PlayerId,
        val ammunitionType: String,
    ) : MatchEvent

    data class ProjectileFired(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val projectileId: EntityId,
        val catapultId: EntityId,
        val ammunitionType: String = "ROCK",
        val x: Float = 0f,
        val y: Float = 0f,
        val shooterTeam: Team? = null,
    ) : MatchEvent

    data class ProjectileSplit(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val projectileId: EntityId,
        val fragmentProjectileIds: List<EntityId>,
        val ammunitionType: String,
        val x: Float,
        val y: Float,
    ) : MatchEvent

    data class ProjectileHitTerrain(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val projectileId: EntityId,
        val ammunitionType: String = "ROCK",
    ) : MatchEvent

    data class ProjectileHitEntity(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val projectileId: EntityId,
        val targetId: EntityId,
        val ammunitionType: String = "ROCK",
        val targetLayer: String = "FORTRESS",
        val x: Float = 0f,
        val y: Float = 0f,
        val targetTeam: Team? = null,
        val targetKind: ProjectileTargetKind? = null,
    ) : MatchEvent

    data class DamageApplied(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val targetId: EntityId,
        val amount: Int,
        val ammunitionType: String = "ROCK",
        val x: Float = 0f,
        val y: Float = 0f,
        val targetLayer: String = "FORTRESS",
        val sourceProjectileId: EntityId? = null,
        val falloffMultiplier: Float = 1f,
        val targetTeam: Team? = null,
        val targetKind: ProjectileTargetKind? = null,
    ) : MatchEvent

    data class SpellCast(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val playerId: PlayerId,
        val spellType: String,
        val targetId: EntityId? = null,
        val manaCost: Int = 0,
    ) : MatchEvent

    data class ShieldDamaged(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val fortressId: EntityId,
        val projectileId: EntityId,
        val amount: Int,
        val remainingHealth: Int,
        val x: Float,
        val y: Float,
    ) : MatchEvent

    data class UnitDeployed(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val unitId: EntityId,
        val unitType: String,
        val team: Team? = null,
    ) : MatchEvent

    data class ProjectileResolved(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val projectileId: EntityId,
        val reason: ProjectileResolution,
    ) : MatchEvent

    data class UnitDefeated(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val unitId: EntityId,
    ) : MatchEvent

    data class TargetAcquired(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val unitId: EntityId,
        val targetId: EntityId,
    ) : MatchEvent

    data class AttackStarted(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val attackerId: EntityId,
        val targetId: EntityId,
        val attackType: String,
        val attackCycleId: Long,
    ) : MatchEvent

    data class AttackHit(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val attackerId: EntityId,
        val targetId: EntityId,
        val attackType: String,
        val attackCycleId: Long,
        val baseDamage: Int,
        val rolledDamage: Int,
        val synergyCount: Int,
        val synergyMultiplier: Float,
        val finalDamage: Int,
    ) : MatchEvent

    data class AttackGroupJoined(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val groupId: String,
        val unitId: EntityId,
        val targetId: EntityId,
    ) : MatchEvent

    data class ExplosionTriggered(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val sourceId: EntityId,
        val x: Float,
        val y: Float,
        val radius: Float,
        val baseDamage: Int,
    ) : MatchEvent

    data class TerrainDeformed(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val ammunitionType: String = "ROCK",
    ) : MatchEvent

    data class MatchFinished(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val outcome: MatchOutcome,
    ) : MatchEvent
}

enum class ProjectileTargetKind {
    UNIT,
    FORTRESS,
    FORTRESS_SHIELD,
    BASTION,
    BOSS,
}

enum class ProjectileResolution {
    ENTITY_IMPACT,
    SHIELD_IMPACT,
    TERRAIN_IMPACT,
    OUT_OF_BOUNDS,
    LIFETIME_EXPIRED,
}
