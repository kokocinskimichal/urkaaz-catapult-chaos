package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.screens.loadout.LoadoutAssetCatalog
import com.urkaaaz.screens.loadout.LoadoutAssetLookup
import com.urkaaaz.screens.loadout.LoadoutItemReference
import java.util.Locale
import kotlin.math.roundToInt

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
            val reloading = reload > 0f
            val reloadProgress = ammunitionReloadProgress(
                remainingSeconds = reload,
                reloadDurationSeconds = slot.reloadDurationSeconds,
            )
            val selected = slot.type == state.selectedAmmunitionType
            slot.button.isEnabled =
                available && !reloading && state.statusLabel == "RUNNING"
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
            slot.stockBadge.visibility = View.VISIBLE
            slot.stockCountText.text = count?.let {
                context.getString(R.string.match_hud_stock, it)
            }
            slot.stockCountText.visibility =
                if (count == null) View.GONE else View.VISIBLE
            slot.unlimitedStockIcon.visibility =
                if (count == null) View.VISIBLE else View.GONE
            slot.reloadShade.coveredFraction = 1f - reloadProgress
            slot.reloadShade.visibility = if (reloading) View.VISIBLE else View.GONE
            val reloadDescription = if (reloading) {
                context.getString(
                    R.string.match_hud_reload_seconds,
                    String.format(Locale.US, "%.1f", reload),
                )
            } else {
                context.getString(R.string.match_hud_ready)
            }
            slot.reloadText.text = if (reloading) reloadDescription else ""
            slot.reloadText.visibility = if (reloading) View.VISIBLE else View.GONE
            val stockDescription = when {
                !available -> context.getString(R.string.match_hud_out_of_stock)
                count == null -> context.getString(R.string.match_hud_unlimited)
                else -> context.getString(R.string.match_hud_stock, count)
            }
            slot.button.contentDescription = context.getString(
                R.string.match_hud_ammunition_accessibility,
                context.getString(slot.nameResource),
                stockDescription,
                reloadDescription,
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
        val reloadShade = AmmoReloadShade(context, dp(9).toFloat())
        val nameText = slotText().apply {
            text = context.getString(ammunitionShortNameResource(type) ?: nameResource)
        }
        val stockCountText = TextView(context).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = 7f
            setTextColor(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
        val unlimitedStockIcon = ImageView(context).apply {
            setImageResource(LoadoutAssetCatalog.unlimitedIcon())
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val stockBadge = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(225, 26, 26, 26))
                setStroke(dp(1), Color.rgb(225, 193, 119))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(stockCountText, FrameLayout.LayoutParams(-1, -1))
            addView(unlimitedStockIcon, FrameLayout.LayoutParams(-1, -1))
        }
        val reloadText = TextView(context).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = 8f
            maxLines = 1
            setTextColor(Color.WHITE)
            UiTypography.applyButtonTypeface(this)
            setPadding(dp(3), 0, dp(3), 0)
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 20, 20, 20))
                cornerRadius = dp(3).toFloat()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val iconFrame = FrameLayout(context).apply {
            addView(button, FrameLayout.LayoutParams(-1, -1))
            addView(reloadShade, FrameLayout.LayoutParams(-1, -1))
            addView(
                stockBadge,
                FrameLayout.LayoutParams(
                    dp(16),
                    dp(16),
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = dp(1)
                    marginEnd = dp(1)
                },
            )
            addView(
                reloadText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(16),
                    Gravity.CENTER,
                ),
            )
        }
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            addView(iconFrame, LayoutParams(dp(48), dp(48)))
            addView(nameText, LayoutParams(-1, dp(11)))
        }
        val reloadDurationSeconds =
            LoadoutCatalog.findAmmunition(type)?.reloadSeconds ?: 0f
        return AmmoSlot(
            type = type,
            nameResource = nameResource,
            reloadDurationSeconds = reloadDurationSeconds,
            button = button,
            stockBadge = stockBadge,
            stockCountText = stockCountText,
            unlimitedStockIcon = unlimitedStockIcon,
            reloadShade = reloadShade,
            reloadText = reloadText,
            root = root,
        )
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
        val reloadDurationSeconds: Float,
        val button: ImageButton,
        val stockBadge: FrameLayout,
        val stockCountText: TextView,
        val unlimitedStockIcon: ImageView,
        val reloadShade: AmmoReloadShade,
        val reloadText: TextView,
        val root: LinearLayout,
        var selected: Boolean = false,
    )

    private companion object {
        const val MAX_VISIBLE_AMMUNITION = 3
    }
}

internal fun ammunitionReloadProgress(
    remainingSeconds: Float,
    reloadDurationSeconds: Float,
): Float = if (reloadDurationSeconds <= 0f) {
    1f
} else {
    (1f - remainingSeconds / reloadDurationSeconds).coerceIn(0f, 1f)
}

private class AmmoReloadShade(
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
