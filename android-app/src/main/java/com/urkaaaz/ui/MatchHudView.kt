package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R

class MatchHudView(context: Context) : FrameLayout(context) {
    private val timerText = hudText("0:00", 12f).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val windIndicator = ImageView(context).apply {
        setImageResource(R.drawable.wind_low_right)
        contentDescription = "Wind direction and strength"
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val pauseButton = Button(context).apply {
        text = "II"
        textSize = 10f
        setTextColor(Color.WHITE)
        background = panelBackground(Color.argb(150, 61, 72, 78))
        minWidth = 28
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
    }
    private val timerPanel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        background = panelBackground(Color.argb(210, 36, 43, 47))
        setPadding(6, 2, 6, 2)
        addView(timerText, LinearLayout.LayoutParams(-2, 32))
        addView(
            pauseButton,
            LinearLayout.LayoutParams(32, 30).apply {
                marginStart = 4
                gravity = Gravity.CENTER_VERTICAL
            },
        )
    }

    init {
        clipChildren = false
        clipToPadding = false
        elevation = 8f
        addView(
            timerPanel,
            LayoutParams(-2, 36).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },
        )
        addView(
            windIndicator,
            LayoutParams(208, 136).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 38
            },
        )
        pauseButton.setOnClickListener { onPauseClicked?.invoke() }
    }

    var onPauseClicked: (() -> Unit)? = null

    fun render(state: RenderState) {
        timerText.text = formatMatchTime(state.matchTimeRemainingMilliseconds)
        val visible = state.statusLabel == "RUNNING" || state.statusLabel == "PAUSED"
        timerPanel.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        windIndicator.visibility = timerPanel.visibility
        windIndicator.setImageResource(
            windIndicatorResource(state.windDirection, state.windStrength),
        )
        pauseButton.text = if (state.statusLabel == "PAUSED") "▶" else "II"
    }

    private fun formatMatchTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    private fun windIndicatorResource(direction: Float, strength: Float): Int {
        val isLeft = direction < 0f
        return when {
            strength >= 0.75f && isLeft -> R.drawable.wind_high_left
            strength >= 0.75f -> R.drawable.wind_high_right
            strength >= 0.5f && isLeft -> R.drawable.wind_medium_left
            strength >= 0.5f -> R.drawable.wind_medium_right
            isLeft -> R.drawable.wind_low_left
            else -> R.drawable.wind_low_right
        }
    }

    private fun hudText(value: String, size: Float) = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(Color.rgb(239, 220, 179))
        setPadding(4, 2, 4, 2)
    }

    private fun panelBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 10f
        setStroke(1, Color.argb(120, 255, 235, 190))
    }
}
