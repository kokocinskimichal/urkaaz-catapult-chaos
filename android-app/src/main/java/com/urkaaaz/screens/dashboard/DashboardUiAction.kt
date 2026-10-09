package com.urkaaaz.screens.dashboard

sealed interface DashboardUiAction {
    data object OpenCampaign : DashboardUiAction
    data object OpenShop : DashboardUiAction
    data object OpenLoadout : DashboardUiAction
    data object StartMatch : DashboardUiAction
}
