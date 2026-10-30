package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BossPresentationLayoutTest {
    @Test
    fun warOakHitboxIsInsetWhileTheVisibleTreeKeepsItsPreviousSize() {
        val presentation = BossPresentationCatalog.definition("ancient_war_oak")
        val animation = requireNotNull(presentation.idleAnimation)
        val collisionWidth = 432f
        val collisionHeight = 576f
        val fittedVisualWidth = collisionWidth * presentation.visualScale
        val fittedVisualHeight = fittedVisualWidth
        val visibleWidth = fittedVisualWidth * animation.referenceArtBounds.width
        val visibleHeight = fittedVisualHeight * animation.referenceArtBounds.height

        assertTrue(collisionWidth < visibleWidth)
        assertTrue(collisionHeight < visibleHeight)
        assertEquals(
            720f * animation.referenceArtBounds.width,
            visibleWidth,
            0.001f,
        )
        assertEquals(
            720f * animation.referenceArtBounds.height,
            visibleHeight,
            0.001f,
        )
    }

    @Test
    fun warOakVisibleArtFollowsItsAdjustedHitboxAndKeepsItsRootsOnTheBaseline() {
        val presentation = BossPresentationCatalog.definition("ancient_war_oak")
        val animation = requireNotNull(presentation.idleAnimation)
        val originalWidth = 400f
        val originalHeight = 500f
        val baselineY = 500f
        val bounds = BossPresentationLayout.frameDrawBounds(
            centerX = 200f,
            baselineY = baselineY,
            visualWidth = originalWidth * presentation.visualScale,
            visualHeight = originalHeight * presentation.visualScale,
            animation = animation,
        )
        val visibleWidth = bounds.width * animation.frameArtBounds.width
        val visibleHeight = bounds.height * animation.frameArtBounds.height
        val visibleBottom = bounds.baselineY -
            (1f - animation.frameArtBounds.bottom) * bounds.height

        assertEquals(
            originalWidth * presentation.visualScale *
                animation.referenceArtBounds.width,
            visibleWidth,
            0.001f,
        )
        assertEquals(
            originalHeight * presentation.visualScale *
                animation.referenceArtBounds.height,
            visibleHeight,
            0.001f,
        )
        assertEquals(baselineY, visibleBottom, 0.001f)
    }

    @Test
    fun expandedAnimationCanvasMapsBackToTheOriginalArtSizeAndGroundAnchor() {
        val animation = requireNotNull(
            BossPresentationCatalog.definition("ancient_war_oak").idleAnimation,
        )
        val centerX = 200f
        val baselineY = 500f
        val visualWidth = 400f
        val visualHeight = 500f
        val drawBounds = BossPresentationLayout.frameDrawBounds(
            centerX = centerX,
            baselineY = baselineY,
            visualWidth = visualWidth,
            visualHeight = visualHeight,
            animation = animation,
        )
        val frameBounds = animation.frameArtBounds
        val referenceBounds = animation.referenceArtBounds
        val artLeft = drawBounds.centerX - drawBounds.width / 2f +
            frameBounds.left * drawBounds.width
        val artRight = drawBounds.centerX - drawBounds.width / 2f +
            frameBounds.right * drawBounds.width
        val artTop = drawBounds.baselineY - drawBounds.height +
            frameBounds.top * drawBounds.height
        val artBottom = drawBounds.baselineY - drawBounds.height +
            frameBounds.bottom * drawBounds.height

        assertEquals(
            centerX - visualWidth / 2f + referenceBounds.left * visualWidth,
            artLeft,
            0.001f,
        )
        assertEquals(
            centerX - visualWidth / 2f + referenceBounds.right * visualWidth,
            artRight,
            0.001f,
        )
        assertEquals(
            baselineY - visualHeight + referenceBounds.top * visualHeight,
            artTop,
            0.001f,
        )
        assertEquals(
            baselineY - visualHeight + referenceBounds.bottom * visualHeight,
            artBottom,
            0.001f,
        )
    }
}
