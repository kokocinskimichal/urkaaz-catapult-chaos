package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignLevelStartFailure
import com.urkaaaz.application.campaign.CampaignLevelStartRejection
import com.urkaaaz.application.progression.LifeSnapshot
import com.urkaaaz.application.progression.LifeSystemFailure

data class CampaignUiState(
    val isLoading: Boolean = false,
    val progress: CampaignProgress? = null,
    val failure: CampaignProgressFailure? = null,
    val selectionRejection: CampaignLevelStartRejection? = null,
    val startFailure: CampaignLevelStartFailure? = null,
    val lifeSnapshot: LifeSnapshot? = null,
    val lifeFailure: LifeSystemFailure? = null,
    val mapProgression: CampaignMapProgressionState? = null,
    val selectedChapterId: String? = null,
    val selectedLevel: Int? = null,
)
