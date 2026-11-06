package com.urkaaaz.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageLoadSource
import com.urkaaaz.application.language.LanguageSaveResult
import com.urkaaaz.application.language.LanguageSettings
import com.urkaaaz.application.language.LanguageSettingsFailure
import java.io.IOException
import kotlinx.coroutines.flow.first

internal val Context.urkaazLanguageDataStore by preferencesDataStore(
    name = "language_settings",
)

class DataStoreLanguageSettings(
    private val dataStore: DataStore<Preferences>,
) : LanguageSettings {
    override suspend fun load(): LanguageLoadResult =
        try {
            decode(dataStore.data.first())
        } catch (_: IOException) {
            LanguageLoadResult.Failure(LanguageSettingsFailure.STORAGE_UNAVAILABLE)
        }

    override suspend fun save(language: AppLanguage): LanguageSaveResult {
        var result: LanguageSaveResult? = null
        return try {
            dataStore.edit { preferences ->
                when (val current = decode(preferences)) {
                    is LanguageLoadResult.Loaded -> {
                        if (current.language == language) {
                            result = LanguageSaveResult.Unchanged(language)
                        } else {
                            preferences[LANGUAGE_CODE] = language.code
                            preferences[SCHEMA_VERSION] = CURRENT_SCHEMA_VERSION
                            result = LanguageSaveResult.Saved(language)
                        }
                    }

                    is LanguageLoadResult.Failure -> {
                        result = LanguageSaveResult.Failure(current.reason)
                    }
                }
            }
            checkNotNull(result) { "language settings update did not produce a result" }
        } catch (_: IOException) {
            LanguageSaveResult.Failure(LanguageSettingsFailure.STORAGE_UNAVAILABLE)
        }
    }

    private fun decode(preferences: Preferences): LanguageLoadResult {
        val languageCode = preferences[LANGUAGE_CODE]
        val schemaVersion = preferences[SCHEMA_VERSION]
        if (languageCode == null && schemaVersion == null) {
            return LanguageLoadResult.Loaded(
                language = AppLanguage.DEFAULT,
                source = LanguageLoadSource.DEFAULT_NO_STORED_VALUE,
            )
        }
        if (languageCode == null || schemaVersion != CURRENT_SCHEMA_VERSION) {
            return LanguageLoadResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE)
        }
        val language = AppLanguage.fromCode(languageCode)
            ?: return LanguageLoadResult.Failure(LanguageSettingsFailure.UNSUPPORTED_VALUE)
        return LanguageLoadResult.Loaded(language, LanguageLoadSource.STORED)
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val LANGUAGE_CODE = stringPreferencesKey("language.code")
        val SCHEMA_VERSION = intPreferencesKey("language.schema_version")
    }
}
