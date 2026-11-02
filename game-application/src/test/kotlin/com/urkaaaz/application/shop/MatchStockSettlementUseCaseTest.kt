package com.urkaaaz.application.shop

import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.domain.GameBalanceFixtures
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchStockSettlementUseCaseTest {
    @Test
    fun rejectsUnlimitedUnknownAndNonShopItemsBeforePersistence() = runBlocking {
        val port = RecordingPort()
        val useCases = MatchStockSettlementUseCase(port, GameBalanceFixtures.balance)
        val id = MatchStockSettlementId(MatchId("settlement-validation"), 1)

        assertEquals(
            MatchStockSettlementResult.Rejected(
                MatchStockSettlementRejection.INVALID_USAGE,
            ),
            useCases.settle(
                id,
                MatchStockUsage(mapOf("ROCK" to 1), emptyMap()),
            ),
        )
        assertEquals(
            MatchStockSettlementResult.Rejected(
                MatchStockSettlementRejection.INVALID_USAGE,
            ),
            useCases.settle(
                id,
                MatchStockUsage(mapOf("UNKNOWN" to 1), emptyMap()),
            ),
        )
        assertEquals(
            MatchStockSettlementResult.Rejected(
                MatchStockSettlementRejection.INVALID_USAGE,
            ),
            useCases.settle(
                id,
                MatchStockUsage(
                    emptyMap(),
                    mapOf("HASTE" to 1),
                ),
            ),
        )
        assertEquals(0, port.callCount)
    }

    @Test
    fun forwardsValidUsageAndSettlementIdentity() = runBlocking {
        val profile = LoadoutProgressionSnapshot(
            unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
            ammunitionStocks = mapOf("POWDER_BARREL" to 2),
            specialSpellStocks = mapOf("CATAPULT_LOCK" to 1),
            goldBalance = 300,
        )
        val port = RecordingPort(
            MatchStockSettlementResult.Settled(profile),
        )
        val id = MatchStockSettlementId(MatchId("valid-settlement"), 3)
        val usage = MatchStockUsage(
            ammunitionUsed = mapOf("POWDER_BARREL" to 1),
            specialSpellsUsed = mapOf("CATAPULT_LOCK" to 1),
        )

        assertEquals(
            MatchStockSettlementResult.Settled(profile),
            MatchStockSettlementUseCase(port, GameBalanceFixtures.balance).settle(id, usage),
        )
        assertEquals(id, port.receivedId)
        assertEquals(usage, port.receivedUsage)
        assertEquals(1, port.callCount)
    }

    private class RecordingPort(
        private val result: MatchStockSettlementResult =
            MatchStockSettlementResult.Rejected(MatchStockSettlementRejection.STOCK_CONFLICT),
    ) : MatchStockSettlementPort {
        var callCount = 0
        var receivedId: MatchStockSettlementId? = null
        var receivedUsage: MatchStockUsage? = null

        override suspend fun settle(
            id: MatchStockSettlementId,
            usage: MatchStockUsage,
        ): MatchStockSettlementResult {
            callCount += 1
            receivedId = id
            receivedUsage = usage
            return result
        }
    }
}
