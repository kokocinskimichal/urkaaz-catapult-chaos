package com.urkaaaz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.application.progression.CampaignAwareLoadoutProgressionPort
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.navigation.AppNavigator
import com.urkaaaz.navigation.AppScreen
import com.urkaaaz.navigation.ScreenHost
import com.urkaaaz.persistence.DataStoreCampaignProgressRepository
import com.urkaaaz.persistence.DataStoreLoadoutRepository
import com.urkaaaz.persistence.DataStorePlayerProgressionRepository
import com.urkaaaz.persistence.urkaazPreferencesDataStore
import com.urkaaaz.screens.campaign.CampaignScreen
import com.urkaaaz.screens.dashboard.DashboardScreen
import com.urkaaaz.screens.loadout.LoadoutScreen
import com.urkaaaz.screens.match.CampaignCompletionPresenter
import com.urkaaaz.screens.match.MatchScreen
import com.urkaaaz.screens.result.ResultScreen
import com.urkaaaz.screens.shop.ShopScreen
import com.urkaaaz.screens.start.StartScreen

class MainActivity : ComponentActivity() {
    private lateinit var screenHost: ScreenHost
    private lateinit var navigator: AppNavigator
    private var activityResumed = false
    private val campaignCompletionPresenter by lazy {
        CampaignCompletionPresenter(this, campaignSystem)
    }
    private val campaignSystem by lazy {
        CampaignSystem(
            DataStoreCampaignProgressRepository(
                applicationContext.urkaazPreferencesDataStore,
            ),
        )
    }
    private val loadoutUseCases by lazy {
        val dataStore = applicationContext.urkaazPreferencesDataStore
        LoadoutUseCases(
            progression = CampaignAwareLoadoutProgressionPort(
                playerProgression = PlayerProgressionUseCases(
                    DataStorePlayerProgressionRepository(dataStore),
                ),
                campaignSystem = campaignSystem,
            ),
            repository = DataStoreLoadoutRepository(dataStore),
        )
    }

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
                if (!screenHost.handleBackPressed() && !navigator.back()) {
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

    override fun onDestroy() {
        if (::screenHost.isInitialized) {
            screenHost.pauseActiveScreen()
        }
        campaignCompletionPresenter.onDestroy()
        super.onDestroy()
    }

    private fun showScreen(screen: AppScreen) {
        val content = when (screen) {
            AppScreen.Start -> StartScreen(
                context = this,
                onPlay = { navigator.navigate(AppScreen.Dashboard) },
                onTestMode = {
                    navigator.navigate(
                        AppScreen.Match(levelId = "DEMO_LEVEL", campaignLevel = 1),
                    )
                },
            )
            AppScreen.Dashboard -> DashboardScreen(
                context = this,
                onBack = { navigator.back() },
                onCampaign = { navigator.navigate(AppScreen.Campaign) },
                onShop = { navigator.navigate(AppScreen.Shop) },
            )
            AppScreen.Campaign -> CampaignScreen(
                context = this,
                campaignSystem = campaignSystem,
                onBack = { navigator.back() },
                onLevelSelected = { level ->
                    navigator.navigate(
                        AppScreen.Loadout(campaignLevel = level),
                    )
                },
            )
            is AppScreen.Loadout -> LoadoutScreen(
                context = this,
                campaignLevel = screen.campaignLevel,
                loadoutUseCases = loadoutUseCases,
                onBack = { navigator.back() },
                onStart = { playerLoadout ->
                    navigator.replace(
                        AppScreen.Match(
                            levelId = "LEVEL_${screen.campaignLevel}",
                            campaignLevel = screen.campaignLevel,
                            playerLoadout = playerLoadout,
                        ),
                    )
                },
            )
            AppScreen.Shop -> ShopScreen(this)
            is AppScreen.Match -> MatchScreen(
                activity = this,
                campaignLevel = screen.campaignLevel,
                playerLoadout = screen.playerLoadout,
                demoMode = screen.levelId == "DEMO_LEVEL",
                onBackClicked = {
                    if (!navigator.back()) {
                        finish()
                    }
                },
                openDebugOnStart = screen.levelId == "DEMO_LEVEL",
                onMatchFinished = { level, event, onCompletionRecorded ->
                    campaignCompletionPresenter.onMatchFinished(
                        level,
                        event,
                        onCompletionRecorded,
                    )
                },
                onNextLevelClicked = { nextLevel ->
                    navigator.replace(AppScreen.Loadout(nextLevel))
                },
            )
            is AppScreen.Result -> ResultScreen(this)
        }
        screenHost.show(content)
        if (activityResumed) {
            content.onResume()
        }
    }
}
