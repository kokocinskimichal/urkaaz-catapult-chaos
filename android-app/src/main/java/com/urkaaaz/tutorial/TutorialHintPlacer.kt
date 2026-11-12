package com.urkaaaz.tutorial

import android.graphics.RectF
import com.urkaaaz.application.tutorial.TutorialPlacementPreference

data class TutorialHintPlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val bounds: RectF
        get() = RectF(left, top, left + width, top + height)
}

object TutorialHintPlacer {
    fun place(
        viewportWidth: Float,
        viewportHeight: Float,
        hintWidth: Float,
        hintHeight: Float,
        target: RectF?,
        preference: TutorialPlacementPreference,
        gap: Float,
        margin: Float,
    ): TutorialHintPlacement? {
        if (
            viewportWidth <= 0f || viewportHeight <= 0f ||
            hintWidth <= 0f || hintHeight <= 0f ||
            hintWidth + margin * 2f > viewportWidth ||
            hintHeight + margin * 2f > viewportHeight
        ) {
            return null
        }
        val candidates = candidates(
            viewportWidth,
            viewportHeight,
            hintWidth,
            hintHeight,
            target,
            preference,
            gap,
            margin,
        )
        return candidates.firstOrNull { candidate ->
            candidate.left >= margin &&
                candidate.top >= margin &&
                candidate.bounds.right <= viewportWidth - margin &&
                candidate.bounds.bottom <= viewportHeight - margin &&
                (target == null || !RectF.intersects(candidate.bounds, target))
        }
    }

    private fun candidates(
        viewportWidth: Float,
        viewportHeight: Float,
        hintWidth: Float,
        hintHeight: Float,
        target: RectF?,
        preference: TutorialPlacementPreference,
        gap: Float,
        margin: Float,
    ): List<TutorialHintPlacement> {
        val centeredLeft = (viewportWidth - hintWidth) / 2f
        val centeredTop = (viewportHeight - hintHeight) / 2f
        if (target == null) {
            return listOf(
                TutorialHintPlacement(centeredLeft, centeredTop, hintWidth, hintHeight),
            )
        }
        val aboveRegionBottom = target.top - gap
        val belowRegionTop = target.bottom + gap
        val leftRegionRight = target.left - gap
        val rightRegionLeft = target.right + gap
        val above = TutorialHintPlacement(
            centeredLeft,
            margin + (aboveRegionBottom - margin - hintHeight) / 2f,
            hintWidth,
            hintHeight,
        )
        val below = TutorialHintPlacement(
            centeredLeft,
            belowRegionTop +
                (viewportHeight - margin - belowRegionTop - hintHeight) / 2f,
            hintWidth,
            hintHeight,
        )
        val left = TutorialHintPlacement(
            margin + (leftRegionRight - margin - hintWidth) / 2f,
            centeredTop,
            hintWidth,
            hintHeight,
        )
        val right = TutorialHintPlacement(
            rightRegionLeft +
                (viewportWidth - margin - rightRegionLeft - hintWidth) / 2f,
            centeredTop,
            hintWidth,
            hintHeight,
        )
        val sideCandidates = if (
            leftRegionRight - margin >= viewportWidth - margin - rightRegionLeft
        ) {
            listOf(left, right)
        } else {
            listOf(right, left)
        }
        val ordered = when (preference) {
            TutorialPlacementPreference.AUTO -> sideCandidates + listOf(above, below)
            TutorialPlacementPreference.ABOVE -> listOf(above, below, left, right)
            TutorialPlacementPreference.BELOW -> listOf(below, above, left, right)
            TutorialPlacementPreference.LEFT -> listOf(left, right, above, below)
            TutorialPlacementPreference.RIGHT -> listOf(right, left, above, below)
        }
        return ordered + TutorialHintPlacement(
            centeredLeft,
            centeredTop,
            hintWidth,
            hintHeight,
        )
    }
}
