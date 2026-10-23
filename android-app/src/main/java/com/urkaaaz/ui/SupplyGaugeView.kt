package com.urkaaaz.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.urkaaaz.android.R
import com.urkaaaz.application.loadout.LoadoutCatalog
import kotlin.math.min

class SupplyGaugeView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val supplyIconBitmap = requireNotNull(
        BitmapFactory.decodeResource(resources, R.drawable.match_hud_supply_icon),
    ) { "Unable to decode the Supply icon asset" }
    private val supplyIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val counterBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 14, 13, 12)
        style = Paint.Style.FILL
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 232, 176)
        textAlign = Paint.Align.CENTER
        typeface = UiTypography.typeface(context)
        setShadowLayer(
            2f * density,
            0f,
            1f * density,
            Color.argb(210, 10, 18, 27),
        )
    }
    private var supply = 0
    private var animatedSupply = 0f
    private var supplyAnimator: ValueAnimator? = null
    private val maximum = LoadoutCatalog.maximumSupply

    fun setSupply(value: Int) {
        val nextSupply = value.coerceIn(0, maximum)
        contentDescription = context.getString(
            R.string.match_hud_supply_description,
            nextSupply,
            maximum,
        )
        if (nextSupply == supply) return

        supply = nextSupply
        supplyAnimator?.cancel()
        supplyAnimator = ValueAnimator.ofFloat(animatedSupply, supply.toFloat()).apply {
            duration = SUPPLY_PROGRESS_ANIMATION_MILLISECONDS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                animatedSupply = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val ringRadius = min(width * 0.42f, height * 0.39f)
        ringPaint.strokeWidth = 2.5f * density
        ringPaint.color = Color.argb(125, 20, 25, 32)
        canvas.drawCircle(centerX, centerY, ringRadius, ringPaint)
        ringPaint.color = Color.rgb(218, 166, 66)
        canvas.drawArc(
            RectF(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius,
            ),
            -90f,
            360f * animatedSupply / maximum,
            false,
            ringPaint,
        )

        val iconSize = min(width * 0.625f, height * 0.625f)
        val iconBounds = RectF(
            centerX - iconSize / 2f,
            centerY - iconSize / 2f,
            centerX + iconSize / 2f,
            centerY + iconSize / 2f,
        )
        canvas.drawBitmap(supplyIconBitmap, null, iconBounds, supplyIconPaint)

        valuePaint.textSize = 10f * density
        val value = "$supply/$maximum"
        val fontMetrics = valuePaint.fontMetrics
        val valueBaseline = centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val valueHalfWidth = (
            valuePaint.measureText(value) / 2f
                + COUNTER_HORIZONTAL_PADDING_DP * density
            )
        val valueHalfHeight = (
            (fontMetrics.descent - fontMetrics.ascent) / 2f
                + COUNTER_VERTICAL_PADDING_DP * density
            )
        canvas.drawRoundRect(
            RectF(
                centerX - valueHalfWidth,
                centerY - valueHalfHeight,
                centerX + valueHalfWidth,
                centerY + valueHalfHeight,
            ),
            COUNTER_CORNER_RADIUS_DP * density,
            COUNTER_CORNER_RADIUS_DP * density,
            counterBackgroundPaint,
        )
        canvas.drawText(value, centerX, valueBaseline, valuePaint)
    }

    override fun onDetachedFromWindow() {
        supplyAnimator?.cancel()
        supplyAnimator = null
        animatedSupply = supply.toFloat()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SUPPLY_PROGRESS_ANIMATION_MILLISECONDS = 300L
        const val COUNTER_HORIZONTAL_PADDING_DP = 3f
        const val COUNTER_VERTICAL_PADDING_DP = 2f
        const val COUNTER_CORNER_RADIUS_DP = 3f
    }
}
