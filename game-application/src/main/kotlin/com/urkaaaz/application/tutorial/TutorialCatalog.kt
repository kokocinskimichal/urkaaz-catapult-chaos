package com.urkaaaz.application.tutorial

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TutorialCatalog(
    val schemaVersion: Int,
    val revision: String,
    val targets: List<TutorialTargetDefinition>,
    val tutorials: List<TutorialDefinition>,
    val registeredMechanicIds: List<String> = emptyList(),
    val registeredEventKinds: List<String> = emptyList(),
    val registeredMilestoneIds: List<String> = emptyList(),
) {
    init {
        val issues = TutorialCatalogValidator.validate(
            schemaVersion = schemaVersion,
            revision = revision,
            targets = targets,
            tutorials = tutorials,
            registeredMechanicIds = registeredMechanicIds,
            registeredEventKinds = registeredEventKinds,
            registeredMilestoneIds = registeredMilestoneIds,
        )
        if (issues.isNotEmpty()) throw TutorialCatalogValidationException(issues)
    }

    fun tutorial(tutorialId: String): TutorialDefinition? =
        tutorials.firstOrNull { it.tutorialId == tutorialId }
}

@Serializable
data class TutorialTargetDefinition(
    val targetId: String,
    val screen: TutorialScreen,
    val kind: TutorialTargetKind,
)

@Serializable
data class TutorialDefinition(
    val tutorialId: String,
    val scope: TutorialScope,
    val enabled: Boolean = true,
    val trigger: TutorialTrigger,
    val requires: Set<String> = emptySet(),
    val priority: Int = 0,
    val introduces: Set<String> = emptySet(),
    val rules: TutorialRuleSet = TutorialRuleSet(),
    val steps: List<TutorialStep>,
)

@Serializable
sealed interface TutorialTrigger {
    @Serializable
    @SerialName("SCREEN_ENTERED")
    data class ScreenEntered(val screen: TutorialScreen) : TutorialTrigger

    @Serializable
    @SerialName("MATCH_STARTED")
    data class MatchStarted(
        val campaignLevelRange: TutorialLevelRange? = null,
        val requiresMechanicId: String? = null,
    ) : TutorialTrigger

    @Serializable
    @SerialName("MATCH_EVENT_OBSERVED")
    data class MatchEventObserved(val eventKind: String) : TutorialTrigger

    @Serializable
    @SerialName("CAMPAIGN_MILESTONE_APPLIED")
    data class CampaignMilestoneApplied(val milestoneId: String) : TutorialTrigger
}

@Serializable
data class TutorialLevelRange(
    val first: Int,
    val last: Int,
) {
    operator fun contains(level: Int): Boolean = level in first..last
}

@Serializable
data class TutorialStep(
    val stepId: String,
    val targetId: String? = null,
    val text: Map<String, String>,
    val placementPreference: TutorialPlacementPreference = TutorialPlacementPreference.AUTO,
    val completion: TutorialStepCompletion = TutorialStepCompletion.Next,
    val unlocksTargets: Set<String> = emptySet(),
    val supplementalContent: TutorialSupplementalContent? = null,
)

@Serializable
sealed interface TutorialStepCompletion {
    @Serializable
    @SerialName("NEXT")
    data object Next : TutorialStepCompletion

    @Serializable
    @SerialName("PLAYER_ACTION")
    data class PlayerAction(
        val kind: TutorialPlayerActionKind,
        val count: Int,
    ) : TutorialStepCompletion

    @Serializable
    @SerialName("MATCH_EVENT_COUNT")
    data class MatchEventCount(
        val eventKind: String,
        val count: Int,
    ) : TutorialStepCompletion
}

@Serializable
data class TutorialRuleSet(
    val pauseSimulationOnHint: Boolean = true,
    val suspendAiWhileActive: Boolean = true,
    val restrictAiToIntroducedMechanics: Boolean = false,
)

@Serializable
enum class TutorialScreen {
    DASHBOARD,
    CAMPAIGN,
    LOADOUT,
    MATCH,
    SHOP,
}

@Serializable
enum class TutorialTargetKind {
    VIEW,
    BATTLEFIELD,
}

@Serializable
enum class TutorialScope {
    SCREEN,
    MATCH,
}

@Serializable
enum class TutorialPlacementPreference {
    AUTO,
    ABOVE,
    BELOW,
    LEFT,
    RIGHT,
}

@Serializable
enum class TutorialPlayerActionKind {
    FIRE,
    DEPLOY_UNIT,
    SEND_WAVE,
    CAST_SPELL,
    SELECT_AMMUNITION,
}

@Serializable
enum class TutorialSupplementalContent {
    WIND_STRENGTH_LEGEND,
}

