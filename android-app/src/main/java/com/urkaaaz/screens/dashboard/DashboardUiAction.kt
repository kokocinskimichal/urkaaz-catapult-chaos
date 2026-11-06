package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.language.AppLanguage

sealed interface DashboardUiAction {
    data object OpenCampaign : DashboardUiAction
    data object OpenShop : DashboardUiAction
    data object OpenLoadout : DashboardUiAction
    data object StartMatch : DashboardUiAction
    data class SelectLanguage(val language: AppLanguage) : DashboardUiAction
}
