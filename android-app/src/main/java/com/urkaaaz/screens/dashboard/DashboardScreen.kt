package com.urkaaaz.screens.dashboard

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import com.urkaaaz.android.R
import com.urkaaaz.application.progression.PlayerProgressResetResult
import com.urkaaaz.application.progression.DeveloperProgressSummary
import com.urkaaaz.navigation.ScreenContent
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
    private val onResetProgress: suspend () -> PlayerProgressResetResult,
    private val loadProgress: suspend () -> DeveloperProgressSummary,
) : ScreenContent {
    private val viewModel = DashboardViewModel()
    private var screenScope: CoroutineScope? = null
    private val livesValue = label("5", 16f)
    private val currentLevelValue = label(context.getString(R.string.dashboard_current_level, 1), 10f)
    private val goldValue = label("0", 16f)
    private val resetProgressButton = Button(context).apply {
        text = context.getString(R.string.dashboard_reset_progress)
        UiTypography.applyButtonTypeface(this)
        textSize = 11f
        setTextColor(Color.WHITE)
        setPadding(dp(12), dp(6), dp(12), dp(6))
        minHeight = 0
        minWidth = 0
        background = context.getDrawable(R.drawable.bg_hud_panel)
        setOnClickListener { showResetConfirmation() }
    }

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

        addView(resetProgressButton, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(96)
            marginEnd = dp(16)
        })

        addView(bottomActions(), FrameLayout.LayoutParams(-1, dp(104)).apply {
            gravity = Gravity.BOTTOM
        })
    }

    override fun onResume() {
        if (screenScope != null) return
        screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        resetProgressButton.isEnabled = true
        screenScope?.launch {
            try {
                renderProgress(viewModel.updateProgress(loadProgress()))
            } catch (error: IOException) {
                Log.e("DashboardScreen", "Could not load player progress summary", error)
            } catch (error: IllegalArgumentException) {
                Log.e("DashboardScreen", "Could not load player progress summary", error)
            }
        }
    }

    override fun onPause() {
        val scope = screenScope
        screenScope = null
        resetProgressButton.isEnabled = false
        scope?.cancel()
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
            addView(livesValue, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
            addView(currentLevelValue, LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(8)
            })
            addView(Button(context).apply {
                text = "RESET"
                UiTypography.applyButtonTypeface(this)
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
            addView(label(context.getString(R.string.dashboard_life_regeneration_unavailable), 13f), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(6)
            })
        })
        addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(image(R.drawable.icon_orcoins, 50, 42))
            addView(goldValue, LinearLayout.LayoutParams(-2, -2).apply {
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
        setTextColor(Color.WHITE)
    }

    private fun renderProgress(state: DashboardUiState) {
        livesValue.text = state.lives.toString()
        currentLevelValue.text = context.getString(
            R.string.dashboard_current_level,
            state.currentLevel,
        )
        goldValue.text = state.gold.toString()
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
                    renderProgress(viewModel.updateProgress(loadProgress()))
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
                resetProgressButton.isEnabled = screenScope != null
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
