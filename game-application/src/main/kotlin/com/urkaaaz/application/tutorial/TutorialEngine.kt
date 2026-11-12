package com.urkaaaz.application.tutorial

class TutorialEngine(
    private val catalog: TutorialCatalog,
    initialTerminalStatuses: Map<String, TutorialStatus> = emptyMap(),
) {
    private data class PendingTutorial(
        val definition: TutorialDefinition,
        val context: TutorialContext,
        val targetInstanceId: String?,
        val catalogIndex: Int,
    )

    private data class ActiveTutorial(
        val pending: PendingTutorial,
        var stepIndex: Int = 0,
        var completionCount: Int = 0,
    )

    private val terminalStatuses = initialTerminalStatuses
        .filterKeys { catalog.tutorial(it) != null }
        .toMutableMap()
    private val pending = mutableListOf<PendingTutorial>()
    private var active: ActiveTutorial? = null

    fun handle(signal: TutorialSignal): TutorialEngineUpdate {
        if (signal.context.isDemo) return currentUpdate()
        val effects = mutableListOf<TutorialEffect>()
        val events = mutableListOf<TutorialEvent>()
        var terminalStatus: Pair<String, TutorialStatus>? = null

        when (signal) {
            is TutorialSignal.ScreenEntered -> enqueueMatching(signal)
            is TutorialSignal.MatchStarted -> {
                interruptActiveMatchIfDifferent(signal.context, effects, events)
                enqueueMatching(signal)
            }
            is TutorialSignal.MatchEventObserved -> {
                enqueueMatching(signal)
                if (active != null) {
                    val completion = currentStep().completion
                    if (
                        completion is TutorialStepCompletion.MatchEventCount &&
                        completion.eventKind == signal.eventKind
                    ) {
                        active!!.completionCount += 1
                        if (active!!.completionCount >= completion.count) {
                            advance(effects, events)
                        }
                    }
                }
            }
            is TutorialSignal.CampaignMilestoneApplied -> enqueueMatching(signal)
            is TutorialSignal.UserAction -> {
                val result = handleUserAction(signal.action)
                effects += result.effects
                events += result.events
                terminalStatus = result.terminalStatus
            }
            is TutorialSignal.MatchLifecycleEnded -> {
                interruptActiveMatch(effects, events)
                pending.removeAll { it.definition.scope == TutorialScope.MATCH }
            }
        }

        activateNext(effects, events)
        if (terminalStatus == null) {
            terminalStatus = when (val terminalEvent = events.firstOrNull {
                it is TutorialEvent.Completed || it is TutorialEvent.Skipped
            }) {
                is TutorialEvent.Completed ->
                    terminalEvent.tutorialId to TutorialStatus.COMPLETED
                is TutorialEvent.Skipped ->
                    terminalEvent.tutorialId to TutorialStatus.SKIPPED
                else -> null
            }
        }
        return TutorialEngineUpdate(
            uiState = currentUiState(),
            effects = effects,
            events = events,
            terminalStatus = terminalStatus,
        )
    }

    fun reject(reason: TutorialRejection): TutorialEngineUpdate =
        TutorialEngineUpdate(
            uiState = currentUiState(),
            events = listOf(TutorialEvent.Rejected(reason)),
        )

    fun terminalStatuses(): Map<String, TutorialStatus> = terminalStatuses.toMap()

    fun currentState(): TutorialUiState? = currentUiState()

    fun forgetTerminalStatus(tutorialId: String): Boolean {
        if (catalog.tutorial(tutorialId) == null) return false
        terminalStatuses.remove(tutorialId)
        pending.removeAll { it.definition.tutorialId == tutorialId }
        return true
    }

    fun resetProgress() {
        terminalStatuses.clear()
        pending.clear()
    }

    private fun enqueueMatching(signal: TutorialSignal) {
        catalog.tutorials.forEachIndexed { index, definition ->
            if (!definition.enabled || definition.tutorialId in terminalStatuses) return@forEachIndexed
            if (active?.pending?.definition?.tutorialId == definition.tutorialId) return@forEachIndexed
            if (pending.any { it.definition.tutorialId == definition.tutorialId }) return@forEachIndexed
            if (matches(definition.trigger, signal)) {
                val needsInstanceId = definition.steps.any { step ->
                    catalog.targets.any { target ->
                        target.targetId == step.targetId &&
                            target.kind == TutorialTargetKind.BATTLEFIELD
                    }
                }
                val targetInstanceId = if (needsInstanceId) {
                    (signal as? TutorialSignal.MatchEventObserved)?.targetInstanceId
                } else {
                    null
                }
                pending += PendingTutorial(
                    definition = definition,
                    context = signal.context,
                    targetInstanceId = targetInstanceId,
                    catalogIndex = index,
                )
            }
        }
        pending.sortWith(
            compareByDescending<PendingTutorial> { it.definition.priority }
                .thenBy { it.catalogIndex },
        )
    }

    private fun matches(
        trigger: TutorialTrigger,
        signal: TutorialSignal,
    ): Boolean = when (trigger) {
        is TutorialTrigger.ScreenEntered ->
            signal is TutorialSignal.ScreenEntered && trigger.screen == signal.screen
        is TutorialTrigger.MatchStarted -> {
            if (signal !is TutorialSignal.MatchStarted) {
                false
            } else {
                val rangeMatches = trigger.campaignLevelRange?.let { range ->
                    signal.scenarioFacts.campaignLevel?.let { it in range } ?: false
                } ?: true
                val mechanicMatches = trigger.requiresMechanicId?.let {
                    it in signal.scenarioFacts.availableMechanicIds
                } ?: true
                rangeMatches && mechanicMatches
            }
        }
        is TutorialTrigger.MatchEventObserved ->
            signal is TutorialSignal.MatchEventObserved &&
                trigger.eventKind == signal.eventKind
        is TutorialTrigger.CampaignMilestoneApplied ->
            signal is TutorialSignal.CampaignMilestoneApplied &&
                trigger.milestoneId == signal.milestoneId
    }

    private fun handleUserAction(action: TutorialUserAction): TutorialEngineUpdate {
        val current = active ?: return TutorialEngineUpdate(
            uiState = null,
            events = listOf(TutorialEvent.Rejected(TutorialRejection.NO_ACTIVE_TUTORIAL)),
        )
        val effects = mutableListOf<TutorialEffect>()
        val events = mutableListOf<TutorialEvent>()
        return when (action) {
            TutorialUserAction.Skip -> {
                finish(
                    current = current,
                    status = TutorialStatus.SKIPPED,
                    effects = effects,
                    events = events,
                )
            }
            TutorialUserAction.Next -> {
                if (currentStep().completion != TutorialStepCompletion.Next) {
                    TutorialEngineUpdate(
                        uiState = currentUiState(),
                        events = listOf(TutorialEvent.Rejected(TutorialRejection.ACTION_NOT_ALLOWED)),
                    )
                } else {
                    advance(effects, events)
                    val status = events.firstOrNull { it is TutorialEvent.Completed }
                        ?.let { TutorialStatus.COMPLETED }
                    TutorialEngineUpdate(
                        uiState = currentUiState(),
                        effects = effects,
                        events = events,
                        terminalStatus = status?.let {
                            current.pending.definition.tutorialId to it
                        },
                    )
                }
            }
            is TutorialUserAction.PlayerActionPerformed -> {
                val completion = currentStep().completion
                if (completion !is TutorialStepCompletion.PlayerAction ||
                    completion.kind != action.kind
                ) {
                    TutorialEngineUpdate(
                        uiState = currentUiState(),
                        events = listOf(TutorialEvent.Rejected(TutorialRejection.ACTION_NOT_ALLOWED)),
                    )
                } else {
                    current.completionCount += 1
                    var terminalStatus: Pair<String, TutorialStatus>? = null
                    if (current.completionCount >= completion.count) {
                        advance(effects, events)
                        if (events.any { it is TutorialEvent.Completed }) {
                            terminalStatus =
                                current.pending.definition.tutorialId to TutorialStatus.COMPLETED
                        }
                    }
                    TutorialEngineUpdate(
                        uiState = currentUiState(),
                        effects = effects,
                        events = events,
                        terminalStatus = terminalStatus,
                    )
                }
            }
        }
    }

    private fun advance(
        effects: MutableList<TutorialEffect>,
        events: MutableList<TutorialEvent>,
    ) {
        val current = active ?: return
        val completedStep = currentStep()
        events += TutorialEvent.StepCompleted(
            current.pending.definition.tutorialId,
            completedStep.stepId,
        )
        current.stepIndex += 1
        current.completionCount = 0
        if (current.stepIndex == current.pending.definition.steps.size) {
            finish(
                current = current,
                status = TutorialStatus.COMPLETED,
                effects = effects,
                events = events,
            )
        } else {
            configureCurrentStep(current, effects)
        }
    }

    private fun finish(
        current: ActiveTutorial,
        status: TutorialStatus,
        effects: MutableList<TutorialEffect>,
        events: MutableList<TutorialEvent>,
    ): TutorialEngineUpdate {
        val definition = current.pending.definition
        terminalStatuses[definition.tutorialId] = status
        if (definition.scope == TutorialScope.MATCH) {
            if (definition.rules.pauseSimulationOnHint) {
                effects += TutorialEffect.ResumeSimulation
            }
            if (definition.rules.suspendAiWhileActive) effects += TutorialEffect.ResumeAi
            effects += TutorialEffect.UnlockTargets(definition.introduces)
        }
        events += when (status) {
            TutorialStatus.COMPLETED -> TutorialEvent.Completed(definition.tutorialId)
            TutorialStatus.SKIPPED -> TutorialEvent.Skipped(definition.tutorialId)
        }
        active = null
        activateNext(effects, events)
        return TutorialEngineUpdate(
            uiState = currentUiState(),
            effects = effects,
            events = events,
            terminalStatus = definition.tutorialId to status,
        )
    }

    private fun activateNext(
        effects: MutableList<TutorialEffect>,
        events: MutableList<TutorialEvent>,
    ) {
        if (active != null) return
        val nextIndex = pending.indexOfFirst { item ->
            item.definition.requires.all { it in terminalStatuses }
        }
        if (nextIndex < 0) return
        val next = pending.removeAt(nextIndex)
        active = ActiveTutorial(next)
        events += TutorialEvent.Started(next.definition.tutorialId)
        configureCurrentStep(active!!, effects)
    }

    private fun configureCurrentStep(
        current: ActiveTutorial,
        effects: MutableList<TutorialEffect>,
    ) {
        val definition = current.pending.definition
        val step = currentStep()
        val targetInstanceId = step.targetId
            ?.let { targetId -> catalog.targets.firstOrNull { it.targetId == targetId } }
            ?.takeIf { it.kind == TutorialTargetKind.BATTLEFIELD }
            ?.let { current.pending.targetInstanceId }
        if (definition.scope == TutorialScope.MATCH) {
            if (current.stepIndex == 0) {
                if (definition.rules.suspendAiWhileActive) effects += TutorialEffect.SuspendAi
                if (definition.rules.restrictAiToIntroducedMechanics) {
                    effects += TutorialEffect.RestrictAiToIntroduced(definition.introduces)
                }
                effects += TutorialEffect.LockTargets(definition.introduces)
            }
            val isLiveStep = step.completion is TutorialStepCompletion.PlayerAction ||
                step.completion is TutorialStepCompletion.MatchEventCount
            if (definition.rules.pauseSimulationOnHint) {
                effects += if (isLiveStep) {
                    TutorialEffect.ResumeSimulation
                } else {
                    TutorialEffect.PauseSimulation
                }
            }
            step.targetId?.let {
                effects += TutorialEffect.FocusTarget(
                    targetId = it,
                    targetInstanceId = current.pending.targetInstanceId,
                )
            }
        }
    }

    private fun interruptActiveMatch(
        effects: MutableList<TutorialEffect>,
        events: MutableList<TutorialEvent>,
    ) {
        val current = active ?: return
        if (current.pending.definition.scope != TutorialScope.MATCH) return
        if (current.pending.definition.rules.pauseSimulationOnHint) {
            effects += TutorialEffect.ResumeSimulation
        }
        if (current.pending.definition.rules.suspendAiWhileActive) effects += TutorialEffect.ResumeAi
        effects += TutorialEffect.UnlockTargets(current.pending.definition.introduces)
        events += TutorialEvent.Interrupted(current.pending.definition.tutorialId)
        active = null
    }

    private fun interruptActiveMatchIfDifferent(
        context: TutorialContext,
        effects: MutableList<TutorialEffect>,
        events: MutableList<TutorialEvent>,
    ) {
        val current = active ?: return
        if (current.pending.definition.scope != TutorialScope.MATCH) return
        val previousContext = current.pending.context
        if (
            previousContext.matchId != context.matchId ||
            previousContext.matchGeneration != context.matchGeneration
        ) {
            interruptActiveMatch(effects, events)
        }
    }

    private fun currentStep(): TutorialStep =
        checkNotNull(active) { "tutorial step requested without an active tutorial" }
            .let { it.pending.definition.steps[it.stepIndex] }

    private fun currentUiState(): TutorialUiState? {
        val current = active ?: return null
        val definition = current.pending.definition
        val step = currentStep()
        val targetInstanceId = step.targetId
            ?.let { targetId -> catalog.targets.firstOrNull { it.targetId == targetId } }
            ?.takeIf { it.kind == TutorialTargetKind.BATTLEFIELD }
            ?.let { current.pending.targetInstanceId }
        val touchAllowed = if (
            step.completion is TutorialStepCompletion.PlayerAction &&
            step.targetId != null
        ) {
            setOf(step.targetId)
        } else {
            emptySet()
        }
        val lockedTargets = if (touchAllowed.isEmpty()) {
            catalog.targets.mapTo(mutableSetOf()) { it.targetId }
        } else {
            catalog.targets.mapNotNullTo(mutableSetOf()) { target ->
                target.targetId.takeUnless { it in touchAllowed || it in step.unlocksTargets }
            }
        }
        return TutorialUiState(
            tutorialId = definition.tutorialId,
            stepId = step.stepId,
            text = step.text.getValue(current.pending.context.language.code),
            targetId = step.targetId,
            targetInstanceId = targetInstanceId,
            placementPreference = step.placementPreference,
            stepIndex = current.stepIndex + 1,
            stepCount = definition.steps.size,
            actionLabel = if (
                definition.trigger is TutorialTrigger.ScreenEntered &&
                definition.trigger.screen == TutorialScreen.LOADOUT &&
                current.stepIndex == definition.steps.lastIndex
            ) {
                TutorialActionLabel.START_BATTLE
            } else {
                TutorialActionLabel.NEXT
            },
            skipAvailable = true,
            supplementalContent = step.supplementalContent,
            touchAllowedTargetIds = touchAllowed,
            lockedTargetIds = lockedTargets,
            lockedMechanicIds = definition.introduces,
        )
    }

    private fun currentUpdate(): TutorialEngineUpdate =
        TutorialEngineUpdate(uiState = currentUiState())
}
