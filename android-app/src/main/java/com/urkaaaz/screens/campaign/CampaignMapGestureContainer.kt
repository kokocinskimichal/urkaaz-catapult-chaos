package com.urkaaaz.screens.campaign

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max

internal class CampaignMapGestureContainer(
    context: Context,
    private val onSwipe: (CampaignChapterNavigationDirection) -> Unit,
) : FrameLayout(context) {
    var swipeEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) isSwiping = false
        }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumSwipeDistance = 48 * resources.displayMetrics.density
        private val swipeThreshold = max(touchSlop.toFloat(), minimumSwipeDistance)
    private var downX = 0f
    private var downY = 0f
    private var isSwiping = false

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        clipChildren = false
        clipToPadding = false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isSwiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeEnabled && isHorizontalSwipe(event.x, event.y)) {
                    isSwiping = true
                    return true
                }
            }
            MotionEvent.ACTION_UP -> if (!isSwiping) resetGesture()
            MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return isSwiping
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isSwiping = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isHorizontalSwipe(event.x, event.y)) isSwiping = true
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isSwiping) {
                    chapterDirectionForSwipe(
                        deltaX = event.x - downX,
                        minimumDistance = minimumSwipeDistance,
                    )?.let(onSwipe)
                }
                resetGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetGesture()
                return true
            }
        }
        return true
    }

    private fun isHorizontalSwipe(x: Float, y: Float): Boolean {
        val deltaX = x - downX
        val deltaY = y - downY
        return isHorizontalChapterSwipe(deltaX, deltaY, swipeThreshold)
    }

    private fun resetGesture() {
        isSwiping = false
    }
}

internal fun isHorizontalChapterSwipe(
    deltaX: Float,
    deltaY: Float,
    minimumDistance: Float,
): Boolean = abs(deltaX) >= minimumDistance && abs(deltaX) > abs(deltaY)

internal fun chapterDirectionForSwipe(
    deltaX: Float,
    minimumDistance: Float,
): CampaignChapterNavigationDirection? {
    if (abs(deltaX) < minimumDistance) return null
    return if (deltaX < 0f) {
        CampaignChapterNavigationDirection.NEXT
    } else {
        CampaignChapterNavigationDirection.PREVIOUS
    }
}
