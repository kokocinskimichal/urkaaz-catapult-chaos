package com.urkaaaz.contracts

data class UnitHitboxDimensions(
    val width: Float,
    val height: Float,
) {
    init {
        require(width > 0f && height > 0f) {
            "unit hitbox dimensions must be positive"
        }
    }

    val halfWidth: Float
        get() = width / 2f
}

object UnitHitboxProfile {
    const val VISIBLE_BODY_RATIO = 0.8f

    // Approximate measured opaque bounds of living animation art, in world units.
    private data class VisibleBodyBounds(
        val width: Float,
        val height: Float,
    )

    private val visibleBodyBoundsByUnitType = mapOf(
        "DEFENDER" to VisibleBodyBounds(width = 190f, height = 228f),
        "SAPPER" to VisibleBodyBounds(width = 137f, height = 220f),
        "DEMOLISHER" to VisibleBodyBounds(width = 180f, height = 172f),
        "RAGING_BOAR" to VisibleBodyBounds(width = 189f, height = 183f),
        "SLINGMASTER" to VisibleBodyBounds(width = 127f, height = 127f),
    )

    fun forUnitType(unitType: String): UnitHitboxDimensions {
        val visibleBounds = visibleBodyBoundsByUnitType[unitType]
            ?: error("unit hitbox profile is missing for: $unitType")
        return UnitHitboxDimensions(
            width = visibleBounds.width * VISIBLE_BODY_RATIO,
            height = visibleBounds.height * VISIBLE_BODY_RATIO,
        )
    }
}
