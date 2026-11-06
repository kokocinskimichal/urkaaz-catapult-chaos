package com.urkaaaz.application.language

enum class LanguageSettingsFailure {
    STORAGE_UNAVAILABLE,
    UNSUPPORTED_VALUE,
}

enum class LanguageLoadSource {
    STORED,
    DEFAULT_NO_STORED_VALUE,
}

sealed interface LanguageLoadResult {
    data class Loaded(
        val language: AppLanguage,
        val source: LanguageLoadSource,
    ) : LanguageLoadResult

    data class Failure(
        val reason: LanguageSettingsFailure,
    ) : LanguageLoadResult
}

sealed interface LanguageSaveResult {
    data class Saved(val language: AppLanguage) : LanguageSaveResult
    data class Unchanged(val language: AppLanguage) : LanguageSaveResult
    data class Failure(val reason: LanguageSettingsFailure) : LanguageSaveResult
}

interface LanguageSettings {
    suspend fun load(): LanguageLoadResult
    suspend fun save(language: AppLanguage): LanguageSaveResult
}
