package com.urkaaaz.screens.splash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.UiTypography

class SplashScreen(
    private val context: Context,
    private val onFinished: () -> Unit,
) : ScreenContent {
    private val handler = Handler(Looper.getMainLooper())
    private var isResumed = false
    private var hasPendingTransition = false
    private var hasFinished = false

    private val runeIcon = ImageView(context).apply {
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.splash_rune_art)
    }
    private val companyLogo = ImageView(context).apply {
        contentDescription = context.getString(R.string.splash_company_logo_accessibility)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.splash_company_logo)
    }

    private val runePulse = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = RUNE_PULSE_DURATION_MILLISECONDS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            val pulse = it.animatedValue as Float
            runeIcon.alpha = 0.72f + pulse * 0.28f
        }
    }

    private val finishTransition = Runnable {
        hasPendingTransition = false
        if (!isResumed || hasFinished) return@Runnable

        view.animate()
            .alpha(0f)
            .setDuration(FADE_DURATION_MILLISECONDS)
            .withEndAction {
                if (isResumed && !hasFinished) {
                    hasFinished = true
                    onFinished()
                }
            }
            .start()
    }

    override val view = FrameLayout(context).apply {
        setBackgroundColor(Color.rgb(10, 11, 15))
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.splash_background)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER

            addView(runeIcon, LinearLayout.LayoutParams(dp(176), dp(176)))

            addView(TextView(context).apply {
                text = context.getString(R.string.dashboard_title)
                contentDescription = context.getString(R.string.app_name)
                textSize = 28f
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(Color.rgb(230, 195, 115))
                setShadowLayer(dp(8).toFloat(), 0f, 0f, Color.rgb(130, 89, 34))
                UiTypography.applyButtonTypeface(this)
            }, LinearLayout.LayoutParams(-2, dp(44)).apply {
                topMargin = dp(8)
            })
        }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        addView(View(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = context.getDrawable(R.drawable.splash_company_logo_glow)
        }, FrameLayout.LayoutParams(dp(204), dp(92)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        })

        addView(companyLogo, FrameLayout.LayoutParams(dp(160), dp(58)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = dp(20)
            bottomMargin = dp(18)
        })
    }

    override fun onResume() {
        isResumed = true
        if (hasFinished || hasPendingTransition) return

        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(FADE_DURATION_MILLISECONDS).start()
        runePulse.start()
        hasPendingTransition = true
        handler.postDelayed(finishTransition, DISPLAY_DURATION_MILLISECONDS)
    }

    override fun onPause() {
        isResumed = false
        runePulse.cancel()
        if (!hasFinished) {
            handler.removeCallbacks(finishTransition)
            hasPendingTransition = false
            view.animate().cancel()
            view.alpha = 1f
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val DISPLAY_DURATION_MILLISECONDS = 2_200L
        const val FADE_DURATION_MILLISECONDS = 300L
        const val RUNE_PULSE_DURATION_MILLISECONDS = 1_400L
    }
}
