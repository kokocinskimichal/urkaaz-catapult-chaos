package com.urkaaaz.ui

import android.util.Log
import com.urkaaaz.simulation.CombatLogRecord
import com.urkaaaz.simulation.CombatLogSink

class AndroidCombatLogSink(
    private val enabled: Boolean = true,
) : CombatLogSink {
    override fun log(record: CombatLogRecord) {
        if (!enabled) return
        Log.d(
            TAG,
            "match=${record.matchId.value} t=${record.simulationTimeMilliseconds} " +
                "reason=${record.reason} units=${record.units.size}",
        )
        record.units.forEach { unit ->
            Log.d(
                TAG,
                "unit=${unit.id.value} type=${unit.type} team=${unit.team} " +
                    "hp=${unit.health}/${unit.maxHealth} pos=(${format(unit.x)}," +
                    "${format(unit.y)}) moving=${unit.moving} action=${unit.action} " +
                    "target=${unit.targetId?.value ?: "-"} group=${unit.attackGroupId ?: "-"} " +
                    "distance=${unit.distanceToTarget?.let(::format) ?: "-"} " +
                    "cycle=${unit.attackCycleId} progress=${format(unit.attackProgress)} " +
                    "bomb=${unit.sapperHasBomb}",
            )
        }
    }

    private fun format(value: Float): String = "%.2f".format(java.util.Locale.US, value)

    private companion object {
        const val TAG = "UrkaaazCombat"
    }
}
