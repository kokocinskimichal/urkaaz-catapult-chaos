package com.urkaaaz.contracts

/** Shared world-space shape for fortress shield rendering and collision. */
object FortressShieldGeometry {
    const val HORIZONTAL_RADIUS_SCALE = 0.62f
    const val VERTICAL_RADIUS_SCALE = 0.62f

    fun horizontalRadius(fortressWidth: Float): Float =
        fortressWidth * HORIZONTAL_RADIUS_SCALE

    fun verticalRadius(fortressHeight: Float): Float =
        fortressHeight * VERTICAL_RADIUS_SCALE
}
