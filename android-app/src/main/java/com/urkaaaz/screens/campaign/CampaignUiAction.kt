package com.urkaaaz.screens.campaign

sealed interface CampaignUiAction {
    data class SelectLevel(val level: Int) : CampaignUiAction
}

enum class CampaignChapterNavigationDirection {
    PREVIOUS,
    NEXT,
}
