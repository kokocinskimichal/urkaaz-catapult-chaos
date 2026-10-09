package com.urkaaaz.screens.shop

import android.content.Context
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.common.PlaceholderScreenView

class ShopScreen(context: Context) : ScreenContent {
    override val view = PlaceholderScreenView(
        context,
        "Shop",
        "Placeholder sklepu",
    ).view
}
