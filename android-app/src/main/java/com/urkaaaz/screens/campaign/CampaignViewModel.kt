package com.urkaaaz.screens.campaign

import com.urkaaaz.campaign.CampaignLevelDefinition

class CampaignViewModel {
    var state: CampaignUiState = CampaignUiState()
        private set

    fun dispatch(action: CampaignUiAction): CampaignUiState {
        when (action) {
            is CampaignUiAction.SelectLevel -> {
                if (action.level in 1..state.highestUnlockedLevel) {
                    state = state.copy(
                        highestUnlockedLevel = action.level.coerceAtMost(
                            CampaignLevelDefinition.MAX_LEVELS,
                        ),
                    )
                }
            }
        }
        return state
    }
}
