package com.urkaaaz.screens.match

import android.app.Activity
import android.view.View
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.contracts.MatchEvent
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.ui.MatchScreenController

class MatchScreen(
    activity: Activity,
    campaignLevel: Int,
    playerLoadout: PlayerLoadoutConfig? = null,
    demoMode: Boolean = false,
    onBackClicked: () -> Unit,
    openDebugOnStart: Boolean = false,
    onMatchFinished: (Int, MatchEvent.MatchFinished, (Boolean) -> Unit) -> Unit =
        { _, _, onCompletionRecorded -> onCompletionRecorded(false) },
    onNextLevelClicked: (Int) -> Unit = {},
) : ScreenContent {
    private val controller = MatchScreenController(
        activity = activity,
        campaignLevel = campaignLevel,
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
