package com.urkaaaz.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.urkaaaz.android.R
import com.urkaaaz.application.loadout.LoadoutCatalog
import kotlin.math.min

class SupplyGaugeView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val cratePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 176, 77)
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 232, 176)
        textAlign = Paint.Align.CENTER
        typeface = UiTypography.typeface(context)
    }
    private var supply = 0
    private val maximum = LoadoutCatalog.maximumSupply

    fun setSupply(value: Int) {
        supply = value.coerceIn(0, maximum)
        contentDescription = context.getString(
            R.string.match_hud_supply_description,
            supply,
            maximum,
        )
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val ringRadius = min(width * 0.32f, height * 0.29f)
        val centerY = ringRadius + 4f * density
        ringPaint.strokeWidth = 3f * density
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
            360f * supply / maximum,
            false,
            ringPaint,
        )
        drawCrate(canvas, centerX, centerY)
        valuePaint.textSize = 9f * density
        canvas.drawText(
            "$supply/$maximum",
            centerX,
            height - 2f * density,
            valuePaint,
        )
    }

    private fun drawCrate(canvas: Canvas, centerX: Float, centerY: Float) {
        val halfWidth = 8f * density
        val halfHeight = 6f * density
        canvas.drawRect(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight,
            cratePaint,
        )
        canvas.drawLine(
            centerX - halfWidth,
            centerY,
            centerX + halfWidth,
            centerY,
            cratePaint,
        )
        canvas.drawLine(
            centerX,
            centerY - halfHeight,
            centerX,
            centerY + halfHeight,
            cratePaint,
        )
        canvas.drawLine(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight,
            cratePaint,
        )
        canvas.drawLine(
            centerX + halfWidth,
            centerY - halfHeight,
            centerX - halfWidth,
            centerY + halfHeight,
            cratePaint,
        )
    }
}
