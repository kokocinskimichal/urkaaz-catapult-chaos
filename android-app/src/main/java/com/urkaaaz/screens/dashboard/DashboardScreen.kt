package com.urkaaaz.screens.dashboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import com.urkaaaz.android.R
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.SetLanguage
import com.urkaaaz.application.progression.DeveloperProgressSummary
import com.urkaaaz.application.progression.PlayerProgressResetResult
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.ScreenChromeMetrics
import com.urkaaaz.ui.UiTypography
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DashboardScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onCampaign: () -> Unit,
    private val onShop: () -> Unit,
    private val onDemoMode: () -> Unit,
    private val onResetProgress: suspend () -> PlayerProgressResetResult,
    private val loadProgress: suspend () -> DeveloperProgressSummary,
    initialLanguage: AppLanguage,
    setLanguage: SetLanguage,
    private val onLanguageSaved: () -> Unit,
) : ScreenContent {
    private val viewModel = DashboardViewModel(initialLanguage, setLanguage)
    private var screenScope: CoroutineScope? = null
    private var isResetInProgress = false
    private val languageFlagButtons = mutableListOf<ImageButton>()

    private val livesValue = label(R.string.dashboard_progress_placeholder, 14f)
    private val currentLevelValue = label(R.string.dashboard_progress_placeholder, 12f)
    private val goldValue = label(R.string.dashboard_progress_placeholder, 14f)
    private val regenerationValue =
        label(R.string.dashboard_life_regeneration_unavailable, 11f)
    private val progressStatusMessage = label("", 12f)
    private val progressRetryButton = Button(context).apply {
        text = context.getString(R.string.dashboard_progress_retry)
        UiTypography.applyButtonTypeface(this)
        textSize = 10f
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(3), dp(8), dp(3))
        minHeight = 0
        minWidth = 0
        background = context.getDrawable(R.drawable.ui_button_dark_s)
        setOnClickListener { loadProgressSummary() }
    }
    private val progressStatusPanel = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(6), dp(8), dp(6))
        background = context.getDrawable(R.drawable.bg_hud_panel)
        visibility = View.GONE
        addView(progressStatusMessage, LinearLayout.LayoutParams(0, -2, 1f))
        addView(progressRetryButton, LinearLayout.LayoutParams(dp(76), dp(36)).apply {
            marginStart = dp(8)
        })
    }
    private val resetProgressButton = Button(context).apply {
        text = context.getString(R.string.dashboard_reset_progress)
        UiTypography.applyButtonTypeface(this)
        textSize = 11f
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        minHeight = 0
        minWidth = 0
        compoundDrawablePadding = dp(5)
        setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_dashboard_reset, 0, 0, 0)
        background = context.getDrawable(R.drawable.ui_button_dark_m)
        setOnClickListener { showResetConfirmation() }
    }
    private val livesIcon = image(R.drawable.icon_lives_heart, 24, 22)
    private val goldIcon = image(R.drawable.icon_orcoins, 28, 22)
    private val regenerationIcon = image(R.drawable.ic_dashboard_life_regeneration, 24, 22)
    private val avatarFrame = avatarFrame()
    private val profilePanel = profilePanel()
    private var languagePopup: PopupWindow? = null
    private val languageButton = languageButton()
    private val leftEventSlot = eventSlot()
    private val rightEventSlot = eventSlot()
    private val campaignArea = campaignArea()

    override val view = FrameLayout(context).apply {
        clipChildren = false
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.dashboard_background_approved)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(campaignArea, FrameLayout.LayoutParams(-1, dp(150)).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(54)
        })

        addView(bottomActions(), FrameLayout.LayoutParams(-1, dp(62)).apply {
            gravity = Gravity.BOTTOM
            bottomMargin = dp(12)
        })

        addView(resetProgressButton, FrameLayout.LayoutParams(dp(126), dp(48)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = dp(12)
            bottomMargin = dp(14)
        })

        addView(progressStatusPanel, FrameLayout.LayoutParams(dp(360), -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(98)
        })

        addView(topHeader(), FrameLayout.LayoutParams(-1, dp(92)).apply {
            gravity = Gravity.TOP
        })
    }

    override fun onResume() {
        if (screenScope != null) return
        screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        resetProgressButton.isEnabled = true
        loadProgressSummary()
    }

    override fun onPause() {
        val scope = screenScope
        screenScope = null
        resetProgressButton.isEnabled = false
        progressRetryButton.isEnabled = false
        languagePopup?.dismiss()
        languagePopup = null
        scope?.cancel()
    }

    override fun onBackPressed(): Boolean {
        val popup = languagePopup ?: return false
        if (!popup.isShowing) return false
        popup.dismiss()
        languagePopup = null
        return true
    }

    private fun topHeader(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(
            dp(ScreenChromeMetrics.EDGE_INSET_DP),
            dp(4),
            dp(ScreenChromeMetrics.EDGE_INSET_DP),
            dp(4),
        )

        addView(backButton(), LinearLayout.LayoutParams(dp(112), dp(48)))

        addView(titlePanel(), LinearLayout.LayoutParams(0, dp(76), 1.25f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        })

        addView(profilePanel, LinearLayout.LayoutParams(0, dp(66), 1.9f))
        addView(Space(context), LinearLayout.LayoutParams(0, 1, 0.1f))
        addView(languageButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginStart = dp(4)
        })
    }

    private fun backButton(): Button = Button(context).apply {
        text = context.getString(R.string.dashboard_back_action)
        contentDescription = context.getString(R.string.dashboard_back_accessibility)
        UiTypography.applyButtonTypeface(this)
        textSize = 14f
        setTextColor(Color.rgb(57, 34, 20))
        setPadding(dp(5), 0, dp(5), 0)
        minHeight = 0
        minWidth = 0
        background = context.getDrawable(R.drawable.loadout_start_match_button_xs_short)
        setOnClickListener { onBack() }
    }

    private fun titlePanel(): View = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.FIT_XY
            setImageResource(R.drawable.loadout_header_frame)
        }, FrameLayout.LayoutParams(-1, -1))
        addView(label(R.string.dashboard_title, 25f).apply {
            setTextColor(Color.rgb(244, 207, 123))
            setShadowLayer(1.5f, 0f, dp(1).toFloat(), Color.BLACK)
        }, FrameLayout.LayoutParams(-1, dp(28), Gravity.TOP).apply {
            topMargin = dp(22)
        })
    }

    private fun profilePanel(): View = FrameLayout(context).apply {
        clipChildren = false
        addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 24, 22))
                cornerRadius = dp(14).toFloat()
            }
        }, FrameLayout.LayoutParams(-1, -1))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(62), 0, dp(7), 0)
            addView(currentLevelValue, LinearLayout.LayoutParams(-2, -2))
            addView(statDivider(), dividerParams())
            addView(stat(livesIcon, livesValue))
            addView(statDivider(), dividerParams())
            addView(stat(goldIcon, goldValue), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(7)
            })
            addView(statDivider(), dividerParams())
            addView(stat(regenerationIcon, regenerationValue), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(7)
            })
        }, FrameLayout.LayoutParams(-1, -1))

        addView(avatarFrame, FrameLayout.LayoutParams(dp(56), dp(56)).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = dp(3)
        })

        addView(View(context).apply {
            background = context.getDrawable(R.drawable.match_hud_universal_frame)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun avatarFrame(): View = FrameLayout(context).apply {
        contentDescription = context.getString(R.string.dashboard_avatar_accessibility)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.player_avatar_01)
        }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        foreground = context.getDrawable(R.drawable.dashboard_hud_portrait_frame)
    }

    private fun stat(icon: ImageView, value: TextView): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon)
            addView(value, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(3)
            })
        }

    private fun statDivider(): View = View(context).apply {
        setBackgroundColor(Color.rgb(145, 117, 75))
        alpha = 0.75f
    }

    private fun dividerParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(1), dp(28)).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        }

    private fun languageButton(): ImageButton = ImageButton(context).apply {
        background = context.getDrawable(R.drawable.ui_button_dark_xs_normal)
        contentDescription =
            context.getString(R.string.dashboard_open_language_icons_accessibility)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(5), dp(5), dp(5), dp(5))
        setImageResource(R.drawable.ic_dashboard_language_globe)
        setOnClickListener { showLanguageIcons(this) }
    }

    internal fun showLanguageIcons(anchor: View) {
        languagePopup?.let { popup ->
            if (popup.isShowing) {
                popup.dismiss()
                languagePopup = null
                return
            }
        }

        languageFlagButtons.clear()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundResource(R.drawable.bg_hud_panel)
            DashboardLanguageFlag.entries.forEachIndexed { index, flag ->
                addView(
                    languageFlag(flag),
                    languageFlagParams(dp(5).takeIf { index > 0 } ?: 0),
                )
            }
        }
        val popup = PopupWindow(
            content,
            dp(166),
            -2,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            setOnDismissListener {
                if (languagePopup === this) languagePopup = null
            }
        }
        languagePopup = popup
        popup.showAsDropDown(anchor, 0, dp(4), Gravity.END)
    }

    private fun languageFlag(flag: DashboardLanguageFlag): View = FrameLayout(context).apply {
        val selected = flag.isActive(viewModel.state.selectedLanguage)
        background = GradientDrawable().apply {
            setColor(if (selected) Color.rgb(82, 62, 33) else Color.TRANSPARENT)
            cornerRadius = dp(8).toFloat()
            setStroke(
                dp(if (selected) 2 else 1),
                if (selected) Color.rgb(243, 210, 138) else Color.rgb(111, 92, 63),
            )
        }
        val languageName = context.getString(flag.languageNameResource)
        val descriptionResource = if (selected) {
            R.string.dashboard_language_flag_selected_accessibility
        } else {
            R.string.dashboard_language_flag_accessibility
        }
        val button = ImageButton(context).apply {
            contentDescription = context.getString(descriptionResource, languageName)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isSelected = selected
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = ColorDrawable(Color.TRANSPARENT)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setImageResource(flag.drawableResource)
            setOnClickListener { selectLanguage(flag.language) }
        }
        languageFlagButtons += button
        addView(button, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
    }

    private fun languageFlagParams(marginStart: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            if (marginStart > 0) this.marginStart = marginStart
        }

    internal fun selectLanguage(language: AppLanguage) {
        if (viewModel.state.isLanguageSaving) return
        val scope = screenScope ?: return
        languageFlagButtons.forEach { it.isEnabled = false }
        scope.launch {
            val result = viewModel.selectLanguage(language)
            when (result) {
                is LanguageSaveResult.Saved -> {
                    languagePopup?.dismiss()
                    languagePopup = null
                    onLanguageSaved()
                }
                is LanguageSaveResult.Unchanged -> {
                    languagePopup?.dismiss()
                    languagePopup = null
                }
                is LanguageSaveResult.Failure -> {
                    languageFlagButtons.forEach { it.isEnabled = true }
                    Toast.makeText(
                        context,
                        R.string.dashboard_language_change_failure,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun campaignArea(): View = FrameLayout(context).apply {
        clipChildren = false
        addView(leftEventSlot, FrameLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            marginStart = dp(12)
        })
        addView(rightEventSlot, FrameLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = dp(12)
        })
        val buttonWidth = minOf(
            dp(480),
            (context.resources.displayMetrics.widthPixels * 0.52f).toInt(),
        )
        val buttonHeight = (buttonWidth * 0.21f).toInt()
        addView(campaignButton(), FrameLayout.LayoutParams(buttonWidth, buttonHeight, Gravity.CENTER))
    }

    private fun eventSlot(): FrameLayout = FrameLayout(context).apply {
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun bottomActions(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(
            darkActionButton(
                labelResource = R.string.dashboard_shop_action,
                iconResource = R.drawable.icon_orcoins,
                descriptionResource = R.string.dashboard_shop_accessibility,
                action = onShop,
            ),
            LinearLayout.LayoutParams(dp(140), dp(52)),
        )
        addView(
            darkActionButton(
                labelResource = R.string.test_mode,
                iconResource = null,
                descriptionResource = R.string.test_mode,
                action = onDemoMode,
            ),
            LinearLayout.LayoutParams(dp(140), dp(52)).apply {
                marginStart = dp(12)
            },
        )
        addView(
            darkActionButton(
                labelResource = R.string.dashboard_ad_reward_action,
                iconResource = R.drawable.ic_dashboard_ad_reward,
                descriptionResource = R.string.dashboard_ad_reward_accessibility,
                action = null,
            ),
            LinearLayout.LayoutParams(dp(140), dp(55)).apply {
                marginStart = dp(12)
            },
        )
    }

    private fun campaignButton(): Button = Button(context).apply {
        text = context.getString(R.string.dashboard_campaign_action)
        contentDescription = context.getString(R.string.dashboard_campaign_accessibility)
        UiTypography.applyButtonTypeface(this)
        textSize = 30f
        setTextColor(Color.rgb(59, 35, 18))
        setPadding(dp(12), 0, dp(12), 0)
        minHeight = 0
        minWidth = 0
        background = context.getDrawable(R.drawable.loadout_start_match_button)
        setOnClickListener { onCampaign() }
    }

    private fun darkActionButton(
        labelResource: Int,
        iconResource: Int?,
        descriptionResource: Int,
        action: (() -> Unit)?,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setBackgroundResource(R.drawable.ui_button_dark_m)
        contentDescription = context.getString(descriptionResource)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = action != null
        isFocusable = action != null
        if (action != null) {
            setOnClickListener { action() }
        }
        iconResource?.let { addView(image(it, 28, 26)) }
        addView(label(context.getString(labelResource), 14f), LinearLayout.LayoutParams(-2, -2).apply {
            if (iconResource != null) marginStart = dp(7)
        })
    }

    private fun image(resource: Int, width: Int, height: Int): ImageView =
        ImageView(context).apply {
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(resource)
            layoutParams = LinearLayout.LayoutParams(dp(width), dp(height))
        }

    private fun label(value: String, size: Float): TextView = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(Color.WHITE)
        UiTypography.applyButtonTypeface(this)
    }

    private fun label(stringResource: Int, size: Float): TextView =
        label(context.getString(stringResource), size)

    private fun renderProgress(state: DashboardUiState) {
        val summary = state.progressSummary
        val placeholder = context.getString(R.string.dashboard_progress_placeholder)
        currentLevelValue.text = summary?.let {
            context.getString(R.string.dashboard_current_level, it.currentLevel)
        } ?: placeholder
        livesValue.text = summary?.lives?.toString() ?: placeholder
        goldValue.text = summary?.gold?.toString() ?: placeholder

        progressStatusMessage.text = when {
            state.isLoading -> context.getString(R.string.dashboard_progress_loading)
            state.hasProgressError -> context.getString(R.string.dashboard_progress_unavailable)
            else -> ""
        }
        progressStatusPanel.visibility =
            if (state.isLoading || state.hasProgressError) View.VISIBLE else View.GONE
        progressRetryButton.visibility = if (state.hasProgressError) View.VISIBLE else View.GONE
        progressRetryButton.isEnabled = state.hasProgressError && screenScope != null
    }

    private fun loadProgressSummary() {
        val scope = screenScope ?: return
        renderProgress(viewModel.beginProgressLoad())
        scope.launch {
            try {
                renderProgress(viewModel.updateProgress(loadProgress()))
            } catch (error: IOException) {
                Log.e("DashboardScreen", "Could not load player progress summary", error)
                renderProgress(viewModel.failProgressLoad())
            } catch (error: IllegalArgumentException) {
                Log.e("DashboardScreen", "Could not load player progress summary", error)
                renderProgress(viewModel.failProgressLoad())
            }
        }
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(context)
            .setTitle(R.string.dashboard_reset_progress_title)
            .setMessage(R.string.dashboard_reset_progress_message)
            .setNegativeButton(R.string.dashboard_reset_cancel, null)
            .setPositiveButton(R.string.dashboard_reset_progress_confirm) { _, _ ->
                resetPlayerProgress()
            }
            .show()
    }

    private fun resetPlayerProgress() {
        val scope = screenScope ?: return
        if (isResetInProgress) return
        isResetInProgress = true
        resetProgressButton.isEnabled = false
        scope.launch {
            try {
                val result = onResetProgress()
                val message = when (result) {
                    PlayerProgressResetResult.Reset ->
                        R.string.dashboard_reset_progress_success
                    is PlayerProgressResetResult.Failure ->
                        R.string.dashboard_reset_progress_failure
                }
                if (result == PlayerProgressResetResult.Reset) {
                    loadProgressSummary()
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (error: IOException) {
                Log.e("DashboardScreen", "Could not reset player progress", error)
                Toast.makeText(
                    context,
                    R.string.dashboard_reset_progress_failure,
                    Toast.LENGTH_LONG,
                ).show()
            } catch (error: IllegalArgumentException) {
                Log.e("DashboardScreen", "Could not reset player progress", error)
                Toast.makeText(
                    context,
                    R.string.dashboard_reset_progress_failure,
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                isResetInProgress = false
                resetProgressButton.isEnabled = screenScope != null
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
