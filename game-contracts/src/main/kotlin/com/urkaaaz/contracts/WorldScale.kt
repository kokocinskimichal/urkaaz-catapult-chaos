package com.urkaaaz.contracts

/** Shared presentation and collision scale rules for match entities. */
object WorldScale {
    const val FORTRESS_SCALE = 0.70f
    const val UNIT_SCALE = 3.0f

    const val LEGACY_FORTRESS_HALF_WIDTH = 196f
    const val FORTRESS_HALF_WIDTH = LEGACY_FORTRESS_HALF_WIDTH * FORTRESS_SCALE

    const val UNIT_COLLISION_RADIUS = 18f
}
