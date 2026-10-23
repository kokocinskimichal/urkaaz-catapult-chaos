package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
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
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setImageResource(option.iconResource)
            background = HudPanelStyle.background(context, Color.argb(80, 28, 33, 36))
            setOnClickListener { onUnitSelected?.invoke(option.type) }
        }
    }
    private val sendWaveButton = ImageButton(context).apply {
        contentDescription = context.getString(R.string.match_hud_send_wave)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(3), dp(3), dp(3), dp(3))
        setImageResource(R.drawable.send_wave_icon)
        background = HudPanelStyle.background(context, Color.argb(100, 58, 50, 36))
        setOnClickListener { onSendWave?.invoke() }
    }
    private val sendWaveSlot = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(sendWaveButton, LayoutParams(dp(48), dp(48)))
        addView(
            TextView(context).apply {
                text = context.getString(R.string.match_hud_send_wave_label)
                gravity = Gravity.CENTER
                textSize = 7f
                maxLines = 1
                setTextColor(Color.rgb(239, 220, 179))
                UiTypography.applyButtonTypeface(this)
            },
            LayoutParams(-1, dp(12)),
        )
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(5), dp(8), dp(5), dp(3))
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(
                    groupHeader(context.getString(R.string.match_hud_supply)),
                    LayoutParams(dp(58), -1),
                )
                addView(
                    groupHeader(context.getString(R.string.match_hud_units)),
                    LayoutParams(0, -1, 1f).apply {
                        marginStart = dp(2)
                    },
                )
            },
            LayoutParams(-1, dp(13)),
        )
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(supplyGauge, LayoutParams(dp(58), dp(64)))
                options.forEach { option ->
                    addView(
                        unitSlot(option, buttons.getValue(option)),
                        LayoutParams(dp(48), dp(74)).apply {
                            marginStart = dp(2)
                        },
                    )
                }
                addView(
                    sendWaveSlot,
                    LayoutParams(dp(48), dp(64)).apply {
                        marginStart = dp(3)
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
            addView(button, LayoutParams(dp(48), dp(48)))
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
            addView(
                TextView(context).apply {
                    text = option.supplyCost.toString()
                    textSize = 8f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(Color.rgb(225, 193, 119))
                    UiTypography.applyButtonTypeface(this)
                },
                LayoutParams(-1, dp(12)),
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
