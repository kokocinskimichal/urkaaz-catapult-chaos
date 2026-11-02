package com.urkaaaz.application.shop

import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionMutation
import com.urkaaaz.application.progression.PlayerProgressionMutationResult
import com.urkaaaz.application.progression.PlayerProgressionRepository
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.PlayerProgressionRepositoryResult
import com.urkaaaz.domain.SpellType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ShopUseCasesTest {
    @Test
    fun catalogUsesConfiguredPricesAndOnlyShopEligibleItems() = runBlocking {
        val result = assertIs<ShopCatalogResult.Available>(
            ShopUseCases(FakeRepository(profile(gold = 900))).catalog(),
        )
        val snapshot = result.snapshot

        assertEquals(900, snapshot.goldBalance)
        assertEquals(
            listOf(
                "CATAPULT_LOCK",
                "SLOWING_FIELD",
                "VAMPIRIC_BANNER",
                "STONE_SKIN",
                "SIEGE_FRENZY",
                "BERSERKER",
            ),
            snapshot.offers(ShopCategory.SPELLS).map { it.item.identifier },
        )
        assertEquals(
            listOf("POWDER_BARREL", "FIRE_RAIN", "PLAGUE_CAULDRON"),
            snapshot.offers(ShopCategory.AMMUNITION).map { it.item.identifier },
        )
        assertEquals(
            listOf(250, 350, 500),
            snapshot.offers(ShopCategory.AMMUNITION).map(ShopOffer::priceGold),
        )
        assertFalse(snapshot.offers(ShopCategory.SPELLS).first().runtimeImplemented)
    }

    @Test
    fun ammunitionPurchaseAtomicallyDebitsOrCoinsAndAddsOneStock() = runBlocking {
        val repository = FakeRepository(profile(gold = 1_000))
        val useCases = ShopUseCases(repository)

        val result = assertIs<ShopPurchaseResult.Purchased>(
            useCases.purchase(ShopItemReference(ShopCategory.AMMUNITION, "POWDER_BARREL")),
        )

        assertEquals(750, result.profile.goldBalance)
        assertEquals(1, result.profile.ammunitionStock("POWDER_BARREL"))
        assertEquals(0, result.profile.specialSpellStock("POWDER_BARREL"))
        assertEquals(result.profile, repository.current)
    }

    @Test
    fun specialSpellPurchaseUsesExistingGoldCostWithoutUnlockingOrEquipping() = runBlocking {
        val repository = FakeRepository(profile(gold = 1_500))
        val useCases = ShopUseCases(repository)

        val result = assertIs<ShopPurchaseResult.Purchased>(
            useCases.purchase(ShopItemReference(ShopCategory.SPELLS, "CATAPULT_LOCK")),
        )

        assertEquals(500, result.profile.goldBalance)
        assertEquals(1, result.profile.specialSpellStock("CATAPULT_LOCK"))
        assertEquals(setOf(SpellType.FORTRESS_SHIELD.name), result.profile.unlockedSpellTypes)
    }

    @Test
    fun repeatedPurchasesAddOneStockEachAndNeverOverflow() = runBlocking {
        val repository = FakeRepository(profile(gold = 1_000))
        val useCases = ShopUseCases(repository)
        val item = ShopItemReference(ShopCategory.AMMUNITION, "POWDER_BARREL")

        assertIs<ShopPurchaseResult.Purchased>(useCases.purchase(item))
        assertIs<ShopPurchaseResult.Purchased>(useCases.purchase(item))

        assertEquals(2, repository.current.ammunitionStock(item.identifier))
        assertEquals(500, repository.current.goldBalance)

        val fullStock = FakeRepository(
            profile(gold = 1_000).copy(
                ammunitionStocks = mapOf(item.identifier to Int.MAX_VALUE),
            ),
        )
        assertEquals(
            ShopPurchaseResult.StockOverflow,
            ShopUseCases(fullStock).purchase(item),
        )
        assertEquals(Int.MAX_VALUE, fullStock.current.ammunitionStock(item.identifier))
        assertEquals(1_000, fullStock.current.goldBalance)
    }

    @Test
    fun invalidUnknownUnavailableAndInsufficientPurchasesDoNotMutateProfile() = runBlocking {
        val startingProfile = profile(gold = 100)
        val repository = FakeRepository(startingProfile)
        val useCases = ShopUseCases(repository)

        assertEquals(
            ShopPurchaseResult.UnknownItem,
            useCases.purchase(ShopItemReference(ShopCategory.SPELLS, "UNKNOWN")),
        )
        assertEquals(
            ShopPurchaseResult.NotForSale,
            useCases.purchase(ShopItemReference(ShopCategory.SPELLS, "HASTE")),
        )
        assertEquals(
            ShopPurchaseResult.NotForSale,
            useCases.purchase(ShopItemReference(ShopCategory.AMMUNITION, "ROCK")),
        )
        assertEquals(
            ShopPurchaseResult.InsufficientOrCoins,
            useCases.purchase(ShopItemReference(ShopCategory.AMMUNITION, "POWDER_BARREL")),
        )
        assertEquals(startingProfile, repository.current)
    }

    @Test
    fun repositoryFailuresRemainExplicit() = runBlocking {
        val repository = FakeRepository(
            result = PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE,
            ),
        )
        val useCases = ShopUseCases(repository)

        assertEquals(
            ShopCatalogResult.Failure(ShopReadFailure.STORAGE_UNAVAILABLE),
            useCases.catalog(),
        )
        assertEquals(
            ShopPurchaseResult.StorageUnavailable,
            useCases.purchase(ShopItemReference(ShopCategory.AMMUNITION, "POWDER_BARREL")),
        )
    }

    private fun profile(gold: Int) = LoadoutProgressionSnapshot(
        unlockedSpellTypes = setOf(SpellType.FORTRESS_SHIELD.name),
        ammunitionStocks = emptyMap(),
        specialSpellStocks = emptyMap(),
        goldBalance = gold,
    )

    private class FakeRepository(
        initialProfile: LoadoutProgressionSnapshot? = null,
        private val result: PlayerProgressionRepositoryResult? = null,
    ) : PlayerProgressionRepository {
        private var storedProfile = initialProfile
        val current: LoadoutProgressionSnapshot
            get() = requireNotNull(storedProfile)

        override suspend fun loadOrInitialize(
            initialProfile: LoadoutProgressionSnapshot,
        ): PlayerProgressionRepositoryResult {
            result?.let { return it }
            if (storedProfile == null) storedProfile = initialProfile
            return PlayerProgressionRepositoryResult.Available(current)
        }

        override suspend fun <T> updateProfile(
            initialProfile: LoadoutProgressionSnapshot,
            transform: (LoadoutProgressionSnapshot) -> PlayerProgressionMutation<T>,
        ): PlayerProgressionMutationResult<T> {
            result?.let {
                if (it is PlayerProgressionRepositoryResult.Failure) {
                    return PlayerProgressionMutationResult.Failure(it.reason)
                }
            }
            val source = storedProfile ?: initialProfile.also { storedProfile = it }
            val mutation = transform(source)
            if (mutation.shouldPersist) storedProfile = mutation.snapshot
            return PlayerProgressionMutationResult.Completed(
                snapshot = mutation.snapshot,
                value = mutation.value,
            )
        }
    }
}
