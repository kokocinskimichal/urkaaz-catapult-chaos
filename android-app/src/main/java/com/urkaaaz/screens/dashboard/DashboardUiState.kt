package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.progression.DeveloperProgressSummary

data class DashboardUiState(
    val progressSummary: DeveloperProgressSummary? = null,
    val isLoading: Boolean = true,
    val hasProgressError: Boolean = false,
)
