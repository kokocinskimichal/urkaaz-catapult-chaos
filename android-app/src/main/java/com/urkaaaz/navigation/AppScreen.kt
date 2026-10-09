package com.urkaaaz.navigation

sealed interface AppScreen {
    data object Start : AppScreen
    data object Dashboard : AppScreen
    data object Campaign : AppScreen
    data object Loadout : AppScreen
    data object Shop : AppScreen
    data class Match(val levelId: String) : AppScreen
    data class Result(val matchId: String) : AppScreen
}
