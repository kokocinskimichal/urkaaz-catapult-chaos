package com.urkaaaz.navigation

import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.application.shop.ShopItemReference

sealed interface AppScreen {
    data object Splash : AppScreen
    data object Dashboard : AppScreen
    data object Campaign : AppScreen
    data class Loadout(
        val campaignLevel: Int,
        val demoMode: Boolean = false,
        val focusItem: ShopItemReference? = null,
    ) : AppScreen
    data class Shop(
        val returnToLoadout: ShopLoadoutReturn? = null,
    ) : AppScreen
    data class Match(
        val levelId: String,
        val campaignLevel: Int = 1,
        val matchId: MatchId,
        val playerLoadout: PlayerLoadoutConfig? = null,
        val playerInventory: PlayerInventoryConfig? = null,
    ) : AppScreen
    data class Result(val matchId: String) : AppScreen
}

data class ShopLoadoutReturn(
    val campaignLevel: Int,
    val demoMode: Boolean,
    val item: ShopItemReference,
)
