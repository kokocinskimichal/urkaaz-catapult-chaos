package com.urkaaaz.application.campaign

import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignLevelRegistry
import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.application.reward.RewardSettlementOutcome
import com.urkaaaz.application.reward.RewardSettlementRejection
import com.urkaaaz.application.reward.RewardSettlementRepositoryResult
import com.urkaaaz.application.reward.RewardSettlementUseCase
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchOutcome

sealed interface CampaignProgressRead {
    data class Available(val progress: CampaignProgress) : CampaignProgressRead
    data class Failure(val reason: CampaignProgressFailure) : CampaignProgressRead
}

enum class CampaignSelectionRejection {
    INVALID_LEVEL,
    LEVEL_LOCKED,
    LEVEL_ALREADY_COMPLETED,
}

sealed interface CampaignLevelSelection {
    data class Selected(val definition: CampaignLevelDefinition) : CampaignLevelSelection
    data class Rejected(val reason: CampaignSelectionRejection) : CampaignLevelSelection
    data class Failure(val reason: CampaignProgressFailure) : CampaignLevelSelection
}

sealed interface CampaignCompletionResult {
    data class Completed(
        val progress: CampaignProgress,
        val reward: LevelRewardResult? = null,
    ) : CampaignCompletionResult
    data class NoProgress(
        val progress: CampaignProgress,
        val outcome: MatchOutcome,
    ) : CampaignCompletionResult
    data class Rejected(val reason: CampaignSelectionRejection) : CampaignCompletionResult
    data class RewardRejected(
        val reason: RewardSettlementRejection,
    ) : CampaignCompletionResult
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
    private val rewards: CampaignRewardRegistry = CampaignRewardRegistry.default(),
    private val rewardSettlement: RewardSettlementUseCase? = null,
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
        if (level in progress.completedLevels) {
            return CampaignLevelSelection.Rejected(
                CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED,
            )
        }
        return CampaignLevelSelection.Selected(levels.level(level))
    }

    suspend fun completeLevel(
        level: Int,
        event: MatchEvent.MatchFinished,
        rewardResult: LevelRewardResult? = null,
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

        if (rewardSettlement != null) {
            return when (val settlement = rewardSettlement.settle(level, event, rewardResult)) {
                is RewardSettlementRepositoryResult.Failure ->
                    CampaignCompletionResult.Failure(settlement.reason.toCampaignFailure())
                is RewardSettlementRepositoryResult.Completed ->
                    when (val outcome = settlement.outcome) {
                        is RewardSettlementOutcome.Settled ->
                            CampaignCompletionResult.Completed(
                                outcome.state.campaign,
                                outcome.reward,
                            )
                        is RewardSettlementOutcome.AlreadySettled ->
                            CampaignCompletionResult.Completed(
                                outcome.state.campaign,
                                outcome.reward,
                            )
                        is RewardSettlementOutcome.Rejected ->
                            when (outcome.reason) {
                                RewardSettlementRejection.INVALID_LEVEL ->
                                    CampaignCompletionResult.Rejected(
                                        CampaignSelectionRejection.INVALID_LEVEL,
                                    )
                                RewardSettlementRejection.LEVEL_LOCKED ->
                                    CampaignCompletionResult.Rejected(
                                        CampaignSelectionRejection.LEVEL_LOCKED,
                                    )
                                RewardSettlementRejection.LEVEL_ALREADY_COMPLETED ->
                                    CampaignCompletionResult.Rejected(
                                        CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED,
                                    )
                                RewardSettlementRejection.GOLD_OVERFLOW,
                                RewardSettlementRejection.RESULT_MISMATCH,
                                RewardSettlementRejection.NOT_A_WIN,
                                -> CampaignCompletionResult.RewardRejected(outcome.reason)
                                RewardSettlementRejection.INVALID_PROGRESS ->
                                    CampaignCompletionResult.Failure(
                                        CampaignProgressFailure.INVALID_DATA,
                                    )
                            }
                    }
            }
        }

        var rejection: CampaignSelectionRejection? = null
        val update = repository.update { current ->
            if (
                !current.isValid()
            ) {
                current
            } else if (level > current.highestUnlockedLevel) {
                rejection = CampaignSelectionRejection.LEVEL_LOCKED
                current
            } else if (level in current.completedLevels) {
                rejection = CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED
                current
            } else {
                val newlyAppliedMilestones = rewards.milestones.milestonesForCompletedLevel(level)
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
                } else if (rejection != null) {
                    CampaignCompletionResult.Rejected(requireNotNull(rejection))
                } else if (level > update.progress.highestUnlockedLevel) {
                    CampaignCompletionResult.Rejected(CampaignSelectionRejection.LEVEL_LOCKED)
                } else if (level !in update.progress.completedLevels) {
                    CampaignCompletionResult.Rejected(
                        CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED,
                    )
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
            rewards.milestones.milestone(milestoneId)
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
                rewards.milestones.milestone(milestoneId)
                    ?.let { it.completedLevel in completedLevels } == true
            }

    private fun com.urkaaaz.application.reward.RewardSettlementFailure.toCampaignFailure() =
        when (this) {
            com.urkaaaz.application.reward.RewardSettlementFailure.STORAGE_UNAVAILABLE ->
                CampaignProgressFailure.STORAGE_UNAVAILABLE
            com.urkaaaz.application.reward.RewardSettlementFailure.INVALID_DATA ->
                CampaignProgressFailure.INVALID_DATA
            com.urkaaaz.application.reward.RewardSettlementFailure.UNSUPPORTED_VERSION ->
                CampaignProgressFailure.UNSUPPORTED_VERSION
        }
}
