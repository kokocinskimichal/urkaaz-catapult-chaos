package com.urkaaaz.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.urkaaaz.android.R
import kotlin.math.min
import kotlin.math.sin

internal class ManaOrbView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val shellBitmap = requireNotNull(
        BitmapFactory.decodeResource(resources, R.drawable.match_hud_mana_orb_shell),
    ) { "Unable to decode the Mana orb shell asset" }
    private val shellBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(23, 33, 45)
        style = Paint.Style.FILL
    }
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 89, 142)
        style = Paint.Style.FILL
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 220, 179)
        textAlign = Paint.Align.CENTER
        textSize = 10f * density
        typeface = UiTypography.typeface(context)
        setShadowLayer(
            2f * density,
            0f,
            1f * density,
            Color.argb(210, 10, 18, 27),
        )
    }
    private val liquidPath = Path()
    private var mana = 0
    private var maximumMana = 1
    private var rippleStartedAt = 0L

    private val rippleFrame = object : Runnable {
        override fun run() {
            if (SystemClock.uptimeMillis() - rippleStartedAt < RIPPLE_DURATION_MS && isShown) {
                invalidate()
                postOnAnimation(this)
            }
        }
    }

    fun render(current: Int, maximum: Int) {
        val safeMaximum = maximum.coerceAtLeast(1)
        val safeMana = current.coerceIn(0, safeMaximum)
        if (safeMana != mana || safeMaximum != maximumMana) {
            mana = safeMana
            maximumMana = safeMaximum
            rippleStartedAt = SystemClock.uptimeMillis()
            removeCallbacks(rippleFrame)
            postOnAnimation(rippleFrame)
            invalidate()
        }
        contentDescription = "Mana $mana of $maximumMana"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val radius = min(width * 0.38f, height * 0.30f)
        val centerY = height / 2f
        val shellSize = min(width, height).toFloat()
        val shellLeft = (width - shellSize) / 2f
        val shellTop = (height - shellSize) / 2f
        val shellBounds = RectF(
            shellLeft,
            shellTop,
            shellLeft + shellSize,
            shellTop + shellSize,
        )

        canvas.drawCircle(centerX, centerY, radius, glassPaint)
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(centerX, centerY, radius - 1f * density, Path.Direction.CW) })
        val fraction = ManaFill.fraction(mana, maximumMana)
        val bottom = centerY + radius
        val surface = bottom - (radius * 2f * fraction)
        val rippleProgress = (
            (SystemClock.uptimeMillis() - rippleStartedAt).toFloat() / RIPPLE_DURATION_MS
            ).coerceIn(0f, 1f)
        val amplitude = if (rippleProgress < 1f && mana > 0 && mana < maximumMana) {
            1.6f * density * (1f - rippleProgress)
        } else {
            0f
        }
        val phase = (SystemClock.uptimeMillis() - rippleStartedAt) / 110f
        liquidPath.reset()
        liquidPath.moveTo(centerX - radius, bottom)
        liquidPath.lineTo(centerX - radius, surface)
        for (step in 0..16) {
            val x = centerX - radius + (radius * 2f * step / 16f)
            val y = surface + sin(step / 16f * Math.PI * 2.0 + phase).toFloat() * amplitude
            liquidPath.lineTo(x, y)
        }
        liquidPath.lineTo(centerX + radius, bottom)
        liquidPath.close()
        canvas.drawPath(liquidPath, liquidPaint)
        canvas.restore()

        canvas.drawBitmap(shellBitmap, null, shellBounds, shellBitmapPaint)
        val valueBaseline = centerY -
            (valuePaint.fontMetrics.ascent + valuePaint.fontMetrics.descent) / 2f
        canvas.drawText(
            "$mana/$maximumMana",
            centerX,
            valueBaseline,
            valuePaint,
        )
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(rippleFrame)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val RIPPLE_DURATION_MS = 700L
    }
}
