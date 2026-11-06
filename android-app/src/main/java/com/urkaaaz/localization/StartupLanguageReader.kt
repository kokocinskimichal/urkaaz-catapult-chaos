package com.urkaaaz.localization

import android.util.Log
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.LanguageLoadResult
import com.urkaaaz.application.language.LanguageSettings
import kotlinx.coroutines.runBlocking

class StartupLanguageReader(
    private val languageSettings: LanguageSettings,
) {
    // This is the single synchronous settings read needed before Activity resources are created.
    fun read(): AppLanguage = runBlocking {
        when (val result = languageSettings.load()) {
            is LanguageLoadResult.Loaded -> result.language
            is LanguageLoadResult.Failure -> {
                Log.e(TAG, "Could not load saved language: ${result.reason}")
                AppLanguage.DEFAULT
            }
        }
    }

    private companion object {
        const val TAG = "StartupLanguageReader"
    }
}
