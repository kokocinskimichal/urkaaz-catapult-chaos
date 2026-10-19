package com.urkaaaz.application.campaign

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignLevelRegistry
import com.urkaaaz.campaign.CampaignMilestoneRegistry
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchOutcome

sealed interface CampaignProgressRead {
    data class Available(val progress: CampaignProgress) : CampaignProgressRead
    data class Failure(val reason: CampaignProgressFailure) : CampaignProgressRead
}

enum class CampaignSelectionRejection {
    INVALID_LEVEL,
    LEVEL_LOCKED,
}

sealed interface CampaignLevelSelection {
    data class Selected(val definition: CampaignLevelDefinition) : CampaignLevelSelection
    data class Rejected(val reason: CampaignSelectionRejection) : CampaignLevelSelection
    data class Failure(val reason: CampaignProgressFailure) : CampaignLevelSelection
}

sealed interface CampaignCompletionResult {
    data class Completed(val progress: CampaignProgress) : CampaignCompletionResult
    data class NoProgress(
        val progress: CampaignProgress,
        val outcome: MatchOutcome,
    ) : CampaignCompletionResult
    data class Rejected(val reason: CampaignSelectionRejection) : CampaignCompletionResult
    data class Failure(val reason: CampaignProgressFailure) : CampaignCompletionResult
}

data class CampaignLoadoutRewards(
    val unlockedSpellTypes: Set<String>,
    val defaultSpellTypes: Set<String>,
)

sealed interface CampaignLoadoutRewardsRead {
    data class Available(val rewards: CampaignLoadoutRewards) : CampaignLoadoutRewardsRead
    data class Rejected(val reason: CampaignSelectionRejection) : CampaignLoadoutRewardsRead
    data class Failure(val reason: CampaignProgressFailure) : CampaignLoadoutRewardsRead
}

class CampaignSystem(
    private val repository: CampaignProgressRepository,
    private val levels: CampaignLevelRegistry = CampaignLevelRegistry.default(),
    private val milestones: CampaignMilestoneRegistry = CampaignMilestoneRegistry.default(),
) {
    suspend fun progress(): CampaignProgressRead =
        when (val result = repository.loadOrInitialize()) {
            is CampaignProgressRepositoryResult.Available ->
                if (result.progress.isValid()) {
                    CampaignProgressRead.Available(result.progress)
                } else {
                    CampaignProgressRead.Failure(CampaignProgressFailure.INVALID_DATA)
                }
            is CampaignProgressRepositoryResult.Failure ->
                CampaignProgressRead.Failure(result.reason)
        }

    suspend fun selectLevel(level: Int): CampaignLevelSelection {
        if (!isKnownLevel(level)) {
            return CampaignLevelSelection.Rejected(CampaignSelectionRejection.INVALID_LEVEL)
        }
        val progress = when (val read = progress()) {
            is CampaignProgressRead.Available -> read.progress
            is CampaignProgressRead.Failure -> return CampaignLevelSelection.Failure(read.reason)
        }
        if (level > progress.highestUnlockedLevel) {
            return CampaignLevelSelection.Rejected(CampaignSelectionRejection.LEVEL_LOCKED)
        }
        return CampaignLevelSelection.Selected(levels.level(level))
    }

    suspend fun completeLevel(
        level: Int,
        event: MatchEvent.MatchFinished,
    ): CampaignCompletionResult {
        if (!isKnownLevel(level)) {
            return CampaignCompletionResult.Rejected(CampaignSelectionRejection.INVALID_LEVEL)
        }
        if (event.outcome != MatchOutcome.BLUE_WIN) {
            val progress = when (val read = progress()) {
                is CampaignProgressRead.Available -> read.progress
                is CampaignProgressRead.Failure -> return CampaignCompletionResult.Failure(read.reason)
            }
            if (level > progress.highestUnlockedLevel) {
                return CampaignCompletionResult.Rejected(CampaignSelectionRejection.LEVEL_LOCKED)
            }
            return CampaignCompletionResult.NoProgress(progress, event.outcome)
        }

        val update = repository.update { current ->
            if (!current.isValid() || level > current.highestUnlockedLevel) {
                current
            } else {
                val newlyAppliedMilestones = milestones.milestonesForCompletedLevel(level)
                    .asSequence()
                    .map { it.id }
                    .filterNot { it in current.appliedMilestoneIds }
                    .toSet()
                current.copy(
                    highestUnlockedLevel = maxOf(
                        current.highestUnlockedLevel,
                        (level + 1).coerceAtMost(CampaignLevelDefinition.MAX_LEVELS),
                    ),
                    completedLevels = current.completedLevels + level,
                    appliedMilestoneIds = current.appliedMilestoneIds + newlyAppliedMilestones,
                )
            }
        }
        return when (update) {
            is CampaignProgressRepositoryResult.Failure ->
                CampaignCompletionResult.Failure(update.reason)
            is CampaignProgressRepositoryResult.Available -> {
                if (!update.progress.isValid()) {
                    CampaignCompletionResult.Failure(CampaignProgressFailure.INVALID_DATA)
                } else if (level > update.progress.highestUnlockedLevel) {
                    CampaignCompletionResult.Rejected(CampaignSelectionRejection.LEVEL_LOCKED)
                } else {
                    CampaignCompletionResult.Completed(update.progress)
                }
            }
        }
    }

    suspend fun loadoutRewards(level: Int): CampaignLoadoutRewardsRead {
        if (!isKnownLevel(level)) {
            return CampaignLoadoutRewardsRead.Rejected(CampaignSelectionRejection.INVALID_LEVEL)
        }
        val progress = when (val read = progress()) {
            is CampaignProgressRead.Available -> read.progress
            is CampaignProgressRead.Failure -> return CampaignLoadoutRewardsRead.Failure(read.reason)
        }
        if (level > progress.highestUnlockedLevel) {
            return CampaignLoadoutRewardsRead.Rejected(CampaignSelectionRejection.LEVEL_LOCKED)
        }
        val granted = progress.appliedMilestoneIds.map { milestoneId ->
            milestones.milestone(milestoneId)
                ?: return CampaignLoadoutRewardsRead.Failure(CampaignProgressFailure.INVALID_DATA)
        }
        return CampaignLoadoutRewardsRead.Available(
            CampaignLoadoutRewards(
                unlockedSpellTypes = granted.mapTo(mutableSetOf()) { it.spellType.name },
                defaultSpellTypes = granted.asSequence()
                    .filter { it.preferInNewDefaultLoadout && it.availableFromLevel <= level }
                    .mapTo(mutableSetOf()) { it.spellType.name },
            ),
        )
    }

    private fun isKnownLevel(level: Int): Boolean =
        level in 1..CampaignLevelDefinition.MAX_LEVELS && levels.contains(level)

    private fun CampaignProgress.isValid(): Boolean =
        levels.contains(highestUnlockedLevel) &&
            completedLevels.all { completedLevel ->
                levels.contains(completedLevel) &&
                    (completedLevel < highestUnlockedLevel ||
                        highestUnlockedLevel == CampaignLevelDefinition.MAX_LEVELS)
            } &&
            appliedMilestoneIds.all { milestoneId ->
                milestones.milestone(milestoneId)
                    ?.let { it.completedLevel in completedLevels } == true
            }
}
