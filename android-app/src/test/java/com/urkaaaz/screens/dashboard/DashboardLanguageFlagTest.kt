package com.urkaaaz.screens.dashboard

import com.urkaaaz.application.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLanguageFlagTest {
    @Test
    fun flagsMapToSupportedLanguagesInSelectorOrder() {
        assertEquals(
            listOf(AppLanguage.PL, AppLanguage.EN, AppLanguage.ES),
            DashboardLanguageFlag.entries.map(DashboardLanguageFlag::language),
        )
    }

    @Test
    fun onlyTheMatchingLanguageFlagIsActive() {
        assertEquals(
            listOf(false, true, false),
            DashboardLanguageFlag.entries.map { it.isActive(AppLanguage.EN) },
        )
    }
}
