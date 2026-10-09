package com.urkaaaz.screens.result

import android.content.Context
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.common.PlaceholderScreenView

class ResultScreen(context: Context) : ScreenContent {
    override val view = PlaceholderScreenView(
        context,
        "Result",
        "Placeholder wyniku meczu",
    ).view
}
