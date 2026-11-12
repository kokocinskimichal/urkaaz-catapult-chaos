package com.urkaaaz.tutorial

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.tutorial.TutorialCatalog
import com.urkaaaz.application.tutorial.TutorialCatalogFailure
import com.urkaaaz.application.tutorial.TutorialCatalogRead
import com.urkaaaz.application.tutorial.TutorialCatalogRepository
import com.urkaaaz.application.tutorial.TutorialContext
import com.urkaaaz.application.tutorial.TutorialEffect
import com.urkaaaz.application.tutorial.TutorialEngine
import com.urkaaaz.application.tutorial.TutorialEngineUpdate
import com.urkaaaz.application.tutorial.TutorialEvent
import com.urkaaaz.application.tutorial.TutorialPlayerActionKind
import com.urkaaaz.application.tutorial.TutorialProgressFailure
import com.urkaaaz.application.tutorial.TutorialProgressRead
import com.urkaaaz.application.tutorial.TutorialProgressRepository
import com.urkaaaz.application.tutorial.TutorialProgressWrite
import com.urkaaaz.application.tutorial.TutorialRejection
import com.urkaaaz.application.tutorial.TutorialScenarioFacts
import com.urkaaaz.application.tutorial.TutorialScreen
import com.urkaaaz.application.tutorial.TutorialMatchEventMapper
import com.urkaaaz.application.tutorial.TutorialTargetKind
import com.urkaaaz.application.tutorial.TutorialSignal
import com.urkaaaz.application.tutorial.TutorialStatus
import com.urkaaaz.application.tutorial.TutorialScope
import com.urkaaaz.application.tutorial.TutorialUiState
import com.urkaaaz.application.tutorial.TutorialUserAction
import com.urkaaaz.application.tutorial.TutorialTrigger
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.navigation.ScreenHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class TutorialDashboardItem(
    val tutorialId: String,
    val status: TutorialStatus?,
    val isMatchTutorial: Boolean,
    val enabled: Boolean,
)

interface TutorialSignalSink {
    fun onMatchEvents(
        events: List<MatchEvent>,
        scenarioFacts: TutorialScenarioFacts,
        isDemo: Boolean,
        matchGeneration: Long,
    )

    fun onAcceptedPlayerAction(
        kind: TutorialPlayerActionKind,
        isDemo: Boolean,
        matchId: String?,
        matchGeneration: Long,
    )
}

