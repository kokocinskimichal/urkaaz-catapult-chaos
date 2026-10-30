package com.urkaaaz.ui

data class BossSpriteDrawBounds(
    val centerX: Float,
    val baselineY: Float,
    val width: Float,
    val height: Float,
)

object BossPresentationLayout {
    fun frameDrawBounds(
        centerX: Float,
        baselineY: Float,
        visualWidth: Float,
        visualHeight: Float,
        animation: BossSpriteAnimationDefinition,
    ): BossSpriteDrawBounds {
        require(visualWidth > 0f && visualHeight > 0f)

        val reference = animation.referenceArtBounds
        val frame = animation.frameArtBounds
        val artCenterX = centerX + (reference.centerX - 0.5f) * visualWidth
        val frameCanvasWidth = visualWidth * reference.width / frame.width
        val frameCanvasHeight = visualHeight * reference.height / frame.height
        val artBottom = baselineY - (1f - reference.bottom) * visualHeight

        return BossSpriteDrawBounds(
            centerX = artCenterX + (0.5f - frame.centerX) * frameCanvasWidth,
            baselineY = artBottom + (1f - frame.bottom) * frameCanvasHeight,
            width = frameCanvasWidth,
            height = frameCanvasHeight,
        )
    }
}
