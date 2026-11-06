package com.urkaaaz.application.language

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SetLanguageTest {
    @Test
    fun languageCodesAreStableAndUnknownCodesAreRejected() {
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.PL, AppLanguage.fromCode("pl"))
        assertEquals(AppLanguage.ES, AppLanguage.fromCode("es"))
        assertEquals(null, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.EN, AppLanguage.DEFAULT)
    }

    @Test
    fun useCaseReturnsTheRepositoryResult() = runBlocking {
        val expected = LanguageSaveResult.Saved(AppLanguage.PL)
        val useCase = SetLanguage(object : LanguageSettings {
            override suspend fun load(): LanguageLoadResult =
                LanguageLoadResult.Loaded(AppLanguage.EN, LanguageLoadSource.DEFAULT_NO_STORED_VALUE)

            override suspend fun save(language: AppLanguage): LanguageSaveResult {
                assertEquals(AppLanguage.PL, language)
                return expected
            }
        })

        assertEquals(expected, useCase(AppLanguage.PL))
    }

    @Test
    fun sameLanguageDoesNotCallSave() = runBlocking {
        val useCase = SetLanguage(object : LanguageSettings {
            override suspend fun load(): LanguageLoadResult =
                LanguageLoadResult.Loaded(AppLanguage.PL, LanguageLoadSource.STORED)

            override suspend fun save(language: AppLanguage): LanguageSaveResult {
                throw AssertionError("same language must not be written")
            }
        })

        assertEquals(LanguageSaveResult.Unchanged(AppLanguage.PL), useCase(AppLanguage.PL))
    }

    @Test
    fun loadFailureIsReturnedWithoutAttemptingToSave() = runBlocking {
        val useCase = SetLanguage(object : LanguageSettings {
            override suspend fun load(): LanguageLoadResult =
                LanguageLoadResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE)

            override suspend fun save(language: AppLanguage): LanguageSaveResult {
                throw AssertionError("unsupported settings must not be overwritten")
            }
        })

        assertEquals(
            LanguageSaveResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE),
            useCase(AppLanguage.ES),
        )
    }
}
