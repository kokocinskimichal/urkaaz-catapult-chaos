package com.urkaaaz.ui

object HasteOrbitingChevronPresentation {
    const val FRAME_COUNT = 12
    const val FRAME_WIDTH = 1024
    const val FRAME_HEIGHT = 576
    const val RENDER_FRAMES_PER_ANIMATION_LOOP = 16
    const val EFFECT_WIDTH_TO_UNIT_WIDTH = 2f
    const val EFFECT_TOP_FROM_BASELINE_HEIGHT = 0.85f

    fun frameIndex(animationFrame: Int): Int {
        val loopFrame =
            animationFrame.coerceAtLeast(0) % RENDER_FRAMES_PER_ANIMATION_LOOP
        return loopFrame * FRAME_COUNT / RENDER_FRAMES_PER_ANIMATION_LOOP
    }

    fun shouldRender(hasted: Boolean, moving: Boolean, defeated: Boolean): Boolean =
        hasted && moving && !defeated

    fun shouldMirrorHorizontally(facingDirection: Float): Boolean = facingDirection < 0f

    fun spriteBounds(centerX: Float, baselineY: Float, unitSpriteWidth: Float): SpriteBounds {
        val width = unitSpriteWidth * EFFECT_WIDTH_TO_UNIT_WIDTH
        val height = width * FRAME_HEIGHT / FRAME_WIDTH
        val top = baselineY - height * EFFECT_TOP_FROM_BASELINE_HEIGHT
        return SpriteBounds(centerX - width / 2f, top, centerX + width / 2f, top + height)
    }

    data class SpriteBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
}
