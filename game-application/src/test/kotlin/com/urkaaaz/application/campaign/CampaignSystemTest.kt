package com.urkaaaz.application.campaign

import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.campaign.CampaignLevelDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CampaignSystemTest {
    @Test
    fun newProgressStartsAtLevelOneAndSelectionRejectsLockedLevels() = runTest {
        val system = CampaignSystem(FakeRepository())

        assertEquals(
            CampaignProgressRead.Available(CampaignProgress.INITIAL),
            system.progress(),
        )
        assertIs<CampaignLevelSelection.Selected>(system.selectLevel(1))
        assertEquals(
            CampaignLevelSelection.Rejected(CampaignSelectionRejection.LEVEL_LOCKED),
            system.selectLevel(2),
        )
        assertEquals(
            CampaignLevelSelection.Rejected(CampaignSelectionRejection.INVALID_LEVEL),
            system.selectLevel(0),
        )
    }

    @Test
    fun blueWinAtomicallyCompletesLevelUnlocksNextAndGrantsMilestone() = runTest {
        val system = CampaignSystem(FakeRepository(CampaignProgress(highestUnlockedLevel = 5)))

        val result = assertIs<CampaignCompletionResult.Completed>(
            system.completeLevel(5, finished(MatchOutcome.BLUE_WIN)),
        )

        assertEquals(6, result.progress.highestUnlockedLevel)
        assertEquals(setOf(5), result.progress.completedLevels)
        assertEquals(setOf("campaign_spell_haste"), result.progress.appliedMilestoneIds)
        assertIs<CampaignLevelSelection.Selected>(system.selectLevel(6))

        val rewards = assertIs<CampaignLoadoutRewardsRead.Available>(
            system.loadoutRewards(6),
        ).rewards
        assertEquals(setOf("HASTE"), rewards.unlockedSpellTypes)
        assertEquals(setOf("HASTE"), rewards.defaultSpellTypes)
    }

    @Test
    fun healIsGrantedOnlyAfterWinningLevelSeven() = runTest {
        val system = CampaignSystem(
            FakeRepository(
                CampaignProgress(
                    highestUnlockedLevel = 7,
                    completedLevels = (1..6).toSet(),
                ),
            ),
        )

        val result = assertIs<CampaignCompletionResult.Completed>(
            system.completeLevel(7, finished(MatchOutcome.BLUE_WIN)),
        )
        assertEquals(8, result.progress.highestUnlockedLevel)
        assertEquals(
            setOf("campaign_spell_heal"),
            result.progress.appliedMilestoneIds,
        )
        assertEquals(
            CampaignLoadoutRewards(
                unlockedSpellTypes = setOf("HEAL"),
                defaultSpellTypes = setOf("HEAL"),
            ),
            assertIs<CampaignLoadoutRewardsRead.Available>(system.loadoutRewards(8)).rewards,
        )
    }

    @Test
    fun nonWinningOutcomesDoNotChangeProgressOrGrantRewards() = runTest {
        for (outcome in listOf(MatchOutcome.RED_WIN, MatchOutcome.DRAW, MatchOutcome.SURRENDER)) {
            val repository = FakeRepository(CampaignProgress(highestUnlockedLevel = 5))
            val result = assertIs<CampaignCompletionResult.NoProgress>(
                CampaignSystem(repository).completeLevel(5, finished(outcome)),
            )
            assertEquals(outcome, result.outcome)
            assertEquals(CampaignProgress(highestUnlockedLevel = 5), result.progress)
            assertEquals(CampaignProgress(highestUnlockedLevel = 5), repository.progress)
        }
    }

    @Test
    fun repeatedCompletionIsIdempotentAndCanReconcileMissingMilestoneId() = runTest {
        val repository = FakeRepository(
            CampaignProgress(
                highestUnlockedLevel = 6,
                completedLevels = setOf(5),
            ),
        )
        val system = CampaignSystem(repository)
        val event = finished(MatchOutcome.BLUE_WIN)

        val first = assertIs<CampaignCompletionResult.Completed>(
            system.completeLevel(5, event),
        )
        val second = assertIs<CampaignCompletionResult.Completed>(
            system.completeLevel(5, event),
        )

        assertEquals(first.progress, second.progress)
        assertEquals(6, second.progress.highestUnlockedLevel)
        assertEquals(setOf(5), second.progress.completedLevels)
        assertEquals(setOf("campaign_spell_haste"), second.progress.appliedMilestoneIds)
    }

    @Test
    fun lockedWinAndUnknownMilestoneDataAreRejectedWithoutGrantingRewards() = runTest {
        val locked = FakeRepository(CampaignProgress.INITIAL)
        assertEquals(
            CampaignCompletionResult.Rejected(CampaignSelectionRejection.LEVEL_LOCKED),
            CampaignSystem(locked).completeLevel(5, finished(MatchOutcome.BLUE_WIN)),
        )
        assertEquals(CampaignProgress.INITIAL, locked.progress)

        val invalid = CampaignSystem(
            FakeRepository(CampaignProgress(appliedMilestoneIds = setOf("removed_reward"))),
        )
        assertEquals(
            CampaignProgressRead.Failure(CampaignProgressFailure.INVALID_DATA),
            invalid.progress(),
        )
    }

    @Test
    fun finalLevelCompletionDoesNotOverflowCampaignMaximum() = runTest {
        val progress = CampaignProgress(highestUnlockedLevel = CampaignLevelDefinition.MAX_LEVELS)
        val system = CampaignSystem(FakeRepository(progress))

        val result = assertIs<CampaignCompletionResult.Completed>(
            system.completeLevel(CampaignLevelDefinition.MAX_LEVELS, finished(MatchOutcome.BLUE_WIN)),
        )

        assertEquals(CampaignLevelDefinition.MAX_LEVELS, result.progress.highestUnlockedLevel)
        assertEquals(setOf(CampaignLevelDefinition.MAX_LEVELS), result.progress.completedLevels)
    }

    @Test
    fun repositoryFailureIsNotPresentedAsCampaignProgress() = runTest {
        val system = CampaignSystem(
            FakeRepository(failure = CampaignProgressFailure.STORAGE_UNAVAILABLE),
        )

        assertEquals(
            CampaignProgressRead.Failure(CampaignProgressFailure.STORAGE_UNAVAILABLE),
            system.progress(),
        )
        assertEquals(
            CampaignCompletionResult.Failure(CampaignProgressFailure.STORAGE_UNAVAILABLE),
            system.completeLevel(1, finished(MatchOutcome.BLUE_WIN)),
        )
    }

    private fun finished(outcome: MatchOutcome) = MatchEvent.MatchFinished(
        eventId = EventId("finished"),
        matchId = MatchId("match-1"),
        simulationTimeMilliseconds = 1L,
        outcome = outcome,
    )

    private class FakeRepository(
        var progress: CampaignProgress = CampaignProgress.INITIAL,
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

private fun <T> runTest(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
