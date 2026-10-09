package com.urkaaaz.screens.campaign

import android.content.Context
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.common.PlaceholderScreenView

class CampaignScreen(context: Context) : ScreenContent {
    override val view = PlaceholderScreenView(
        context,
        "Campaign",
        "Placeholder wyboru kampanii",
    ).view
}
