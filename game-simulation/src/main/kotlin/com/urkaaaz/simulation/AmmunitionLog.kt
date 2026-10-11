package com.urkaaaz.simulation

import com.urkaaaz.contracts.EntityId
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.Team

enum class AmmunitionLogMode {
    OFF,
    EVENTS_ONLY,
    STATE_CHANGES,
    TICK,
}

data class AmmunitionLogRecord(
    val matchId: MatchId,
    val simulationTimeMilliseconds: Long,
    val reason: String,
    val projectileId: EntityId? = null,
    val ammunitionType: String? = null,
    val team: Team? = null,
    val x: Float? = null,
    val y: Float? = null,
    val targetId: EntityId? = null,
    val amount: Int? = null,
    val radius: Float? = null,
    val remainingReloadSeconds: Float? = null,
)

fun interface AmmunitionLogSink {
    fun log(record: AmmunitionLogRecord)
}

object NoOpAmmunitionLogSink : AmmunitionLogSink {
    override fun log(record: AmmunitionLogRecord) = Unit
}

class FilteringAmmunitionLogSink(
    private val mode: AmmunitionLogMode,
    private val delegate: AmmunitionLogSink,
) : AmmunitionLogSink {
    override fun log(record: AmmunitionLogRecord) {
        val isTick = record.reason == "tick"
        val isStateChange = record.reason in STATE_CHANGE_REASONS
        val accepted = when (mode) {
            AmmunitionLogMode.OFF -> false
            AmmunitionLogMode.EVENTS_ONLY -> !isTick && !isStateChange
            AmmunitionLogMode.STATE_CHANGES -> !isTick
            AmmunitionLogMode.TICK -> true
        }
        if (accepted) delegate.log(record)
    }

    private companion object {
        val STATE_CHANGE_REASONS = setOf(
            "reload-started",
            "reload-finished",
            "inventory-consumed",
            "status-applied",
            "status-refreshed",
            "status-expired",
        )
    }
}
