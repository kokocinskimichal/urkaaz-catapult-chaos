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
import com.urkaaaz.application.tutorial.TutorialEffect
import com.urkaaaz.application.tutorial.TutorialScreen
import com.urkaaaz.application.tutorial.TutorialUiState
import com.urkaaaz.tutorial.TutorialScreenContent
import com.urkaaaz.tutorial.TutorialSignalSink
import com.urkaaaz.tutorial.TutorialTargetRegistration
import android.graphics.RectF

class MatchScreen(
    activity: Activity,
    campaignLevel: Int,
    rewardRegistry: CampaignRewardRegistry = BundledCampaignBalance.rewardRegistry(),
    playerLoadout: PlayerLoadoutConfig? = null,
    playerInventory: PlayerInventoryConfig? = null,
    matchId: MatchId = MatchId("local-match"),
    stockSettlementUseCase: MatchStockSettlementUseCase? = null,
    loadoutCatalog: LoadoutCatalog = LoadoutCatalog.bundled(),
    private val demoMode: Boolean = false,
    private val testMode: Boolean = false,
    onBackClicked: () -> Unit,
    openDebugOnStart: Boolean = false,
    onMatchFinished: (
        Int,
        MatchEvent.MatchFinished,
        LevelRewardResult?,
        (CampaignCompletionPresentation) -> Unit,
    ) -> Unit = { _, _, _, onCompletion -> onCompletion(CampaignCompletionPresentation(false, null)) },
    onNextLevelClicked: (Int) -> Unit = {},
    tutorialSignalSink: TutorialSignalSink? = null,
) : TutorialScreenContent {
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
        testMode = testMode,
        onBackClicked = onBackClicked,
        openDebugOnStart = openDebugOnStart,
        onMatchFinished = onMatchFinished,
        onNextLevelClicked = onNextLevelClicked,
        tutorialSignalSink = tutorialSignalSink,
    )

    override val view: View = controller.createView()
    override val tutorialScreen: TutorialScreen = TutorialScreen.MATCH
    override val registeredTutorialTargetIds: Set<String> =
        TutorialTargetRegistration.idsFor(tutorialScreen)
    override val isTutorialDemoMode: Boolean
        get() = demoMode || testMode
    override val tutorialTargetsReady: Boolean
        get() = controller.tutorialTargetsReady()
    override val tutorialMatchId: String?
        get() = controller.tutorialMatchId()
    override val tutorialMatchGeneration: Long
        get() = controller.tutorialMatchGeneration()

    override fun tutorialTargetBounds(targetId: String, targetInstanceId: String?): RectF? =
        controller.tutorialTargetBounds(targetId, targetInstanceId)

    override fun focusTutorialTarget(targetId: String, targetInstanceId: String?): Boolean =
        controller.focusTutorialTarget(targetId, targetInstanceId)

    override fun setTutorialUiState(state: TutorialUiState?) {
        controller.setTutorialUiState(state)
    }

    override fun dispatchTutorialEffect(effect: TutorialEffect): Boolean =
        controller.dispatchTutorialEffect(effect)

    override fun onResume() {
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
    }

    override fun onBackPressed(): Boolean = controller.onBackPressed()
}