class TutorialHost(
    context: Context,
    private val screenHost: ScreenHost,
    private val catalogRepository: TutorialCatalogRepository,
    private val progressRepository: TutorialProgressRepository,
    private val languageSettings: LanguageSettings,
) : FrameLayout(context), TutorialSignalSink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val targetRegistry = TutorialTargetRegistry()
    private val overlay = TutorialOverlayView(
        context = context,
        onNext = ::dispatchNext,
        onSkip = { dispatchUserAction(TutorialUserAction.Skip) },
        onViewportReady = { currentUiState?.let(::renderState) },
    )
    private val errorText = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(12), dp(18), dp(12))
        setBackgroundResource(R.drawable.bg_hud_panel)
        visibility = View.GONE
    }
    private val errorRetry = Button(context).apply {
        text = context.getString(R.string.tutorial_retry)
        setOnClickListener { initialize() }
        visibility = View.GONE
    }
    private val errorPanel = FrameLayout(context).apply {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = false
        addView(errorText, LayoutParams(dp(420), -2, Gravity.CENTER))
        addView(errorRetry, LayoutParams(dp(160), dp(48), Gravity.CENTER).apply {
            topMargin = dp(96)
        })
        visibility = View.GONE
    }
    private var catalog: TutorialCatalog? = null
    private var engine: TutorialEngine? = null
    private var currentUiState: TutorialUiState? = null
    private var currentContent: ScreenContent? = null
    private var currentTutorialContent: TutorialScreenContent? = null
    private var currentLanguage: com.urkaaaz.application.language.AppLanguage? = null
    private var isInitializing = false
    private val pendingSignals = mutableListOf<TutorialSignal>()
    private val pendingMilestoneIds = linkedSetOf<String>()
    private var pendingUiState: TutorialUiState? = null
    private var waitingForTargetState: TutorialUiState? = null
    private var targetLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    init {
        addView(screenHost, LayoutParams(-1, -1))
        addView(overlay, LayoutParams(-1, -1))
        addView(errorPanel, LayoutParams(-1, -1))
    }

    fun show(content: ScreenContent) {
        clearTargetLayoutListener()
        currentContent = content
        currentTutorialContent = content as? TutorialScreenContent
        targetRegistry.clear()
        currentTutorialContent?.let(targetRegistry::register)
        screenHost.show(content)
        if (currentTutorialContent?.tutorialScreen == TutorialScreen.LOADOUT) {
            currentLanguage?.let { language ->
                deliverPendingMilestones(
                    screen = TutorialScreen.LOADOUT,
                    context = TutorialContext(isDemo = false, language = language),
                )
            }
        }
        dispatchScreenEntered()
    }

    fun resumeActiveScreen() {
        screenHost.resumeActiveScreen()
        if (engine == null && !isInitializing) initialize()
        else currentUiState?.let(::renderState)
    }

    fun pauseActiveScreen() {
        screenHost.pauseActiveScreen()
        overlay.visibility = View.GONE
    }

    fun handleBackPressed(): Boolean {
        if (engine?.currentState() == null) return false
        dispatchUserAction(TutorialUserAction.Skip)
        return true
    }

    fun destroy() {
        screenHost.pauseActiveScreen()
        clearTargetLayoutListener()
        scope.cancel()
        targetRegistry.clear()
    }

    fun onPlayerProgressReset() {
        engine?.resetProgress()
    }

    fun dashboardItems(): List<TutorialDashboardItem> {
        val currentCatalog = catalog ?: return emptyList()
        val currentEngine = engine ?: return emptyList()
        val statuses = currentEngine.terminalStatuses()
        return currentCatalog.tutorials.map { definition ->
            TutorialDashboardItem(
                tutorialId = definition.tutorialId,
                status = statuses[definition.tutorialId],
                isMatchTutorial = definition.scope == TutorialScope.MATCH,
                enabled = definition.enabled,
            )
        }
    }

    suspend fun requestRepeat(tutorialId: String): TutorialProgressWrite {
        val currentCatalog = catalog
            ?: return TutorialProgressWrite.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE)
        val currentEngine = engine
            ?: return TutorialProgressWrite.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE)
        if (currentCatalog.tutorial(tutorialId) == null) {
            return TutorialProgressWrite.Failure(TutorialProgressFailure.INVALID_DATA)
        }
        val result = progressRepository.reset(tutorialId)
        if (result == TutorialProgressWrite.Saved) {
            currentEngine.forgetTerminalStatus(tutorialId)
            currentContent?.let(::dispatchScreenEntered)
        }
        return result
    }

    override fun onMatchEvents(
        events: List<MatchEvent>,
        scenarioFacts: TutorialScenarioFacts,
        isDemo: Boolean,
        matchGeneration: Long,
    ) {
        events.forEach { event ->
            val context = TutorialContext(
                isDemo = isDemo,
                language = currentLanguage ?: return,
                matchId = event.matchId.value,
                matchGeneration = matchGeneration,
            )
            if (event is MatchEvent.MatchStarted) {
                dispatch(
                    TutorialSignal.MatchStarted(
                        scenarioFacts = scenarioFacts,
                        context = context,
                    ),
                )
                deliverPendingMilestones(TutorialScreen.MATCH, context)
            } else {
                TutorialMatchEventMapper.observe(event)?.let { observation ->
                    dispatch(
                        TutorialSignal.MatchEventObserved(
                            eventKind = observation.eventKind,
                            targetInstanceId = observation.targetInstanceId,
                            context = context,
                        ),
                    )
                }
                if (event is MatchEvent.MatchFinished) {
                    dispatch(TutorialSignal.MatchLifecycleEnded(context))
                }
            }
        }
    }

    override fun onAcceptedPlayerAction(
        kind: TutorialPlayerActionKind,
        isDemo: Boolean,
        matchId: String?,
        matchGeneration: Long,
    ) {
        val language = currentLanguage ?: return
        dispatch(
            TutorialSignal.UserAction(
                action = TutorialUserAction.PlayerActionPerformed(kind),
                context = TutorialContext(
                    isDemo = isDemo,
                    language = language,
                    matchId = matchId,
                    matchGeneration = matchGeneration,
                ),
            ),
        )
    }

    fun onCampaignMilestonesApplied(milestoneIds: Set<String>) {
        val language = currentLanguage ?: return
        val source = currentTutorialContent
        val context = TutorialContext(
            isDemo = source?.isTutorialDemoMode == true,
            language = language,
            matchId = source?.tutorialMatchId,
            matchGeneration = source?.tutorialMatchGeneration ?: 0L,
        )
        pendingMilestoneIds += milestoneIds
        val screen = source?.tutorialScreen ?: return
        deliverPendingMilestones(screen, context)
    }

    private fun deliverPendingMilestones(
        screen: TutorialScreen,
        context: TutorialContext,
    ) {
        val currentCatalog = catalog ?: return
        val readyMilestones = pendingMilestoneIds.filter { milestoneId ->
            currentCatalog.tutorials.any { definition ->
                val trigger = definition.trigger as? TutorialTrigger.CampaignMilestoneApplied
                trigger?.milestoneId == milestoneId &&
                    when (definition.scope) {
                        TutorialScope.SCREEN -> screen == TutorialScreen.LOADOUT
                        TutorialScope.MATCH -> screen == TutorialScreen.MATCH &&
                            context.matchId != null
                    }
            }
        }
        readyMilestones.forEach { milestoneId ->
            dispatch(TutorialSignal.CampaignMilestoneApplied(milestoneId, context))
            pendingMilestoneIds.remove(milestoneId)
        }
    }

    private fun initialize() {
        if (isInitializing) return
        if (currentTutorialContent?.isTutorialDemoMode == true) return
        isInitializing = true
        errorPanel.visibility = View.GONE
        scope.launch {
            when (val catalogRead = catalogRepository.load()) {
                is TutorialCatalogRead.Failure -> {
                    showFailure(
                        if (
                            catalogRead.reason == TutorialCatalogFailure.INVALID_CATALOG ||
                            catalogRead.reason == TutorialCatalogFailure.INVALID_JSON ||
                            catalogRead.reason == TutorialCatalogFailure.RESOURCE_UNAVAILABLE
                        ) {
                            R.string.tutorial_catalog_invalid
                        } else {
                            R.string.tutorial_progress_unavailable
                        },
                    )
                    isInitializing = false
                    return@launch
                }
                is TutorialCatalogRead.Available -> catalog = catalogRead.catalog
            }
            when (val languageRead = languageSettings.load()) {
                is LanguageLoadResult.Loaded -> currentLanguage = languageRead.language
                is LanguageLoadResult.Failure -> {
                    showFailure(R.string.tutorial_progress_unavailable)
                    isInitializing = false
                    return@launch
                }
            }
            val loadedCatalog = requireNotNull(catalog)
            when (
                val progressRead = progressRepository.load(
                    loadedCatalog.tutorials.mapTo(mutableSetOf()) { it.tutorialId },
                )
            ) {
                is TutorialProgressRead.Failure -> {
                    showFailure(R.string.tutorial_progress_unavailable)
                    isInitializing = false
                    return@launch
                }
                is TutorialProgressRead.Available -> {
                    engine = TutorialEngine(
                        loadedCatalog,
                        progressRead.progress.terminalStatuses,
                    )
                }
            }
            isInitializing = false
            dispatchScreenEntered()
            val queuedSignals = pendingSignals.toList()
            pendingSignals.clear()
            queuedSignals.forEach(::dispatch)
        }
    }

    private fun dispatchScreenEntered(content: ScreenContent? = currentContent) {
        val source = content as? TutorialScreenContent ?: return
        if (source.isTutorialDemoMode) return
        val activeEngine = engine
        val language = currentLanguage
        if (activeEngine == null || language == null) {
            initialize()
            return
        }
        renderUpdate(
            activeEngine.handle(
                TutorialSignal.ScreenEntered(
                    screen = source.tutorialScreen,
                    context = TutorialContext(
                        isDemo = source.isTutorialDemoMode,
                        language = language,
                    ),
                ),
            ),
        )
    }

    private fun dispatchUserAction(action: TutorialUserAction) {
        val activeEngine = engine ?: return
        val content = currentTutorialContent
        val language = currentLanguage ?: return
        val context = TutorialContext(
            isDemo = content?.isTutorialDemoMode == true,
            language = language,
            matchId = content?.tutorialMatchId,
            matchGeneration = content?.tutorialMatchGeneration ?: 0L,
        )
        renderUpdate(
            activeEngine.handle(TutorialSignal.UserAction(action, context)),
        )
    }

    private fun dispatchNext() {
        val state = engine?.currentState()
        if (state?.actionLabel != com.urkaaaz.application.tutorial.TutorialActionLabel.START_BATTLE) {
            dispatchUserAction(TutorialUserAction.Next)
            return
        }
        dispatchUserAction(TutorialUserAction.Next)
        if (currentTutorialContent?.startBattleFromTutorial() != true) {
            showFailure(R.string.tutorial_presentation_unavailable)
        }
    }

    private fun dispatch(signal: TutorialSignal) {
        val activeEngine = engine
        if (activeEngine == null) {
            if (signal.context.isDemo) return
            if (signal !is TutorialSignal.ScreenEntered) pendingSignals += signal
            initialize()
            return
        }
        renderUpdate(activeEngine.handle(signal))
    }

    private fun renderUpdate(update: TutorialEngineUpdate) {
        var effectFailure = false
        update.effects.forEach { effect ->
            val handled = applyEffect(effect)
            if (!handled && effect !is TutorialEffect.FocusTarget) {
                Log.e(TAG, "Active tutorial screen did not handle effect $effect")
                effectFailure = true
            }
        }
        update.events.filterIsInstance<TutorialEvent.Rejected>().forEach { event ->
            Log.e(TAG, "Tutorial action rejected: ${event.reason}")
        }
        renderState(update.uiState)
        if (effectFailure) showFailure(R.string.tutorial_presentation_unavailable)
        update.terminalStatus?.let { (tutorialId, status) ->
            val loadedCatalog = catalog ?: return@let
            scope.launch {
                when (
                    val result = progressRepository.markTerminal(
                        tutorialId = tutorialId,
                        status = status,
                        schemaVersion = loadedCatalog.schemaVersion,
                        catalogRevision = loadedCatalog.revision,
                    )
                ) {
                    TutorialProgressWrite.Saved -> Unit
                    is TutorialProgressWrite.Failure -> {
                        Log.e(TAG, "Failed to save tutorial progress: ${result.reason}")
                        errorText.setText(R.string.tutorial_progress_save_failed)
                        errorRetry.visibility = View.GONE
                        errorPanel.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun applyEffect(effect: TutorialEffect): Boolean =
        when (effect) {
            TutorialEffect.PauseSimulation,
            TutorialEffect.ResumeSimulation,
            TutorialEffect.SuspendAi,
            TutorialEffect.ResumeAi,
            is TutorialEffect.RestrictAiToIntroduced,
            is TutorialEffect.LockTargets,
            is TutorialEffect.UnlockTargets,
            -> targetRegistry.dispatch(effect)
            is TutorialEffect.FocusTarget -> {
                val target = catalog?.targets?.firstOrNull { it.targetId == effect.targetId }
                if (target?.kind == TutorialTargetKind.BATTLEFIELD) {
                    targetRegistry.focus(effect.targetId, effect.targetInstanceId)
                } else {
                    true
                }
            }
        }

    private fun renderState(state: TutorialUiState?) {
        currentUiState = state
        errorPanel.visibility = View.GONE
        currentTutorialContent?.setTutorialUiState(state)
        if (state == null) {
            pendingUiState = null
            waitingForTargetState = null
            clearTargetLayoutListener()
            overlay.render(null, null)
            return
        }
        if (width <= 0 || height <= 0) {
            pendingUiState = state
            return
        }
        pendingUiState = null
        val targetId = state.targetId
        if (targetId != null && targetId !in targetRegistry.registeredTargetIds()) {
            reject(TutorialRejection.TARGET_NOT_REGISTERED)
            showFailure(R.string.tutorial_target_not_registered)
            return
        }
        val target = if (targetId == null) null else targetRegistry.resolve(
            targetId,
            state.targetInstanceId,
        )
        if (targetId != null && target == null) {
            if (currentTutorialContent?.tutorialTargetsReady == false) {
                deferUntilTargetLayout(state)
                return
            }
            waitingForTargetState = null
            clearTargetLayoutListener()
            reject(TutorialRejection.TARGET_NOT_VISIBLE)
            showFailure(R.string.tutorial_target_not_visible)
            return
        }
        waitingForTargetState = null
        clearTargetLayoutListener()
        var visibleTarget = target
        if (target != null && !isFullyVisible(target)) {
            val kind = catalog?.targets?.firstOrNull { it.targetId == targetId }?.kind
            if (kind == TutorialTargetKind.BATTLEFIELD) {
                targetRegistry.focus(requireNotNull(targetId), state.targetInstanceId)
                visibleTarget = targetRegistry.resolve(requireNotNull(targetId), state.targetInstanceId)
            }
            if (visibleTarget == null || !isFullyVisible(visibleTarget)) {
                reject(TutorialRejection.TARGET_NOT_VISIBLE)
                showFailure(R.string.tutorial_target_not_visible)
                return
            }
        }
        if (!overlay.render(state, visibleTarget)) {
            reject(TutorialRejection.HINT_PLACEMENT_IMPOSSIBLE)
            showFailure(R.string.tutorial_hint_placement_impossible)
        }
    }

    private fun reject(reason: TutorialRejection) {
        engine?.reject(reason)?.events?.forEach { event ->
            Log.e(TAG, "Tutorial presentation rejected: $event")
        }
    }

    private fun isFullyVisible(target: RectF): Boolean =
        target.left >= 0f && target.top >= 0f &&
            target.right <= width.toFloat() && target.bottom <= height.toFloat()

    private fun showFailure(messageResource: Int) {
        overlay.render(null, null)
        errorText.setText(messageResource)
        errorRetry.visibility = if (engine == null) View.VISIBLE else View.GONE
        errorPanel.visibility = View.VISIBLE
    }

    private fun deferUntilTargetLayout(state: TutorialUiState) {
        waitingForTargetState = state
        if (targetLayoutListener != null) return
        val view = currentContent?.view ?: return
        val observer = view.viewTreeObserver
        if (!observer.isAlive) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val pending = waitingForTargetState ?: return@OnGlobalLayoutListener
            if (currentTutorialContent?.tutorialTargetsReady == true) {
                clearTargetLayoutListener()
                renderState(pending)
            }
        }
        targetLayoutListener = listener
        observer.addOnGlobalLayoutListener(listener)
    }

    private fun clearTargetLayoutListener() {
        val listener = targetLayoutListener ?: return
        currentContent?.view?.viewTreeObserver?.let { observer ->
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
        targetLayoutListener = null
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) pendingUiState?.let(::renderState)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "TutorialHost"
    }
}
