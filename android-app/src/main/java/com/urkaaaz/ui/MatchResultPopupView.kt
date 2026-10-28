package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.urkaaaz.android.R
import kotlin.math.min

internal class MatchResultPopupView(
    context: Context,
    private val onAction: (MatchResultActionType) -> Unit,
) : FrameLayout(context) {
    private val panel = FrameLayout(context)
    private val contextLabel = popupTextView(14f)
    private val victoryIllustration = ImageView(context)
    private val defeatIllustration = ImageView(context)
    private val titlePlaque = ImageView(context)
    private val titleLabel = popupTextView(26f)
    private val rewardPanel = View(context)
    private val rewardLabel = popupTextView(11f)
    private val statusLabel = popupTextView(12f)
    private val actionButtons = mutableListOf<TextView>()
    private var currentState: MatchResultPopupState? = null
    private var currentOverlayWidth = 0
    private var currentOverlayHeight = 0
    private var currentPanelWidth = 0
    private var currentPanelHeight = 0

    init {
        setBackgroundColor(Color.argb(225, 0, 0, 0))
        isClickable = true
        isFocusable = true
        setOnClickListener { }
        elevation = dp(48f).toFloat()
        translationZ = dp(48f).toFloat()

        val panelImage = ImageView(context).apply {
            setImageResource(R.drawable.match_result_popup_panel)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        victoryIllustration.apply {
            setImageResource(R.drawable.match_result_victory_illustration)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        defeatIllustration.apply {
            setImageResource(R.drawable.match_result_defeat_illustration)
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        panel.addView(victoryIllustration)
        panel.addView(defeatIllustration)
        panel.addView(panelImage, LayoutParams(-1, -1))
        titlePlaque.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        panel.addView(titlePlaque)
        panel.addView(contextLabel)
        panel.addView(titleLabel)
        rewardPanel.background = GradientDrawable().apply {
            setColor(Color.argb(150, 12, 9, 7))
            cornerRadius = dp(8f).toFloat()
            setStroke(dp(1f), Color.argb(165, 243, 210, 138))
        }
        panel.addView(rewardPanel)
        panel.addView(rewardLabel)
        panel.addView(statusLabel)
        panel.clipChildren = false
        addView(panel, LayoutParams(0, 0, Gravity.CENTER))

        statusLabel.visibility = View.GONE
        titleLabel.setShadowLayer(dp(2f).toFloat(), 0f, dp(1f).toFloat(), Color.BLACK)
        contextLabel.setShadowLayer(dp(1f).toFloat(), 0f, dp(1f).toFloat(), Color.BLACK)
    }

    fun render(state: MatchResultPopupState) {
        if (state == currentState) return
        currentState = state
        victoryIllustration.visibility = if (state.result == MatchResultKind.VICTORY) {
            View.VISIBLE
        } else {
            View.GONE
        }
        defeatIllustration.visibility = if (state.result == MatchResultKind.DEFEAT) {
            View.VISIBLE
        } else {
            View.GONE
        }
        contextLabel.text = context.getString(
            R.string.match_result_level_context,
            state.campaignLevel,
            state.biomeName,
        )
        titleLabel.text = context.getString(
            when (state.result) {
                MatchResultKind.VICTORY -> R.string.match_result_victory
                MatchResultKind.DEFEAT -> R.string.match_result_defeat
                MatchResultKind.DRAW -> R.string.match_result_draw
            },
        )
        val plaqueResource = when (state.result) {
            MatchResultKind.VICTORY -> R.drawable.match_result_victory_plaque
            MatchResultKind.DEFEAT -> R.drawable.match_result_defeat_plaque
            MatchResultKind.DRAW -> null
        }
        if (plaqueResource == null) {
            titlePlaque.visibility = View.GONE
        } else {
            titlePlaque.setImageResource(plaqueResource)
            titlePlaque.visibility = View.VISIBLE
        }
        titleLabel.setTextColor(
            when (state.result) {
                MatchResultKind.VICTORY -> Color.rgb(243, 210, 138)
                MatchResultKind.DEFEAT -> Color.rgb(226, 132, 106)
                MatchResultKind.DRAW -> Color.rgb(232, 218, 190)
            },
        )
        rewardLabel.text = state.reward?.let { reward ->
            val lines = reward.rewards.map { entry ->
                val label = context.getString(
                    when (entry.id) {
                        "level_win" -> R.string.reward_level_win
                        "direct_enemy_unit_hit" -> R.string.reward_direct_unit_hit
                        "fast_win" -> R.string.reward_fast_win
                        "untouched_fortress" -> R.string.reward_untouched_fortress
                        "fortress_hit_streak" -> R.string.reward_fortress_streak
                        else -> R.string.reward_generic
                    },
                    *if (entry.id in KNOWN_REWARD_IDS) emptyArray() else arrayOf(entry.id),
                )
                context.getString(R.string.match_result_reward_line, label, entry.gold)
            }
            (lines + context.getString(R.string.match_result_reward_total, reward.totalGold))
                .joinToString("\n")
        }.orEmpty()
        rewardLabel.visibility = if (state.reward == null) View.GONE else View.VISIBLE
        rewardPanel.visibility = rewardLabel.visibility
        statusLabel.text = state.status?.let { status ->
            context.getString(
                when (status) {
                    MatchResultPopupStatus.SAVING_PROGRESS ->
                        R.string.match_result_saving_status
                    MatchResultPopupStatus.COMPLETION_FAILED ->
                        R.string.match_result_completion_failed
                },
            )
        }.orEmpty()
        statusLabel.visibility = if (state.status == null) View.GONE else View.VISIBLE
        renderActions(state.actions)
        if (currentPanelWidth > 0 && currentPanelHeight > 0) {
            layoutPanelContent(currentPanelWidth, currentPanelHeight)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        if (width > 0 && height > 0) {
            currentOverlayWidth = width
            currentOverlayHeight = height
            val safeWidth = (width - dp(32f)).coerceAtLeast(1)
            val safeHeight = (height - dp(32f)).coerceAtLeast(1)
            val widthFromHeight = safeHeight * 10 / 7
            val panelWidth = min(dp(600f), min(safeWidth, widthFromHeight))
            val panelHeight = panelWidth * 7 / 10
            if (panelWidth != currentPanelWidth || panelHeight != currentPanelHeight) {
                currentPanelWidth = panelWidth
                currentPanelHeight = panelHeight
                panel.layoutParams = (panel.layoutParams as LayoutParams).apply {
                    this.width = currentPanelWidth
                    this.height = currentPanelHeight
                }
                layoutPanelContent(currentPanelWidth, currentPanelHeight)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun renderActions(actions: List<MatchResultPopupAction>) {
        actionButtons.forEach(::removeView)
        actionButtons.clear()
        actions.forEach { action ->
            createActionButton(action).also { button ->
                actionButtons += button
                addView(button)
            }
        }
        if (currentPanelWidth > 0 && currentPanelHeight > 0) {
            layoutPanelContent(currentPanelWidth, currentPanelHeight)
        }
    }

    private fun createActionButton(action: MatchResultPopupAction): TextView =
        TextView(context).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(6f), 0, dp(6f), 0)
            setTextColor(
                if (action.style == MatchResultButtonStyle.GOLD) {
                    Color.rgb(64, 38, 20)
                } else {
                    Color.rgb(255, 239, 210)
                },
            )
            UiTypography.applyButtonTypeface(this)
            setBackgroundResource(
                when (action.style) {
                    MatchResultButtonStyle.GOLD -> R.drawable.loadout_start_match_button
                    MatchResultButtonStyle.RED -> R.drawable.ui_button_red_m
                    MatchResultButtonStyle.DARK -> R.drawable.ui_button_dark_m
                },
            )
            text = context.getString(
                when (action.type) {
                    MatchResultActionType.NEXT_LEVEL -> R.string.match_next_level
                    MatchResultActionType.EXIT_TO_CAMPAIGN ->
                        R.string.match_result_exit_campaign
                    MatchResultActionType.RETRY -> R.string.match_result_retry
                    MatchResultActionType.RETRY_SAVE -> R.string.match_result_retry_save
                    MatchResultActionType.BACK_TO_DASHBOARD ->
                        R.string.match_result_back_dashboard
                },
            )
            isEnabled = action.enabled
            isClickable = action.enabled
            visibility = View.VISIBLE
            alpha = if (action.enabled) 1f else 0.65f
            elevation = dp(4f).toFloat()
            setOnClickListener {
                if (action.enabled) onAction(action.type)
            }
        }

    private fun layoutPanelContent(panelWidth: Int, panelHeight: Int) {
        val scale = panelWidth.toFloat() / dp(600f)
        victoryIllustration.layoutParams = panelWindowLayoutParams(panelWidth, panelHeight)
        defeatIllustration.layoutParams = panelWindowLayoutParams(panelWidth, panelHeight)
        val centeredLabelWidth = (panelWidth * 0.78f).toInt()
        contextLabel.textSize = (14f * scale).coerceAtLeast(11f)
        contextLabel.gravity = Gravity.CENTER
        contextLabel.layoutParams = panelLayoutParams(
            centeredLabelWidth,
            (panelHeight * 0.10f).toInt(),
            topMargin = (panelHeight * 0.025f).toInt(),
        )
        titleLabel.textSize = (26f * scale).coerceAtLeast(18f)
        titleLabel.gravity = Gravity.CENTER
        titleLabel.layoutParams = panelLayoutParams(
            (panelWidth * 0.72f).toInt(),
            (panelHeight * 0.11f).toInt(),
            topMargin = (panelHeight * 0.18f).toInt(),
        )
        titlePlaque.layoutParams = panelLayoutParams(
            (panelWidth * 0.84f).toInt(),
            (panelHeight * 0.27f).toInt(),
            topMargin = (panelHeight * 0.10f).toInt(),
        )
        rewardLabel.textSize = (11f * scale).coerceAtLeast(9f)
        rewardLabel.gravity = Gravity.CENTER
        rewardLabel.maxLines = 6
        rewardLabel.layoutParams = panelLayoutParams(
            (panelWidth * 0.78f).toInt(),
            (panelHeight * 0.25f).toInt(),
            topMargin = (panelHeight * 0.34f).toInt(),
        )
        rewardPanel.layoutParams = panelLayoutParams(
            (panelWidth * 0.80f).toInt(),
            (panelHeight * 0.27f).toInt(),
            topMargin = (panelHeight * 0.33f).toInt(),
        )
        statusLabel.textSize = (12f * scale).coerceAtLeast(10f)
        statusLabel.gravity = Gravity.CENTER
        statusLabel.layoutParams = panelLayoutParams(
            (panelWidth * 0.74f).toInt(),
            (panelHeight * 0.13f).toInt(),
            topMargin = (panelHeight * 0.59f).toInt(),
        )

        val buttonHeight = (dp(55f) * scale).toInt().coerceAtLeast(dp(40f))
        val preferredButtonWidth = (dp(220f) * scale).toInt()
        val gap = (dp(10f) * scale).toInt()
        val buttonCount = actionButtons.size
        if (buttonCount == 0) return

        val maximumRowWidth = (panelWidth * 0.86f).toInt()
        val buttonWidth = if (buttonCount == 1) {
            min(preferredButtonWidth, maximumRowWidth)
        } else {
            min(
                preferredButtonWidth,
                (maximumRowWidth - gap * (buttonCount - 1)) / buttonCount,
            )
        }
        val rowWidth = buttonWidth * buttonCount + gap * (buttonCount - 1)
        val firstButtonLeft = (panelWidth - rowWidth) / 2
        val top = panelHeight - (panelHeight * 0.065f).toInt() - buttonHeight
        val panelLeft = (currentOverlayWidth - panelWidth) / 2
        val panelTop = (currentOverlayHeight - panelHeight) / 2
        actionButtons.forEachIndexed { index, button ->
            button.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                (14f * scale).coerceAtLeast(11f),
            )
            button.layoutParams = LayoutParams(
                buttonWidth,
                buttonHeight,
                Gravity.TOP or Gravity.START,
            ).apply {
                leftMargin = panelLeft + firstButtonLeft + index * (buttonWidth + gap)
                topMargin = panelTop + top
            }
        }
    }

    private fun panelWindowLayoutParams(panelWidth: Int, panelHeight: Int): LayoutParams =
        LayoutParams(
            (panelWidth * PANEL_WINDOW_WIDTH_RATIO).toInt(),
            (panelHeight * PANEL_WINDOW_HEIGHT_RATIO).toInt(),
            Gravity.TOP or Gravity.START,
        ).apply {
            leftMargin = (panelWidth * PANEL_WINDOW_LEFT_RATIO).toInt()
            topMargin = (panelHeight * PANEL_WINDOW_TOP_RATIO).toInt()
        }

    private fun panelLayoutParams(
        width: Int,
        height: Int,
        topMargin: Int,
    ): LayoutParams = LayoutParams(width, height, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
        this.topMargin = topMargin
    }

    private fun popupTextView(textSize: Float): TextView =
        TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 229, 199))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSize)
            UiTypography.applyButtonTypeface(this)
            includeFontPadding = false
        }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        val KNOWN_REWARD_IDS = setOf(
            "level_win",
            "direct_enemy_unit_hit",
            "fast_win",
            "untouched_fortress",
            "fortress_hit_streak",
        )
        const val PANEL_WINDOW_LEFT_RATIO = 188f / 2400f
        const val PANEL_WINDOW_TOP_RATIO = 413f / 1680f
        const val PANEL_WINDOW_WIDTH_RATIO = 2024f / 2400f
        const val PANEL_WINDOW_HEIGHT_RATIO = 698f / 1680f
    }
}
