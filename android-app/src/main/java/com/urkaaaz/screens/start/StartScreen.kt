package com.urkaaaz.screens.start

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.navigation.ScreenContent

class StartScreen(
    context: Context,
    private val onPlay: () -> Unit,
    private val onTestMode: () -> Unit,
) : ScreenContent {
    private val screenContext = context

    override val view = FrameLayout(context).apply {
        clipChildren = false
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.bg_main_menu)
        }, FrameLayout.LayoutParams(-1, -1))
        addView(View(context).apply {
            background = resources.getDrawable(R.drawable.bg_menu_soft_focus, context.theme)
        }, FrameLayout.LayoutParams(dp(620), dp(430)).apply {
            gravity = Gravity.CENTER
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(ImageView(context).apply {
                contentDescription = context.getString(R.string.app_title)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setImageResource(R.drawable.logo_main_menu)
            }, LinearLayout.LayoutParams(dp(520), dp(125)))
            addView(ImageButton(context).apply {
                background = null
                contentDescription = context.getString(R.string.open_dashboard)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(0, 0, 0, 0)
                setImageResource(R.drawable.play_game_1)
                setOnClickListener { onPlay() }
            }, LinearLayout.LayoutParams(dp(520), dp(145)).apply {
                topMargin = dp(8)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.test_mode)
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(255, 211, 106))
                setPadding(dp(22), dp(8), dp(22), dp(8))
                background = resources.getDrawable(R.drawable.bg_hud_panel, context.theme)
                isClickable = true
                isFocusable = true
                setOnClickListener { onTestMode() }
            }, LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = dp(8)
            })
        }, FrameLayout.LayoutParams(dp(520), -2).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun dp(value: Int): Int =
        (value * screenContext.resources.displayMetrics.density).toInt()
}
