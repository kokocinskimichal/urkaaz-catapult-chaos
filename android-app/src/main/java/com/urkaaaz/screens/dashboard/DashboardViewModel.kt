package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.progression.DeveloperProgressSummary

class DashboardViewModel {
    var state: DashboardUiState = DashboardUiState()
        private set

    fun dispatch(action: DashboardUiAction): DashboardUiState {
        state = when (action) {
            DashboardUiAction.OpenCampaign,
            DashboardUiAction.OpenShop,
            DashboardUiAction.OpenLoadout,
            DashboardUiAction.StartMatch -> state
        }
        return state
    }

    fun updateProgress(summary: DeveloperProgressSummary): DashboardUiState {
        state = state.copy(
            currentLevel = summary.currentLevel,
            lives = summary.lives,
            gold = summary.gold,
        )
        return state
    }
}
