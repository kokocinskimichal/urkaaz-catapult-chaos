package com.urkaaaz.tutorial

import android.view.View
import android.widget.FrameLayout
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageLoadSource
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.tutorial.TutorialCatalogFailure
import com.urkaaaz.application.tutorial.TutorialCatalogRead
import com.urkaaaz.application.tutorial.TutorialCatalogRepository
import com.urkaaaz.application.tutorial.TutorialProgressFailure
import com.urkaaaz.application.tutorial.TutorialProgressRead
import com.urkaaaz.application.tutorial.TutorialProgressRepository
import com.urkaaaz.application.tutorial.TutorialProgressWrite
import com.urkaaaz.application.tutorial.TutorialScreen
import com.urkaaaz.application.tutorial.TutorialStatus
import com.urkaaaz.navigation.ScreenHost
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TutorialHostDemoIsolationTest {
    @Test
    fun demoScreenDoesNotReadCatalogLanguageOrTutorialProgress() {
        var catalogReads = 0
        var progressReads = 0
        var progressWrites = 0
        val context = RuntimeEnvironment.getApplication()
        val repository = object : TutorialProgressRepository {
            override suspend fun load(validTutorialIds: Set<String>): TutorialProgressRead {
                progressReads += 1
                return TutorialProgressRead.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE)
            }

            override suspend fun markTerminal(
                tutorialId: String,
                status: TutorialStatus,
                schemaVersion: Int,
                catalogRevision: String,
            ): TutorialProgressWrite {
                progressWrites += 1
                return TutorialProgressWrite.Saved
            }

            override suspend fun reset(tutorialId: String?): TutorialProgressWrite {
                progressWrites += 1
                return TutorialProgressWrite.Saved
            }
        }
        val host = TutorialHost(
            context = context,
            screenHost = ScreenHost(context),
            catalogRepository = TutorialCatalogRepository {
                catalogReads += 1
                TutorialCatalogRead.Failure(TutorialCatalogFailure.RESOURCE_UNAVAILABLE)
            },
            progressRepository = repository,
            languageSettings = object : LanguageSettings {
                override suspend fun load(): LanguageLoadResult {
                    error("Demo must not read language settings for tutorial context")
                }

                override suspend fun save(language: AppLanguage): LanguageSaveResult =
                    error("TutorialHost must not save language settings")
            },
        )
        host.show(DemoMatchScreen(context))
        host.resumeActiveScreen()
        host.onAcceptedPlayerAction(
            kind = com.urkaaaz.application.tutorial.TutorialPlayerActionKind.FIRE,
            isDemo = true,
            matchId = "demo-match",
            matchGeneration = 1L,
        )

        assertEquals(0, catalogReads)
        assertEquals(0, progressReads)
        assertEquals(0, progressWrites)
        host.destroy()
    }

    private class DemoMatchScreen(context: android.content.Context) : TutorialScreenContent {
        override val view: View = FrameLayout(context)
        override val tutorialScreen: TutorialScreen = TutorialScreen.MATCH
        override val isTutorialDemoMode: Boolean = true

        override fun tutorialTargetBounds(targetId: String, targetInstanceId: String?) = null
    }
}
