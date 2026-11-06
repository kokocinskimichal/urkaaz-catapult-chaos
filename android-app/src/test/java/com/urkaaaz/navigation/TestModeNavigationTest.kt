package com.urkaaaz.navigation

import com.urkaaaz.contracts.MatchId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestModeNavigationTest {
    @Test
    fun selectedTestLevelFlowsIntoLoadoutAndMatchWithoutLosingPickerBackStack() {
        val navigator = AppNavigator {}
        navigator.start(AppScreen.Dashboard)
        navigator.navigate(AppScreen.TestLevelPicker)
        navigator.navigate(AppScreen.Loadout(campaignLevel = 54, testMode = true))

        assertEquals(
            AppScreen.Loadout(campaignLevel = 54, testMode = true),
            navigator.currentScreen,
        )

        navigator.replace(
            AppScreen.Match(
                levelId = "LEVEL_54",
                campaignLevel = 54,
                testMode = true,
                matchId = MatchId("test-level-54"),
            ),
        )
        assertTrue(navigator.back())
        assertEquals(AppScreen.TestLevelPicker, navigator.currentScreen)
    }
}
