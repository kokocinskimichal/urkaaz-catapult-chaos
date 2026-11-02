package com.urkaaaz.screens.shop

import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionMutation
import com.urkaaaz.application.progression.PlayerProgressionMutationResult
import com.urkaaaz.application.progression.PlayerProgressionRepository
import com.urkaaaz.application.progression.PlayerProgressionRepositoryResult
import com.urkaaaz.application.shop.ShopCategory
import com.urkaaaz.application.shop.ShopItemReference
import com.urkaaaz.application.shop.ShopUseCases
import com.urkaaaz.domain.SpellType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopViewModelTest {
    @Test
    fun selectedLoadoutOfferIsPreservedAndPurchaseRefreshesLiveProfile() = runBlocking {
        val repository = FakeRepository(
            LoadoutProgressionSnapshot(
                unlockedSpellTypes = setOf(SpellType.FORTRESS_SHIELD.name),
                ammunitionStocks = emptyMap(),
                specialSpellStocks = emptyMap(),
                goldBalance = 500,
            ),
        )
        val initialItem = ShopItemReference(ShopCategory.AMMUNITION, "POWDER_BARREL")
        val viewModel = ShopViewModel(ShopUseCases(repository), initialItem)

        viewModel.load()
        assertEquals(initialItem, viewModel.state.selectedItem)
        assertEquals(500, viewModel.state.catalog?.goldBalance)

        assertTrue(viewModel.purchaseSelected())

        assertEquals(initialItem, viewModel.state.selectedItem)
        assertEquals(250, viewModel.state.catalog?.goldBalance)
        assertEquals(1, viewModel.state.selectedOffer?.stock)
        assertEquals(ShopUiMessage.PURCHASED, viewModel.state.message)
    }

    @Test
    fun categorySwitchKeepsCatalogsSeparateAndSpecialSpellWarningMetadata() = runBlocking {
        val viewModel = ShopViewModel(ShopUseCases(FakeRepository(initialProfile())))
        viewModel.load()

        viewModel.selectCategory(ShopCategory.AMMUNITION)
        assertEquals(
            setOf("POWDER_BARREL", "FIRE_RAIN", "PLAGUE_CAULDRON"),
            viewModel.state.offers.map { it.item.identifier }.toSet(),
        )

        viewModel.selectCategory(ShopCategory.SPELLS)
        assertFalse(viewModel.state.offers.any { it.item.identifier == "HASTE" })
        assertFalse(viewModel.state.offers.any { it.item.identifier == "HEAL" })
        assertTrue(viewModel.state.offers.none { it.runtimeImplemented })
        assertNotNull(viewModel.state.selectedOffer)
    }

    private fun initialProfile() = LoadoutProgressionSnapshot(
        unlockedSpellTypes = setOf(SpellType.FORTRESS_SHIELD.name),
        ammunitionStocks = emptyMap(),
        specialSpellStocks = emptyMap(),
        goldBalance = 1_000,
    )

    private class FakeRepository(
        private var profile: LoadoutProgressionSnapshot,
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
            if (mutation.shouldPersist) profile = mutation.snapshot
            return PlayerProgressionMutationResult.Completed(
                snapshot = mutation.snapshot,
                value = mutation.value,
            )
        }
    }
}
