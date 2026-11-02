package com.urkaaaz.screens.shop

import com.urkaaaz.application.shop.ShopCatalogResult
import com.urkaaaz.application.shop.ShopCatalogSnapshot
import com.urkaaaz.application.shop.ShopCategory
import com.urkaaaz.application.shop.ShopItemReference
import com.urkaaaz.application.shop.ShopOffer
import com.urkaaaz.application.shop.ShopPurchaseResult
import com.urkaaaz.application.shop.ShopReadFailure
import com.urkaaaz.application.shop.ShopUseCases

enum class ShopUiMessage {
    PURCHASED,
    INSUFFICIENT_ORCOINS,
}

sealed interface ShopUiError {
    data class Catalog(val reason: ShopReadFailure) : ShopUiError
    data object UNKNOWN_ITEM : ShopUiError
    data object NOT_FOR_SALE : ShopUiError
    data object INVALID_PROFILE : ShopUiError
    data object UNSUPPORTED_PROFILE_VERSION : ShopUiError
    data object STORAGE_UNAVAILABLE : ShopUiError
    data object STOCK_OVERFLOW : ShopUiError
    data object PURCHASE_SAVED_REFRESH_FAILED : ShopUiError
}

data class ShopUiState(
    val isLoading: Boolean = true,
    val isPurchasing: Boolean = false,
    val catalog: ShopCatalogSnapshot? = null,
    val category: ShopCategory = ShopCategory.SPELLS,
    val selectedItem: ShopItemReference? = null,
    val message: ShopUiMessage? = null,
    val error: ShopUiError? = null,
) {
    val offers: List<ShopOffer>
        get() = catalog?.offers(category).orEmpty()

    val selectedOffer: ShopOffer?
        get() = selectedItem?.let { catalog?.find(it) }
}

class ShopViewModel(
    private val useCases: ShopUseCases,
    initialItem: ShopItemReference? = null,
) {
    var state = ShopUiState(
        category = initialItem?.category ?: ShopCategory.SPELLS,
        selectedItem = initialItem,
    )
        private set

    suspend fun load() {
        state = state.copy(
            isLoading = true,
            isPurchasing = false,
            catalog = null,
            message = null,
            error = null,
        )
        when (val result = useCases.catalog()) {
            is ShopCatalogResult.Available -> {
                val offer = state.selectedItem?.let(result.snapshot::find)
                    ?: result.snapshot.offers(state.category).firstOrNull()
                    ?: result.snapshot.offers(ShopCategory.SPELLS).firstOrNull()
                    ?: result.snapshot.offers(ShopCategory.AMMUNITION).firstOrNull()
                state = state.copy(
                    isLoading = false,
                    catalog = result.snapshot,
                    category = offer?.item?.category ?: state.category,
                    selectedItem = offer?.item,
                )
            }
            is ShopCatalogResult.Failure -> state = state.copy(
                isLoading = false,
                error = ShopUiError.Catalog(result.reason),
            )
        }
    }

    fun selectCategory(category: ShopCategory) {
        val offers = state.catalog?.offers(category).orEmpty()
        val selected = state.selectedItem
            ?.takeIf { it.category == category && state.catalog?.find(it) != null }
            ?: offers.firstOrNull()?.item
        state = state.copy(
            category = category,
            selectedItem = selected,
            message = null,
            error = null,
        )
    }

    fun selectItem(item: ShopItemReference) {
        if (state.catalog?.find(item) == null) return
        state = state.copy(
            category = item.category,
            selectedItem = item,
            message = null,
            error = null,
        )
    }

    suspend fun purchaseSelected(): Boolean {
        val item = state.selectedItem ?: return false
        if (state.isLoading || state.isPurchasing) return false
        state = state.copy(isPurchasing = true, message = null, error = null)
        return when (val result = useCases.purchase(item)) {
            is ShopPurchaseResult.Purchased -> {
                when (val refreshed = useCases.catalog()) {
                    is ShopCatalogResult.Available -> {
                        state = state.copy(
                            isPurchasing = false,
                            catalog = refreshed.snapshot,
                            selectedItem = item,
                            message = ShopUiMessage.PURCHASED,
                        )
                    }
                    is ShopCatalogResult.Failure -> {
                        state = state.copy(
                            isPurchasing = false,
                            catalog = null,
                            message = ShopUiMessage.PURCHASED,
                            error = ShopUiError.PURCHASE_SAVED_REFRESH_FAILED,
                        )
                    }
                }
                true
            }
            ShopPurchaseResult.InsufficientOrCoins -> {
                state = state.copy(
                    isPurchasing = false,
                    message = ShopUiMessage.INSUFFICIENT_ORCOINS,
                )
                false
            }
            ShopPurchaseResult.UnknownItem -> reject(ShopUiError.UNKNOWN_ITEM)
            ShopPurchaseResult.NotForSale -> reject(ShopUiError.NOT_FOR_SALE)
            ShopPurchaseResult.InvalidProfile -> reject(ShopUiError.INVALID_PROFILE)
            ShopPurchaseResult.UnsupportedProfileVersion ->
                reject(ShopUiError.UNSUPPORTED_PROFILE_VERSION)
            ShopPurchaseResult.StorageUnavailable -> reject(ShopUiError.STORAGE_UNAVAILABLE)
            ShopPurchaseResult.StockOverflow -> reject(ShopUiError.STOCK_OVERFLOW)
        }
    }

    private fun reject(error: ShopUiError): Boolean {
        state = state.copy(isPurchasing = false, error = error)
        return false
    }
}
