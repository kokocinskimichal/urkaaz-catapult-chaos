package com.urkaaaz.screens.dashboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.navigation.ScreenContent

class DashboardScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onCampaign: () -> Unit,
    private val onShop: () -> Unit,
) : ScreenContent {
    private val viewModel = DashboardViewModel()

    override val view = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.dashboard_background)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(topHud(), FrameLayout.LayoutParams(-1, dp(86)).apply {
            gravity = Gravity.TOP
        })

        addView(ImageButton(context).apply {
            background = null
            contentDescription = "Player avatar"
            foreground = context.getDrawable(R.drawable.dashboard_hud_portrait_frame)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(R.drawable.player_avatar_01)
        }, FrameLayout.LayoutParams(dp(91), dp(91)).apply {
            gravity = Gravity.TOP or Gravity.START
            marginStart = dp(24)
        })

        addView(Button(context).apply {
            text = "RESET"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            minHeight = 0
            minWidth = 0
            background = context.getDrawable(R.drawable.bg_hud_panel)
        }, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(96)
            marginEnd = dp(16)
        })

        addView(bottomActions(), FrameLayout.LayoutParams(-1, dp(104)).apply {
            gravity = Gravity.BOTTOM
        })
    }

    private fun topHud(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.dashboard_hud_top_panel)
        setPadding(dp(132), 0, dp(8), 0)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(image(R.drawable.icon_lives_heart, 42, 42))
            addView(label("5", 16f), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
            addView(Button(context).apply {
                text = "RESET"
                textSize = 9f
                setTextColor(Color.WHITE)
                setPadding(dp(7), dp(3), dp(7), dp(3))
                minHeight = 0
                minWidth = 0
                background = context.getDrawable(R.drawable.bg_hud_panel)
            }, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
        })
        addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(image(R.drawable.icon_next_life_hourglass, 36, 36))
            addView(label("FULL", 13f), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
        })
        addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(image(R.drawable.icon_orcoins, 50, 42))
            addView(label("0", 16f), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
        })
        addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
    }

    private fun bottomActions(): View = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            alpha = 0.38f
            scaleType = ImageView.ScaleType.FIT_XY
            setImageResource(R.drawable.dashboard_background)
        }, FrameLayout.LayoutParams(-1, -1))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            addView(actionButton(R.drawable.dashboard_back_button, "Back", onBack), weightParams())
            addView(actionButton(R.drawable.dashboard_shop_button, "Shop", onShop), weightParams(dp(10)))
            addView(actionButton(R.drawable.dashboard_campaign_button, "Campaign", onCampaign), weightParams(dp(12), 78))
            addView(actionButton(R.drawable.dashboard_ad_reward_button, "Watch ad", {}), weightParams(dp(12)))
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun actionButton(resource: Int, description: String, action: () -> Unit): ImageButton =
        ImageButton(context).apply {
            background = null
            contentDescription = description
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(resource)
            setOnClickListener { action() }
        }

    private fun weightParams(marginStart: Int = 0, height: Int = 64): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(height), 1f).apply {
            if (marginStart > 0) this.marginStart = marginStart
        }

    private fun image(resource: Int, width: Int, height: Int) = ImageView(context).apply {
        contentDescription = null
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(resource)
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
    }

    private fun label(value: String, size: Float) = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        includeFontPadding = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
