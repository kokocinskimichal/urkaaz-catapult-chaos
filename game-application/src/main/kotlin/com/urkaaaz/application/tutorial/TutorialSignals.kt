package com.urkaaaz.application.tutorial

import com.urkaaaz.application.language.AppLanguage

data class TutorialContext(
    val isDemo: Boolean,
    val language: AppLanguage,
    val matchId: String? = null,
    val matchGeneration: Long = 0L,
)

data class TutorialScenarioFacts(
    val campaignLevel: Int?,
    val availableMechanicIds: Set<String>,
    val availableUnits: Set<String>,
    val availableAmmunition: Set<String>,
    val availableSpells: Set<String>,
    val bossPresent: Boolean,
)

sealed interface TutorialSignal {
    val context: TutorialContext

    data class ScreenEntered(
        val screen: TutorialScreen,
        override val context: TutorialContext,
    ) : TutorialSignal

    data class MatchStarted(
        val scenarioFacts: TutorialScenarioFacts,
        override val context: TutorialContext,
    ) : TutorialSignal

    data class MatchEventObserved(
        val eventKind: String,
        val targetInstanceId: String? = null,
        override val context: TutorialContext,
    ) : TutorialSignal

    data class CampaignMilestoneApplied(
        val milestoneId: String,
        override val context: TutorialContext,
    ) : TutorialSignal

    data class UserAction(
        val action: TutorialUserAction,
        override val context: TutorialContext,
    ) : TutorialSignal

    data class MatchLifecycleEnded(
        override val context: TutorialContext,
    ) : TutorialSignal
}

sealed interface TutorialUserAction {
    data object Next : TutorialUserAction
    data object Skip : TutorialUserAction
    data class PlayerActionPerformed(val kind: TutorialPlayerActionKind) : TutorialUserAction
}

data class TutorialUiState(
    val tutorialId: String,
    val stepId: String,
    val text: String,
    val targetId: String?,
    val targetInstanceId: String?,
    val placementPreference: TutorialPlacementPreference,
    val stepIndex: Int,
    val stepCount: Int,
    val actionLabel: TutorialActionLabel,
    val skipAvailable: Boolean,
    val supplementalContent: TutorialSupplementalContent?,
    val touchAllowedTargetIds: Set<String>,
    val lockedTargetIds: Set<String>,
    val lockedMechanicIds: Set<String>,
)

enum class TutorialActionLabel {
    NEXT,
    START_BATTLE,
}

sealed interface TutorialEffect {
    data object PauseSimulation : TutorialEffect
    data object ResumeSimulation : TutorialEffect
    data object SuspendAi : TutorialEffect
    data object ResumeAi : TutorialEffect
    data class RestrictAiToIntroduced(val mechanicIds: Set<String>) : TutorialEffect
    data class LockTargets(val identifiers: Set<String>) : TutorialEffect
    data class UnlockTargets(val identifiers: Set<String>) : TutorialEffect
    data class FocusTarget(
        val targetId: String,
        val targetInstanceId: String?,
    ) : TutorialEffect
}

sealed interface TutorialEvent {
    data class Started(val tutorialId: String) : TutorialEvent
    data class StepCompleted(val tutorialId: String, val stepId: String) : TutorialEvent
    data class Skipped(val tutorialId: String) : TutorialEvent
    data class Completed(val tutorialId: String) : TutorialEvent
    data class Interrupted(val tutorialId: String) : TutorialEvent
    data class Rejected(val reason: TutorialRejection) : TutorialEvent
}

enum class TutorialRejection {
    NO_ACTIVE_TUTORIAL,
    ACTION_NOT_ALLOWED,
    TARGET_NOT_REGISTERED,
    TARGET_NOT_VISIBLE,
    HINT_PLACEMENT_IMPOSSIBLE,
}

data class TutorialEngineUpdate(
    val uiState: TutorialUiState?,
    val effects: List<TutorialEffect> = emptyList(),
    val events: List<TutorialEvent> = emptyList(),
    val terminalStatus: Pair<String, TutorialStatus>? = null,
)

interface MatchTutorialPort {
    fun pauseSimulation()
    fun resumeSimulation()
    fun suspendAi()
    fun resumeAi()
    fun restrictAiToIntroducedMechanics(mechanicIds: Set<String>)
    fun lockTargets(identifiers: Set<String>)
    fun unlockTargets(identifiers: Set<String>)
    fun focusTarget(targetId: String, targetInstanceId: String?)
}
