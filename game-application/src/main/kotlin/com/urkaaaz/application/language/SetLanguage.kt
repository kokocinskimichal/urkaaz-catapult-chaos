package com.urkaaaz.application.language

class SetLanguage(
    private val languageSettings: LanguageSettings,
) {
    suspend operator fun invoke(language: AppLanguage): LanguageSaveResult =
        when (val current = languageSettings.load()) {
            is LanguageLoadResult.Loaded ->
                if (current.language == language) {
                    LanguageSaveResult.Unchanged(language)
                } else {
                    languageSettings.save(language)
                }
            is LanguageLoadResult.Failure -> LanguageSaveResult.Failure(current.reason)
        }
}
