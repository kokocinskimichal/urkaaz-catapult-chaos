package com.urkaaaz.screens.campaign

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.navigation.ScreenContent

class CampaignScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onLevelSelected: (Int) -> Unit,
) : ScreenContent {
    private val viewModel = CampaignViewModel()
    private val levelsContainer = LinearLayout(context)
    private val chapterNames = listOf(
        "The Green Frontier",
        "The Broken Valley",
        "The Ashen March",
        "The Whispering Marsh",
        "The Ironwood",
        "The Northern March",
        "The Runic Wastes",
        "The Red Peaks",
        "The Final Warpath",
        "The Last Siege",
    )

    override val view = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.bg_main_menu)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(levelsContainer.apply {
                    orientation = LinearLayout.VERTICAL
                }, FrameLayout.LayoutParams(-1, -2))
            }, LinearLayout.LayoutParams(dp(420), 0, 1f).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(14)
            })
        }, FrameLayout.LayoutParams(dp(420), -1).apply {
            gravity = Gravity.CENTER
        })

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            addView(ImageButton(context).apply {
                background = null
                contentDescription = "Back to dashboard"
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.dashboard_back_button)
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(0, dp(64), 1f))
        }, FrameLayout.LayoutParams(-1, dp(104)).apply {
            gravity = Gravity.BOTTOM
        })

        render(viewModel.state)
    }

    private fun render(state: CampaignUiState) {
        levelsContainer.removeAllViews()
        val chapterCount =
            (CampaignLevelDefinition.MAX_LEVELS + 9) / 10
        for (chapter in 1..chapterCount) {
            val chapterName = chapterNames.getOrElse(chapter - 1) {
                "Chapter $chapter"
            }
            levelsContainer.addView(TextView(context).apply {
                text = context.getString(
                    R.string.campaign_chapter_header,
                    chapter,
                    chapterName,
                )
                textSize = 18f
                setTextColor(Color.rgb(255, 211, 106))
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(14), dp(12), dp(10))
                setBackgroundResource(R.drawable.bg_hud_panel)
                setShadowLayer(8f, 0f, 2f, Color.BLACK)
            }, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(12), 0, dp(4))
            })

            val firstLevel = (chapter - 1) * 10 + 1
            val lastLevel = minOf(
                chapter * 10,
                CampaignLevelDefinition.MAX_LEVELS,
            )
            for (level in firstLevel..lastLevel) {
                val unlocked = level <= state.highestUnlockedLevel
                levelsContainer.addView(TextView(context).apply {
                    text = if (unlocked) {
                        context.getString(R.string.campaign_level, level)
                    } else {
                        context.getString(R.string.campaign_level_locked, level)
                    }
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    alpha = if (unlocked) 1f else 0.42f
                    isEnabled = unlocked
                    isClickable = unlocked
                    setBackgroundResource(R.drawable.bg_hud_panel)
                    setOnClickListener {
                        viewModel.dispatch(CampaignUiAction.SelectLevel(level))
                        onLevelSelected(level)
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(5), 0, dp(5))
                })
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
