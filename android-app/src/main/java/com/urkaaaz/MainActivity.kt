package com.urkaaaz

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.application.language.SetLanguage
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.application.campaign.BundledCampaignBalance
import com.urkaaaz.localization.LocalizedContext
import com.urkaaaz.localization.StartupLanguageReader
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.progression.CampaignAwareLoadoutProgressionPort
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.application.progression.DeveloperProgressPresetApply
import com.urkaaaz.application.progression.DeveloperProgressPresetRepository
import com.urkaaaz.application.progression.DeveloperProgressPresetUseCase
import com.urkaaaz.application.progression.DemoLoadoutProgressionPort
import com.urkaaaz.navigation.AppNavigator
import com.urkaaaz.navigation.AppScreen
import com.urkaaaz.navigation.DashboardRestoreState
import com.urkaaaz.navigation.ShopLoadoutReturn
import com.urkaaaz.navigation.ScreenHost
import com.urkaaaz.persistence.DataStoreCampaignProgressRepository
import com.urkaaaz.persistence.DataStoreLoadoutRepository
import com.urkaaaz.persistence.DataStorePlayerProgressionRepository
import com.urkaaaz.persistence.DataStorePlayerProgressResetRepository
import com.urkaaaz.persistence.DataStoreDeveloperProgressPresetAdapter
import com.urkaaaz.persistence.DataStoreLanguageSettings
import com.urkaaaz.persistence.DataStoreRewardSettlementAdapter
import com.urkaaaz.persistence.LoadoutStorageScope
import com.urkaaaz.persistence.urkaazLanguageDataStore
import com.urkaaaz.persistence.urkaazPreferencesDataStore
import com.urkaaaz.screens.campaign.CampaignScreen
import com.urkaaaz.screens.dashboard.DashboardScreen
import com.urkaaaz.screens.loadout.LoadoutScreen
import com.urkaaaz.screens.match.CampaignCompletionPresenter
import com.urkaaaz.application.reward.RewardSettlementUseCase
import com.urkaaaz.application.shop.MatchStockSettlementUseCase
import com.urkaaaz.application.shop.ShopCategory
import com.urkaaaz.application.shop.ShopItemReference
import com.urkaaaz.application.shop.ShopUseCases
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.screens.loadout.LoadoutItemReference
import com.urkaaaz.screens.match.MatchScreen
import com.urkaaaz.screens.result.ResultScreen
import com.urkaaaz.screens.shop.ShopScreen
import com.urkaaaz.screens.splash.SplashScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var screenHost: ScreenHost
    private lateinit var navigator: AppNavigator
    private var activityResumed = false
    private var startupLanguage = AppLanguage.DEFAULT
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loadoutCatalog by lazy { LoadoutCatalog.bundled() }
    private val campaignCompletionPresenter by lazy {
        CampaignCompletionPresenter(this, campaignSystem)
    }

    private fun LoadoutItemReference.toShopItemReference(): ShopItemReference =
        when (this) {
            is LoadoutItemReference.Ammunition ->
                ShopItemReference(ShopCategory.AMMUNITION, identifier)
            is LoadoutItemReference.Spell ->
                ShopItemReference(ShopCategory.SPELLS, identifier)
            is LoadoutItemReference.Unit ->
                error("Units are not sold in the Shop")
        }

    private fun ShopItemReference.toLoadoutItemReference(): LoadoutItemReference =
        when (category) {
            ShopCategory.AMMUNITION -> LoadoutItemReference.Ammunition(identifier)
            ShopCategory.SPELLS -> LoadoutItemReference.Spell(identifier)
        }
    private val campaignRewardRegistry by lazy { BundledCampaignBalance.rewardRegistry() }
    private val campaignSystem by lazy {
        CampaignSystem(
            DataStoreCampaignProgressRepository(
                applicationContext.urkaazPreferencesDataStore,
            ),
            rewards = campaignRewardRegistry,
            rewardSettlement = RewardSettlementUseCase(
                DataStoreRewardSettlementAdapter(
                    applicationContext.urkaazPreferencesDataStore,
                ),
                rewards = campaignRewardRegistry,
            ),
        )
    }
    private val preferencesDataStore by lazy {
        applicationContext.urkaazPreferencesDataStore
    }
    private val languageSettings by lazy {
        DataStoreLanguageSettings(applicationContext.urkaazLanguageDataStore)
    }
    private val setLanguage by lazy { SetLanguage(languageSettings) }
    private val playerProgressionRepository by lazy {
        DataStorePlayerProgressionRepository(preferencesDataStore)
    }
    private val playerProgressionUseCases by lazy {
        PlayerProgressionUseCases(playerProgressionRepository)
    }
    private val shopUseCases by lazy {
        ShopUseCases(playerProgressionRepository)
    }
    private val matchStockSettlementUseCase by lazy {
        MatchStockSettlementUseCase(playerProgressionRepository)
    }
    private val developerProgressPresetAdapter by lazy {
        DataStoreDeveloperProgressPresetAdapter(preferencesDataStore)
    }
    private val developerProgressPreset by lazy {
        DeveloperProgressPresetUseCase(
            repository = DeveloperProgressPresetRepository.bundled,
            port = developerProgressPresetAdapter,
        )
    }
    private val playerProgressResetRepository by lazy {
        DataStorePlayerProgressResetRepository(preferencesDataStore)
    }
    private val campaignAwareLoadoutProgression by lazy {
        CampaignAwareLoadoutProgressionPort(
            playerProgression = playerProgressionUseCases,
            campaignSystem = campaignSystem,
        )
    }
    private val loadoutUseCases by lazy {
        LoadoutUseCases(
            progression = campaignAwareLoadoutProgression,
            repository = DataStoreLoadoutRepository(preferencesDataStore),
        )
    }
    private val demoLoadoutUseCases by lazy {
        LoadoutUseCases(
            progression = DemoLoadoutProgressionPort(loadoutCatalog),
            repository = DataStoreLoadoutRepository(
                preferencesDataStore,
                scope = LoadoutStorageScope.DEMO,
            ),
        )
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        startupLanguage = StartupLanguageReader(
            DataStoreLanguageSettings(newBase.urkaazLanguageDataStore),
        ).read()
        super.attachBaseContext(LocalizedContext(newBase, startupLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialScreen = DashboardRestoreState.restore(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
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
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            startupScope.launch {
                when (val result = developerProgressPreset.applyBundled()) {
                    DeveloperProgressPresetApply.Applied ->
                        Log.i("MainActivity", "Applied developer progress preset")
                    is DeveloperProgressPresetApply.Failure ->
                        Log.e("MainActivity", "Developer progress preset failed: ${result.reason}")
                    DeveloperProgressPresetApply.AlreadyApplied,
                    DeveloperProgressPresetApply.NotRequested,
                    -> Unit
                }
                navigator.start(initialScreen)
            }
        } else {
            navigator.start(initialScreen)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        DashboardRestoreState.save(
            outState,
            if (::navigator.isInitialized) navigator.currentScreen else null,
        )
        super.onSaveInstanceState(outState)
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
        startupScope.cancel()
        if (::screenHost.isInitialized) {
            screenHost.pauseActiveScreen()
        }
        campaignCompletionPresenter.onDestroy()
        super.onDestroy()
    }

    private fun showScreen(screen: AppScreen) {
        val content = when (screen) {
            AppScreen.Splash -> SplashScreen(
                context = this,
                onFinished = { navigator.replace(AppScreen.Dashboard) },
            )
            AppScreen.Dashboard -> DashboardScreen(
                context = this,
                onBack = { if (!navigator.back()) finish() },
                onCampaign = { navigator.navigate(AppScreen.Campaign) },
                onShop = { navigator.navigate(AppScreen.Shop()) },
                onDemoMode = {
                    navigator.navigate(AppScreen.Loadout(campaignLevel = 1, demoMode = true))
                },
                onResetProgress = { playerProgressResetRepository.reset() },
                loadProgress = { developerProgressPreset.current() },
                initialLanguage = startupLanguage,
                setLanguage = setLanguage,
                onLanguageSaved = { recreate() },
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
                loadoutUseCases = if (screen.demoMode) demoLoadoutUseCases else loadoutUseCases,
                loadoutCatalog = loadoutCatalog,
                onBack = { navigator.back() },
                demoMode = screen.demoMode,
                initialDetailItem = screen.focusItem?.toLoadoutItemReference(),
                onShop = { item ->
                    navigator.replace(
                        AppScreen.Shop(
                            returnToLoadout = ShopLoadoutReturn(
                                campaignLevel = screen.campaignLevel,
                                demoMode = screen.demoMode,
                                item = item.toShopItemReference(),
                            ),
                        ),
                    )
                },
                onStart = { playerLoadout, playerInventory ->
                    navigator.replace(
                        AppScreen.Match(
                            levelId = if (screen.demoMode) {
                                "DEMO_LEVEL"
                            } else {
                                "LEVEL_${screen.campaignLevel}"
                            },
                            campaignLevel = screen.campaignLevel,
                            matchId = MatchId(UUID.randomUUID().toString()),
                            playerLoadout = playerLoadout,
                            playerInventory = playerInventory.takeUnless { screen.demoMode },
                        ),
                    )
                },
            )
            is AppScreen.Shop -> ShopScreen(
                context = this,
                useCases = shopUseCases,
                initialItem = screen.returnToLoadout?.item,
                onBack = {
                    val returnToLoadout = screen.returnToLoadout
                    if (returnToLoadout == null) {
                        if (!navigator.back()) finish()
                    } else {
                        navigator.replace(
                            AppScreen.Loadout(
                                campaignLevel = returnToLoadout.campaignLevel,
                                demoMode = returnToLoadout.demoMode,
                                focusItem = returnToLoadout.item,
                            ),
                        )
                    }
                },
            )
            is AppScreen.Match -> MatchScreen(
                activity = this,
                campaignLevel = screen.campaignLevel,
                rewardRegistry = campaignRewardRegistry,
                playerLoadout = screen.playerLoadout,
                playerInventory = screen.playerInventory,
                matchId = screen.matchId,
                stockSettlementUseCase = matchStockSettlementUseCase.takeIf {
                    screen.playerInventory != null
                },
                loadoutCatalog = loadoutCatalog,
                demoMode = screen.levelId == "DEMO_LEVEL",
                onBackClicked = {
                    if (!navigator.back()) {
                        finish()
                    }
                },
                openDebugOnStart = screen.levelId == "DEMO_LEVEL",
                onMatchFinished = { level, event, reward, onCompletion ->
                    campaignCompletionPresenter.onMatchFinished(
                        level,
                        event,
                        reward,
                        onCompletion,
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
