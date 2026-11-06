package com.urkaaaz.screens.dashboard

import com.urkaaaz.android.R
import com.urkaaaz.application.language.AppLanguage

internal enum class DashboardLanguageFlag(
    val language: AppLanguage,
    val drawableResource: Int,
    val languageNameResource: Int,
) {
    POLISH(
        AppLanguage.PL,
        R.drawable.ic_dashboard_language_flag_poland,
        R.string.language_name_polish,
    ),
    ENGLISH(
        AppLanguage.EN,
        R.drawable.ic_dashboard_language_flag_uk,
        R.string.language_name_english,
    ),
    SPANISH(
        AppLanguage.ES,
        R.drawable.ic_dashboard_language_flag_spain,
        R.string.language_name_spanish,
    ),
    ;

    fun isActive(language: AppLanguage): Boolean = this.language == language
}