enum class TutorialStatus {
    COMPLETED,
    SKIPPED,
}

data class TutorialCatalogIssue(
    val path: String,
    val message: String,
)

class TutorialCatalogValidationException(
    val issues: List<TutorialCatalogIssue>,
) : IllegalArgumentException(
    issues.joinToString(separator = "\n") { "${it.path}: ${it.message}" },
)

object TutorialCatalogValidator {
    private val stableId = Regex("[a-z][a-z0-9._-]*")
    private val supportedLanguages = setOf("pl", "en", "es")

    fun validate(
        schemaVersion: Int,
        revision: String,
        targets: List<TutorialTargetDefinition>,
        tutorials: List<TutorialDefinition>,
        registeredMechanicIds: List<String>,
        registeredEventKinds: List<String>,
        registeredMilestoneIds: List<String>,
    ): List<TutorialCatalogIssue> {
        val issues = mutableListOf<TutorialCatalogIssue>()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            issues += TutorialCatalogIssue("schemaVersion", "must be $SUPPORTED_SCHEMA_VERSION")
        }
        if (revision.isBlank()) {
            issues += TutorialCatalogIssue("revision", "must not be blank")
        }
        targets.forEachIndexed { index, target ->
            val path = "targets[$index].targetId"
            if (!stableId.matches(target.targetId)) {
                issues += TutorialCatalogIssue(path, "must be a stable lowercase identifier")
            }
        }
        duplicateIssues(targets.map { it.targetId }, "targets", issues)
        validateRegisteredIds(registeredMechanicIds, "registeredMechanicIds", issues)
        validateRegisteredIds(registeredEventKinds, "registeredEventKinds", issues)
        validateRegisteredIds(registeredMilestoneIds, "registeredMilestoneIds", issues)
        val registeredMechanicIdSet = registeredMechanicIds.toSet()
        val registeredEventKindSet = registeredEventKinds.toSet()

