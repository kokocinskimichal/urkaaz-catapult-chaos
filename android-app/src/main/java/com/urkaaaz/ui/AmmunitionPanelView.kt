package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.screens.loadout.LoadoutAssetCatalog
import com.urkaaaz.screens.loadout.LoadoutAssetLookup
import com.urkaaaz.screens.loadout.LoadoutItemReference
import java.util.Locale

class AmmunitionPanelView(
    context: Context,
    availableAmmunitionTypes: List<String>,
) : LinearLayout(context) {
    private val slots = availableAmmunitionTypes.take(MAX_VISIBLE_AMMUNITION)
        .mapNotNull(::createSlot)

    var onAmmoSelected: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(8), dp(5), dp(3))
        addView(
            groupHeader(context.getString(R.string.match_hud_ammunition)),
            LayoutParams(-1, dp(13)),
        )
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                slots.forEach { slot ->
                    addView(
                        slot.root,
                        LayoutParams(0, -1, 1f).apply {
                            marginStart = dp(2)
                            marginEnd = dp(2)
                        },
                    )
                }
            },
            LayoutParams(-1, 0, 1f),
        )
    }

    fun render(state: RenderState) {
        slots.forEach { slot ->
            val count = state.playerAmmunition[slot.type]
            val available = count == null || count > 0
            val reload = state.playerReloadByAmmunition[slot.type]
                ?: if (slot.type == state.selectedAmmunitionType) {
                    state.playerReloadRemainingSeconds
                } else {
                    0f
                }
            val selected = slot.type == state.selectedAmmunitionType
            slot.button.isEnabled = available && state.statusLabel == "RUNNING"
            slot.button.alpha = if (available) 1f else 0.38f
            if (selected != slot.selected) {
                if (selected) {
                    slot.button.background = HudPanelStyle.background(
                        context,
                        Color.argb(190, 128, 89, 39),
                        selected = true,
                    )
                } else {
                    slot.button.background = HudPanelStyle.background(
                        context,
                        Color.argb(82, 28, 33, 36),
                    )
                }
                slot.selected = selected
            }
            slot.stockText.text = when {
                !available -> context.getString(R.string.match_hud_out_of_stock)
                count == null -> context.getString(R.string.match_hud_unlimited)
                else -> context.getString(R.string.match_hud_stock, count)
            }
            slot.reloadText.text = if (reload > 0f) {
                context.getString(
                    R.string.match_hud_reload_seconds,
                    String.format(Locale.US, "%.1f", reload),
                )
            } else {
                context.getString(R.string.match_hud_ready)
            }
            slot.button.contentDescription = context.getString(
                R.string.match_hud_ammunition_accessibility,
                context.getString(slot.nameResource),
                slot.stockText.text,
                slot.reloadText.text,
            )
        }
    }

    private fun createSlot(type: String): AmmoSlot? {
        val nameResource = ammunitionNameResource(type) ?: return null
        val artwork = LoadoutAssetCatalog.illustration(LoadoutItemReference.Ammunition(type))
        val iconResource = (artwork as? LoadoutAssetLookup.Found)?.drawableId
            ?: return null
        val button = ImageButton(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setImageResource(iconResource)
            background = HudPanelStyle.background(context, Color.argb(82, 28, 33, 36))
            setOnClickListener { onAmmoSelected?.invoke(type) }
        }
        val nameText = slotText().apply {
            text = context.getString(ammunitionShortNameResource(type) ?: nameResource)
        }
        val stockText = slotText()
        val reloadText = slotText()
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(button, LayoutParams(dp(48), dp(48)))
            addView(nameText, LayoutParams(-1, dp(11)))
            addView(stockText, LayoutParams(-1, dp(12)))
            addView(reloadText, LayoutParams(-1, dp(12)))
        }
        return AmmoSlot(type, nameResource, button, stockText, reloadText, root)
    }

    private fun ammunitionNameResource(type: String): Int? = when (type) {
        "ROCK" -> R.string.loadout_name_rock
        "SIEGE_BOMB" -> R.string.loadout_name_siege_bomb
        "POWDER_BARREL" -> R.string.loadout_name_powder_barrel
        "FIRE_RAIN" -> R.string.loadout_name_fire_rain
        "PLAGUE_CAULDRON" -> R.string.loadout_name_plague_cauldron
        else -> null
    }

    private fun ammunitionShortNameResource(type: String): Int? = when (type) {
        "ROCK" -> R.string.match_hud_ammo_rock
        "SIEGE_BOMB" -> R.string.match_hud_ammo_siege
        "POWDER_BARREL" -> R.string.match_hud_ammo_barrel
        "FIRE_RAIN" -> R.string.match_hud_ammo_fire_rain
        "PLAGUE_CAULDRON" -> R.string.match_hud_ammo_plague
        else -> null
    }

    private fun slotText() = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        textSize = 7f
        maxLines = 1
        setTextColor(Color.rgb(239, 220, 179))
        UiTypography.applyButtonTypeface(this)
    }

    private fun groupHeader(label: String) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 9f
        maxLines = 1
        setTextColor(Color.rgb(225, 193, 119))
        UiTypography.applyButtonTypeface(this)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private data class AmmoSlot(
        val type: String,
        val nameResource: Int,
        val button: ImageButton,
        val stockText: TextView,
        val reloadText: TextView,
        val root: LinearLayout,
        var selected: Boolean = false,
    )

    private companion object {
        const val MAX_VISIBLE_AMMUNITION = 3
    }
}
