package com.urkaaaz.screens.dashboard

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
}
