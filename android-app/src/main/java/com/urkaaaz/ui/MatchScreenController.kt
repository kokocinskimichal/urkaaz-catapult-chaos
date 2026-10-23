package com.urkaaaz.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.CheckBox
import android.widget.ScrollView
import android.widget.TextView
import com.urkaaaz.campaign.MapDefinitionRepository
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerLoadoutConfig

class MatchScreenController(
    private val activity: Activity,
    private val campaignLevel: Int,
    playerLoadout: PlayerLoadoutConfig? = null,
    private val demoMode: Boolean = false,
    private val onBackClicked: () -> Unit,
    private val openDebugOnStart: Boolean = false,
    onMatchFinished: (Int, MatchEvent.MatchFinished, (Boolean) -> Unit) -> Unit =
        { _, _, onCompletionRecorded -> onCompletionRecorded(false) },
    private val onNextLevelClicked: (Int) -> Unit = {},
) {
    private val viewModel = MatchViewModel(
        campaignLevel = campaignLevel,
        playerLoadout = playerLoadout,
        demoMode = demoMode,
        mapRepository = MapDefinitionRepository { path -> activity.assets.open(path) },
        combatLogSink = AndroidCombatLogSink(),
        ammunitionLogSink = AndroidAmmunitionLogSink(),
        onMatchFinished = { level, event ->
            onMatchFinished(level, event) { completionRecorded ->
                campaignCompletionRecorded = completionRecorded
                lastRenderedState?.let(::render)
            }
        },
    )
    private val gameHandler = Handler(Looper.getMainLooper())
    private lateinit var battlefieldView: BattlefieldView
    private lateinit var hudView: MatchHudView
    private lateinit var ammunitionPanel: AmmunitionPanelView
    private lateinit var unitPanel: UnitPanelView
    private lateinit var spellPanel: SpellPanelView
    private lateinit var commandDock: LinearLayout
    private lateinit var feedbackToast: MatchFeedbackToastView
    private lateinit var debugButton: TextView
    private lateinit var snapshotCloseButton: TextView
    private lateinit var resultPopupView: MatchResultPopupView
    private var snapshotMode = false
    private var debugPausedMatch = false
    private var campaignCompletionRecorded: Boolean? = null
    private var lastRenderedState: RenderState? = null

    private var aimAngle = 45f
    private var aimPower = 50f

    private val gameTick = object : Runnable {
        override fun run() {
            if (!activity.isFinishing) {
                val state = try {
                    if (viewModel.renderState.statusLabel == "RUNNING") {
                        viewModel.dispatch(MatchUiAction.AdvanceSimulation)
                    } else {
                        viewModel.renderState
                    }
                } catch (exception: IllegalStateException) {
                    Log.e(TAG, "Match tick failed", exception)
                    viewModel.renderState
                }
                render(state)
                gameHandler.postDelayed(this, 33L)
            }

        }
    }

    fun createView(): FrameLayout {
        battlefieldView = BattlefieldView(activity)
        hudView = MatchHudView(activity).apply {
            onPauseClicked = {
                render(viewModel.dispatch(MatchUiAction.TogglePause))
            }
        }
        ammunitionPanel = AmmunitionPanelView(
            activity,
            viewModel.selectableAmmunitionTypes,
        ).apply {
            onAmmoSelected = { ammunitionType ->
                render(viewModel.dispatch(MatchUiAction.SelectAmmo(ammunitionType)))
            }
        }
        unitPanel = UnitPanelView(
            activity,
            viewModel.selectableUnitTypes,
        ).apply {
            onUnitSelected = { unitType ->
                render(viewModel.dispatch(MatchUiAction.DeployUnit(unitType)))
            }
            onSendWave = {
                render(viewModel.dispatch(MatchUiAction.SendWave))
            }
        }
        feedbackToast = MatchFeedbackToastView(activity)
        spellPanel = SpellPanelView(
            activity,
            viewModel.selectableSpellTypes,
        ).apply {
            onSpellCast = { spellType ->
                val state = viewModel.dispatch(MatchUiAction.CastSpell(spellType))
                render(state)
                feedbackToast.showFeedback(state.spellFeedbackLabel)
            }
        }
        commandDock = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            background = HudPanelStyle.universalFrameBackground(activity)
            HudPanelStyle.applyShadow(this)
            setPadding(dp(4), 0, dp(4), 0)
            val sidePanelWeight = 1.3f
            addView(unitPanel, LinearLayout.LayoutParams(0, -1, sidePanelWeight))
            addView(
                View(activity).apply {
                    setBackgroundColor(Color.argb(88, 181, 143, 78))
                },
                LinearLayout.LayoutParams(dp(1), -1).apply {
                    topMargin = dp(14)
                    bottomMargin = dp(14)
                },
            )
            addView(ammunitionPanel, LinearLayout.LayoutParams(0, -1, 0.88f))
            addView(
                View(activity).apply {
                    setBackgroundColor(Color.argb(88, 181, 143, 78))
                },
                LinearLayout.LayoutParams(dp(1), -1).apply {
                    topMargin = dp(14)
                    bottomMargin = dp(14)
                },
            )
            addView(spellPanel, LinearLayout.LayoutParams(0, -1, sidePanelWeight))
        }
        debugButton = TextView(activity).apply {
                text = "DEBUG"
                setTextColor(Color.WHITE)
                textSize = 11f
                UiTypography.applyButtonTypeface(this)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
                setOnClickListener { showDebugToolsDialog() }
        }
        snapshotCloseButton = TextView(activity).apply {
                text = "CLOSE SNAPSHOT"
                setTextColor(Color.WHITE)
                textSize = 12f
                UiTypography.applyButtonTypeface(this)
                setPadding(dp(14), dp(7), dp(14), dp(7))
                setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
                visibility = View.GONE
                setOnClickListener { setSnapshotMode(false) }
        }
        resultPopupView = MatchResultPopupView(activity) { action ->
            dispatchMatchResultAction(
                action = action,
                onRetry = ::restartMatch,
                onNextLevel = { onNextLevelClicked(campaignLevel + 1) },
                onCampaignReturn = onBackClicked,
                onDashboardReturn = onBackClicked,
            )
        }

        battlefieldView.onAimChanged = { aim ->
            aimAngle = aim.angleDegrees
            aimPower = aim.power
            if (viewModel.renderState.outcomeLabel == null) {
                render(viewModel.dispatch(MatchUiAction.Aim(aimAngle, aimPower)))
            }
        }
        battlefieldView.onAimReleased = { aim ->
            aimAngle = aim.angleDegrees
            aimPower = aim.power
            render(viewModel.dispatch(MatchUiAction.Aim(aimAngle, aimPower)))
            render(viewModel.dispatch(MatchUiAction.FireRock))
        }
        battlefieldView.onAimCancelled = { previousAim ->
            if (previousAim != null) {
                aimAngle = previousAim.angleDegrees
                aimPower = previousAim.power
                render(viewModel.dispatch(MatchUiAction.Aim(aimAngle, aimPower)))
            } else {
                render(viewModel.renderState)
            }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(6, 6, 6, 6)
            addView(battlefieldView, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(
                commandDock,
                FrameLayout.LayoutParams(-1, dp(108)).apply {
                    gravity = Gravity.BOTTOM
                    leftMargin = dp(8)
                    rightMargin = dp(8)
                    bottomMargin = dp(10)
                },
            )
            addView(
                hudView,
                FrameLayout.LayoutParams(-1, dp(58)).apply {
                    gravity = Gravity.TOP
                },
            )
            addView(
                feedbackToast,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(122)
                },
            )
            addView(
                debugButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(180)
                    rightMargin = dp(12)
                },
            )
            addView(
                snapshotCloseButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(12)
                    rightMargin = dp(12)
                },
            )
            addView(resultPopupView, FrameLayout.LayoutParams(-1, -1))
            render(viewModel.dispatch(MatchUiAction.StartMatch))
            if (openDebugOnStart) {
                viewModel.setAiUnitAutomationEnabled(false)
                post { showDebugToolsDialog() }
            }
        }
    }

    fun onResume() {
        gameHandler.removeCallbacks(gameTick)
        if (::battlefieldView.isInitialized) {
            battlefieldView.setLifecyclePaused(false)
        }
        gameHandler.postDelayed(gameTick, 33L)
    }

    fun onPause() {
        gameHandler.removeCallbacks(gameTick)
        if (::battlefieldView.isInitialized) {
            battlefieldView.setLifecyclePaused(true)
        }
        if (::feedbackToast.isInitialized) {
            feedbackToast.hideImmediately()
        }
    }

    private fun render(state: RenderState) {
        lastRenderedState = state
        val popupState = MatchResultPopupStateMapper.map(
            isFinished = state.statusLabel == MatchStatus.FINISHED.name && !snapshotMode,
            outcome = state.outcomeLabel?.let(MatchOutcome::valueOf),
            campaignLevel = campaignLevel,
            biomeName = state.terrainBiome,
            demoMode = demoMode,
            campaignCompletionRecorded = campaignCompletionRecorded,
        )
        hudView.render(state)
        ammunitionPanel.render(state)
        unitPanel.render(state)
        spellPanel.render(state)
        battlefieldView.render(state, showOutcomeLabel = popupState == null)
        debugButton.visibility = if (
            openDebugOnStart && state.statusLabel == "RUNNING" && !snapshotMode
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        resultPopupView.visibility = if (popupState == null) View.GONE else View.VISIBLE
        if (popupState != null) {
            resultPopupView.render(popupState)
            resultPopupView.bringToFront()
        }
    }

    private fun restartMatch() {
        campaignCompletionRecorded = null
        aimAngle = 45f
        aimPower = 50f
        render(viewModel.dispatch(MatchUiAction.RestartMatch))
    }

    private fun showDebugToolsDialog() {
        debugPausedMatch = viewModel.renderState.statusLabel == "RUNNING"
        if (debugPausedMatch) {
            render(viewModel.dispatch(MatchUiAction.TogglePause))
        }
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
        }
        root.addView(TextView(activity).apply {
            text = "DEBUG TOOLS"
            setTextColor(Color.WHITE)
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(CheckBox(activity).apply {
            text = "Show hitboxes"
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
            isChecked = battlefieldView.debugHitboxesVisible
            setOnCheckedChangeListener { _, checked ->
                battlefieldView.debugHitboxesVisible = checked
            }
        })
        if (demoMode) {
            root.addView(CheckBox(activity).apply {
                text = "Show cursor world coordinates"
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
                isChecked = battlefieldView.debugCoordinatesVisible
                setOnCheckedChangeListener { _, checked ->
                    battlefieldView.debugCoordinatesVisible = checked
                }
            })
        }
        root.addView(TextView(activity).apply {
            text = "SNAPSHOT"
            setTextColor(Color.WHITE)
            textSize = 14f
            UiTypography.applyButtonTypeface(this)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
            setOnClickListener {
                dialog.dismiss()
                setSnapshotMode(true)
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
        })
        root.addView(TextView(activity).apply {
            text = "ENEMY UNITS"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
        val unitChecks = mutableMapOf<String, CheckBox>()
        val recruitButtons = mutableMapOf<String, TextView>()
        DEBUG_UNIT_IDS.sorted().forEach { unitId ->
            val checkBox = CheckBox(activity).apply {
                text = debugUnitLabel(unitId)
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
                isChecked = unitId in viewModel.aiAllowedUnitIds()
            }
            unitChecks[unitId] = checkBox
            checkBox.setOnCheckedChangeListener { _, checked ->
                val selected = unitChecks
                    .filterValues { it.isChecked }
                    .keys
                    .toSet()
                if (!checked && selected.isEmpty()) {
                    checkBox.isChecked = true
                    return@setOnCheckedChangeListener
                }
                viewModel.setAiAllowedUnitIds(selected)
                recruitButtons.forEach { (id, button) ->
                    button.isEnabled = id in selected
                    button.alpha = if (button.isEnabled) 1f else 0.4f
                }
            }
            root.addView(checkBox)
            recruitButtons[unitId] = TextView(activity).apply {
                text = "RECRUIT ENEMY ${debugUnitLabel(unitId).uppercase()}"
                setTextColor(Color.WHITE)
                textSize = 13f
                UiTypography.applyButtonTypeface(this)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
                isEnabled = unitId in viewModel.aiAllowedUnitIds()
                alpha = if (isEnabled) 1f else 0.4f
                setOnClickListener {
                    render(viewModel.debugRecruitEnemyUnit(unitId))
                }
            }
            root.addView(
                recruitButtons.getValue(unitId),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(2)
                },
            )
        }
        root.addView(TextView(activity).apply {
            text = "SEND ENEMY WAVE"
            setTextColor(Color.WHITE)
            textSize = 14f
            UiTypography.applyButtonTypeface(this)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
            setOnClickListener {
                render(viewModel.debugSendEnemyWave())
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(10)
        })
        root.addView(TextView(activity).apply {
            text = "CLOSE"
            setTextColor(Color.WHITE)
            textSize = 14f
            UiTypography.applyButtonTypeface(this)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(14)
        })
        dialog.setContentView(ScrollView(activity).apply {
            isFillViewport = true
            addView(root)
        })
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.45f).toInt(),
                -2,
            )
        }
        dialog.setOnDismissListener {
            if (debugPausedMatch) {
                debugPausedMatch = false
                render(viewModel.dispatch(MatchUiAction.TogglePause))
            }
        }
        dialog.show()
    }

    private fun setSnapshotMode(enabled: Boolean) {
        snapshotMode = enabled
        battlefieldView.setSnapshotMode(enabled)
        hudView.visibility = if (enabled) View.GONE else View.VISIBLE
        commandDock.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) {
            feedbackToast.hideImmediately()
        }
        debugButton.visibility = if (
            !enabled &&
            openDebugOnStart &&
            viewModel.renderState.statusLabel == "RUNNING"
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        snapshotCloseButton.visibility = if (enabled) View.VISIBLE else View.GONE
        battlefieldView.invalidate()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun debugUnitLabel(id: String): String = when (id) {
        "RAGING_BOAR" -> "Raging Boar"
        "SLINGMASTER" -> "Slingmaster"
        else -> id.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private companion object {
        const val TAG = "MatchScreenController"
        val DEBUG_UNIT_IDS = setOf(
            "DEFENDER",
            "SAPPER",
            "DEMOLISHER",
            "RAGING_BOAR",
            "SLINGMASTER",
        )
    }
}
