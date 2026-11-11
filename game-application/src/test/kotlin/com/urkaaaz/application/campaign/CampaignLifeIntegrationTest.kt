package com.urkaaaz.application.campaign

import com.urkaaaz.application.progression.LifeOperationStatus
import com.urkaaaz.application.progression.LifeState
import com.urkaaaz.application.progression.LifeStateMutation
import com.urkaaaz.application.progression.LifeStateRepository
import com.urkaaaz.application.progression.LifeStateRepositoryResult
import com.urkaaaz.application.progression.LifeSystem
import com.urkaaaz.application.progression.LifeSystemResult
import com.urkaaaz.application.progression.WallClock
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.domain.LifeBalance
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CampaignLifeIntegrationTest {
    @Test
    fun campaignMapSelectionRemainsAvailableButStartIsRejectedAtZeroLives() = runBlocking {
        val campaign = CampaignSystem(InMemoryCampaignProgressRepository())
        val start = CampaignLevelStartUseCase(campaign, lifeSystem(lives = 0))

        assertIs<CampaignLevelSelection.Selected>(campaign.selectLevel(1))
        assertEquals(
            CampaignLevelStartResult.Rejected(CampaignLevelStartRejection.NO_LIVES),
            start.authorize(1),
        )
    }

    @Test
    fun activeUnlimitedGrantAuthorizesCampaignStartAtZeroLives() = runBlocking {
        val campaign = CampaignSystem(InMemoryCampaignProgressRepository())
        val start = CampaignLevelStartUseCase(
            campaign,
            lifeSystem(
                lives = 0,
                unlimitedUntil = 60_000L,
                pausedRegeneration = 900_000L,
            ),
        )

        assertTrue(start.authorize(1) is CampaignLevelStartResult.Allowed)
    }

    @Test
    fun nonWinResultConsumesOneLifeIdempotentlyWithoutChangingCampaignProgress() = runBlocking {
        val campaign = CampaignSystem(InMemoryCampaignProgressRepository())
        val lives = lifeSystem(lives = 5)
        val completion = CampaignMatchCompletionUseCase(campaign, lives)
        val event = MatchEvent.MatchFinished(
            eventId = EventId("finish-surrender"),
            matchId = MatchId("campaign-surrender"),
            simulationTimeMilliseconds = 20,
            outcome = MatchOutcome.SURRENDER,
        )

        val first = assertIs<CampaignMatchCompletionResult.LifeSettlement>(
            completion.complete(1, event, null),
        )
        val firstSettlement =
            assertIs<LifeSystemResult.Success<com.urkaaaz.application.progression.LifeOperationResult>>(
                first.result,
            ).value
        assertEquals(LifeOperationStatus.CHARGED, firstSettlement.outcome.status)
        assertEquals(4, firstSettlement.snapshot.lives)
        assertEquals(CampaignProgress.INITIAL, assertIs<CampaignProgressRead.Available>(
            campaign.progress(),
        ).progress)

        val repeated = assertIs<CampaignMatchCompletionResult.LifeSettlement>(
            completion.complete(1, event, null),
        )
        val repeatedSettlement =
            assertIs<LifeSystemResult.Success<com.urkaaaz.application.progression.LifeOperationResult>>(
                repeated.result,
            ).value
        assertTrue(repeatedSettlement.alreadyApplied)
        assertEquals(4, repeatedSettlement.snapshot.lives)
    }

    @Test
    fun campaignWinUsesCampaignCompletionWithoutChargingLife() = runBlocking {
        val campaign = CampaignSystem(InMemoryCampaignProgressRepository())
        val lives = lifeSystem(lives = 2)
        val completion = CampaignMatchCompletionUseCase(campaign, lives)
        val event = MatchEvent.MatchFinished(
            eventId = EventId("finish-win"),
            matchId = MatchId("campaign-win"),
            simulationTimeMilliseconds = 20,
            outcome = MatchOutcome.BLUE_WIN,
        )

        val result = assertIs<CampaignMatchCompletionResult.Campaign>(
            completion.complete(1, event, null),
        )
        assertIs<CampaignCompletionResult.Completed>(result.result)
        assertEquals(
            2,
            assertIs<LifeSystemResult.Success<com.urkaaaz.application.progression.LifeSnapshot>>(
                lives.snapshot(),
            ).value.lives,
        )
    }

    private fun lifeSystem(
        lives: Int,
        unlimitedUntil: Long? = null,
        pausedRegeneration: Long? = null,
    ): LifeSystem {
        val initialState = LifeState(
            lives = lives,
            nextLifeAtEpochMilliseconds =
                if (lives < 5 && unlimitedUntil == null) 900_000L else null,
            pausedRegenerationMilliseconds = pausedRegeneration,
            unlimitedUntilEpochMilliseconds = unlimitedUntil,
            effectiveTimeHighWaterMarkEpochMilliseconds = 0L,
        )
        return LifeSystem(
            repository = object : LifeStateRepository {
                private var state: LifeState? = initialState

                override suspend fun <T> updateLifeState(
                    initialProfile: com.urkaaaz.application.loadout.LoadoutProgressionSnapshot,
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
            balance = LifeBalance(5, 5, 900_000L),
            initialProfile = com.urkaaaz.application.progression.PlayerProgressionUseCases.INITIAL_PROFILE,
        )
    }

    private class InMemoryCampaignProgressRepository(
        private var progress: CampaignProgress = CampaignProgress.INITIAL,
    ) : CampaignProgressRepository {
        override suspend fun loadOrInitialize(): CampaignProgressRepositoryResult =
            CampaignProgressRepositoryResult.Available(progress)

        override suspend fun update(
            transform: (CampaignProgress) -> CampaignProgress,
        ): CampaignProgressRepositoryResult {
            progress = transform(progress)
            return CampaignProgressRepositoryResult.Available(progress)
        }
    }
}
