package com.urkaaaz.screens.match

import android.app.Activity
import android.view.View
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.MatchScreenController

class MatchScreen(
    activity: Activity,
    campaignLevel: Int,
) : ScreenContent {
    private val controller = MatchScreenController(activity, campaignLevel)

    override val view: View = controller.createView()

    override fun onResume() {
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
    }
}
