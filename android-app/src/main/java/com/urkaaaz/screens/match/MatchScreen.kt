package com.urkaaaz.screens.match

import android.app.Activity
import android.view.View
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.PlayerInventoryConfig
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.ui.MatchScreenController
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.campaign.BundledCampaignBalance
import com.urkaaaz.application.shop.MatchStockSettlementUseCase

class MatchScreen(
    activity: Activity,
    campaignLevel: Int,
    rewardRegistry: CampaignRewardRegistry = BundledCampaignBalance.rewardRegistry(),
    playerLoadout: PlayerLoadoutConfig? = null,
    playerInventory: PlayerInventoryConfig? = null,
    matchId: MatchId = MatchId("local-match"),
    stockSettlementUseCase: MatchStockSettlementUseCase? = null,
    loadoutCatalog: LoadoutCatalog = LoadoutCatalog.bundled(),
    demoMode: Boolean = false,
    onBackClicked: () -> Unit,
    openDebugOnStart: Boolean = false,
    onMatchFinished: (
        Int,
        MatchEvent.MatchFinished,
        LevelRewardResult?,
        (CampaignCompletionPresentation) -> Unit,
    ) -> Unit = { _, _, _, onCompletion -> onCompletion(CampaignCompletionPresentation(false, null)) },
    onNextLevelClicked: (Int) -> Unit = {},
) : ScreenContent {
    private val controller = MatchScreenController(
        activity = activity,
        campaignLevel = campaignLevel,
        rewardRegistry = rewardRegistry,
        playerLoadout = playerLoadout,
        playerInventory = playerInventory,
        matchId = matchId,
        stockSettlementUseCase = stockSettlementUseCase,
        loadoutCatalog = loadoutCatalog,
        demoMode = demoMode,
        onBackClicked = onBackClicked,
        openDebugOnStart = openDebugOnStart,
        onMatchFinished = onMatchFinished,
        onNextLevelClicked = onNextLevelClicked,
    )

    override val view: View = controller.createView()

    override fun onResume() {
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
    }

    override fun onBackPressed(): Boolean = controller.onBackPressed()
}
