package com.urkaaaz.contracts

/** Shared presentation and collision scale rules for match entities. */
object WorldScale {
    const val FORTRESS_SCALE = 2.4f
    const val UNIT_SCALE = 3.0f

    const val LEGACY_FORTRESS_WIDTH = 280f
    const val FORTRESS_WIDTH = LEGACY_FORTRESS_WIDTH * FORTRESS_SCALE
    const val LEGACY_FORTRESS_HEIGHT = 210f
    const val FORTRESS_HEIGHT = LEGACY_FORTRESS_HEIGHT * FORTRESS_SCALE
    const val LEGACY_FORTRESS_HALF_WIDTH = 196f
    const val FORTRESS_HALF_WIDTH = LEGACY_FORTRESS_HALF_WIDTH * FORTRESS_SCALE

    const val LEGACY_CATAPULT_RENDER_WIDTH = 95f
    const val CATAPULT_RENDER_WIDTH = LEGACY_CATAPULT_RENDER_WIDTH * FORTRESS_SCALE

    const val UNIT_COLLISION_RADIUS = 18f
}
