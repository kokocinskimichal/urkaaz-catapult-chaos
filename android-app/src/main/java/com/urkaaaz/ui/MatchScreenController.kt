package com.urkaaaz.ui

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout

class MatchScreenController(
    private val activity: Activity,
    campaignLevel: Int,
) {
    private val viewModel = MatchViewModel(campaignLevel = campaignLevel)
    private val gameHandler = Handler(Looper.getMainLooper())
    private lateinit var battlefieldView: BattlefieldView
    private lateinit var hudView: MatchHudView
    private lateinit var ammunitionPanel: AmmunitionPanelView

    private var aimAngle = 45f
    private var aimPower = 50f

    private val gameTick = object : Runnable {
        override fun run() {
            if (!activity.isFinishing) {
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

    fun createView(): FrameLayout {
        battlefieldView = BattlefieldView(activity)
        hudView = MatchHudView(activity).apply {
            onPauseClicked = {
                render(viewModel.dispatch(MatchUiAction.TogglePause))
            }
        }
        ammunitionPanel = AmmunitionPanelView(activity).apply {
            onAmmoSelected = { ammunitionType ->
                render(viewModel.dispatch(MatchUiAction.SelectAmmo(ammunitionType)))
            }
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

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(6, 6, 6, 6)
            addView(battlefieldView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(ammunitionPanel, LinearLayout.LayoutParams(-1, 82))
        }
        return FrameLayout(activity).apply {
            setBackgroundColor(Color.rgb(28, 35, 39))
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(
                hudView,
                FrameLayout.LayoutParams(-1, 180).apply {
                    gravity = Gravity.TOP
                },
            )
            render(viewModel.dispatch(MatchUiAction.StartMatch))
        }
    }

    fun onResume() {
        gameHandler.removeCallbacks(gameTick)
        gameHandler.postDelayed(gameTick, 33L)
    }

    fun onPause() {
        gameHandler.removeCallbacks(gameTick)
    }

    private fun render(state: RenderState) {
        hudView.render(state)
        ammunitionPanel.render(state)
        battlefieldView.render(state)
    }
}
