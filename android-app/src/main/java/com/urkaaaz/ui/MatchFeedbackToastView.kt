package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView

internal class MatchFeedbackToastView(context: Context) : TextView(context) {
    private val hideRunnable = Runnable { fadeOut() }

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        textSize = 13f
        setTextColor(Color.rgb(239, 220, 179))
        UiTypography.applyButtonTypeface(this)
        setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.argb(220, 0, 0, 0))
        setPadding(0, dp(2), 0, dp(2))
        maxWidth = dp(320)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
        alpha = 0f
    }

    fun showFeedback(message: String?) {
        if (message.isNullOrBlank()) {
            hideImmediately()
            return
        }

        removeCallbacks(hideRunnable)
        animate().cancel()
        text = message
        contentDescription = message
        visibility = View.VISIBLE
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(FADE_IN_DURATION_MS)
            .start()
        announceForAccessibility(message)
        postDelayed(hideRunnable, DISPLAY_DURATION_MS)
    }

    fun hideImmediately() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        alpha = 0f
        visibility = View.GONE
        text = ""
        contentDescription = null
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun fadeOut() {
        animate().cancel()
        animate()
            .alpha(0f)
            .setDuration(FADE_OUT_DURATION_MS)
            .withEndAction {
                visibility = View.GONE
                text = ""
                contentDescription = null
            }
            .start()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val DISPLAY_DURATION_MS = 2_500L
        const val FADE_IN_DURATION_MS = 150L
        const val FADE_OUT_DURATION_MS = 350L
    }
}
