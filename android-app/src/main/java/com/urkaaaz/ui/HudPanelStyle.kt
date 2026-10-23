package com.urkaaaz.ui

import android.content.Context
import android.graphics.Outline
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import com.urkaaaz.android.R

internal object HudPanelStyle {
    private const val CORNER_RADIUS_DP = 9f
    private const val FRAME_FILL_CORNER_RADIUS_DP = 10f

    fun background(
        context: Context,
        color: Int = Color.argb(204, 26, 26, 26),
        selected: Boolean = false,
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.resources.displayMetrics.density * CORNER_RADIUS_DP
        setStroke(
            (context.resources.displayMetrics.density * if (selected) 1.5f else 1f).toInt()
                .coerceAtLeast(1),
            if (selected) Color.rgb(235, 184, 82) else Color.argb(155, 181, 143, 78),
        )
    }

    fun universalFrameBackground(context: Context) = LayerDrawable(
        arrayOf(
            GradientDrawable().apply {
                setColor(Color.argb(204, 26, 26, 26))
                cornerRadius =
                    context.resources.displayMetrics.density * FRAME_FILL_CORNER_RADIUS_DP
            },
            context.resources.getDrawable(R.drawable.match_hud_universal_frame, context.theme),
        ),
    )

    fun applyShadow(view: View, elevationDp: Float = 3f) {
        val density = view.resources.displayMetrics.density
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    CORNER_RADIUS_DP * density,
                )
            }
        }
        view.elevation = elevationDp * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineAmbientShadowColor = Color.argb(48, 12, 9, 7)
            view.outlineSpotShadowColor = Color.argb(76, 12, 9, 7)
        }
    }
}
