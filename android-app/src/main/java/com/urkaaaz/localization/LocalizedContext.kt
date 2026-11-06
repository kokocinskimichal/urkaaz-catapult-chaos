package com.urkaaaz.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import com.urkaaaz.application.language.AppLanguage
import java.util.Locale

class LocalizedContext(
    base: Context,
    language: AppLanguage,
) : ContextWrapper(
    base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language.code))
        },
    ),
)
