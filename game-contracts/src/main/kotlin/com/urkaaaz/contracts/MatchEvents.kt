package com.urkaaaz.contracts

sealed interface MatchEvent {
    val eventId: EventId
    val matchId: MatchId
    val simulationTimeMilliseconds: Long

    data class MatchStarted(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
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
    ) : MatchEvent

    data class DamageApplied(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val targetId: EntityId,
        val amount: Int,
    ) : MatchEvent

    data class SpellCast(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val playerId: PlayerId,
        val spellType: String,
    ) : MatchEvent

    data class UnitDeployed(
        override val eventId: EventId,
        override val matchId: MatchId,
        override val simulationTimeMilliseconds: Long,
        val unitId: EntityId,
        val unitType: String,
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
