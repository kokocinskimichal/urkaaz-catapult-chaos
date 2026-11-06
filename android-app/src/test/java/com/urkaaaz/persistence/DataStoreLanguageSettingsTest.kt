package com.urkaaaz.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageLoadSource
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.LanguageSettingsFailure
import com.urkaaaz.application.progression.PlayerProgressResetResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreLanguageSettingsTest {
    @Test
    fun missingSettingDefaultsToEnglishAndLanguageChangesPersist() = runBlocking {
        testStore().use { store ->
            val settings = DataStoreLanguageSettings(store.dataStore)

            assertEquals(
                LanguageLoadResult.Loaded(
                    AppLanguage.EN,
                    LanguageLoadSource.DEFAULT_NO_STORED_VALUE,
                ),
                settings.load(),
            )
            assertEquals(
                LanguageSaveResult.Unchanged(AppLanguage.EN),
                settings.save(AppLanguage.EN),
            )
            assertTrue(store.dataStore.data.first().asMap().isEmpty())
            assertEquals(
                LanguageSaveResult.Saved(AppLanguage.PL),
                settings.save(AppLanguage.PL),
            )
            assertEquals(
                LanguageLoadResult.Loaded(AppLanguage.PL, LanguageLoadSource.STORED),
                settings.load(),
            )
            assertEquals(
                LanguageSaveResult.Unchanged(AppLanguage.PL),
                settings.save(AppLanguage.PL),
            )
            assertEquals(
                LanguageSaveResult.Saved(AppLanguage.ES),
                settings.save(AppLanguage.ES),
            )
            assertEquals(
                LanguageLoadResult.Loaded(AppLanguage.ES, LanguageLoadSource.STORED),
                settings.load(),
            )
        }
    }

    @Test
    fun unsupportedStoredValuesAreReportedAndNeverOverwritten() = runBlocking {
        testStore().use { store ->
            store.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("language.code")] = "fr"
                preferences[intPreferencesKey("language.schema_version")] = 1
            }
            val settings = DataStoreLanguageSettings(store.dataStore)
            val before = store.dataStore.data.first()

            assertEquals(
                LanguageLoadResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE),
                settings.load(),
            )
            assertEquals(
                LanguageSaveResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE),
                settings.save(AppLanguage.ES),
            )
            assertEquals(before, store.dataStore.data.first())
        }
    }

    @Test
    fun newerSchemaIsReportedAndNeverOverwritten() = runBlocking {
        testStore().use { store ->
            store.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("language.code")] = "pl"
                preferences[intPreferencesKey("language.schema_version")] = 2
            }
            val settings = DataStoreLanguageSettings(store.dataStore)
            val before = store.dataStore.data.first()

            assertEquals(
                LanguageLoadResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE),
                settings.load(),
            )
            assertEquals(
                LanguageSaveResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE),
                settings.save(AppLanguage.ES),
            )
            assertEquals(before, store.dataStore.data.first())
        }
    }

    @Test
    fun storageErrorsAreReturnedExplicitly() = runBlocking {
        val settings = DataStoreLanguageSettings(FailingDataStore(IOException("unavailable")))

        assertEquals(
            LanguageLoadResult.Failure(LanguageSettingsFailure.STORAGE_UNAVAILABLE),
            settings.load(),
        )
        assertEquals(
            LanguageSaveResult.Failure(LanguageSettingsFailure.STORAGE_UNAVAILABLE),
            settings.save(AppLanguage.PL),
        )
    }

    @Test
    fun resettingPlayerProgressDoesNotResetLanguageSettings() = runBlocking {
        testStore().use { profileStore ->
            testStore().use { languageStore ->
                val languageSettings = DataStoreLanguageSettings(languageStore.dataStore)
                assertEquals(
                    LanguageSaveResult.Saved(AppLanguage.PL),
                    languageSettings.save(AppLanguage.PL),
                )

                assertEquals(
                    PlayerProgressResetResult.Reset,
                    DataStorePlayerProgressResetRepository(profileStore.dataStore).reset(),
                )
                assertEquals(
                    LanguageLoadResult.Loaded(
                        AppLanguage.PL,
                        LanguageLoadSource.STORED,
                    ),
                    languageSettings.load(),
                )
            }
        }
    }

    private fun testStore(): TestStore {
        val file = File.createTempFile("urkaaz-language-", ".preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return TestStore(
            file = file,
            scope = scope,
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            ),
        )
    }

    private class TestStore(
        private val file: File,
        private val scope: CoroutineScope,
        val dataStore: DataStore<Preferences>,
    ) : AutoCloseable {
        override fun close() {
            scope.cancel()
            file.delete()
        }
    }

    private class FailingDataStore(
        private val failure: IOException,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw failure
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw failure
    }
}
