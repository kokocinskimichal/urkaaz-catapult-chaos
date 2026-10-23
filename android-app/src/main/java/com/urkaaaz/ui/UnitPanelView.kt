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
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.loadout.LoadoutCatalog

class UnitPanelView(
    context: Context,
    selectableUnitTypes: List<String>,
) : LinearLayout(context) {
    var onUnitSelected: ((String) -> Unit)? = null
    var onSendWave: (() -> Unit)? = null

    private val supplyGauge = SupplyGaugeView(context)
    private val options = selectableUnitTypes.take(MAX_VISIBLE_UNITS).mapNotNull(::unitOption)
    private val buttons = options.associateWith { option ->
        ImageButton(context).apply {
            contentDescription = context.getString(option.nameResource)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setImageResource(option.iconResource)
            background = HudPanelStyle.background(context, Color.argb(80, 28, 33, 36))
            clipToOutline = true
            setOnClickListener { onUnitSelected?.invoke(option.type) }
        }
    }
    private val sendWaveButton = ImageButton(context).apply {
        contentDescription = context.getString(R.string.match_hud_send_wave)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(0, 0, 0, 0)
        setImageResource(R.drawable.match_hud_send_wave_button_states)
        background = null
        setOnClickListener { onSendWave?.invoke() }
    }
    private val sendWaveSlot = FrameLayout(context).apply {
        addView(
            sendWaveButton,
            FrameLayout.LayoutParams(
                dp(66),
                dp(66),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(2), dp(8), dp(2), dp(3))
        val supplyColumnWidth = dp(80)
        val unitSlotWidth = dp(60)
        val unitSlotSpacing = dp(1)
        val sendWaveSlotWidth = dp(66)
        val unitHeaderWidth = options.size * (unitSlotWidth + unitSlotSpacing) +
            sendWaveSlotWidth + unitSlotSpacing
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(
                    groupHeader(context.getString(R.string.match_hud_supply)),
                    LayoutParams(supplyColumnWidth, -1),
                )
                addView(
                    groupHeader(context.getString(R.string.match_hud_units)),
                    LayoutParams(unitHeaderWidth, -1).apply {
                        marginStart = dp(1)
                    },
                )
            },
            LayoutParams(supplyColumnWidth + dp(1) + unitHeaderWidth, dp(13)),
        )
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(supplyGauge, LayoutParams(supplyColumnWidth, dp(80)))
                options.forEach { option ->
                    addView(
                        unitSlot(option, buttons.getValue(option)),
                        LayoutParams(unitSlotWidth, dp(76)).apply {
                            marginStart = unitSlotSpacing
                        },
                    )
                }
                addView(
                    sendWaveSlot,
                    LayoutParams(sendWaveSlotWidth, dp(76)).apply {
                        marginStart = unitSlotSpacing
                    },
                )
            },
            LayoutParams(-1, 0, 1f),
        )
    }

    fun render(state: RenderState) {
        supplyGauge.setSupply(state.playerSupply)
        buttons.forEach { (option, button) ->
            val enabled = state.statusLabel == "RUNNING" &&
                state.outcomeLabel == null &&
                state.playerSupply >= option.supplyCost
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.38f
            button.contentDescription = context.getString(
                R.string.match_hud_unit_accessibility,
                context.getString(option.nameResource),
                option.supplyCost,
                if (enabled) {
                    context.getString(R.string.match_hud_available)
                } else {
                    context.getString(R.string.match_hud_unavailable)
                },
            )
        }
        val canSendWave = state.statusLabel == "RUNNING" &&
            state.outcomeLabel == null &&
            state.units.any { it.teamLabel == "BLUE" }
        sendWaveButton.isEnabled = canSendWave
        sendWaveButton.alpha = if (canSendWave) 1f else 0.38f
    }

    private fun unitSlot(option: UnitOption, button: ImageButton): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            val costBadge = TextView(context).apply {
                text = option.supplyCost.toString()
                gravity = Gravity.CENTER
                includeFontPadding = false
                textSize = 9f
                setTextColor(Color.rgb(239, 220, 179))
                setPadding(dp(1), 0, dp(1), 0)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(225, 26, 26, 26))
                    setStroke(dp(1), Color.rgb(225, 193, 119))
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
                isFocusable = false
            }
            addView(
                FrameLayout(context).apply {
                    addView(button, FrameLayout.LayoutParams(-1, -1))
                    addView(
                        costBadge,
                        FrameLayout.LayoutParams(
                            dp(20),
                            dp(20),
                            Gravity.TOP or Gravity.END,
                        ).apply {
                            topMargin = dp(1)
                            marginEnd = dp(1)
                        },
                    )
                },
                LayoutParams(dp(60), dp(60)),
            )
            addView(
                TextView(context).apply {
                    text = context.getString(option.shortNameResource)
                    textSize = 8f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(Color.rgb(239, 220, 179))
                    UiTypography.applyButtonTypeface(this)
                },
                LayoutParams(-1, dp(14)),
            )
        }

    private fun unitOption(type: String): UnitOption? {
        val unit = LoadoutCatalog.findUnit(type) ?: return null
        val (icon, name, shortName) = when (type) {
            "SAPPER" -> Triple(
                R.drawable.sapper_icon,
                R.string.loadout_name_sapper,
                R.string.match_hud_unit_sapper,
            )
            "DEFENDER" -> Triple(
                R.drawable.defender_icon,
                R.string.loadout_name_defender,
                R.string.match_hud_unit_defender,
            )
            "DEMOLISHER" -> Triple(
                R.drawable.demolisher_icon,
                R.string.loadout_name_demolisher,
                R.string.match_hud_unit_demolisher,
            )
            "RAGING_BOAR" -> Triple(
                R.drawable.goblin_raging_boar_icon,
                R.string.loadout_name_raging_boar,
                R.string.match_hud_unit_boar,
            )
            "SLINGMASTER" -> Triple(
                R.drawable.goblin_slingmaster_icon,
                R.string.loadout_name_slingmaster,
                R.string.match_hud_unit_slingmaster,
            )
            else -> return null
        }
        return UnitOption(type, unit.supplyCost, icon, name, shortName)
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

    private data class UnitOption(
        val type: String,
        val supplyCost: Int,
        val iconResource: Int,
        val nameResource: Int,
        val shortNameResource: Int,
    )

    private companion object {
        const val MAX_VISIBLE_UNITS = 3
    }
}
