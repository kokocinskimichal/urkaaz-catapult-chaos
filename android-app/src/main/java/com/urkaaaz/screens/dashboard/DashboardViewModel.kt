package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.SetLanguage
import com.urkaaaz.application.progression.DashboardProgressSummary

class DashboardViewModel(
    initialLanguage: AppLanguage,
    private val setLanguage: SetLanguage,
) {
    var state: DashboardUiState = DashboardUiState(selectedLanguage = initialLanguage)
        private set

    fun beginProgressLoad(): DashboardUiState {
        state = state.copy(
            progressSummary = null,
            isLoading = true,
            hasProgressError = false,
        )
        return state
    }

    fun dispatch(action: DashboardUiAction): DashboardUiState {
        state = when (action) {
            is DashboardUiAction.SelectLanguage -> state.copy(
                isLanguageSaving = true,
                hasLanguageError = false,
            )
            DashboardUiAction.OpenCampaign,
            DashboardUiAction.OpenShop,
            DashboardUiAction.OpenLoadout,
            DashboardUiAction.StartMatch -> state
        }
        return state
    }

    fun updateProgress(summary: DashboardProgressSummary): DashboardUiState {
        state = state.copy(
            progressSummary = summary,
            isLoading = false,
            hasProgressError = false,
        )
        return state
    }

    fun elapseLifeCountdown(milliseconds: Long): DashboardUiState {
        val summary = state.progressSummary ?: return state
        val life = summary.lifeSnapshot
        state = state.copy(
            progressSummary = summary.copy(
                lifeSnapshot = life.copy(
                    regenerationTimeRemainingMilliseconds =
                        life.regenerationTimeRemainingMilliseconds?.minus(milliseconds)?.coerceAtLeast(0L),
                    unlimitedTimeRemainingMilliseconds =
                        life.unlimitedTimeRemainingMilliseconds?.minus(milliseconds)?.coerceAtLeast(0L),
                ),
            ),
        )
        return state
    }

    fun failProgressLoad(): DashboardUiState {
        state = state.copy(
            progressSummary = null,
            isLoading = false,
            hasProgressError = true,
        )
        return state
    }

    suspend fun selectLanguage(language: AppLanguage): LanguageSaveResult {
        dispatch(DashboardUiAction.SelectLanguage(language))
        val result = setLanguage(language)
        completeLanguageSave(result)
        return result
    }

    private fun completeLanguageSave(result: LanguageSaveResult): DashboardUiState {
        state = when (result) {
            is LanguageSaveResult.Saved -> state.copy(
                selectedLanguage = result.language,
                isLanguageSaving = false,
                hasLanguageError = false,
            )
            is LanguageSaveResult.Unchanged -> state.copy(
                selectedLanguage = result.language,
                isLanguageSaving = false,
                hasLanguageError = false,
            )
            is LanguageSaveResult.Failure -> state.copy(
                isLanguageSaving = false,
                hasLanguageError = true,
            )
        }
        return state
    }
}
