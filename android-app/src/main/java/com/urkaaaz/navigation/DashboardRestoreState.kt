package com.urkaaaz.navigation

import android.os.Bundle

internal object DashboardRestoreState {
    private const val KEY_RESTORE_DASHBOARD = "restore_dashboard_after_recreation"

    fun restore(savedInstanceState: Bundle?): AppScreen =
        if (savedInstanceState?.getBoolean(KEY_RESTORE_DASHBOARD) == true) {
            AppScreen.Dashboard
        } else {
            AppScreen.Splash
        }

    fun save(outState: Bundle, currentScreen: AppScreen?) {
        outState.putBoolean(KEY_RESTORE_DASHBOARD, currentScreen == AppScreen.Dashboard)
    }
}
