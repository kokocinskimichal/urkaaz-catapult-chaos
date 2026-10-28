package com.urkaaaz.application.reward

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.contracts.EventId
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RewardSettlementUseCaseTest {
    @Test
    fun settlementAtomicallyAddsGoldCampaignProgressMilestoneAndReceipt() = runTest {
        val port = FakePort(
            RewardSettlementState(
                campaign = CampaignProgress(highestUnlockedLevel = 5),
                profile = profile(40),
                receipts = emptyMap(),
            ),
        )
        val useCase = RewardSettlementUseCase(port)
        val event = finished(levelMatchId)

        val first = assertIs<RewardSettlementRepositoryResult.Completed>(
            useCase.settle(5, event, reward(5, levelMatchId.value, 30L)),
        )
        val settled = assertIs<RewardSettlementOutcome.Settled>(first.outcome)
        assertEquals(70, settled.state.profile.goldBalance)
        assertEquals(6, settled.state.campaign.highestUnlockedLevel)
        assertEquals(setOf(5), settled.state.campaign.completedLevels)
        assertEquals(setOf("campaign_spell_haste"), settled.state.campaign.appliedMilestoneIds)

        val duplicate = assertIs<RewardSettlementRepositoryResult.Completed>(
            useCase.settle(5, event, reward(5, levelMatchId.value, 50L)),
        )
        val alreadySettled = assertIs<RewardSettlementOutcome.AlreadySettled>(duplicate.outcome)
        assertEquals(70, alreadySettled.state.profile.goldBalance)
        assertEquals(30, alreadySettled.reward.totalGold)
    }

    @Test
    fun completedLevelWithoutReceiptDoesNotReceiveRetroactiveGold() = runTest {
        val port = FakePort(
            RewardSettlementState(
                campaign = CampaignProgress(highestUnlockedLevel = 6, completedLevels = setOf(5)),
                profile = profile(40),
                receipts = emptyMap(),
            ),
        )

        val result = assertIs<RewardSettlementRepositoryResult.Completed>(
            RewardSettlementUseCase(port).settle(
                5,
                finished(levelMatchId),
                reward(5, levelMatchId.value, 30L),
            ),
        )

        assertEquals(
            RewardSettlementOutcome.Rejected(RewardSettlementRejection.LEVEL_ALREADY_COMPLETED),
            result.outcome,
        )
        assertEquals(40, port.state.profile.goldBalance)
    }

    @Test
    fun goldOverflowRejectsWholeSettlement() = runTest {
        val port = FakePort(
            RewardSettlementState(
                campaign = CampaignProgress.INITIAL,
                profile = profile(Int.MAX_VALUE),
                receipts = emptyMap(),
            ),
        )

        val result = assertIs<RewardSettlementRepositoryResult.Completed>(
            RewardSettlementUseCase(port).settle(
                1,
                finished(levelMatchId),
                reward(1, levelMatchId.value, 1L),
            ),
        )

        assertEquals(
            RewardSettlementOutcome.Rejected(RewardSettlementRejection.GOLD_OVERFLOW),
            result.outcome,
        )
        assertEquals(CampaignProgress.INITIAL, port.state.campaign)
        assertEquals(Int.MAX_VALUE, port.state.profile.goldBalance)
        assertEquals(emptyMap(), port.state.receipts)
    }

    private class FakePort(
        var state: RewardSettlementState,
    ) : RewardSettlementPort {
        override suspend fun update(
            transform: (RewardSettlementState) -> RewardSettlementTransaction,
        ): RewardSettlementRepositoryResult.Completed {
            val transaction = transform(state)
            if (transaction.shouldPersist) state = transaction.state
            return RewardSettlementRepositoryResult.Completed(transaction.outcome)
        }
    }

    private fun profile(gold: Int) = PlayerProgressionUseCases.INITIAL_PROFILE.copy(
        goldBalance = gold,
    )

    private fun reward(level: Int, matchId: String, gold: Long) = LevelRewardResult(
        level = level,
        matchId = matchId,
        rewards = listOf(EarnedReward("level_win", gold)),
        totalGold = gold,
        issues = emptySet(),
    )

    private fun finished(matchId: MatchId) = MatchEvent.MatchFinished(
        eventId = EventId("finished-${matchId.value}"),
        matchId = matchId,
        simulationTimeMilliseconds = 1,
        outcome = MatchOutcome.BLUE_WIN,
    )

    companion object {
        private val levelMatchId = MatchId("level-5-match")
    }
}

private fun <T> runTest(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
