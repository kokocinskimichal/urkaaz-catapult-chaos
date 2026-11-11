package com.urkaaaz.screens.dashboard

import androidx.activity.ComponentActivity
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageLoadSource
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.language.SetLanguage
import com.urkaaaz.application.progression.DashboardProgressRead
import com.urkaaaz.application.progression.DashboardProgressSummary
import com.urkaaaz.application.progression.LifeSnapshot
import com.urkaaaz.application.progression.PlayerProgressResetResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DashboardLanguageSelectorTest {
    @Test
    fun systemBackClosesTheLanguagePopup() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val screen = screen(activity)
        activity.setContentView(screen.view)

        screen.showLanguageIcons(screen.view)

        assertTrue(screen.onBackPressed())
        assertFalse(screen.onBackPressed())
        screen.onPause()
    }

    @Test
    fun selectingAnotherLanguageClosesThePopupAndRequestsRecreation() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        var savedLanguage = false
        val screen = screen(activity) { savedLanguage = true }
        activity.setContentView(screen.view)
        screen.onResume()
        screen.showLanguageIcons(screen.view)

        screen.selectLanguage(AppLanguage.PL)
        shadowOf(activity.mainLooper).idle()

        assertTrue(savedLanguage)
        assertFalse(screen.onBackPressed())
        screen.onPause()
    }

    private fun screen(
        activity: ComponentActivity,
        onLanguageSaved: () -> Unit = {},
    ) = DashboardScreen(
        context = activity,
        onBack = {},
        onCampaign = {},
        onShop = {},
        onDemoMode = {},
        onResetProgress = { PlayerProgressResetResult.Reset },
        loadProgress = {
            DashboardProgressRead.Available(
                DashboardProgressSummary(
                    currentLevel = 1,
                    gold = 0,
                    lifeSnapshot = LifeSnapshot(5, 5, true, null, null),
                ),
            )
        },
        initialLanguage = AppLanguage.EN,
        setLanguage = SetLanguage(object : LanguageSettings {
            override suspend fun load(): LanguageLoadResult =
                LanguageLoadResult.Loaded(AppLanguage.EN, LanguageLoadSource.STORED)

            override suspend fun save(language: AppLanguage): LanguageSaveResult =
                LanguageSaveResult.Saved(language)
        }),
        onLanguageSaved = onLanguageSaved,
    )
}
