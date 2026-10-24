package com.urkaaaz.ui

/**
 * Screen-layer presentation rules for the Fortress Shield dome. The dome sprite
 * is stretched over the same ellipse that collision uses; gameplay state is
 * never touched here.
 */
object FortressShieldPresentation {
    const val FRAME_COUNT = 24
    const val FRAME_WIDTH = 640
    const val FRAME_HEIGHT = 480
    const val RENDER_FRAMES_PER_ANIMATION_STEP = 6
    const val HIT_RENDER_FRAMES_PER_ANIMATION_STEP = 3
    const val HIT_FLASH_START_FRAME = 4
    const val ACTIVE_DOME_ALPHA = 255

    // Visible dome bounds (alpha > 16) shared by the approved AnimGen frames.
    private const val DOME_LEFT = 77f
    private const val DOME_TOP = 112f
    private const val DOME_RIGHT = 562f
    private const val DOME_BOTTOM = 369f

    fun frameIndex(animationFrame: Int): Int =
        (animationFrame.coerceAtLeast(0) / RENDER_FRAMES_PER_ANIMATION_STEP) % FRAME_COUNT

    fun hitFrameIndex(elapsedRenderFrames: Int): Int {
        val elapsedAnimationSteps =
            elapsedRenderFrames.coerceAtLeast(0) / HIT_RENDER_FRAMES_PER_ANIMATION_STEP
        return (HIT_FLASH_START_FRAME + elapsedAnimationSteps).coerceAtMost(FRAME_COUNT - 1)
    }

    fun hitAnimationDurationRenderFrames(): Int =
        (FRAME_COUNT - HIT_FLASH_START_FRAME) * HIT_RENDER_FRAMES_PER_ANIMATION_STEP

    fun shouldRenderShield(health: Int?, hasHitAnimation: Boolean): Boolean =
        (health != null && health > 0) || hasHitAnimation

    fun shouldMirrorHitHorizontally(teamLabel: String): Boolean = teamLabel == "BLUE"

    fun shieldIconCoveredFraction(health: Int?, maxHealth: Int?): Float {
        val max = maxHealth?.takeIf { it > 0 } ?: return 0f
        val remainingFraction = health?.toFloat()?.div(max)?.coerceIn(0f, 1f) ?: return 0f
        return 1f - remainingFraction
    }

    fun spriteBounds(
        ellipseLeft: Float,
        ellipseTop: Float,
        ellipseWidth: Float,
        ellipseHeight: Float,
    ): SpriteBounds {
        val scaleX = ellipseWidth / (DOME_RIGHT - DOME_LEFT)
        val scaleY = ellipseHeight / (DOME_BOTTOM - DOME_TOP)
        val left = ellipseLeft - DOME_LEFT * scaleX
        val top = ellipseTop - DOME_TOP * scaleY
        return SpriteBounds(left, top, left + FRAME_WIDTH * scaleX, top + FRAME_HEIGHT * scaleY)
    }

    data class SpriteBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
}

data class FortressShieldHitAnimationFrame(
    val teamLabel: String,
    val frameIndex: Int,
)
