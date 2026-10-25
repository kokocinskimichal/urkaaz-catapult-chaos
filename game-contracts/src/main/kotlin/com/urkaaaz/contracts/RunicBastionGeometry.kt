package com.urkaaaz.contracts

import kotlin.math.hypot
import kotlin.math.max

/** Shared world-space geometry for Runic Bastion collision and presentation. */
object RunicBastionGeometry {
    const val WIDTH = 288f
    const val HEIGHT = 216f
    const val VERTICAL_LIFT = 12f
    const val FRONT_OFFSET_FROM_GATE = WorldScale.FORTRESS_WIDTH / 2f + 100f

    fun raisedFoundationY(foundationY: Float): Float = foundationY - VERTICAL_LIFT

    fun centerY(foundationY: Float): Float =
        raisedFoundationY(foundationY) - HEIGHT / 2f

    fun intersects(
        centerX: Float,
        centerY: Float,
        x: Float,
        y: Float,
        radius: Float,
    ): Boolean {
        require(radius >= 0f) { "collision radius must not be negative" }
        val distanceX = max(kotlin.math.abs(x - centerX) - WIDTH / 2f, 0f)
        val distanceY = max(kotlin.math.abs(y - centerY) - HEIGHT / 2f, 0f)
        return hypot(distanceX, distanceY) <= radius
    }
}
