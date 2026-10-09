package com.urkaaaz.screens.dashboard

import android.content.Context
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.common.PlaceholderScreenView

class DashboardScreen(context: Context) : ScreenContent {
    private val viewModel = DashboardViewModel()

    override val view = PlaceholderScreenView(
        context = context,
        title = "Dashboard",
        description = "Placeholder ekranu głównego",
    ).view
}
