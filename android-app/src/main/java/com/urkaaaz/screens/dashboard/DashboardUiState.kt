package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.progression.DashboardProgressSummary

data class DashboardUiState(
    val progressSummary: DashboardProgressSummary? = null,
    val isLoading: Boolean = true,
    val hasProgressError: Boolean = false,
    val selectedLanguage: AppLanguage = AppLanguage.DEFAULT,
    val isLanguageSaving: Boolean = false,
    val hasLanguageError: Boolean = false,
)
