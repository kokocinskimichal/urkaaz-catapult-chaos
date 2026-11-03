package com.urkaaaz.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashNavigationTest {
    @Test
    fun replacingSplashWithDashboardDoesNotKeepSplashInBackStack() {
        val navigator = AppNavigator {}

        navigator.start(AppScreen.Splash)
        navigator.replace(AppScreen.Dashboard)

        assertEquals(AppScreen.Dashboard, navigator.currentScreen)
        assertFalse(navigator.back())
    }

    @Test
    fun dashboardNavigationCanStillReturnToDashboard() {
        val navigator = AppNavigator {}

        navigator.start(AppScreen.Splash)
        navigator.replace(AppScreen.Dashboard)
        navigator.navigate(AppScreen.Campaign)

        assertTrue(navigator.back())
        assertEquals(AppScreen.Dashboard, navigator.currentScreen)
    }
}
