package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignLevelSelection
import com.urkaaaz.application.campaign.CampaignProgressRead
import com.urkaaaz.application.campaign.CampaignSystem

class CampaignViewModel(
    private val campaignSystem: CampaignSystem,
    private val chapterProgressMapper: CampaignChapterProgressMapper =
        CampaignChapterProgressMapper(),
) {
    var state: CampaignUiState = CampaignUiState(isLoading = true)
        private set

    suspend fun load(): CampaignUiState {
        state = CampaignUiState(isLoading = true)
        state = when (val result = campaignSystem.progress()) {
            is CampaignProgressRead.Available -> {
                val mapProgression = chapterProgressMapper.map(result.progress)
                CampaignUiState(
                    isLoading = false,
                    progress = result.progress,
                    mapProgression = mapProgression,
                    selectedChapterId = mapProgression.selectedChapterId,
                    selectedLevel = availableLevel(
                        mapProgression.pages.first {
                            it.chapter.id == mapProgression.selectedChapterId
                        },
                    ),
                )
            }
            is CampaignProgressRead.Failure ->
                CampaignUiState(isLoading = false, failure = result.reason)
        }
        return state
    }

    fun selectChapter(chapterId: String): Boolean {
        val page = state.mapProgression?.pages?.firstOrNull {
            it.chapter.id == chapterId
        } ?: return false
        state = state.copy(
            selectedChapterId = page.chapter.id,
            selectedLevel = availableLevel(page),
            selectionRejection = null,
        )
        return true
    }

    fun selectMapLevel(level: Int): Boolean {
        val page = state.mapProgression?.pages?.firstOrNull {
            it.chapter.id == state.selectedChapterId
        } ?: return false
        val marker = page.markers.firstOrNull { it.level == level } ?: return false
        if (marker.status != CampaignLevelMarkerStatus.AVAILABLE) return false
        state = state.copy(
            selectedLevel = level,
            selectionRejection = null,
        )
        return true
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

    private fun availableLevel(page: CampaignChapterPageState): Int? =
        page.markers.firstOrNull {
            it.status == CampaignLevelMarkerStatus.AVAILABLE
        }?.level
}
