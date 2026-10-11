package com.urkaaaz.ui

import android.util.Log
import com.urkaaaz.simulation.AmmunitionLogRecord
import com.urkaaaz.simulation.AmmunitionLogSink

class AndroidAmmunitionLogSink(
    private val enabled: Boolean = true,
) : AmmunitionLogSink {
    override fun log(record: AmmunitionLogRecord) {
        if (!enabled) return
        Log.d(
            TAG,
            "match=${record.matchId.value} t=${record.simulationTimeMilliseconds} " +
                "reason=${record.reason} projectile=${record.projectileId?.value ?: "-"} " +
                "ammo=${record.ammunitionType ?: "-"} team=${record.team ?: "-"} " +
                "pos=(${format(record.x)},${format(record.y)}) " +
                "target=${record.targetId?.value ?: "-"} amount=${record.amount ?: "-"} " +
                "radius=${format(record.radius)} reload=${format(record.remainingReloadSeconds)}",
        )
    }

    private fun format(value: Float?): String =
        value?.let { "%.2f".format(java.util.Locale.US, it) } ?: "-"

    private companion object {
        const val TAG = "UrkaaazAmmunition"
    }
}
