package com.urkaaaz.tutorial

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.tutorial.TutorialActionLabel
import com.urkaaaz.application.tutorial.TutorialSupplementalContent
import com.urkaaaz.application.tutorial.TutorialUiState
import com.urkaaaz.ui.ButtonTypographyControl
import com.urkaaaz.ui.HudPanelStyle
import com.urkaaaz.ui.UiTypography

class TutorialHintView(
    context: Context,
    private val onNext: () -> Unit,
    private val onSkip: () -> Unit,
) : LinearLayout(context) {
    private val title = TextView(context).apply {
        text = context.getString(R.string.tutorial_title)
        textSize = 11f
        setTextColor(Color.rgb(243, 190, 62))
        UiTypography.applyButtonTypeface(this)
        includeFontPadding = false
    }
    private val progress = TextView(context).apply {
        textSize = 11f
        setTextColor(Color.rgb(245, 229, 199))
        gravity = Gravity.END
        UiTypography.applyButtonTypeface(this)
        includeFontPadding = false
    }
    private val header = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(title, LayoutParams(0, -2, 1f))
        addView(progress, LayoutParams(-2, -2))
    }
    private val message = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.rgb(245, 239, 230))
        UiTypography.applyButtonTypeface(this)
        setLineSpacing(dp(1).toFloat(), 1f)
        includeFontPadding = false
    }
    private val divider = View(context).apply {
        setBackgroundColor(Color.argb(145, 181, 143, 78))
    }
    private val supplemental = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        visibility = GONE
    }
    private val skipButton = actionButton(
        textResource = R.string.tutorial_skip,
        typographyControl = ButtonTypographyControl.TUTORIAL_SKIP,
        contentDescriptionResource = R.string.tutorial_skip_accessibility,
        backgroundResource = R.drawable.ui_button_dark_xs_short,
        textColor = Color.rgb(245, 229, 199),
        onClick = onSkip,
    )
    private val nextButton = actionButton(
        textResource = R.string.tutorial_next,
        typographyControl = ButtonTypographyControl.TUTORIAL_NEXT,
        contentDescriptionResource = R.string.tutorial_next_accessibility,
        backgroundResource = R.drawable.loadout_start_match_button_xs_short,
        textColor = Color.rgb(57, 34, 20),
        onClick = onNext,
    )
    private val actions = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(skipButton, LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(8)
        })
        addView(nextButton, LayoutParams(0, dp(48), 1f))
    }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        background = HudPanelStyle.universalFrameBackground(context)
        setPadding(dp(12), dp(8), dp(12), 0)
        HudPanelStyle.applyShadow(this, elevationDp = 4f)
        addView(header, LayoutParams(-1, -2))
        addView(divider, LayoutParams(-1, dp(1)).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        })
        addView(message, LayoutParams(-1, -2))
        addView(supplemental, LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        addView(actions, LayoutParams(-1, dp(48)).apply {
            topMargin = dp(3)
        })
    }

    fun render(state: TutorialUiState) {
        message.text = state.text
        val nextLabel = if (state.actionLabel == TutorialActionLabel.START_BATTLE) {
            R.string.tutorial_start
        } else {
            R.string.tutorial_next
        }
        nextButton.text = context.getString(nextLabel)
        UiTypography.applyTextSize(
            nextButton,
            if (state.actionLabel == TutorialActionLabel.START_BATTLE) {
                ButtonTypographyControl.TUTORIAL_START
            } else {
                ButtonTypographyControl.TUTORIAL_NEXT
            },
        )
        nextButton.contentDescription = context.getString(
            if (state.actionLabel == TutorialActionLabel.START_BATTLE) {
                R.string.tutorial_start_accessibility
            } else {
                R.string.tutorial_next_accessibility
            },
        )
        nextButton.ellipsize = TextUtils.TruncateAt.END
        progress.text = context.getString(
            R.string.tutorial_step_progress,
            state.stepIndex,
            state.stepCount,
        )
        contentDescription = context.getString(
            R.string.tutorial_hint_accessibility,
            state.stepIndex,
            state.stepCount,
            state.text,
        )
        skipButton.visibility = if (state.skipAvailable) VISIBLE else GONE
        nextButton.visibility = if (
            state.touchAllowedTargetIds.isEmpty()
        ) {
            VISIBLE
        } else {
            GONE
        }
        when (state.supplementalContent) {
            TutorialSupplementalContent.WIND_STRENGTH_LEGEND -> showWindLegend()
            null -> supplemental.visibility = GONE
        }
    }

    private fun actionButton(
        textResource: Int,
        typographyControl: ButtonTypographyControl,
        contentDescriptionResource: Int,
        backgroundResource: Int,
        textColor: Int,
        onClick: () -> Unit,
    ) = TextView(context).apply {
        text = context.getString(textResource)
        contentDescription = context.getString(contentDescriptionResource)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(6), 0, dp(6), 0)
        UiTypography.applyTextSize(this, typographyControl)
        minHeight = dp(48)
        setTextColor(textColor)
        UiTypography.applyButtonTypeface(this)
        setBackgroundResource(backgroundResource)
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun showWindLegend() {
        supplemental.removeAllViews()
        listOf(
            R.drawable.wind_low_right to R.string.tutorial_wind_low,
            R.drawable.wind_medium_right to R.string.tutorial_wind_medium,
            R.drawable.wind_high_right to R.string.tutorial_wind_high,
        ).forEachIndexed { index, (iconResource, labelResource) ->
            supplemental.addView(
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    addView(ImageView(context).apply {
                        contentDescription = null
                        setImageResource(iconResource)
                    }, LayoutParams(dp(24), dp(24)))
                    addView(TextView(context).apply {
                        text = context.getString(labelResource)
                        textSize = 9f
                        setTextColor(Color.WHITE)
                        UiTypography.applyButtonTypeface(this)
                    }, LayoutParams(-2, -2).apply { topMargin = dp(1) })
                },
                LayoutParams(0, -2, 1f).apply {
                    if (index > 0) marginStart = dp(8)
                },
            )
        }
        supplemental.visibility = VISIBLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
