package com.urkaaaz.localization

import com.urkaaaz.android.R
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.language.LanguageSettingsFailure
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.screens.campaign.CampaignChapterTitleResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalizedContextTest {
    @Test
    fun localizedContextResolvesStringsAndDecimalNumbersForEachLanguage() {
        val base = RuntimeEnvironment.getApplication()
        val english = LocalizedContext(base, AppLanguage.EN)
        val polish = LocalizedContext(base, AppLanguage.PL)
        val spanish = LocalizedContext(base, AppLanguage.ES)

        assertEquals("LEVEL 3", english.getString(R.string.campaign_level, 3))
        assertEquals("POZIOM 3", polish.getString(R.string.campaign_level, 3))
        assertEquals("NIVEL 3", spanish.getString(R.string.campaign_level, 3))
        assertEquals("1.5 s", english.getString(R.string.match_hud_reload_seconds, 1.5f))
        assertEquals("1,5 s", polish.getString(R.string.match_hud_reload_seconds, 1.5f))
        assertEquals("1,5 s", spanish.getString(R.string.match_hud_reload_seconds, 1.5f))
    }

    @Test
    fun everyRegisteredChapterHasLocalizedTitles() {
        val base = RuntimeEnvironment.getApplication()
        val english = LocalizedContext(base, AppLanguage.EN)
        val polish = LocalizedContext(base, AppLanguage.PL)
        val spanish = LocalizedContext(base, AppLanguage.ES)

        CampaignChapterRegistry.default().chapters.forEach { chapter ->
            val resource = CampaignChapterTitleResources.forChapter(chapter.id)
            val englishTitle = english.getString(resource)
            val polishTitle = polish.getString(resource)
            val spanishTitle = spanish.getString(resource)

            assertTrue(englishTitle.isNotBlank())
            assertTrue(polishTitle.isNotBlank())
            assertTrue(spanishTitle.isNotBlank())
            assertNotEquals(englishTitle, polishTitle)
            assertNotEquals(englishTitle, spanishTitle)
        }
    }

    @Test
    fun startupLanguageReaderFallsBackToEnglishWithoutWritingOnFailure() {
        var loadCount = 0
        val settings = object : LanguageSettings {
            override suspend fun load(): LanguageLoadResult {
                loadCount += 1
                return LanguageLoadResult.Failure(
                    LanguageSettingsFailure.STORAGE_UNAVAILABLE,
                )
            }

            override suspend fun save(language: AppLanguage): LanguageSaveResult {
                throw AssertionError("startup fallback must not overwrite stored language")
            }
        }

        assertEquals(AppLanguage.EN, StartupLanguageReader(settings).read())
        assertEquals(1, loadCount)
    }
}
