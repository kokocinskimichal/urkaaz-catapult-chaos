package com.urkaaaz.contracts

/** Alpha-derived rectangular approximation of the fortress silhouette in world units. */
data class FortressHitboxRectangle(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    fun distanceTo(x: Float, y: Float): Float {
        val dx = when {
            x < left -> left - x
            x > right -> x - right
            else -> 0f
        }
        val dy = when {
            y < top -> top - y
            y > bottom -> y - bottom
            else -> 0f
        }
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

object FortressHitboxProfile {
    /*
     * Derived from the alpha silhouette of the 1024x768
     * fortress_chapter_01 sprites: upper tower, upper body, center body,
     * two lower wings and the base.
     */
    val rectangles = listOf(
        FortressHitboxRectangle(-38f, -92f, 38f, -48f),
        FortressHitboxRectangle(-68f, -58f, 68f, -18f),
        FortressHitboxRectangle(-82f, -28f, 82f, 24f),
        FortressHitboxRectangle(-96f, 8f, -18f, 62f),
        FortressHitboxRectangle(18f, 8f, 96f, 62f),
        FortressHitboxRectangle(-82f, 48f, 82f, 82f),
    )
}
