package com.urkaaaz.screens.campaign

import com.urkaaaz.campaign.CampaignLevelDefinition

data class CampaignUiState(
    val highestUnlockedLevel: Int = CampaignLevelDefinition.MAX_LEVELS,
)
