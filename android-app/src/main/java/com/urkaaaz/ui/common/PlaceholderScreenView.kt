package com.urkaaaz.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.navigation.ScreenContent

class PlaceholderScreenView(
    context: Context,
    title: String,
    description: String,
) : ScreenContent {
    override val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(28, 35, 39))
        addView(TextView(context).apply {
            text = title
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(239, 220, 179))
        })
        addView(TextView(context).apply {
            text = description
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(190, 190, 190))
            setPadding(24, 12, 24, 0)
        })
    }
}
