package com.urkaaaz.tutorial

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.View

class TutorialSpotlightView(context: Context) : View(context) {
    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt()
    }
    private val featheredClearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        maskFilter = BlurMaskFilter(dp(FEATHER_RADIUS_DP), BlurMaskFilter.Blur.NORMAL)
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val featheredTargetBounds = RectF()
    private var targetBounds: RectF? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun render(bounds: RectF?, visible: Boolean) {
        targetBounds = bounds?.let(::RectF)
        visibility = if (visible) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        targetBounds?.let { target ->
            val feather = dp(FEATHER_RADIUS_DP)
            featheredTargetBounds.set(
                target.left - feather,
                target.top - feather,
                target.right + feather,
                target.bottom + feather,
            )
            canvas.drawRoundRect(
                featheredTargetBounds,
                dp(TARGET_CORNER_RADIUS_DP + FEATHER_RADIUS_DP),
                dp(TARGET_CORNER_RADIUS_DP + FEATHER_RADIUS_DP),
                featheredClearPaint,
            )
            canvas.drawRect(target, clearPaint)
        }
        canvas.restoreToCount(layer)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private companion object {
        const val FEATHER_RADIUS_DP = 30f
        const val TARGET_CORNER_RADIUS_DP = 14f
    }
}
