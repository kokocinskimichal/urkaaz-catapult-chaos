package com.urkaaaz.application.language

enum class AppLanguage(val code: String) {
    EN("en"),
    PL("pl"),
    ES("es"),
    ;

    companion object {
        val DEFAULT = EN

        fun fromCode(code: String): AppLanguage? =
            entries.firstOrNull { it.code == code }
    }
}
