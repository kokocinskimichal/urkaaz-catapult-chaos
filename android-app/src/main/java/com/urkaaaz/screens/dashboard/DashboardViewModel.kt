package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.progression.DeveloperProgressSummary

class DashboardViewModel {
    var state: DashboardUiState = DashboardUiState()
        private set

    fun beginProgressLoad(): DashboardUiState {
        state = DashboardUiState(isLoading = true)
        return state
    }

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
        state = DashboardUiState(
            progressSummary = summary,
            isLoading = false,
        )
        return state
    }

    fun failProgressLoad(): DashboardUiState {
        state = DashboardUiState(
            isLoading = false,
            hasProgressError = true,
        )
        return state
    }
}
