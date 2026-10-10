package com.urkaaaz.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Compact legacy-style circular supply indicator. */
class SupplyGaugeView(context: Context) : View(context) {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(225, 193, 119)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private var supply = 0f
    private val maximum = 20f

    fun setSupply(value: Float) {
        supply = value.coerceIn(0f, maximum)
        contentDescription = "Supply ${supply.toInt()} z ${maximum.toInt()}"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.36f
        ringPaint.strokeWidth = radius * 0.23f
        ringPaint.color = Color.argb(150, 20, 25, 32)
        canvas.drawCircle(centerX, centerY, radius, ringPaint)
        ringPaint.color = Color.rgb(218, 166, 66)
        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            -90f,
            360f * (supply / maximum),
            false,
            ringPaint,
        )
        numberPaint.textSize = radius * 0.78f
        canvas.drawText("${supply.toInt()}", centerX, centerY + numberPaint.textSize * 0.34f, numberPaint)
        labelPaint.textSize = radius * 0.27f
        canvas.drawText("SUPPLY", centerX, centerY + radius * 0.82f, labelPaint)
    }
}
