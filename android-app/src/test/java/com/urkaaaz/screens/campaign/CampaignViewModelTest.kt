package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignLevelSelection
import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignProgressRepository
import com.urkaaaz.application.campaign.CampaignProgressRepositoryResult
import com.urkaaaz.application.campaign.CampaignProgressRead
import com.urkaaaz.application.campaign.CampaignSelectionRejection
import com.urkaaaz.application.campaign.CampaignSystem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignViewModelTest {
    @Test
    fun campaignLoadsPersistedProgressAndRejectsLockedSelection() = runBlocking {
        val viewModel = CampaignViewModel(
            CampaignSystem(FakeRepository(CampaignProgress.INITIAL)),
        )

        val loadedState = viewModel.load()
        assertEquals(CampaignProgress.INITIAL, loadedState.progress)
        assertEquals("green_frontier", loadedState.selectedChapterId)
        assertEquals(1, loadedState.selectedLevel)
        assertEquals(
            "green_frontier",
            loadedState.mapProgression?.selectedChapterId,
        )
        assertTrue(viewModel.selectChapter("broken_valley"))
        assertEquals("broken_valley", viewModel.state.selectedChapterId)
        assertEquals(null, viewModel.state.selectedLevel)
        assertFalse(viewModel.selectMapLevel(11))
        assertFalse(viewModel.selectChapter("ashen_march"))
        assertTrue(viewModel.selectChapter("green_frontier"))
        assertEquals(1, viewModel.state.selectedLevel)
        assertFalse(viewModel.selectMapLevel(2))
        assertEquals(1, viewModel.state.selectedLevel)
        assertTrue(viewModel.selectMapLevel(1))

        assertEquals(
            CampaignLevelSelection.Rejected(CampaignSelectionRejection.LEVEL_LOCKED),
            viewModel.dispatch(CampaignUiAction.SelectLevel(2)),
        )
        assertEquals(CampaignSelectionRejection.LEVEL_LOCKED, viewModel.state.selectionRejection)
        assertEquals(1, viewModel.state.progress?.highestUnlockedLevel)
    }

    @Test
    fun progressFailureDoesNotCreateAnAllLevelsUnlockedState() = runBlocking {
        val viewModel = CampaignViewModel(
            CampaignSystem(FakeRepository(failure = CampaignProgressFailure.STORAGE_UNAVAILABLE)),
        )

        assertEquals(
            CampaignProgressRead.Failure(CampaignProgressFailure.STORAGE_UNAVAILABLE),
            CampaignSystem(FakeRepository(failure = CampaignProgressFailure.STORAGE_UNAVAILABLE))
                .progress(),
        )
        assertEquals(
            CampaignUiState(failure = CampaignProgressFailure.STORAGE_UNAVAILABLE),
            viewModel.load(),
        )
        assertEquals(null, viewModel.state.progress)
    }

    private class FakeRepository(
        private var progress: CampaignProgress = CampaignProgress.INITIAL,
        private val failure: CampaignProgressFailure? = null,
    ) : CampaignProgressRepository {
        override suspend fun loadOrInitialize(): CampaignProgressRepositoryResult =
            failure?.let(CampaignProgressRepositoryResult::Failure)
                ?: CampaignProgressRepositoryResult.Available(progress)

        override suspend fun update(
            transform: (CampaignProgress) -> CampaignProgress,
        ): CampaignProgressRepositoryResult {
            failure?.let { return CampaignProgressRepositoryResult.Failure(it) }
            progress = transform(progress)
            return CampaignProgressRepositoryResult.Available(progress)
        }
    }
}
