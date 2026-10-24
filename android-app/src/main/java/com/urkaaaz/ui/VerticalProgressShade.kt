package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import kotlin.math.roundToInt

internal class VerticalProgressShade(
    context: Context,
    cornerRadius: Float,
) : View(context) {
    var coveredFraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            updateClipBounds()
        }

    init {
        background = GradientDrawable().apply {
            setColor(Color.argb(176, 0, 0, 0))
            this.cornerRadius = cornerRadius
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateClipBounds()
    }

    private fun updateClipBounds() {
        clipBounds = Rect(0, 0, width, (height * coveredFraction).roundToInt())
    }
}
