package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.domain.SpellType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlayerProgressionUseCasesTest {
    @Test
    fun firstProfileUsesConfirmedLegacyStartingValues() = runBlocking {
        val repository = FakeRepository(
            PlayerProgressionRepositoryResult.Available(
                LoadoutProgressionSnapshot(
                    unlockedSpellTypes = setOf(SpellType.FORTRESS_SHIELD.name),
                    ammunitionStocks = emptyMap(),
                    specialSpellStocks = emptyMap(),
                    goldBalance = 0,
                ),
            ),
        )

        val result = assertIs<LoadoutProgressionRead.Available>(
            PlayerProgressionUseCases(repository).snapshot(level = 1),
        )

        assertEquals(setOf("FORTRESS_SHIELD"), result.snapshot.unlockedSpellTypes)
        assertEquals(emptyMap(), result.snapshot.ammunitionStocks)
        assertEquals(emptyMap(), result.snapshot.specialSpellStocks)
        assertEquals(0, result.snapshot.goldBalance)
        assertEquals(SpellType.FORTRESS_SHIELD.name, repository.initialProfile?.unlockedSpellTypes?.single())
        assertEquals(0, repository.initialProfile?.goldBalance)
    }

    @Test
    fun invalidLevelIsRejectedWithoutReadingOrInitializingProfile() = runBlocking {
        val repository = FakeRepository(
            PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE,
            ),
        )

        assertEquals(
            LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_LEVEL),
            PlayerProgressionUseCases(repository).snapshot(level = 0),
        )
        assertEquals(0, repository.readCount)
    }

    @Test
    fun storageFailureRemainsTypedAndSpellPurchasesStayBlocked() = runBlocking {
        val repository = FakeRepository(
            PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE,
            ),
        )
        val useCases = PlayerProgressionUseCases(repository)

        assertEquals(
            LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.STORAGE_UNAVAILABLE),
            useCases.snapshot(level = 1),
        )
        assertEquals(
            com.urkaaaz.application.loadout.LoadoutPurchaseResult.Blocked,
            useCases.purchaseSpell("HASTE"),
        )
        assertEquals(1, repository.readCount)
    }

    @Test
    fun unknownOrUnlimitedStockIdentifiersAreRejectedAsInvalidProfile() = runBlocking {
        val repository = FakeRepository(
            PlayerProgressionRepositoryResult.Available(
                LoadoutProgressionSnapshot(
                    unlockedSpellTypes = setOf("UNKNOWN_SPELL"),
                    ammunitionStocks = mapOf("ROCK" to 1),
                    specialSpellStocks = emptyMap(),
                    goldBalance = 10,
                ),
            ),
        )

        assertEquals(
            LoadoutProgressionRead.Blocked(LoadoutProgressionFailure.INVALID_PROFILE),
            PlayerProgressionUseCases(repository).snapshot(level = 1),
        )
    }

    private class FakeRepository(
        private val result: PlayerProgressionRepositoryResult,
    ) : PlayerProgressionRepository {
        var readCount = 0
        var initialProfile: LoadoutProgressionSnapshot? = null

        override suspend fun loadOrInitialize(
            initialProfile: LoadoutProgressionSnapshot,
        ): PlayerProgressionRepositoryResult {
            readCount += 1
            this.initialProfile = initialProfile
            return result
        }
    }
}
