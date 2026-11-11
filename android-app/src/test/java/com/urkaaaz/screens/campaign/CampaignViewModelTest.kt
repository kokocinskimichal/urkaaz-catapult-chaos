package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignLevelStartRejection
import com.urkaaaz.application.campaign.CampaignLevelStartResult
import com.urkaaaz.application.campaign.CampaignLevelStartUseCase
import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignProgressRepository
import com.urkaaaz.application.campaign.CampaignProgressRepositoryResult
import com.urkaaaz.application.campaign.CampaignProgressRead
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.LifeState
import com.urkaaaz.application.progression.LifeStateMutation
import com.urkaaaz.application.progression.LifeStateRepository
import com.urkaaaz.application.progression.LifeStateRepositoryResult
import com.urkaaaz.application.progression.LifeSystem
import com.urkaaaz.application.progression.WallClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignViewModelTest {
    @Test
    fun campaignLoadsPersistedProgressAndRejectsLockedSelection() = runBlocking {
        val viewModel = CampaignViewModel(
            startUseCase(CampaignSystem(FakeRepository(CampaignProgress.INITIAL))),
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
            CampaignLevelStartResult.Rejected(CampaignLevelStartRejection.LEVEL_LOCKED),
            viewModel.dispatch(CampaignUiAction.SelectLevel(2)),
        )
        assertEquals(CampaignLevelStartRejection.LEVEL_LOCKED, viewModel.state.selectionRejection)
        assertEquals(1, viewModel.state.progress?.highestUnlockedLevel)
    }

    @Test
    fun chapterCarouselNavigatesOnlyWithinVisibleCompletedCurrentAndPreviewPages() = runBlocking {
        val viewModel = CampaignViewModel(
            startUseCase(CampaignSystem(
                FakeRepository(
                    CampaignProgress(
                        highestUnlockedLevel = 11,
                        completedLevels = (1..10).toSet(),
                    ),
                ),
            )),
        )
        viewModel.load()

        assertEquals("broken_valley", viewModel.state.selectedChapterId)
        assertTrue(viewModel.navigateChapter(CampaignChapterNavigationDirection.PREVIOUS))
        assertEquals("green_frontier", viewModel.state.selectedChapterId)
        assertNull(viewModel.state.selectedLevel)
        assertFalse(viewModel.navigateChapter(CampaignChapterNavigationDirection.PREVIOUS))
        assertEquals("green_frontier", viewModel.state.selectedChapterId)

        assertTrue(viewModel.navigateChapter(CampaignChapterNavigationDirection.NEXT))
        assertEquals("broken_valley", viewModel.state.selectedChapterId)
        assertEquals(11, viewModel.state.selectedLevel)
        assertTrue(viewModel.navigateChapter(CampaignChapterNavigationDirection.NEXT))
        assertEquals("ashen_march", viewModel.state.selectedChapterId)
        assertNull(viewModel.state.selectedLevel)
        assertFalse(viewModel.navigateChapter(CampaignChapterNavigationDirection.NEXT))
        assertEquals("ashen_march", viewModel.state.selectedChapterId)
    }

    @Test
    fun progressFailureDoesNotCreateAnAllLevelsUnlockedState() = runBlocking {
        val viewModel = CampaignViewModel(
            startUseCase(
                CampaignSystem(FakeRepository(failure = CampaignProgressFailure.STORAGE_UNAVAILABLE)),
            ),
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

    private fun startUseCase(campaignSystem: CampaignSystem): CampaignLevelStartUseCase =
        CampaignLevelStartUseCase(
            campaignSystem,
            LifeSystem(
                repository = object : LifeStateRepository {
                    private var state: LifeState? = null

                    override suspend fun <T> updateLifeState(
                        initialProfile: LoadoutProgressionSnapshot,
                        initialState: LifeState,
                        migrateLegacyLives: (Int?) -> LifeState?,
                        transform: (LifeState) -> LifeStateMutation<T>,
                    ): LifeStateRepositoryResult<T> {
                        val mutation = transform(state ?: initialState)
                        if (mutation.shouldPersist) state = mutation.state
                        return LifeStateRepositoryResult.Completed(mutation.state, mutation.value)
                    }
                },
                wallClock = WallClock { 0L },
            ),
        )
}
