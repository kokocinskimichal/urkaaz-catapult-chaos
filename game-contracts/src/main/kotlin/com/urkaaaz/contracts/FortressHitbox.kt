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
    private const val SOURCE_WIDTH = 1024f
    private const val SOURCE_HEIGHT = 768f

    private data class AlphaBounds(
        // Source-pixel bounds relative to the center of the full sprite canvas.
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private val fortressBounds = AlphaBounds(
        left = -430f,
        top = -325f,
        right = 430f,
        bottom = 325f,
    )

    private val sourceToWorldX = WorldScale.FORTRESS_WIDTH / SOURCE_WIDTH
    private val sourceToWorldY = WorldScale.FORTRESS_HEIGHT / SOURCE_HEIGHT

    val rectangles = listOf(
        FortressHitboxRectangle(
            left = fortressBounds.left * sourceToWorldX,
            top = fortressBounds.top * sourceToWorldY,
            right = fortressBounds.right * sourceToWorldX,
            bottom = fortressBounds.bottom * sourceToWorldY,
        ),
    )
}
