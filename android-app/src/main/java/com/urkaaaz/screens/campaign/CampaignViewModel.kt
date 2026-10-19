package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignLevelSelection
import com.urkaaaz.application.campaign.CampaignProgressRead
import com.urkaaaz.application.campaign.CampaignSystem

class CampaignViewModel(
    private val campaignSystem: CampaignSystem,
) {
    var state: CampaignUiState = CampaignUiState(isLoading = true)
        private set

    suspend fun load(): CampaignUiState {
        state = CampaignUiState(isLoading = true)
        state = when (val result = campaignSystem.progress()) {
            is CampaignProgressRead.Available ->
                CampaignUiState(isLoading = false, progress = result.progress)
            is CampaignProgressRead.Failure ->
                CampaignUiState(isLoading = false, failure = result.reason)
        }
        return state
    }

    suspend fun dispatch(action: CampaignUiAction): CampaignLevelSelection {
        val result = when (action) {
            is CampaignUiAction.SelectLevel -> campaignSystem.selectLevel(action.level)
        }
        state = when (result) {
            is CampaignLevelSelection.Selected -> state.copy(selectionRejection = null)
            is CampaignLevelSelection.Rejected ->
                state.copy(selectionRejection = result.reason)
            is CampaignLevelSelection.Failure ->
                CampaignUiState(isLoading = false, failure = result.reason)
        }
        return result
    }
}
