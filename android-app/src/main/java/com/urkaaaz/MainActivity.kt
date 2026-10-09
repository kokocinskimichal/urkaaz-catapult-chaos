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
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.urkaaaz.android.R
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
    private lateinit var matchTimerText: TextView
    private lateinit var windIndicator: ImageView
    private lateinit var timerWindPanel: View
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
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        battlefieldView = BattlefieldView(this)
        statusText = hudText("READY", 12f)
        selectedAmmoText = hudText("ROCK", 11f)
        matchTimerText = hudText("0:00", 12f).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        windIndicator = ImageView(this).apply {
            setImageResource(R.drawable.wind_low_right)
            contentDescription = "Wind direction and strength"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

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
        battlefieldView.onAimCancelled = { previousAim ->
            if (previousAim != null) {
                aimAngle = previousAim.angleDegrees
                aimPower = previousAim.power
                render(viewModel.dispatch(MatchUiAction.Aim(aimAngle, aimPower)))
            } else {
                render(viewModel.renderState)
            }
        }

        pauseButton = Button(this).apply {
            text = "II"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = buttonBackground(Color.argb(150, 61, 72, 78))
            minWidth = 28
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { render(viewModel.dispatch(MatchUiAction.TogglePause)) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(6, 6, 6, 6)
            addView(battlefieldView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(ammunitionPanel(), LinearLayout.LayoutParams(-1, 82))
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(28, 35, 39))
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(
                topHud(),
                FrameLayout.LayoutParams(-1, 180).apply {
                    gravity = Gravity.TOP
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

    private fun topHud(): View = FrameLayout(this).apply {
        clipChildren = false
        clipToPadding = false
        elevation = 8f
        timerWindPanel = timerWindPanel()
        addView(
            timerWindPanel,
            FrameLayout.LayoutParams(-2, 36).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },
        )
        addView(
            windIndicator,
            FrameLayout.LayoutParams(208, 136).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 38
            },
        )
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

    private fun timerWindPanel(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        background = panelBackground(Color.argb(210, 36, 43, 47))
        setPadding(6, 2, 6, 2)
        addView(
            matchTimerText,
            LinearLayout.LayoutParams(-2, 32).apply {
                gravity = Gravity.CENTER_VERTICAL
            },
        )
        addView(
            pauseButton,
            LinearLayout.LayoutParams(32, 30).apply {
                marginStart = 4
                gravity = Gravity.CENTER_VERTICAL
            },
        )
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
        statusText.text = state.statusLabel
        matchTimerText.text = formatMatchTime(state.matchTimeRemainingMilliseconds)
        timerWindPanel.visibility =
            if (state.statusLabel == "RUNNING" || state.statusLabel == "PAUSED") {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        windIndicator.visibility = timerWindPanel.visibility
        windIndicator.setImageResource(windIndicatorResource(state.windDirection, state.windStrength))
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

    private fun formatMatchTime(elapsedMilliseconds: Long): String {
        val totalSeconds = (elapsedMilliseconds / 1_000L).coerceAtLeast(0L)
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    private fun windIndicatorResource(direction: Float, strength: Float): Int {
        val isLeft = direction < 0f
        return when {
            strength >= 0.75f && isLeft -> R.drawable.wind_high_left
            strength >= 0.75f -> R.drawable.wind_high_right
            strength >= 0.5f && isLeft -> R.drawable.wind_medium_left
            strength >= 0.5f -> R.drawable.wind_medium_right
            isLeft -> R.drawable.wind_low_left
            else -> R.drawable.wind_low_right
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
        includeFontPadding = false
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
