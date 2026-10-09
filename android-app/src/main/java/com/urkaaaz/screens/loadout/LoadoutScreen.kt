package com.urkaaaz.screens.loadout

import android.content.Context
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.common.PlaceholderScreenView

class LoadoutScreen(context: Context) : ScreenContent {
    override val view = PlaceholderScreenView(
        context,
        "Loadout",
        "Placeholder konfiguracji jednostek, amunicji i czarów",
    ).view
}
