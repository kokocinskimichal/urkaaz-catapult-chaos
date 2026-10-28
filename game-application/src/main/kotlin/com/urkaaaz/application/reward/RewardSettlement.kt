package com.urkaaaz.application.reward

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchOutcome

data class RewardSettlementState(
    val campaign: CampaignProgress,
    val profile: LoadoutProgressionSnapshot,
    val receipts: Map<String, LevelRewardResult>,
)

sealed interface RewardSettlementOutcome {
    data class Settled(
        val state: RewardSettlementState,
        val reward: LevelRewardResult,
    ) : RewardSettlementOutcome

    data class AlreadySettled(
        val state: RewardSettlementState,
        val reward: LevelRewardResult,
    ) : RewardSettlementOutcome

    data class Rejected(val reason: RewardSettlementRejection) : RewardSettlementOutcome
}

enum class RewardSettlementRejection {
    INVALID_LEVEL,
    LEVEL_LOCKED,
    LEVEL_ALREADY_COMPLETED,
    NOT_A_WIN,
    RESULT_MISMATCH,
    INVALID_PROGRESS,
    GOLD_OVERFLOW,
}

enum class RewardSettlementFailure {
    STORAGE_UNAVAILABLE,
    INVALID_DATA,
    UNSUPPORTED_VERSION,
}

data class RewardSettlementTransaction(
    val state: RewardSettlementState,
    val outcome: RewardSettlementOutcome,
    val shouldPersist: Boolean,
)

sealed interface RewardSettlementRepositoryResult {
    data class Completed(val outcome: RewardSettlementOutcome) : RewardSettlementRepositoryResult
    data class Failure(val reason: RewardSettlementFailure) : RewardSettlementRepositoryResult
}

interface RewardSettlementPort {
    suspend fun update(
        transform: (RewardSettlementState) -> RewardSettlementTransaction,
    ): RewardSettlementRepositoryResult
}

class RewardSettlementUseCase(
    private val port: RewardSettlementPort,
    private val rewards: CampaignRewardRegistry = CampaignRewardRegistry.default(),
) {
    suspend fun settle(
        level: Int,
        event: MatchEvent.MatchFinished,
        reward: LevelRewardResult?,
    ): RewardSettlementRepositoryResult {
        if (level !in 1..CampaignLevelDefinition.MAX_LEVELS) {
            return rejected(RewardSettlementRejection.INVALID_LEVEL)
        }
        if (event.outcome != MatchOutcome.BLUE_WIN) {
            return rejected(RewardSettlementRejection.NOT_A_WIN)
        }
        if (reward == null ||
            reward.level != level ||
            reward.matchId != event.matchId.value ||
            reward.totalGold < 0
        ) {
            return rejected(RewardSettlementRejection.RESULT_MISMATCH)
        }

        return port.update { current ->
            if (!current.isValid()) {
                return@update rejectedTransaction(
                    current,
                    RewardSettlementRejection.INVALID_PROGRESS,
                )
            }
            val receiptKey = "$level|${event.matchId.value}"
            val priorReceipt = current.receipts[receiptKey]
            if (priorReceipt != null) {
                return@update RewardSettlementTransaction(
                    state = current,
                    outcome = RewardSettlementOutcome.AlreadySettled(current, priorReceipt),
                    shouldPersist = false,
                )
            }
            if (level > current.campaign.highestUnlockedLevel) {
                return@update rejectedTransaction(
                    current,
                    RewardSettlementRejection.LEVEL_LOCKED,
                )
            }
            if (level in current.campaign.completedLevels) {
                return@update rejectedTransaction(
                    current,
                    RewardSettlementRejection.LEVEL_ALREADY_COMPLETED,
                )
            }
            if (
                reward.totalGold >
                Int.MAX_VALUE.toLong() - current.profile.goldBalance.toLong()
            ) {
                return@update rejectedTransaction(
                    current,
                    RewardSettlementRejection.GOLD_OVERFLOW,
                )
            }
            val updatedGold = current.profile.goldBalance.toLong() + reward.totalGold
            val newlyAppliedMilestones = rewards.milestones.milestonesForCompletedLevel(level)
                .asSequence()
                .map { it.id }
                .filterNot { it in current.campaign.appliedMilestoneIds }
                .toSet()
            val nextState = current.copy(
                campaign = current.campaign.copy(
                    highestUnlockedLevel = maxOf(
                        current.campaign.highestUnlockedLevel,
                        (level + 1).coerceAtMost(CampaignLevelDefinition.MAX_LEVELS),
                    ),
                    completedLevels = current.campaign.completedLevels + level,
                    appliedMilestoneIds = current.campaign.appliedMilestoneIds +
                        newlyAppliedMilestones,
                ),
                profile = current.profile.copy(goldBalance = updatedGold.toInt()),
                receipts = current.receipts + (receiptKey to reward),
            )
            RewardSettlementTransaction(
                state = nextState,
                outcome = RewardSettlementOutcome.Settled(nextState, reward),
                shouldPersist = true,
            )
        }
    }

    private fun rejected(reason: RewardSettlementRejection) =
        RewardSettlementRepositoryResult.Completed(RewardSettlementOutcome.Rejected(reason))

    private fun rejectedTransaction(
        state: RewardSettlementState,
        reason: RewardSettlementRejection,
    ) = RewardSettlementTransaction(
        state = state,
        outcome = RewardSettlementOutcome.Rejected(reason),
        shouldPersist = false,
    )

    private fun RewardSettlementState.isValid(): Boolean {
        val highest = campaign.highestUnlockedLevel
        val campaignValid =
            highest in 1..CampaignLevelDefinition.MAX_LEVELS &&
                campaign.completedLevels.all { completed ->
                    completed in 1..CampaignLevelDefinition.MAX_LEVELS &&
                        (
                            completed < highest ||
                                highest == CampaignLevelDefinition.MAX_LEVELS
                            )
                } &&
                campaign.appliedMilestoneIds.all { id ->
                    rewards.milestones.milestone(id)
                        ?.let { it.completedLevel in campaign.completedLevels } == true
                }
        val receiptsValid = receipts.all { (key, result) ->
            key == "${result.level}|${result.matchId}" &&
                result.level in 1..CampaignLevelDefinition.MAX_LEVELS
        }
        return campaignValid &&
            PlayerProgressionUseCases.isValidProfile(profile) &&
            receiptsValid
    }
}
