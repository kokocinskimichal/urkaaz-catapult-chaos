package com.urkaaaz.screens.dashboard

data class DashboardUiState(
    val isLoading: Boolean = false,
    val currentLevel: Int = 1,
    val lives: Int = 5,
    val gold: Int = 0,
)
