package com.urkaaaz.simulation

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.Team

data class CombatLogUnit(
    val id: EntityId,
    val type: String,
    val team: Team,
    val health: Int,
    val maxHealth: Int,
    val x: Float,
    val y: Float,
    val moving: Boolean,
    val action: String,
    val targetId: EntityId?,
    val attackGroupId: String?,
    val attackCycleId: Long,
    val attackProgress: Float,
    val distanceToTarget: Float?,
    val sapperHasBomb: Boolean,
)

data class CombatLogRecord(
    val matchId: MatchId,
    val simulationTimeMilliseconds: Long,
    val reason: String,
    val units: List<CombatLogUnit>,
)

fun interface CombatLogSink {
    fun log(record: CombatLogRecord)
}

object NoOpCombatLogSink : CombatLogSink {
    override fun log(record: CombatLogRecord) = Unit
}
