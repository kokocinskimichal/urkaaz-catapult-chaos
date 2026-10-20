package com.urkaaaz.screens.match

import android.app.Activity
import android.widget.Toast
import com.urkaaaz.android.R
import com.urkaaaz.application.campaign.CampaignCompletionResult
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignSelectionRejection
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.contracts.MatchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CampaignCompletionPresenter(
    private val activity: Activity,
    private val campaignSystem: CampaignSystem,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun onMatchFinished(
        level: Int,
        event: MatchEvent.MatchFinished,
        onCompletionRecorded: (Boolean) -> Unit,
    ) {
        scope.launch {
            val result = campaignSystem.completeLevel(level, event)
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
            onCompletionRecorded(result is CampaignCompletionResult.Completed)
        }
    }

    fun onDestroy() {
        scope.cancel()
    }

    private fun showMessage(messageId: Int) {
        Toast.makeText(activity, activity.getString(messageId), Toast.LENGTH_LONG).show()
    }
}
