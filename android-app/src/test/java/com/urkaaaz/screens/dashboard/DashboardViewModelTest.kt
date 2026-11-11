package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageLoadSource
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.language.LanguageSettingsFailure
import com.urkaaaz.application.language.SetLanguage
import com.urkaaaz.application.progression.DashboardProgressSummary
import com.urkaaaz.application.progression.LifeSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {
    @Test
    fun initialStateDoesNotPresentExampleValuesAsLoadedProgress() {
        val viewModel = viewModel()

        assertTrue(viewModel.state.isLoading)
        assertNull(viewModel.state.progressSummary)
        assertFalse(viewModel.state.hasProgressError)
        assertEquals(AppLanguage.DEFAULT, viewModel.state.selectedLanguage)
    }

    @Test
    fun successfulProgressLoadPublishesTheSummary() {
        val viewModel = viewModel()
        val summary = DashboardProgressSummary(
            currentLevel = 4,
            gold = 120,
            lifeSnapshot = LifeSnapshot(3, 5, true, 600_000L, null),
        )

        val loaded = viewModel.updateProgress(summary)

        assertEquals(summary, loaded.progressSummary)
        assertFalse(loaded.isLoading)
        assertFalse(loaded.hasProgressError)
    }

    @Test
    fun failedProgressLoadDoesNotRetainStaleSummary() {
        val viewModel = viewModel()
        viewModel.updateProgress(
            DashboardProgressSummary(
                currentLevel = 4,
                gold = 120,
                lifeSnapshot = LifeSnapshot(3, 5, true, 600_000L, null),
            ),
        )

        val failed = viewModel.failProgressLoad()

        assertNull(failed.progressSummary)
        assertFalse(failed.isLoading)
        assertTrue(failed.hasProgressError)
    }

    @Test
    fun languageSelectionUpdatesAfterPersistenceSucceeds() = runBlocking {
        val viewModel = viewModel(
            initialLanguage = AppLanguage.EN,
            saveResult = LanguageSaveResult.Saved(AppLanguage.PL),
        )

        assertEquals(
            LanguageSaveResult.Saved(AppLanguage.PL),
            viewModel.selectLanguage(AppLanguage.PL),
        )

        assertEquals(AppLanguage.PL, viewModel.state.selectedLanguage)
        assertFalse(viewModel.state.isLanguageSaving)
        assertFalse(viewModel.state.hasLanguageError)
    }

    @Test
    fun failedLanguageSaveKeepsTheAppliedLanguageAndReportsAnError() = runBlocking {
        val viewModel = viewModel(
            initialLanguage = AppLanguage.ES,
            saveResult = LanguageSaveResult.Failure(
                LanguageSettingsFailure.STORAGE_UNAVAILABLE,
            ),
        )

        assertEquals(
            LanguageSaveResult.Failure(LanguageSettingsFailure.STORAGE_UNAVAILABLE),
            viewModel.selectLanguage(AppLanguage.PL),
        )
        assertEquals(AppLanguage.ES, viewModel.state.selectedLanguage)
        assertFalse(viewModel.state.isLanguageSaving)
        assertTrue(viewModel.state.hasLanguageError)
    }

    @Test
    fun unchangedLanguageDoesNotSaveOrReportAnError() = runBlocking {
        var saveCount = 0
        val viewModel = DashboardViewModel(
            initialLanguage = AppLanguage.PL,
            setLanguage = SetLanguage(object : LanguageSettings {
                override suspend fun load(): LanguageLoadResult =
                    LanguageLoadResult.Loaded(AppLanguage.PL, LanguageLoadSource.STORED)

                override suspend fun save(language: AppLanguage): LanguageSaveResult {
                    saveCount += 1
                    return LanguageSaveResult.Saved(language)
                }
            }),
        )

        assertEquals(
            LanguageSaveResult.Unchanged(AppLanguage.PL),
            viewModel.selectLanguage(AppLanguage.PL),
        )
        assertEquals(0, saveCount)
        assertFalse(viewModel.state.isLanguageSaving)
        assertFalse(viewModel.state.hasLanguageError)
    }

    private fun viewModel(
        initialLanguage: AppLanguage = AppLanguage.EN,
        saveResult: LanguageSaveResult = LanguageSaveResult.Saved(initialLanguage),
    ): DashboardViewModel =
        DashboardViewModel(
            initialLanguage = initialLanguage,
            setLanguage = SetLanguage(object : LanguageSettings {
                override suspend fun load(): LanguageLoadResult =
                    LanguageLoadResult.Loaded(initialLanguage, LanguageLoadSource.STORED)

                override suspend fun save(language: AppLanguage): LanguageSaveResult {
                    saveResult.languageOrNull()?.let { assertEquals(it, language) }
                    return saveResult
                }
            }),
        )

    private fun LanguageSaveResult.languageOrNull(): AppLanguage? =
        when (this) {
            is LanguageSaveResult.Saved -> language
            is LanguageSaveResult.Unchanged -> language
            is LanguageSaveResult.Failure -> null
        }
}
