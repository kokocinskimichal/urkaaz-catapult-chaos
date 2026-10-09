package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R

class AmmunitionPanelView(context: Context) : LinearLayout(context) {
    private val buttons = mutableMapOf<String, ImageButton>()
    private var selectedAmmo = "ROCK"

    var onAmmoSelected: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(4, 2, 4, 2)
        addSlot("ROCK", "ROCK", "rock_flight_00")
        addSlot("SIEGE", "SIEGE_BOMB", "ammo_icon_siege_bomb")
        addSlot("BARREL", "POWDER_BARREL", "ammo_icon_powder_barrel")
        addSlot("CLUSTER", "CLUSTER_BOMB", "cluster_bomb_icon")
        addSlot("FIRE", "FIRE_RAIN", "fire_rain_icon")
        addSlot("PLAGUE", "PLAGUE_CAULDRON", "plague_cauldron_icon")
    }

    fun render(state: RenderState) {
        buttons.forEach { (type, button) ->
            val count = state.playerAmmunition[type]
            button.isEnabled = count == null || count > 0
            button.alpha = if (button.isEnabled) 1f else 0.35f
        }
        updateSelection()
    }

    private fun addSlot(label: String, ammunitionType: String, iconName: String) {
        val slot = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        val icon = ImageButton(context).apply {
            val id = resources.getIdentifier(iconName, "drawable", context.packageName)
            if (id != 0) setImageResource(id)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(5, 5, 5, 5)
            background = slotBackground(ammunitionType == selectedAmmo)
            contentDescription = label
            buttons[ammunitionType] = this
            setOnClickListener {
                selectedAmmo = ammunitionType
                updateSelection()
                onAmmoSelected?.invoke(ammunitionType)
            }
        }
        slot.addView(icon, LayoutParams(-1, 56))
        slot.addView(TextView(context).apply {
            text = label
            textSize = 8f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(239, 220, 179))
        }, LayoutParams(-1, 20))
        addView(slot, LayoutParams(0, -1, 1f))
    }

    private fun updateSelection() {
        buttons.forEach { (type, button) ->
            button.background = slotBackground(type == selectedAmmo)
        }
    }

    private fun slotBackground(selected: Boolean) = GradientDrawable().apply {
        setColor(if (selected) Color.rgb(184, 129, 48) else Color.rgb(73, 61, 49))
        cornerRadius = 10f
        setStroke(1, Color.argb(120, 255, 235, 190))
    }
}
