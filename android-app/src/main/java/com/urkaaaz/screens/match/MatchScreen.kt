package com.urkaaaz.screens.match

import android.app.Activity
import android.view.View
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.campaign.CampaignRewardRegistry
import com.urkaaaz.ui.MatchScreenController

class MatchScreen(
    activity: Activity,
    campaignLevel: Int,
    rewardRegistry: CampaignRewardRegistry = CampaignRewardRegistry.default(),
    playerLoadout: PlayerLoadoutConfig? = null,
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
}
