package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignSelectionRejection

data class CampaignUiState(
    val isLoading: Boolean = false,
    val progress: CampaignProgress? = null,
    val failure: CampaignProgressFailure? = null,
    val selectionRejection: CampaignSelectionRejection? = null,
)
