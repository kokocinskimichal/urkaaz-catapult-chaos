package com.urkaaaz.tutorial

import android.content.Context
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.urkaaaz.application.tutorial.TutorialUiState

class TutorialOverlayView(
    context: Context,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    private val onViewportReady: () -> Unit = {},
) : FrameLayout(context) {
    private val spotlight = TutorialSpotlightView(context)
    private val hint = TutorialHintView(context, onNext, onSkip)
    private var hintBounds: RectF? = null
    private var touchTargetBounds: RectF? = null
    private var isTouchTargetEnabled = false

    init {
        clipChildren = false
        isClickable = true
        addView(spotlight, LayoutParams(-1, -1))
        addView(hint, LayoutParams(-2, -2))
        visibility = GONE
    }

    fun render(
        state: TutorialUiState?,
        targetBounds: RectF?,
    ): Boolean {
        if (state == null) {
            visibility = GONE
            hintBounds = null
            touchTargetBounds = null
            isTouchTargetEnabled = false
            spotlight.render(null, visible = false)
            return true
        }
        visibility = VISIBLE
        spotlight.render(targetBounds, visible = true)
        hint.render(state)
        val margin = dp(12).toFloat()
        val maximumWidth = width.toFloat() - margin * 2f
        val maximumHeight = height.toFloat() - margin * 2f
        if (width <= 0 || height <= 0) {
            requestLayout()
            return true
        }
        if (maximumWidth <= 0f || maximumHeight <= 0f) return false
        hint.measure(
            MeasureSpec.makeMeasureSpec(
                minOf(maximumWidth, dp(MAX_HINT_WIDTH_DP).toFloat()).toInt(),
                MeasureSpec.AT_MOST,
            ),
            MeasureSpec.makeMeasureSpec(maximumHeight.toInt(), MeasureSpec.AT_MOST),
        )
        val hintWidth = hint.measuredWidth.toFloat()
        val hintHeight = hint.measuredHeight.toFloat()
        val position = TutorialHintPlacer.place(
            viewportWidth = width.toFloat(),
            viewportHeight = height.toFloat(),
            hintWidth = hintWidth,
            hintHeight = hintHeight,
            target = targetBounds,
            preference = state.placementPreference,
            gap = dp(HINT_TARGET_GAP_DP).toFloat(),
            margin = margin,
        ) ?: run {
            Log.e(
                TAG,
                "Hint placement failed: viewport=${width}x$height, " +
                    "hint=${hintWidth}x$hintHeight, target=$targetBounds, " +
                    "maximumWidth=$maximumWidth, maximumHeight=$maximumHeight",
            )
            return false
        }
        hint.layoutParams = LayoutParams(
            position.width.toInt(),
            position.height.toInt(),
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = position.left.toInt()
            topMargin = position.top.toInt()
        }
        hintBounds = position.bounds
        touchTargetBounds = targetBounds?.takeIf { state.touchAllowedTargetIds.isNotEmpty() }
        isTouchTargetEnabled = touchTargetBounds != null
        requestLayout()
        invalidate()
        return true
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) onViewportReady()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (hintBounds?.contains(event.x, event.y) == true) {
            return super.dispatchTouchEvent(event)
        }
        if (isTouchTargetEnabled && touchTargetBounds?.contains(event.x, event.y) == true) {
            return false
        }
        return true
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val HINT_TARGET_GAP_DP = 24
        const val MAX_HINT_WIDTH_DP = 280
        const val TAG = "TutorialOverlayView"
    }
}
