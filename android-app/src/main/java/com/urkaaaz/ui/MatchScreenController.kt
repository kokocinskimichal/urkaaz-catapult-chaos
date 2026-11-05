package com.urkaaaz.ui

import android.app.Activity
import android.app.AlertDialog
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.urkaaaz.android.R
import com.urkaaaz.campaign.MapDefinitionRepository
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.contracts.MatchStatus
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.application.reward.RewardTracker
import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.screens.match.CampaignCompletionPresentation
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.campaign.BundledCampaignBalance
import com.urkaaaz.application.shop.MatchStockSettlementResult
import com.urkaaaz.application.shop.MatchStockSettlementUseCase

class MatchScreenController(
    private val activity: Activity,
    private val campaignLevel: Int,
    rewardRegistry: CampaignRewardRegistry = BundledCampaignBalance.rewardRegistry(),
    playerLoadout: PlayerLoadoutConfig? = null,
    private val playerInventory: PlayerInventoryConfig? = null,
    matchId: MatchId = MatchId("local-match"),
    stockSettlementUseCase: MatchStockSettlementUseCase? = null,
    private val loadoutCatalog: LoadoutCatalog = LoadoutCatalog.bundled(),
    private val demoMode: Boolean = false,
    private val onBackClicked: () -> Unit,
    private val openDebugOnStart: Boolean = false,
    private val onMatchFinished: (
        Int,
        MatchEvent.MatchFinished,
        LevelRewardResult?,
        (CampaignCompletionPresentation) -> Unit,
    ) -> Unit = { _, _, _, onCompletion -> onCompletion(CampaignCompletionPresentation(false, null)) },
    private val onNextLevelClicked: (Int) -> Unit = {},
) {
    private val viewModel = MatchViewModel(
        campaignLevel = campaignLevel,
        playerLoadout = playerLoadout,
        playerInventory = playerInventory,
        matchId = matchId,
        stockSettlementUseCase = stockSettlementUseCase,
        loadoutCatalog = loadoutCatalog,
        demoMode = demoMode,
        mapRepository = MapDefinitionRepository { path -> activity.assets.open(path) },
        combatLogSink = AndroidCombatLogSink(),
        ammunitionLogSink = AndroidAmmunitionLogSink(),
        onMatchFinishedWithRewards = { level, event, reward ->
            lastMatchFinishedEvent = event
            lastMatchReward = reward
            onMatchFinished(level, event, reward) { completion ->
                campaignCompletionRecorded = completion.recorded
                campaignRewardResult = completion.reward
                lastRenderedState?.let(::render)
            }
        },
        onStockSettlementRequested = ::settleStockAfterMatchFinished,
        rewardTracker = if (demoMode) null else RewardTracker(campaignLevel, rewardRegistry),
    )
    private val gameHandler = Handler(Looper.getMainLooper())
    private var screenScope: CoroutineScope? = null
    private var stockSettlementJob: Job? = null
    private var stockSettlementUiStatus = MatchStockSettlementUiStatus.NOT_REQUIRED
    private var pendingStockOperation: (suspend () -> MatchStockSettlementResult?)? = null
    private var pendingStockSuccess: (() -> Unit)? = null
    private lateinit var battlefieldView: BattlefieldView
    private lateinit var hudView: MatchHudView
    private lateinit var bossHudView: BossHudView
    private lateinit var ammunitionPanel: AmmunitionPanelView
    private lateinit var unitPanel: UnitPanelView
    private lateinit var spellPanel: SpellPanelView
    private lateinit var commandDock: LinearLayout
    private lateinit var feedbackToast: MatchFeedbackToastView
    private lateinit var debugButton: TextView
    private lateinit var hitboxToggle: CheckBox
    private lateinit var snapshotCloseButton: TextView
    private lateinit var resultPopupView: MatchResultPopupView
    private lateinit var stockSettlementOverlay: FrameLayout
    private lateinit var stockSettlementStatusText: TextView
    private lateinit var stockSettlementRetryButton: TextView
    private var snapshotMode = false
    private var debugPausedMatch = false
    private var aiUnitAutomationEnabled = true
    private var campaignCompletionRecorded: Boolean? = null
    private var campaignRewardResult: LevelRewardResult? = null
    private var lastMatchFinishedEvent: MatchEvent.MatchFinished? = null
    private var lastMatchReward: LevelRewardResult? = null
    private var lastRenderedState: RenderState? = null

    private var aimAngle = loadoutCatalog.initialAimDirectionDegrees
    private var aimPower = loadoutCatalog.initialAimPower

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
            bossHudView = BossHudView(activity)
            onRetreatClicked = ::showRetreatConfirmationDialog
        }
        ammunitionPanel = AmmunitionPanelView(
            activity,
            viewModel.selectableAmmunitionTypes,
            loadoutCatalog,
        ).apply {
            onAmmoSelected = { ammunitionType ->
                render(viewModel.dispatch(MatchUiAction.SelectAmmo(ammunitionType)))
            }
        }
        unitPanel = UnitPanelView(
            activity,
            viewModel.selectableUnitTypes,
            loadoutCatalog,
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
            loadoutCatalog,
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
            addView(unitPanel, LinearLayout.LayoutParams(0, -1, 1.45f))
            addView(
                View(activity).apply {
                    setBackgroundColor(Color.argb(88, 181, 143, 78))
                },
                LinearLayout.LayoutParams(dp(1), -1).apply {
                    topMargin = dp(14)
                    bottomMargin = dp(14)
                },
            )
            addView(ammunitionPanel, LinearLayout.LayoutParams(0, -1, 0.87f))
            addView(
                View(activity).apply {
                    setBackgroundColor(Color.argb(88, 181, 143, 78))
                },
                LinearLayout.LayoutParams(dp(1), -1).apply {
                    topMargin = dp(14)
                    bottomMargin = dp(14)
                },
            )
            addView(spellPanel, LinearLayout.LayoutParams(0, -1, 1.2f))
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
        hitboxToggle = CheckBox(activity).apply {
            text = "Show hitboxes"
            setTextColor(Color.WHITE)
            textSize = 12f
            buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
            isChecked = battlefieldView.debugHitboxesVisible
            setOnCheckedChangeListener { _, checked ->
                battlefieldView.debugHitboxesVisible = checked
            }
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
                onRetrySave = ::retryCampaignSave,
                onRetryStockSettlement = ::retryStockSettlement,
                onNextLevel = {
                    leaveAfterStockSettlement { onNextLevelClicked(campaignLevel + 1) }
                },
                onCampaignReturn = { leaveAfterStockSettlement(onBackClicked) },
                onDashboardReturn = { leaveAfterStockSettlement(onBackClicked) },
            )
        }
        stockSettlementOverlay = createStockSettlementOverlay()

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
                bossHudView,
                FrameLayout.LayoutParams(dp(300), dp(58)).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = dp(58)
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
                hitboxToggle,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(122)
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
            addView(stockSettlementOverlay, FrameLayout.LayoutParams(-1, -1))
            render(viewModel.dispatch(MatchUiAction.StartMatch))
            if (openDebugOnStart) {
                post { showDebugToolsDialog() }
            }
        }
    }

    fun onResume() {
        if (screenScope == null) {
            screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
        gameHandler.removeCallbacks(gameTick)
        if (::battlefieldView.isInitialized) {
            battlefieldView.setLifecyclePaused(false)
        }
        lastRenderedState?.let(::render)
        gameHandler.postDelayed(gameTick, 33L)
    }

    fun onPause() {
        gameHandler.removeCallbacks(gameTick)
        if (stockSettlementJob?.isActive == true) {
            stockSettlementJob?.cancel()
            stockSettlementJob = null
            stockSettlementUiStatus = MatchStockSettlementUiStatus.FAILED
        }
        screenScope?.cancel()
        screenScope = null
        if (::battlefieldView.isInitialized) {
            battlefieldView.setLifecyclePaused(true)
        }
        if (::feedbackToast.isInitialized) {
            feedbackToast.hideImmediately()
        }
    }

    fun onBackPressed(): Boolean {
        val state = viewModel.renderState
        if (
            !snapshotMode &&
            ::battlefieldView.isInitialized &&
            battlefieldView.isBossDeathPresentationPending(state)
        ) {
            return true
        }
        if (state.statusLabel == MatchStatus.RUNNING.name) {
            render(viewModel.dispatch(MatchUiAction.TogglePause))
        }
        leaveAfterStockSettlement(onBackClicked)
        return true
    }

    private fun render(state: RenderState) {
        lastRenderedState = state
        val bossDeathPresentationPending =
            !snapshotMode &&
                state.statusLabel == MatchStatus.FINISHED.name &&
                state.boss?.health?.let { it <= 0 } == true &&
                ::battlefieldView.isInitialized &&
                battlefieldView.isBossDeathPresentationPending(state)
        val popupState = MatchResultPopupStateMapper.map(
            isFinished = state.statusLabel == MatchStatus.FINISHED.name && !snapshotMode,
            bossDeathPresentationPending = bossDeathPresentationPending,
            outcome = state.outcomeLabel?.let(MatchOutcome::valueOf),
            campaignLevel = campaignLevel,
            biomeName = state.terrainBiome,
            demoMode = demoMode,
            campaignCompletionRecorded = campaignCompletionRecorded,
            reward = campaignRewardResult,
            stockSettlementStatus = stockSettlementUiStatus,
        )
        hudView.render(state)
        bossHudView.render(state.boss)
        ammunitionPanel.render(state)
        unitPanel.render(state)
        spellPanel.render(state)
        battlefieldView.render(
            state,
            showOutcomeLabel = popupState == null && !bossDeathPresentationPending,
        )
        debugButton.visibility = if (
            openDebugOnStart && state.statusLabel == "RUNNING" && !snapshotMode
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        resultPopupView.visibility = if (popupState == null) View.GONE else View.VISIBLE
        updateStockSettlementOverlay(state)
        if (popupState != null) {
            resultPopupView.render(popupState)
            resultPopupView.bringToFront()
        }
    }

    private fun settleStockAfterMatchFinished() {
        if (playerInventory == null ||
            stockSettlementUiStatus != MatchStockSettlementUiStatus.NOT_REQUIRED
        ) return
        runStockOperation(
            operation = { viewModel.settleStockUsage() },
            onSuccess = {},
        )
    }

    private fun restartMatch() {
        runStockOperation(
            operation = { viewModel.restartMatch() },
            onSuccess = {
                campaignCompletionRecorded = null
                campaignRewardResult = null
                lastMatchFinishedEvent = null
                lastMatchReward = null
                aimAngle = loadoutCatalog.initialAimDirectionDegrees
                aimPower = loadoutCatalog.initialAimPower
                stockSettlementUiStatus = MatchStockSettlementUiStatus.NOT_REQUIRED
                render(viewModel.renderState)
            },
        )
    }

    private fun leaveAfterStockSettlement(action: () -> Unit) {
        if (viewModel.renderState.statusLabel == MatchStatus.RUNNING.name) {
            render(viewModel.dispatch(MatchUiAction.TogglePause))
        }
        runStockOperation(
            operation = { viewModel.stopMatch() },
            onSuccess = action,
        )
    }

    private fun retryStockSettlement() {
        val operation = pendingStockOperation ?: { viewModel.settleStockUsage() }
        val onSuccess = pendingStockSuccess ?: {}
        runStockOperation(operation, onSuccess)
    }

    private fun runStockOperation(
        operation: suspend () -> MatchStockSettlementResult?,
        onSuccess: () -> Unit,
    ) {
        if (stockSettlementJob?.isActive == true) return
        val scope = screenScope ?: CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate,
        ).also { screenScope = it }
        pendingStockOperation = operation
        pendingStockSuccess = onSuccess
        stockSettlementUiStatus = MatchStockSettlementUiStatus.PENDING
        lastRenderedState?.let(::render)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = operation()
            val success = result == null ||
                result is MatchStockSettlementResult.Settled ||
                result is MatchStockSettlementResult.AlreadySettled
            stockSettlementJob = null
            if (success) {
                stockSettlementUiStatus = if (playerInventory == null) {
                    MatchStockSettlementUiStatus.NOT_REQUIRED
                } else {
                    MatchStockSettlementUiStatus.SETTLED
                }
                val completion = pendingStockSuccess
                pendingStockOperation = null
                pendingStockSuccess = null
                lastRenderedState?.let(::render)
                completion?.invoke()
            } else {
                stockSettlementUiStatus = MatchStockSettlementUiStatus.FAILED
                lastRenderedState?.let(::render)
            }
        }
        stockSettlementJob = job
        job.start()
    }

    private fun createStockSettlementOverlay(): FrameLayout =
        FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(175, 0, 0, 0))
            isClickable = true
            isFocusable = true
            setOnClickListener { }
            val panel = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(18), dp(24), dp(18))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.rgb(28, 23, 18))
                    setStroke(dp(2), Color.rgb(174, 128, 62))
                }
                stockSettlementStatusText = TextView(activity).apply {
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTextColor(Color.rgb(255, 239, 211))
                }
                addView(stockSettlementStatusText, LinearLayout.LayoutParams(-1, -2))
                addView(TextView(activity).apply {
                    text = activity.getString(R.string.match_result_retry_stock)
                    gravity = Gravity.CENTER
                    textSize = 14f
                    setTextColor(Color.rgb(72, 43, 20))
                    UiTypography.applyButtonTypeface(this)
                    setBackgroundResource(R.drawable.loadout_start_match_button)
                    setPadding(dp(18), dp(8), dp(18), dp(8))
                    visibility = View.GONE
                    stockSettlementRetryButton = this
                    setOnClickListener { retryStockSettlement() }
                }, LinearLayout.LayoutParams(-1, dp(48)).apply {
                    topMargin = dp(12)
                })
            }
            addView(panel, FrameLayout.LayoutParams(dp(380), -2, Gravity.CENTER))
            visibility = View.GONE
        }

    private fun updateStockSettlementOverlay(state: RenderState) {
        if (!::stockSettlementOverlay.isInitialized) return
        val isResult = state.statusLabel == MatchStatus.FINISHED.name
        val isBlocking = !isResult &&
            stockSettlementUiStatus in setOf(
                MatchStockSettlementUiStatus.PENDING,
                MatchStockSettlementUiStatus.FAILED,
            )
        stockSettlementOverlay.visibility = if (isBlocking) View.VISIBLE else View.GONE
        if (!isBlocking) return
        stockSettlementStatusText.text = activity.getString(
            if (stockSettlementUiStatus == MatchStockSettlementUiStatus.PENDING) {
                R.string.match_result_saving_stock
            } else {
                R.string.match_result_stock_settlement_failed
            },
        )
        stockSettlementRetryButton.visibility =
            if (stockSettlementUiStatus == MatchStockSettlementUiStatus.FAILED) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun retryCampaignSave() {
        val event = lastMatchFinishedEvent ?: return
        campaignCompletionRecorded = null
        onMatchFinished(campaignLevel, event, lastMatchReward) { completion ->
            campaignCompletionRecorded = completion.recorded
            campaignRewardResult = completion.reward
            lastRenderedState?.let(::render)
        }
    }

    private fun showRetreatConfirmationDialog() {
        val status = viewModel.renderState.statusLabel
        if (status != MatchStatus.RUNNING.name && status != "PAUSED") return

        val wasRunning = status == MatchStatus.RUNNING.name
        if (wasRunning) {
            render(viewModel.dispatch(MatchUiAction.TogglePause))
        }
        var retreatConfirmed = false
        AlertDialog.Builder(activity)
            .setTitle(R.string.match_retreat_confirmation_title)
            .setMessage(R.string.match_retreat_confirmation_message)
            .setNegativeButton(R.string.match_retreat_cancel, null)
            .setPositiveButton(R.string.match_retreat_confirm) { _, _ ->
                retreatConfirmed = true
                feedbackToast.hideImmediately()
                render(viewModel.dispatch(MatchUiAction.Retreat))
            }
            .create()
            .apply {
                setOnDismissListener {
                    if (
                        !retreatConfirmed &&
                        wasRunning &&
                        viewModel.renderState.statusLabel == "PAUSED"
                    ) {
                        render(viewModel.dispatch(MatchUiAction.TogglePause))
                    }
                }
            }
            .show()
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
        root.addView(CheckBox(activity).apply {
            text = activity.getString(R.string.match_debug_ai_unit_automation)
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
            isChecked = aiUnitAutomationEnabled
            setOnCheckedChangeListener { _, enabled ->
                aiUnitAutomationEnabled = enabled
                viewModel.setAiUnitAutomationEnabled(enabled)
            }
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
