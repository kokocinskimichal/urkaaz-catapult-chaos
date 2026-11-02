package com.urkaaaz.application.shop

import com.urkaaaz.application.balance.GameBalanceRepository
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionMutation
import com.urkaaaz.application.progression.PlayerProgressionMutationResult
import com.urkaaaz.application.progression.PlayerProgressionRepository
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.PlayerProgressionRepositoryResult
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.domain.AmmunitionType
import com.urkaaaz.domain.GameBalanceConfig
import com.urkaaaz.domain.SpellType

class ShopUseCases(
    private val progression: PlayerProgressionRepository,
    private val balance: GameBalanceConfig = GameBalanceRepository.bundled.requireConfig(),
) {
    suspend fun catalog(): ShopCatalogResult =
        when (val result = progression.loadOrInitialize(PlayerProgressionUseCases.INITIAL_PROFILE)) {
            is PlayerProgressionRepositoryResult.Failure ->
                ShopCatalogResult.Failure(result.reason.toShopReadFailure())
            is PlayerProgressionRepositoryResult.Available -> {
                if (!PlayerProgressionUseCases.isValidProfile(result.snapshot, balance)) {
                    ShopCatalogResult.Failure(ShopReadFailure.INVALID_PROFILE)
                } else {
                    ShopCatalogResult.Available(result.snapshot.toShopCatalog())
                }
            }
        }

    suspend fun purchase(item: ShopItemReference): ShopPurchaseResult {
        if (!itemIsKnown(item)) return ShopPurchaseResult.UnknownItem
        val offer = offer(item) ?: return ShopPurchaseResult.NotForSale

        return when (
            val result = progression.updateProfile(PlayerProgressionUseCases.INITIAL_PROFILE) {
                current ->
                if (!PlayerProgressionUseCases.isValidProfile(current, balance)) {
                    PlayerProgressionMutation(
                        snapshot = current,
                        value = ShopPurchaseResult.InvalidProfile,
                        shouldPersist = false,
                    )
                } else if (current.goldBalance < offer.priceGold) {
                    PlayerProgressionMutation(
                        snapshot = current,
                        value = ShopPurchaseResult.InsufficientOrCoins,
                        shouldPersist = false,
                    )
                } else {
                    val currentStock = current.stock(item)
                    if (currentStock == Int.MAX_VALUE) {
                        PlayerProgressionMutation(
                            snapshot = current,
                            value = ShopPurchaseResult.StockOverflow,
                            shouldPersist = false,
                        )
                    } else {
                        val next = current.withPurchasedStock(
                            item = item,
                            priceGold = offer.priceGold,
                            stock = currentStock + 1,
                        )
                        PlayerProgressionMutation(
                            snapshot = next,
                            value = ShopPurchaseResult.Purchased(next),
                            shouldPersist = true,
                        )
                    }
                }
            }
        ) {
            is PlayerProgressionMutationResult.Completed -> result.value
            is PlayerProgressionMutationResult.Failure -> result.reason.toShopPurchaseFailure()
        }
    }

    private fun offer(item: ShopItemReference): ShopOffer? =
        when (item.category) {
            ShopCategory.AMMUNITION -> {
                val type = AmmunitionType.entries.firstOrNull { it.name == item.identifier }
                    ?: return null
                val definition = balance.ammunition.definition(type)
                val price = definition.shopCostGold ?: return null
                if (definition.unlimited) return null
                ShopOffer(
                    item = item,
                    priceGold = price,
                    stock = 0,
                    runtimeImplemented = true,
                )
            }
            ShopCategory.SPELLS -> {
                val type = SpellType.entries.firstOrNull { it.name == item.identifier }
                    ?: return null
                val definition = balance.spells.find(item.identifier) ?: return null
                if (!definition.special ||
                    type == SpellType.HASTE ||
                    type == SpellType.HEAL
                ) {
                    return null
                }
                ShopOffer(
                    item = item,
                    priceGold = definition.goldCost,
                    stock = 0,
                    runtimeImplemented = definition.isImplemented,
                )
            }
        }

    private fun itemIsKnown(item: ShopItemReference): Boolean =
        when (item.category) {
            ShopCategory.AMMUNITION ->
                AmmunitionType.entries.any { it.name == item.identifier }
            ShopCategory.SPELLS ->
                SpellType.entries.any { it.name == item.identifier }
        }

    private fun LoadoutProgressionSnapshot.toShopCatalog(): ShopCatalogSnapshot =
        ShopCatalogSnapshot(
            goldBalance = goldBalance,
            offers = buildList {
                balance.spells.all.forEach { spell ->
                    val reference = ShopItemReference(ShopCategory.SPELLS, spell.type.name)
                    val offer = offer(reference) ?: return@forEach
                    add(offer.copy(stock = specialSpellStock(spell.type.name)))
                }
                balance.ammunition.all.forEach { ammunition ->
                    val reference =
                        ShopItemReference(ShopCategory.AMMUNITION, ammunition.type.name)
                    val offer = offer(reference) ?: return@forEach
                    add(offer.copy(stock = ammunitionStock(ammunition.type.name)))
                }
            },
        )

    private fun LoadoutProgressionSnapshot.stock(item: ShopItemReference): Int =
        when (item.category) {
            ShopCategory.SPELLS -> specialSpellStock(item.identifier)
            ShopCategory.AMMUNITION -> ammunitionStock(item.identifier)
        }

    private fun LoadoutProgressionSnapshot.withPurchasedStock(
        item: ShopItemReference,
        priceGold: Int,
        stock: Int,
    ): LoadoutProgressionSnapshot =
        when (item.category) {
            ShopCategory.SPELLS -> copy(
                goldBalance = goldBalance - priceGold,
                specialSpellStocks = specialSpellStocks + (item.identifier to stock),
            )
            ShopCategory.AMMUNITION -> copy(
                goldBalance = goldBalance - priceGold,
                ammunitionStocks = ammunitionStocks + (item.identifier to stock),
            )
        }

    private fun PlayerProgressionRepositoryFailure.toShopReadFailure(): ShopReadFailure =
        when (this) {
            PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE ->
                ShopReadFailure.STORAGE_UNAVAILABLE
            PlayerProgressionRepositoryFailure.INVALID_PROFILE ->
                ShopReadFailure.INVALID_PROFILE
            PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION ->
                ShopReadFailure.UNSUPPORTED_PROFILE_VERSION
        }

    private fun PlayerProgressionRepositoryFailure.toShopPurchaseFailure(): ShopPurchaseResult =
        when (this) {
            PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE ->
                ShopPurchaseResult.StorageUnavailable
            PlayerProgressionRepositoryFailure.INVALID_PROFILE ->
                ShopPurchaseResult.InvalidProfile
            PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION ->
                ShopPurchaseResult.UnsupportedProfileVersion
        }
}
