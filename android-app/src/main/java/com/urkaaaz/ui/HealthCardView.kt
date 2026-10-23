package com.urkaaaz.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.urkaaaz.android.R

internal class HealthCardView(
    context: Context,
    label: String,
    healthColor: Int,
) : LinearLayout(context) {
    private val healthValue = hudText("--/--", 7f).apply {
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val healthProgress = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal,
    ).apply {
        max = 100
        progressTintList = ColorStateList.valueOf(healthColor)
        progressBackgroundTintList =
            ColorStateList.valueOf(Color.argb(100, 9, 12, 15))
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        background = HudPanelStyle.universalFrameBackground(context)
        HudPanelStyle.applyShadow(this, elevationDp = 2f)
        setPadding(dp(4), dp(1), dp(4), dp(1))
        addView(
            FrameLayout(context).apply {
                addView(
                    hudText(label, 7f).apply {
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                    },
                    FrameLayout.LayoutParams(-1, -1),
                )
                addView(
                    healthValue,
                    FrameLayout.LayoutParams(
                        -2,
                        dp(11),
                        Gravity.END or Gravity.CENTER_VERTICAL,
                    ).apply {
                        marginEnd = dp(5)
                    },
                )
            },
            LayoutParams(-1, dp(11)),
        )
        addView(
            healthProgress,
            LayoutParams(-1, dp(2)).apply {
                topMargin = dp(1)
                marginStart = dp(4)
                marginEnd = dp(4)
            },
        )
    }

    fun render(current: Int, maximum: Int) {
        val safeMaximum = maximum.coerceAtLeast(0)
        healthValue.text = if (safeMaximum == 0) "--/--" else "$current/$safeMaximum"
        healthProgress.max = safeMaximum.coerceAtLeast(1)
        healthProgress.progress = current.coerceIn(0, healthProgress.max)
    }

    private fun hudText(value: String, size: Float) = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(Color.rgb(239, 220, 179))
        setPadding(dp(2), dp(1), dp(2), dp(1))
        UiTypography.applyButtonTypeface(this)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
