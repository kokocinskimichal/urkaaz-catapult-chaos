package com.urkaaaz.application.campaign

import com.urkaaaz.application.progression.LifeOperationResult
import com.urkaaaz.application.progression.LifeSystem
import com.urkaaaz.application.progression.LifeSystemResult
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchOutcome

sealed interface CampaignMatchCompletionResult {
    data class Campaign(val result: CampaignCompletionResult) : CampaignMatchCompletionResult
    data class LifeSettlement(
        val result: LifeSystemResult<LifeOperationResult>,
    ) : CampaignMatchCompletionResult
}

class CampaignMatchCompletionUseCase(
    private val campaignSystem: CampaignSystem,
    private val lifeSystem: LifeSystem,
) {
    suspend fun complete(
        level: Int,
        event: MatchEvent.MatchFinished,
        reward: LevelRewardResult?,
    ): CampaignMatchCompletionResult {
        val campaignResult = campaignSystem.completeLevel(level, event, reward)
        if (event.outcome == MatchOutcome.BLUE_WIN) {
            return CampaignMatchCompletionResult.Campaign(campaignResult)
        }
        if (campaignResult !is CampaignCompletionResult.NoProgress) {
            return CampaignMatchCompletionResult.Campaign(campaignResult)
        }
        return CampaignMatchCompletionResult.LifeSettlement(
            lifeSystem.settleCampaignResult(event.matchId, event.outcome),
        )
    }
}
