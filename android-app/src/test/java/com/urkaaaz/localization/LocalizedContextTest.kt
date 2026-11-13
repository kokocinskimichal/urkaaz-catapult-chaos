package com.urkaaaz.localization

import com.urkaaaz.android.R
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.language.LanguageSettingsFailure
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.screens.campaign.CampaignChapterTitleResources
import com.urkaaaz.ui.ButtonTypographyControl
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

        assertEquals(
            "Chapter 2 of 10",
            english.getString(R.string.campaign_chapter_page_accessibility, 2, 10),
        )
        assertEquals(
            "Rozdział 2 z 10",
            polish.getString(R.string.campaign_chapter_page_accessibility, 2, 10),
        )
        assertEquals(
            "Capítulo 2 de 10",
            spanish.getString(R.string.campaign_chapter_page_accessibility, 2, 10),
        )
        assertEquals("Previous chapter", english.getString(R.string.campaign_previous_chapter))
        assertEquals("Poprzedni rozdział", polish.getString(R.string.campaign_previous_chapter))
        assertEquals("Capítulo anterior", spanish.getString(R.string.campaign_previous_chapter))
        assertEquals("1.5 s", english.getString(R.string.match_hud_reload_seconds, 1.5f))
        assertEquals("1,5 s", polish.getString(R.string.match_hud_reload_seconds, 1.5f))
        assertEquals("1,5 s", spanish.getString(R.string.match_hud_reload_seconds, 1.5f))
    }

    @Test
    fun localizedContextResolvesButtonTypographyForEachLanguage() {
        val base = RuntimeEnvironment.getApplication()
        val expectedSizes = mapOf(
            AppLanguage.EN to 14f,
            AppLanguage.PL to 11f,
            AppLanguage.ES to 9f,
        )

        expectedSizes.forEach { (language, expectedSp) ->
            val localized = LocalizedContext(base, language)
            val actualSp = localized.resources
                .getDimension(ButtonTypographyControl.DASHBOARD_TEST_MODE.dimensionResource) /
                localized.resources.displayMetrics.scaledDensity

            assertEquals(expectedSp, actualSp, 0.01f)
        }
    }

    @Test
    fun everyInteractiveTypographyControlHasLocalizedStringsAndDimensions() {
        val base = RuntimeEnvironment.getApplication()
        val languages = listOf(AppLanguage.EN, AppLanguage.PL, AppLanguage.ES)

        ButtonTypographyControl.values().forEach { control ->
            assertEquals(
                "button_text_size_${control.controlId}",
                base.resources.getResourceEntryName(control.dimensionResource),
            )
            assertEquals(
                control.stringKey,
                base.resources.getResourceEntryName(control.stringResource),
            )
            languages.forEach { language ->
                val localized = LocalizedContext(base, language)
                val label = localized.getString(control.stringResource)
                val sizeSp = localized.resources.getDimension(control.dimensionResource) /
                    localized.resources.displayMetrics.scaledDensity

                assertTrue("${control.controlId} has an empty $language label", label.isNotBlank())
                assertTrue(
                    "${control.controlId} has invalid $language size: $sizeSp",
                    sizeSp in 8f..32f,
                )
            }
        }
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
