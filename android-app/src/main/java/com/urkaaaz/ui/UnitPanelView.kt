package com.urkaaaz.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.urkaaaz.android.R

/** Legacy-style supply, unit deployment and send-wave controls. */
class UnitPanelView(context: Context) : LinearLayout(context) {
    var onUnitSelected: ((String) -> Unit)? = null
    var onSendWave: (() -> Unit)? = null

    private val options = listOf(
        UnitOption("SAPPER", "sapper_icon", 10),
        UnitOption("DEFENDER", "defender_icon", 5),
        UnitOption("DEMOLISHER", "demolisher_icon", 10),
        UnitOption("RAGING_BOAR", "goblin_raging_boar_icon", 10),
        UnitOption("SLINGMASTER", "goblin_slingmaster_icon", 5),
    )
    private val supplyGauge = SupplyGaugeView(context)
    private val buttons = options.associateWith { option ->
        ImageButton(context).apply {
            contentDescription = option.type
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setImageResource(
                context.resources.getIdentifier(option.iconName, "drawable", context.packageName),
            )
            background = transparentBackground()
            setOnClickListener { onUnitSelected?.invoke(option.type) }
        }.also { button ->
            addView(button, controlLayoutParams(48))
        }
    }
    private val sendWaveButton = ImageButton(context).apply {
        contentDescription = "Send wave"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(3), dp(3), dp(3), dp(3))
        setImageResource(R.drawable.send_wave_icon)
        background = circularBackground(false)
        setOnClickListener { onSendWave?.invoke() }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        removeAllViews()
        addView(
            supplyGauge,
            LayoutParams(dp(52), dp(52)).apply {
                marginEnd = dp(3)
            },
        )
        buttons.values.forEach { button ->
            addView(button, controlLayoutParams(48))
        }
        addView(
            sendWaveButton,
            controlLayoutParams(52).apply {
                marginStart = dp(2)
            },
        )
    }

    fun render(state: RenderState) {
        supplyGauge.setSupply(state.playerSupply.toFloat())
        buttons.forEach { (option, button) ->
            val enabled = state.statusLabel == "RUNNING" &&
                state.outcomeLabel == null &&
                state.playerSupply >= option.cost
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.38f
            button.background = transparentBackground()
        }
        val canSendWave = state.statusLabel == "RUNNING" &&
            state.outcomeLabel == null &&
            state.units.any { it.teamLabel == "BLUE" }
        sendWaveButton.isEnabled = canSendWave
        sendWaveButton.alpha = if (canSendWave) 1f else 0.38f
        sendWaveButton.background = circularBackground(canSendWave)
    }

    private fun controlLayoutParams(sizeDp: Int) = LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
    }

    private fun circularBackground(enabled: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (enabled) Color.argb(155, 45, 55, 58) else Color.argb(95, 35, 35, 35))
        setStroke(dp(1), Color.argb(170, 239, 220, 179))
    }

    private fun transparentBackground() = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private data class UnitOption(
        val type: String,
        val iconName: String,
        val cost: Int,
    )

}
