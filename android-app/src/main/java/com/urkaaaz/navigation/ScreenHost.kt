package com.urkaaaz.navigation

import android.content.Context
import android.view.View
import android.widget.FrameLayout

interface ScreenContent {
    val view: View

    fun onResume() = Unit

    fun onPause() = Unit
}

class ScreenHost(context: Context) : FrameLayout(context) {
    private var activeContent: ScreenContent? = null

    fun show(content: ScreenContent) {
        activeContent?.onPause()
        removeAllViews()
        activeContent = content
        addView(content.view, LayoutParams(-1, -1))
    }

    fun resumeActiveScreen() {
        activeContent?.onResume()
    }

    fun pauseActiveScreen() {
        activeContent?.onPause()
    }
}
