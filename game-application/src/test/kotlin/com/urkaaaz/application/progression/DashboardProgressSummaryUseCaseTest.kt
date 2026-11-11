package com.urkaaaz.application.progression

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressRepository
import com.urkaaaz.application.campaign.CampaignProgressRepositoryResult
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.domain.LifeBalance
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DashboardProgressSummaryUseCaseTest {
    @Test
    fun combinesCampaignProfileAndCanonicalLifeReadModels() = runBlocking {
        val profile = LoadoutProgressionSnapshot(
            unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
            ammunitionStocks = emptyMap(),
            specialSpellStocks = emptyMap(),
            goldBalance = 275,
        )
        val useCase = DashboardProgressSummaryUseCase(
            campaignSystem = CampaignSystem(
                FakeCampaignRepository(
                    CampaignProgress(
                        highestUnlockedLevel = 3,
                        completedLevels = setOf(1, 2),
                    ),
                ),
            ),
            progression = FakePlayerProgressionRepository(profile),
            lifeSystem = LifeSystem(
                repository = FakeLifeStateRepository(
                    LifeState(
                        lives = 2,
                        nextLifeAtEpochMilliseconds = 900_000L,
                        pausedRegenerationMilliseconds = null,
                        unlimitedUntilEpochMilliseconds = null,
                        effectiveTimeHighWaterMarkEpochMilliseconds = 0L,
                    ),
                ),
                wallClock = WallClock { 0L },
                balance = LifeBalance(5, 5, 900_000L),
                initialProfile = profile,
            ),
        )

        val result = assertIs<DashboardProgressRead.Available>(useCase.load())

        assertEquals(3, result.summary.currentLevel)
        assertEquals(275, result.summary.gold)
        assertEquals(2, result.summary.lifeSnapshot.lives)
        assertEquals(900_000L, result.summary.lifeSnapshot.regenerationTimeRemainingMilliseconds)
    }

    private class FakeCampaignRepository(
        private val progress: CampaignProgress,
    ) : CampaignProgressRepository {
        override suspend fun loadOrInitialize(): CampaignProgressRepositoryResult =
            CampaignProgressRepositoryResult.Available(progress)

        override suspend fun update(
            transform: (CampaignProgress) -> CampaignProgress,
        ): CampaignProgressRepositoryResult =
            CampaignProgressRepositoryResult.Available(transform(progress))
    }

    private class FakePlayerProgressionRepository(
        private val profile: LoadoutProgressionSnapshot,
    ) : PlayerProgressionRepository {
        override suspend fun loadOrInitialize(
            initialProfile: LoadoutProgressionSnapshot,
        ): PlayerProgressionRepositoryResult =
            PlayerProgressionRepositoryResult.Available(profile)

        override suspend fun <T> updateProfile(
            initialProfile: LoadoutProgressionSnapshot,
            transform: (LoadoutProgressionSnapshot) -> PlayerProgressionMutation<T>,
        ): PlayerProgressionMutationResult<T> {
            val mutation = transform(profile)
            return PlayerProgressionMutationResult.Completed(mutation.snapshot, mutation.value)
        }
    }

    private class FakeLifeStateRepository(
        private var state: LifeState,
    ) : LifeStateRepository {
        override suspend fun <T> updateLifeState(
            initialProfile: LoadoutProgressionSnapshot,
            initialState: LifeState,
            migrateLegacyLives: (Int?) -> LifeState?,
            transform: (LifeState) -> LifeStateMutation<T>,
        ): LifeStateRepositoryResult<T> {
            val mutation = transform(state)
            if (mutation.shouldPersist) state = mutation.state
            return LifeStateRepositoryResult.Completed(mutation.state, mutation.value)
        }
    }
}
