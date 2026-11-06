package com.urkaaaz.navigation

import android.os.Bundle
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardRestoreStateTest {
    @Test
    fun dashboardIsRestoredButOtherScreensStartAtSplash() {
        val dashboardState = Bundle()
        DashboardRestoreState.save(dashboardState, AppScreen.Dashboard)
        assertEquals(AppScreen.Dashboard, DashboardRestoreState.restore(dashboardState))

        val campaignState = Bundle()
        DashboardRestoreState.save(campaignState, AppScreen.Campaign)
        assertEquals(AppScreen.Splash, DashboardRestoreState.restore(campaignState))
    }

    @Test
    fun missingStateStartsAtSplash() {
        assertEquals(AppScreen.Splash, DashboardRestoreState.restore(null))
    }
}
