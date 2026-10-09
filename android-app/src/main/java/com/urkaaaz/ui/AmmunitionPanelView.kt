package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.urkaaaz.android.R

/**
 * Legacy-style ammunition selector.
 *
 * The selector shows the currently equipped ammunition and opens a popup grid
 * with the available ammunition types. Selection remains an Android UI action;
 * the match view model owns the gameplay command.
 */
class AmmunitionPanelView(context: Context) : FrameLayout(context) {
    private var selectedAmmo = ROCK
    private var latestState = RenderStateMapper.map(com.urkaaaz.contracts.MatchSnapshot())
    private val selectorButton = ImageButton(context)

    var onAmmoSelected: ((String) -> Unit)? = null

    init {
        foregroundGravity = Gravity.CENTER
        clipChildren = false
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(4, 2, 4, 2)
        selectorButton.apply {
            background = slotBackground(selected = true)
            contentDescription = "Choose ammunition"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(5, 5, 5, 5)
            setImageResource(iconResource(selectedAmmo))
            setOnClickListener { showAmmoMenu() }
        }
        addView(
            selectorButton,
            LayoutParams(dp(68), dp(68)).apply {
                gravity = Gravity.CENTER
                topMargin = -dp(14)
            },
        )
    }

    fun render(state: RenderState) {
        latestState = state
        val count = state.playerAmmunition[selectedAmmo]
        val enabled = count == null || count > 0
        selectorButton.isEnabled = enabled
        selectorButton.alpha = if (enabled) 1f else 0.35f
        selectorButton.setImageResource(iconResource(selectedAmmo))
    }

    private fun showAmmoMenu() {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        lateinit var popup: PopupWindow
        val columns = 4
        ammunitionTypes.forEachIndexed { index, ammunitionType ->
            if (index % columns == 0) {
                content.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }, LinearLayout.LayoutParams(-2, dp(64)))
            }
            val row = content.getChildAt(content.childCount - 1) as LinearLayout
            val count = latestState.playerAmmunition[ammunitionType]
            val enabled = count == null || count > 0
            row.addView(ImageButton(context).apply {
                setImageResource(iconResource(ammunitionType))
                background = slotBackground(ammunitionType == selectedAmmo)
                setPadding(dp(5), dp(5), dp(5), dp(5))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                alpha = if (enabled) 1f else 0.35f
                isEnabled = enabled
                contentDescription = ammunitionType
                setOnClickListener {
                    selectedAmmo = ammunitionType
                    selectorButton.setImageResource(iconResource(selectedAmmo))
                    popup.dismiss()
                    onAmmoSelected?.invoke(ammunitionType)
                }
            }, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }

        val rows = (ammunitionTypes.size + columns - 1) / columns
        popup = PopupWindow(
            content,
            dp(64 * columns + 8),
            dp(64 * rows + 8),
            true,
        ).apply {
            setBackgroundDrawable(slotBackground(selected = false))
            elevation = dp(12).toFloat()
            isOutsideTouchable = true
        }
        selectorButton.post {
            popup.showAsDropDown(
                selectorButton,
                -dp(64 * 2),
                -popup.height - selectorButton.height,
            )
        }
    }

    private fun iconResource(ammunitionType: String): Int = when (ammunitionType) {
        "ROCK" -> R.drawable.rock_flight_00
        "SIEGE_BOMB" -> R.drawable.ammo_icon_siege_bomb
        "POWDER_BARREL" -> R.drawable.ammo_icon_powder_barrel
        "CLUSTER_BOMB" -> R.drawable.cluster_bomb_icon
        "FIRE_RAIN" -> R.drawable.fire_rain_icon
        "PLAGUE_CAULDRON" -> R.drawable.plague_cauldron_icon
        else -> R.drawable.rock_flight_00
    }

    private fun slotBackground(selected: Boolean) = GradientDrawable().apply {
        setColor(
            if (selected) Color.rgb(184, 129, 48) else Color.rgb(73, 61, 49),
        )
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.argb(120, 255, 235, 190))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ROCK = "ROCK"
        val ammunitionTypes = listOf(
            "ROCK",
            "SIEGE_BOMB",
            "POWDER_BARREL",
            "CLUSTER_BOMB",
            "FIRE_RAIN",
            "PLAGUE_CAULDRON",
        )
    }
}
