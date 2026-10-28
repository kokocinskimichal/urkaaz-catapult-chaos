package com.urkaaaz.screens.match

import android.app.Activity
import android.widget.Toast
import com.urkaaaz.android.R
import com.urkaaaz.application.campaign.CampaignCompletionResult
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignSelectionRejection
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.application.reward.RewardSettlementRejection
import com.urkaaaz.contracts.MatchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class CampaignCompletionPresentation(
    val recorded: Boolean,
    val reward: LevelRewardResult?,
)

class CampaignCompletionPresenter(
    private val activity: Activity,
    private val campaignSystem: CampaignSystem,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun onMatchFinished(
        level: Int,
        event: MatchEvent.MatchFinished,
        reward: LevelRewardResult?,
        onCompletion: (CampaignCompletionPresentation) -> Unit,
    ) {
        scope.launch {
            val result = campaignSystem.completeLevel(level, event, reward)
            when (result) {
                is CampaignCompletionResult.Completed,
                is CampaignCompletionResult.NoProgress -> Unit
                is CampaignCompletionResult.Rejected ->
                    showMessage(
                        when (result.reason) {
                            CampaignSelectionRejection.INVALID_LEVEL ->
                                R.string.campaign_selection_invalid
                            CampaignSelectionRejection.LEVEL_LOCKED ->
                                R.string.campaign_completion_level_invalid
                            CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED ->
                                R.string.campaign_completion_already_completed
                        },
                    )
                is CampaignCompletionResult.RewardRejected ->
                    showMessage(
                        when (result.reason) {
                            RewardSettlementRejection.GOLD_OVERFLOW ->
                                R.string.campaign_reward_overflow
                            RewardSettlementRejection.INVALID_LEVEL,
                            RewardSettlementRejection.LEVEL_LOCKED,
                            RewardSettlementRejection.LEVEL_ALREADY_COMPLETED,
                            RewardSettlementRejection.INVALID_PROGRESS,
                            RewardSettlementRejection.NOT_A_WIN,
                            RewardSettlementRejection.RESULT_MISMATCH,
                            -> R.string.campaign_completion_invalid
                        },
                    )
                is CampaignCompletionResult.Failure ->
                    showMessage(
                        when (result.reason) {
                            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                                R.string.campaign_completion_unavailable
                            CampaignProgressFailure.INVALID_DATA ->
                                R.string.campaign_completion_invalid
                            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                                R.string.campaign_progress_version
                        },
                    )
            }
            onCompletion(
                when (result) {
                    is CampaignCompletionResult.Completed ->
                        CampaignCompletionPresentation(true, result.reward ?: reward)
                    is CampaignCompletionResult.NoProgress ->
                        CampaignCompletionPresentation(false, null)
                    else -> CampaignCompletionPresentation(false, null)
                },
            )
        }
    }

    fun onDestroy() {
        scope.cancel()
    }

    private fun showMessage(messageId: Int) {
        Toast.makeText(activity, activity.getString(messageId), Toast.LENGTH_LONG).show()
    }
}
