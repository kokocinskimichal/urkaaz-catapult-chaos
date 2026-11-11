package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignLevelStartResult
import com.urkaaaz.application.campaign.CampaignLevelStartRejection
import com.urkaaaz.application.campaign.CampaignLevelStartUseCase
import com.urkaaaz.application.campaign.CampaignProgressRead
import com.urkaaaz.application.progression.LifeSystemResult

class CampaignViewModel(
    private val campaignLevelStart: CampaignLevelStartUseCase,
    private val chapterProgressMapper: CampaignChapterProgressMapper =
        CampaignChapterProgressMapper(),
) {
    var state: CampaignUiState = CampaignUiState(isLoading = true)
        private set

    suspend fun load(): CampaignUiState {
        state = CampaignUiState(isLoading = true)
        state = when (val result = campaignLevelStart.progress()) {
            is CampaignProgressRead.Available -> {
                val mapProgression = chapterProgressMapper.map(result.progress)
                val lifeRead = campaignLevelStart.lifeSnapshot()
                val lifeSnapshot = (lifeRead as? LifeSystemResult.Success)?.value
                val lifeFailure = when (lifeRead) {
                    is LifeSystemResult.Failure -> lifeRead.reason
                    is LifeSystemResult.Rejected ->
                        com.urkaaaz.application.progression.LifeSystemFailure.INVALID_PROFILE
                    is LifeSystemResult.Success -> null
                }
                CampaignUiState(
                    isLoading = false,
                    progress = result.progress,
                    lifeSnapshot = lifeSnapshot,
                    lifeFailure = lifeFailure,
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

    suspend fun refreshLifeSnapshot(): CampaignUiState {
        val lifeRead = campaignLevelStart.lifeSnapshot()
        state = when (lifeRead) {
            is LifeSystemResult.Success ->
                state.copy(lifeSnapshot = lifeRead.value, lifeFailure = null)
            is LifeSystemResult.Failure ->
                state.copy(lifeSnapshot = null, lifeFailure = lifeRead.reason)
            is LifeSystemResult.Rejected ->
                state.copy(
                    lifeSnapshot = null,
                    lifeFailure =
                        com.urkaaaz.application.progression.LifeSystemFailure.INVALID_PROFILE,
                )
        }
        return state
    }

    fun elapseLifeCountdown(milliseconds: Long): CampaignUiState {
        val life = state.lifeSnapshot ?: return state
        state = state.copy(
            lifeSnapshot = life.copy(
                regenerationTimeRemainingMilliseconds =
                    life.regenerationTimeRemainingMilliseconds?.minus(milliseconds)?.coerceAtLeast(0L),
                unlimitedTimeRemainingMilliseconds =
                    life.unlimitedTimeRemainingMilliseconds?.minus(milliseconds)?.coerceAtLeast(0L),
            ),
        )
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
            startFailure = null,
        )
        return true
    }

    fun navigateChapter(direction: CampaignChapterNavigationDirection): Boolean {
        val progression = state.mapProgression ?: return false
        val selectedIndex = progression.pages.indexOfFirst {
            it.chapter.id == state.selectedChapterId
        }
        if (selectedIndex < 0) return false
        val targetIndex = selectedIndex + when (direction) {
            CampaignChapterNavigationDirection.PREVIOUS -> -1
            CampaignChapterNavigationDirection.NEXT -> 1
        }
        val targetPage = progression.pages.getOrNull(targetIndex) ?: return false
        return selectChapter(targetPage.chapter.id)
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
            startFailure = null,
        )
        return true
    }

    suspend fun dispatch(action: CampaignUiAction): CampaignLevelStartResult {
        val result = when (action) {
            is CampaignUiAction.SelectLevel -> campaignLevelStart.authorize(action.level)
        }
        val refreshedLives = if (
            result == CampaignLevelStartResult.Rejected(CampaignLevelStartRejection.NO_LIVES)
        ) {
            campaignLevelStart.lifeSnapshot()
        } else {
            null
        }
        state = when (result) {
            is CampaignLevelStartResult.Allowed ->
                state.copy(selectionRejection = null, startFailure = null)
            is CampaignLevelStartResult.Rejected ->
                state.copy(
                    selectionRejection = result.reason,
                    startFailure = null,
                    lifeSnapshot = when (refreshedLives) {
                        is LifeSystemResult.Success -> refreshedLives.value
                        else -> state.lifeSnapshot
                    },
                    lifeFailure = when (refreshedLives) {
                        is LifeSystemResult.Failure -> refreshedLives.reason
                        is LifeSystemResult.Rejected ->
                            com.urkaaaz.application.progression.LifeSystemFailure.INVALID_PROFILE
                        else -> state.lifeFailure
                    },
                )
            is CampaignLevelStartResult.Failure ->
                state.copy(
                    startFailure = result.reason,
                    selectionRejection = null,
                    lifeSnapshot = null,
                )
        }
        return result
    }

    private fun availableLevel(page: CampaignChapterPageState): Int? =
        page.markers.firstOrNull {
            it.status == CampaignLevelMarkerStatus.AVAILABLE
        }?.level
}
