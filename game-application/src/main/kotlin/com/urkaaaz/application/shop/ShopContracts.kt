package com.urkaaaz.application.shop

import com.urkaaaz.contracts.MatchId
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot

enum class ShopCategory {
    SPELLS,
    AMMUNITION,
}

data class ShopItemReference(
    val category: ShopCategory,
    val identifier: String,
) {
    init {
        require(identifier.isNotBlank()) { "shop item identifier must not be blank" }
    }
}

data class ShopOffer(
    val item: ShopItemReference,
    val priceGold: Int,
    val stock: Int,
    val runtimeImplemented: Boolean,
) {
    init {
        require(priceGold >= 0) { "shop price must not be negative" }
        require(stock >= 0) { "shop stock must not be negative" }
    }
}

data class ShopCatalogSnapshot(
    val goldBalance: Int,
    val offers: List<ShopOffer>,
) {
    init {
        require(goldBalance >= 0) { "gold balance must not be negative" }
        require(offers.map(ShopOffer::item).distinct().size == offers.size) {
            "shop offers must be unique"
        }
    }

    fun offers(category: ShopCategory): List<ShopOffer> =
        offers.filter { it.item.category == category }

    fun find(item: ShopItemReference): ShopOffer? =
        offers.firstOrNull { it.item == item }
}

enum class ShopReadFailure {
    STORAGE_UNAVAILABLE,
    INVALID_PROFILE,
    UNSUPPORTED_PROFILE_VERSION,
}

sealed interface ShopCatalogResult {
    data class Available(val snapshot: ShopCatalogSnapshot) : ShopCatalogResult
    data class Failure(val reason: ShopReadFailure) : ShopCatalogResult
}

sealed interface ShopPurchaseResult {
    data class Purchased(val profile: LoadoutProgressionSnapshot) : ShopPurchaseResult
    data object UnknownItem : ShopPurchaseResult
    data object NotForSale : ShopPurchaseResult
    data object InsufficientOrCoins : ShopPurchaseResult
    data object StockOverflow : ShopPurchaseResult
    data object InvalidProfile : ShopPurchaseResult
    data object UnsupportedProfileVersion : ShopPurchaseResult
    data object StorageUnavailable : ShopPurchaseResult
}

data class MatchStockSettlementId(
    val matchId: MatchId,
    val generation: Long,
) {
    init {
        require(generation > 0) { "match generation must be positive" }
    }

    fun storageValue(): String = "$generation:${matchId.value}"
}

data class MatchStockUsage(
    val ammunitionUsed: Map<String, Long>,
    val specialSpellsUsed: Map<String, Long>,
) {
    init {
        require(ammunitionUsed.keys.all(String::isNotBlank)) {
            "ammunition identifiers must not be blank"
        }
        require(specialSpellsUsed.keys.all(String::isNotBlank)) {
            "spell identifiers must not be blank"
        }
        require(ammunitionUsed.values.all { it > 0 }) {
            "ammunition usage counts must be positive"
        }
        require(specialSpellsUsed.values.all { it > 0 }) {
            "spell usage counts must be positive"
        }
    }
}

enum class MatchStockSettlementFailure {
    STORAGE_UNAVAILABLE,
    INVALID_PROFILE,
    UNSUPPORTED_PROFILE_VERSION,
}

enum class MatchStockSettlementRejection {
    INVALID_USAGE,
    STOCK_CONFLICT,
}

sealed interface MatchStockSettlementResult {
    data class Settled(
        val profile: LoadoutProgressionSnapshot,
    ) : MatchStockSettlementResult

    data class AlreadySettled(
        val profile: LoadoutProgressionSnapshot,
    ) : MatchStockSettlementResult

    data class Rejected(
        val reason: MatchStockSettlementRejection,
    ) : MatchStockSettlementResult

    data class Failure(
        val reason: MatchStockSettlementFailure,
    ) : MatchStockSettlementResult
}

fun interface MatchStockSettlementPort {
    suspend fun settle(
        id: MatchStockSettlementId,
        usage: MatchStockUsage,
    ): MatchStockSettlementResult
}
