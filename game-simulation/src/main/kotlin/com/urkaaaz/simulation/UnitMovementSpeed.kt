package com.urkaaaz.simulation

import com.urkaaaz.domain.StatusState
import com.urkaaaz.domain.UnitStatus

internal object UnitMovementSpeed {
    fun statusMultiplier(
        statuses: Map<UnitStatus, StatusState>,
        hasteMultiplier: Float,
    ): Float {
        require(hasteMultiplier > 0f) { "haste multiplier must be positive" }
        val slowMultiplier = statuses[UnitStatus.SLOWED]
            ?.strength
            ?.let { (1f - it).coerceIn(0.05f, 1f) }
            ?: 1f
        return slowMultiplier * hasteMultiplier
    }
}
