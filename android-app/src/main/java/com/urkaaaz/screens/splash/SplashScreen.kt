package com.urkaaaz.screens.splash

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.urkaaaz.android.R
import com.urkaaaz.navigation.ScreenContent

class SplashScreen(
    private val context: Context,
    private val onFinished: () -> Unit,
) : ScreenContent {
    private val handler = Handler(Looper.getMainLooper())
    private var isResumed = false
    private var hasPendingTransition = false
    private var hasFinished = false

    private val gameLogo = ImageView(context).apply {
        contentDescription = context.getString(R.string.splash_game_logo_accessibility)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.splash_game_logo)
    }
    private val companyLogo = ImageView(context).apply {
        contentDescription = context.getString(R.string.splash_company_logo_accessibility)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.splash_company_logo)
    }
    private val logoStack = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(gameLogo, LinearLayout.LayoutParams(-1, dp(188)))
        addView(
            companyLogo,
            LinearLayout.LayoutParams(dp(132), dp(47)).apply {
                topMargin = dp(12)
            },
        )
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

        addView(
            logoStack,
            FrameLayout.LayoutParams(
                -1,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ).apply {
                leftMargin = dp(24)
                rightMargin = dp(24)
            },
        )
    }

    override fun onResume() {
        isResumed = true
        if (hasFinished || hasPendingTransition) return

        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(FADE_DURATION_MILLISECONDS).start()
        hasPendingTransition = true
        handler.postDelayed(finishTransition, DISPLAY_DURATION_MILLISECONDS)
    }

    override fun onPause() {
        isResumed = false
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
    }
}
