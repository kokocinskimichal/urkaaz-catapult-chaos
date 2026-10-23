package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.urkaaaz.android.R

class MatchHudView(context: Context) : LinearLayout(context) {
    private val timerText = hudText("0:00", 13f).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val windIndicator = ImageView(context).apply {
        setImageResource(R.drawable.wind_low_right)
        contentDescription = "Wind direction and strength"
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val retreatButton = ImageButton(context).apply {
        setImageResource(R.drawable.match_hud_retreat_runner)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setBackgroundColor(Color.TRANSPARENT)
        minimumWidth = 0
        minimumHeight = 0
        setPadding(dp(4), dp(4), dp(4), dp(4))
        contentDescription = context.getString(R.string.match_hud_retreat_accessibility)
    }
    private val pauseButton = Button(context).apply {
        text = "II"
        UiTypography.applyButtonTypeface(this)
        textSize = 8f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        contentDescription = "Pause match"
    }
    private val blueHealth = HealthCardView(
        context = context,
        label = context.getString(R.string.match_hud_blue),
        healthColor = Color.rgb(74, 153, 211),
    )
    private val redHealth = HealthCardView(
        context = context,
        label = context.getString(R.string.match_hud_red),
        healthColor = Color.rgb(188, 76, 61),
    )

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(dp(6), dp(2), dp(6), dp(2))
        addView(
            blueHealth,
            LayoutParams(0, dp(25), 0.45f),
        )
        addView(
            Space(context),
            LayoutParams(0, dp(25), 0.55f),
        )
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                background = HudPanelStyle.universalFrameBackground(context)
                HudPanelStyle.applyShadow(this, elevationDp = 2f)
                setPadding(dp(4), dp(1), dp(4), dp(1))
                addView(
                    retreatButton,
                    LayoutParams(dp(32), dp(32)).apply {
                        marginEnd = dp(2)
                    },
                )
                addView(
                    View(context).apply {
                        setBackgroundColor(Color.argb(88, 181, 143, 78))
                    },
                    LayoutParams(dp(1), dp(22)).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    },
                )
                addView(timerText, LayoutParams(dp(40), -1))
                addView(windIndicator, LayoutParams(dp(28), dp(28)))
                addView(
                    View(context).apply {
                        setBackgroundColor(Color.argb(88, 181, 143, 78))
                    },
                    LayoutParams(dp(1), dp(22)).apply {
                        marginStart = dp(2)
                        marginEnd = dp(2)
                    },
                )
                addView(
                    pauseButton,
                    LayoutParams(dp(32), dp(32)).apply {
                        marginStart = dp(2)
                    },
                )
            },
            LayoutParams(dp(176), dp(36)),
        )
        addView(
            Space(context),
            LayoutParams(0, dp(25), 0.55f),
        )
        addView(
            redHealth,
            LayoutParams(0, dp(25), 0.45f),
        )
        retreatButton.setOnClickListener { onRetreatClicked?.invoke() }
        pauseButton.setOnClickListener { onPauseClicked?.invoke() }
    }

    var onRetreatClicked: (() -> Unit)? = null
    var onPauseClicked: (() -> Unit)? = null

    fun render(state: RenderState) {
        timerText.text = formatMatchTime(state.matchTimeRemainingMilliseconds)
        val visible = state.statusLabel == "RUNNING" || state.statusLabel == "PAUSED"
        visibility = if (visible) View.VISIBLE else View.INVISIBLE
        windIndicator.setImageResource(
            windIndicatorResource(state.windDirection, state.windStrength),
        )
        val windStrengthLabel = when {
            state.windStrength >= 0.75f -> context.getString(R.string.match_hud_wind_high)
            state.windStrength >= 0.5f -> context.getString(R.string.match_hud_wind_medium)
            else -> context.getString(R.string.match_hud_wind_low)
        }
        windIndicator.contentDescription = context.getString(
            R.string.match_hud_wind_description,
            if (state.windDirection < 0f) {
                context.getString(R.string.match_hud_wind_left)
            } else {
                context.getString(R.string.match_hud_wind_right)
            },
            windStrengthLabel,
        )
        pauseButton.text = if (state.statusLabel == "PAUSED") "▶" else "II"
        pauseButton.contentDescription = context.getString(
            if (state.statusLabel == "PAUSED") {
                R.string.match_hud_resume
            } else {
                R.string.match_hud_pause
            },
        )
        blueHealth.render(state.blueFortressHealth, state.blueFortressMaxHealth)
        redHealth.render(state.redFortressHealth, state.redFortressMaxHealth)
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
        setPadding(dp(2), dp(1), dp(2), dp(1))
        UiTypography.applyButtonTypeface(this)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

}