        val targetIds = targets.mapTo(mutableSetOf()) { it.targetId }
        tutorials.forEachIndexed { index, tutorial ->
            val path = "tutorials[$index]"
            if (!stableId.matches(tutorial.tutorialId)) {
                issues += TutorialCatalogIssue(
                    "$path.tutorialId",
                    "must be a stable lowercase identifier",
                )
            }
            if (tutorial.steps.isEmpty()) {
                issues += TutorialCatalogIssue("$path.steps", "must contain at least one step")
            }
            tutorial.requires.forEach { requiredId ->
                if (!stableId.matches(requiredId)) {
                    issues += TutorialCatalogIssue(
                        "$path.requires",
                        "contains invalid tutorial ID '$requiredId'",
                    )
                }
            }
            tutorial.introduces.forEach { mechanicId ->
                if (!stableId.matches(mechanicId)) {
                    issues += TutorialCatalogIssue(
                        "$path.introduces",
                        "contains invalid mechanic ID '$mechanicId'",
                    )
                } else if (mechanicId !in registeredMechanicIdSet) {
                    issues += TutorialCatalogIssue(
                        "$path.introduces",
                        "references unregistered mechanic '$mechanicId'",
                    )
                }
            }
            validateTrigger(
                trigger = tutorial.trigger,
                path = "$path.trigger",
                issues = issues,
                registeredMechanicIds = registeredMechanicIdSet,
                registeredEventKinds = registeredEventKindSet,
                registeredMilestoneIds = registeredMilestoneIds.toSet(),
            )
            tutorial.steps.forEachIndexed { stepIndex, step ->
                val stepPath = "$path.steps[$stepIndex]"
                if (!stableId.matches(step.stepId)) {
                    issues += TutorialCatalogIssue(
                        "$stepPath.stepId",
                        "must be a stable lowercase identifier",
                    )
                }
                if (step.targetId != null && step.targetId !in targetIds) {
                    issues += TutorialCatalogIssue(
                        "$stepPath.targetId",
                        "references unknown target '${step.targetId}'",
                    )
                }
                if (step.text.keys != supportedLanguages) {
                    issues += TutorialCatalogIssue(
                        "$stepPath.text",
                        "must contain exactly pl, en, and es",
                    )
                }
                step.text.forEach { (language, value) ->
                    if (value.isBlank()) {
                        issues += TutorialCatalogIssue(
                            "$stepPath.text.$language",
                            "must not be blank",
                        )
                    }
                }
                val actionCount = when (val completion = step.completion) {
                    is TutorialStepCompletion.PlayerAction -> completion.count
                    is TutorialStepCompletion.MatchEventCount -> completion.count
                    TutorialStepCompletion.Next -> null
                }
                if (actionCount != null && actionCount < 1) {
                    issues += TutorialCatalogIssue(
                        "$stepPath.completion.count",
                        "must be at least 1",
                    )
                }
                if (
                    step.completion is TutorialStepCompletion.MatchEventCount &&
                    step.completion.eventKind !in registeredEventKinds
                ) {
                    issues += TutorialCatalogIssue(
                        "$stepPath.completion.eventKind",
                        "references unregistered event '${step.completion.eventKind}'",
                    )
                }
                step.unlocksTargets.forEach { targetId ->
                    if (targetId !in targetIds) {
                        issues += TutorialCatalogIssue(
                            "$stepPath.unlocksTargets",
                            "references unknown target '$targetId'",
                        )
                    }
                }
            }
            duplicateIssues(
                tutorial.steps.map { it.stepId },
                "$path.steps",
                issues,
            )
        }
        duplicateIssues(tutorials.map { it.tutorialId }, "tutorials", issues)
        validateRequirements(tutorials, issues)
        return issues
    }

    private fun validateTrigger(
        trigger: TutorialTrigger,
        path: String,
        issues: MutableList<TutorialCatalogIssue>,
        registeredMechanicIds: Set<String>,
        registeredEventKinds: Set<String>,
        registeredMilestoneIds: Set<String>,
    ) {
        when (trigger) {
            is TutorialTrigger.ScreenEntered -> Unit
            is TutorialTrigger.MatchStarted -> {
                val range = trigger.campaignLevelRange
                if (range != null && (range.first < 1 || range.last < range.first)) {
                    issues += TutorialCatalogIssue(
                        "$path.campaignLevelRange",
                        "must be a positive ordered range",
                    )
                }
                val mechanicId = trigger.requiresMechanicId
                if (mechanicId != null && !stableId.matches(mechanicId)) {
                    issues += TutorialCatalogIssue(
                        "$path.requiresMechanicId",
                        "must be a stable lowercase identifier",
                    )
                } else if (mechanicId != null && mechanicId !in registeredMechanicIds) {
                    issues += TutorialCatalogIssue(
                        "$path.requiresMechanicId",
                        "references unregistered mechanic '$mechanicId'",
                    )
                }
            }
            is TutorialTrigger.MatchEventObserved ->
                if (trigger.eventKind !in registeredEventKinds) {
                    issues += TutorialCatalogIssue(
                        "$path.eventKind",
                        "references unregistered event '${trigger.eventKind}'",
                    )
                }
            is TutorialTrigger.CampaignMilestoneApplied ->
                if (trigger.milestoneId !in registeredMilestoneIds) {
                    issues += TutorialCatalogIssue(
                        "$path.milestoneId",
                        "references unregistered milestone '${trigger.milestoneId}'",
                    )
                }
        }
    }

    private fun validateRegisteredIds(
        values: List<String>,
        path: String,
        issues: MutableList<TutorialCatalogIssue>,
    ) {
        values.filterNot(stableId::matches).forEach { invalid ->
            issues += TutorialCatalogIssue(path, "contains invalid identifier '$invalid'")
        }
        duplicateIssues(values, path, issues)
    }

    private fun validateRequirements(
        tutorials: List<TutorialDefinition>,
        issues: MutableList<TutorialCatalogIssue>,
    ) {
        val definitions = tutorials.associateBy { it.tutorialId }
        tutorials.forEach { tutorial ->
            tutorial.requires.forEach { requiredId ->
                if (requiredId !in definitions) {
                    issues += TutorialCatalogIssue(
                        "tutorials.${tutorial.tutorialId}.requires",
                        "references unknown tutorial '$requiredId'",
                    )
                }
            }
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(tutorialId: String, path: List<String>) {
            if (tutorialId in visiting) {
                issues += TutorialCatalogIssue(
                    "tutorials.$tutorialId.requires",
                    "contains a dependency cycle: ${(path + tutorialId).joinToString(" -> ")}",
                )
                return
            }
            if (!visited.add(tutorialId)) return
            visiting += tutorialId
            definitions[tutorialId]?.requires.orEmpty().forEach { requiredId ->
                if (requiredId in definitions) visit(requiredId, path + tutorialId)
            }
            visiting -= tutorialId
        }
        definitions.keys.forEach { visit(it, emptyList()) }
    }

    private fun duplicateIssues(
        values: List<String>,
        path: String,
        issues: MutableList<TutorialCatalogIssue>,
    ) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { duplicate ->
            issues += TutorialCatalogIssue(path, "contains duplicate ID '$duplicate'")
        }
    }

    const val SUPPORTED_SCHEMA_VERSION = 1
}
