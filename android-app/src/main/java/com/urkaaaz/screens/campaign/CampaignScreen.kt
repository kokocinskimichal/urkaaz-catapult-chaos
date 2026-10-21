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
import com.urkaaaz.application.campaign.CampaignLevelSelection
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignSelectionRejection
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.UiTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CampaignScreen(
    private val context: Context,
    campaignSystem: CampaignSystem,
    private val onBack: () -> Unit,
    private val onLevelSelected: (Int) -> Unit,
) : ScreenContent {
    private val viewModel = CampaignViewModel(campaignSystem)
    private val levelsContainer = LinearLayout(context)
    private var screenScope: CoroutineScope? = null
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

    }

    init {
        render(viewModel.state)
    }

    override fun onResume() {
        if (screenScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        screenScope = scope
        scope.launch {
            render(viewModel.load())
        }
    }

    override fun onPause() {
        screenScope?.cancel()
        screenScope = null
    }

    private fun render(state: CampaignUiState) {
        levelsContainer.removeAllViews()
        if (state.isLoading) {
            addStatus(context.getString(R.string.campaign_status_loading))
            return
        }
        if (state.failure != null) {
            addStatus(progressFailureMessage(state.failure))
            addRetryButton()
            return
        }
        val progress = state.progress ?: run {
            addStatus(context.getString(R.string.campaign_status_unavailable))
            return
        }
        state.selectionRejection?.let { rejection ->
            addStatus(selectionRejectionMessage(rejection))
        }
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
                val unlocked = level <= progress.highestUnlockedLevel
                levelsContainer.addView(TextView(context).apply {
                    text = if (unlocked) {
                        context.getString(R.string.campaign_level, level)
                    } else {
                        context.getString(R.string.campaign_level_locked, level)
                    }
                    UiTypography.applyButtonTypeface(this)
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    alpha = if (unlocked) 1f else 0.42f
                    isEnabled = unlocked
                    isClickable = unlocked
                    setBackgroundResource(R.drawable.bg_hud_panel)
                    setOnClickListener {
                        screenScope?.launch {
                            when (val result = viewModel.dispatch(CampaignUiAction.SelectLevel(level))) {
                                is CampaignLevelSelection.Selected ->
                                    onLevelSelected(result.definition.level)
                                is CampaignLevelSelection.Rejected ->
                                    render(viewModel.state)
                                is CampaignLevelSelection.Failure ->
                                    render(viewModel.state)
                            }
                        }
                    }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(5), 0, dp(5))
                })
            }
        }
    }

    private fun addStatus(message: String) {
        levelsContainer.addView(TextView(context).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundResource(R.drawable.bg_hud_panel)
        }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(12), 0, dp(8))
        })
    }

    private fun addRetryButton() {
        levelsContainer.addView(TextView(context).apply {
            text = context.getString(R.string.campaign_retry)
            UiTypography.applyButtonTypeface(this)
            textSize = 14f
            setTextColor(Color.rgb(255, 211, 106))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_hud_panel)
            setOnClickListener {
                screenScope?.launch {
                    render(viewModel.load())
                }
            }
        }, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, dp(8), 0, dp(8))
        })
    }

    private fun progressFailureMessage(reason: CampaignProgressFailure): String =
        context.getString(when (reason) {
            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                R.string.campaign_progress_unavailable
            CampaignProgressFailure.INVALID_DATA ->
                R.string.campaign_progress_invalid
            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                R.string.campaign_progress_version
        })

    private fun selectionRejectionMessage(reason: CampaignSelectionRejection): String =
        context.getString(when (reason) {
            CampaignSelectionRejection.INVALID_LEVEL ->
                R.string.campaign_selection_invalid
            CampaignSelectionRejection.LEVEL_LOCKED ->
                R.string.campaign_selection_locked
        })

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
