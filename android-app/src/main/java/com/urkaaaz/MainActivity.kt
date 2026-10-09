package com.urkaaaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.urkaaaz.navigation.AppNavigator
import com.urkaaaz.navigation.AppScreen
import com.urkaaaz.navigation.ScreenHost
import com.urkaaaz.screens.campaign.CampaignScreen
import com.urkaaaz.screens.dashboard.DashboardScreen
import com.urkaaaz.screens.loadout.LoadoutScreen
import com.urkaaaz.screens.match.MatchScreen
import com.urkaaaz.screens.result.ResultScreen
import com.urkaaaz.screens.shop.ShopScreen
import com.urkaaaz.screens.start.StartScreen

class MainActivity : ComponentActivity() {
    private lateinit var screenHost: ScreenHost
    private lateinit var navigator: AppNavigator
    private var activityResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        screenHost = ScreenHost(this)
        navigator = AppNavigator(::showScreen)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navigator.back()) {
                    finish()
                }
            }
        })
        setContentView(screenHost)
        navigator.start(AppScreen.Start)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        screenHost.resumeActiveScreen()
    }

    override fun onPause() {
        activityResumed = false
        screenHost.pauseActiveScreen()
        super.onPause()
    }

    private fun showScreen(screen: AppScreen) {
        val content = when (screen) {
            AppScreen.Start -> StartScreen(
                context = this,
                onPlay = { navigator.navigate(AppScreen.Dashboard) },
                onTestMode = { navigator.navigate(AppScreen.Match(levelId = "DEMO_LEVEL")) },
            )
            AppScreen.Dashboard -> DashboardScreen(
                context = this,
                onBack = { navigator.back() },
                onCampaign = { navigator.navigate(AppScreen.Campaign) },
                onShop = { navigator.navigate(AppScreen.Shop) },
            )
            AppScreen.Campaign -> CampaignScreen(this)
            AppScreen.Loadout -> LoadoutScreen(this)
            AppScreen.Shop -> ShopScreen(this)
            is AppScreen.Match -> MatchScreen(this)
            is AppScreen.Result -> ResultScreen(this)
        }
        screenHost.show(content)
        if (activityResumed) {
            content.onResume()
        }
    }
}
