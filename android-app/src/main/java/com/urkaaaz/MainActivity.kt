package com.urkaaaz

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.ui.BattlefieldView
import com.urkaaaz.ui.MatchUiAction
import com.urkaaaz.ui.MatchViewModel
import com.urkaaaz.ui.RenderState

class MainActivity : Activity() {
    private val viewModel = MatchViewModel()
    private lateinit var battlefieldView: BattlefieldView
    private lateinit var statusText: TextView
    private lateinit var pauseButton: Button
    private lateinit var selectedAmmoText: TextView
    private lateinit var blueHpText: TextView
    private lateinit var redHpText: TextView
    private val ammoButtons = mutableMapOf<String, ImageButton>()
    private var selectedAmmo = "ROCK"
    private var aimAngle = 45f
    private var aimPower = 50f
    private val gameHandler = Handler(Looper.getMainLooper())
    private val gameTick = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                val state = if (viewModel.renderState.statusLabel == "RUNNING") {
                    viewModel.dispatch(MatchUiAction.AdvanceSimulation)
                } else {
                    viewModel.renderState
                }
                render(state)
                gameHandler.postDelayed(this, 33L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        battlefieldView = BattlefieldView(this)
        statusText = hudText("READY", 12f)
        selectedAmmoText = hudText("ROCK", 11f)

        battlefieldView.onAimChanged = { aim ->
            aimAngle = aim.angleDegrees
            aimPower = aim.power
            if (viewModel.renderState.outcomeLabel == null) {
                render(viewModel.dispatch(MatchUiAction.Aim(aimAngle, aimPower)))
            }
        }
        battlefieldView.onAimReleased = { aim ->
            aimAngle = aim.angleDegrees
            aimPower = aim.power
            render(viewModel.dispatch(MatchUiAction.Aim(aimAngle, aimPower)))
            render(viewModel.dispatch(MatchUiAction.FireRock))
        }
        battlefieldView.onAimCancelled = { render(viewModel.renderState) }

        pauseButton = Button(this).apply {
            text = "II"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = buttonBackground(Color.rgb(61, 72, 78))
            setOnClickListener { render(viewModel.dispatch(MatchUiAction.TogglePause)) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(28, 35, 39))
            setPadding(6, 6, 6, 6)
            addView(topHud(), LinearLayout.LayoutParams(-1, 58))
            addView(battlefieldView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(ammunitionPanel(), LinearLayout.LayoutParams(-1, 82))
            addView(
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(pauseButton, LinearLayout.LayoutParams(58, 48))
                },
                LinearLayout.LayoutParams(-1, 54).apply {
                    gravity = Gravity.CENTER
                },
            )
        }
        setContentView(root)
        render(viewModel.dispatch(MatchUiAction.StartMatch))
    }

    override fun onResume() {
        super.onResume()
        gameHandler.removeCallbacks(gameTick)
        gameHandler.postDelayed(gameTick, 33L)
    }

    override fun onPause() {
        gameHandler.removeCallbacks(gameTick)
        super.onPause()
    }

    private fun topHud(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(4, 3, 4, 3)
        addView(hpPanel("BLUE", Color.rgb(48, 104, 190)), LinearLayout.LayoutParams(0, -1, 1f))
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(statusText, LinearLayout.LayoutParams(-1, 27))
                addView(selectedAmmoText, LinearLayout.LayoutParams(-1, 20))
            },
            LinearLayout.LayoutParams(0, -1, 1.1f),
        )
        addView(hpPanel("RED", Color.rgb(180, 58, 52)), LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun hpPanel(label: String, color: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = panelBackground(color)
        addView(TextView(this@MainActivity).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 10f
            setTextColor(Color.argb(210, 255, 255, 255))
        }, LinearLayout.LayoutParams(-1, 20))
        val healthText = TextView(this@MainActivity).apply {
            text = "0 / 0"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        if (label == "BLUE") blueHpText = healthText else redHpText = healthText
        addView(healthText, LinearLayout.LayoutParams(-1, 30))
    }

    private fun ammunitionPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(4, 2, 4, 2)
        addView(ammoSlot("ROCK", "ROCK", "rock_flight_00"), LinearLayout.LayoutParams(0, -1, 1f))
        addView(ammoSlot("SIEGE", "SIEGE_BOMB", "ammo_icon_siege_bomb"), LinearLayout.LayoutParams(0, -1, 1f))
        addView(ammoSlot("BARREL", "POWDER_BARREL", "ammo_icon_powder_barrel"), LinearLayout.LayoutParams(0, -1, 1f))
        addView(ammoSlot("CLUSTER", "CLUSTER_BOMB", "cluster_bomb_icon"), LinearLayout.LayoutParams(0, -1, 1f))
        addView(ammoSlot("FIRE", "FIRE_RAIN", "fire_rain_icon"), LinearLayout.LayoutParams(0, -1, 1f))
        addView(ammoSlot("PLAGUE", "PLAGUE_CAULDRON", "plague_cauldron_icon"), LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun ammoSlot(label: String, ammunitionType: String, iconName: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val icon = ImageButton(this@MainActivity).apply {
                val id = resources.getIdentifier(iconName, "drawable", packageName)
                if (id != 0) setImageResource(id)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(5, 5, 5, 5)
                background = panelBackground(
                    if (ammunitionType == selectedAmmo) Color.rgb(184, 129, 48) else Color.rgb(73, 61, 49),
                )
                contentDescription = label
                ammoButtons[ammunitionType] = this
                setOnClickListener {
                    selectedAmmo = ammunitionType
                    selectedAmmoText.text = label
                    updateAmmoSelection()
                    render(viewModel.dispatch(MatchUiAction.SelectAmmo(ammunitionType)))
                }
            }
            addView(icon, LinearLayout.LayoutParams(-1, 56))
            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 8f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(239, 220, 179))
            }, LinearLayout.LayoutParams(-1, 20))
        }

    private fun render(state: RenderState) {
        statusText.text = "${state.statusLabel}  ·  WIND ${state.windStrength.toInt()}"
        blueHpText.text = "${state.blueFortressHealth} / ${state.blueFortressMaxHealth}"
        redHpText.text = "${state.redFortressHealth} / ${state.redFortressMaxHealth}"
        val selectedCount = state.playerAmmunition[selectedAmmo]
        selectedAmmoText.text = "$selectedAmmo  ·  ${selectedCount ?: "∞"}"
        battlefieldView.render(state)
        pauseButton.text = if (state.statusLabel == "PAUSED") "▶" else "II"
        ammoButtons.forEach { (type, button) ->
            val count = state.playerAmmunition[type]
            button.isEnabled = count == null || count > 0
            button.alpha = if (button.isEnabled) 1f else 0.35f
        }
    }

    private fun updateAmmoSelection() {
        ammoButtons.forEach { (type, button) ->
            button.background = panelBackground(
                if (type == selectedAmmo) Color.rgb(184, 129, 48) else Color.rgb(73, 61, 49),
            )
        }
    }

    private fun hudText(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(239, 220, 179))
        setPadding(4, 2, 4, 2)
    }

    private fun panelBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 10f
        setStroke(1, Color.argb(120, 255, 235, 190))
    }

    private fun buttonBackground(color: Int) = panelBackground(color)
}
