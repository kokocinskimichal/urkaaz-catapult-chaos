package com.urkaaaz.navigation

import com.urkaaaz.application.shop.ShopCategory
import com.urkaaaz.application.shop.ShopItemReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopNavigationTest {
    @Test
    fun dashboardShopRouteReturnsToDashboard() {
        val navigator = AppNavigator {}
        navigator.start(AppScreen.Dashboard)
        navigator.navigate(AppScreen.Shop())

        assertTrue(navigator.back())
        assertEquals(AppScreen.Dashboard, navigator.currentScreen)
    }

    @Test
    fun loadoutShopReturnCarriesTheSameSelectedItem() {
        val item = ShopItemReference(ShopCategory.SPELLS, "CATAPULT_LOCK")
        val returnTarget = ShopLoadoutReturn(
            campaignLevel = 12,
            demoMode = false,
            item = item,
        )
        val navigator = AppNavigator {}
        navigator.start(AppScreen.Loadout(campaignLevel = 12))
        navigator.replace(AppScreen.Shop(returnTarget))

        assertEquals(AppScreen.Shop(returnTarget), navigator.currentScreen)
        navigator.replace(
            AppScreen.Loadout(
                campaignLevel = returnTarget.campaignLevel,
                demoMode = returnTarget.demoMode,
                focusItem = returnTarget.item,
            ),
        )

        assertEquals(item, (navigator.currentScreen as AppScreen.Loadout).focusItem)
    }
}
