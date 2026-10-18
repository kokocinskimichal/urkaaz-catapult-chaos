package com.urkaaaz.navigation

import com.urkaaaz.contracts.PlayerLoadoutConfig

sealed interface AppScreen {
    data object Start : AppScreen
    data object Dashboard : AppScreen
    data object Campaign : AppScreen
    data class Loadout(val campaignLevel: Int) : AppScreen
    data object Shop : AppScreen
    data class Match(
        val levelId: String,
        val campaignLevel: Int = 1,
        val playerLoadout: PlayerLoadoutConfig? = null,
    ) : AppScreen
    data class Result(val matchId: String) : AppScreen
}
