package com.urkaaaz.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.CheckBox
import android.widget.TextView

class MatchScreenController(
    private val activity: Activity,
    campaignLevel: Int,
) {
    private val viewModel = MatchViewModel(campaignLevel = campaignLevel)
    private val gameHandler = Handler(Looper.getMainLooper())
    private lateinit var battlefieldView: BattlefieldView
    private lateinit var hudView: MatchHudView
    private lateinit var ammunitionPanel: AmmunitionPanelView
    private lateinit var unitPanel: UnitPanelView
    private lateinit var debugButton: TextView
    private lateinit var snapshotCloseButton: TextView
    private var snapshotMode = false

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
        unitPanel = UnitPanelView(activity).apply {
            onUnitSelected = { unitType ->
                render(viewModel.dispatch(MatchUiAction.DeployUnit(unitType)))
            }
            onSendWave = {
                render(viewModel.dispatch(MatchUiAction.SendWave))
            }
        }
        debugButton = TextView(activity).apply {
                text = "DEBUG"
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
                setOnClickListener { showDebugToolsDialog() }
        }
        snapshotCloseButton = TextView(activity).apply {
                text = "CLOSE SNAPSHOT"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(14), dp(7), dp(14), dp(7))
                setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
                visibility = View.GONE
                setOnClickListener { setSnapshotMode(false) }
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
        }
        return FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(content, FrameLayout.LayoutParams(-1, -1))
            addView(
                unitPanel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(68),
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    bottomMargin = dp(22)
                },
            )
            addView(
                ammunitionPanel,
                FrameLayout.LayoutParams(-1, dp(112)).apply {
                    gravity = Gravity.BOTTOM
                },
            )
            addView(
                hudView,
                FrameLayout.LayoutParams(-1, 180).apply {
                    gravity = Gravity.TOP
                },
            )
            addView(
                debugButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(180)
                    rightMargin = dp(12)
                },
            )
            addView(
                snapshotCloseButton,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(12)
                    rightMargin = dp(12)
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
        unitPanel.render(state)
        battlefieldView.render(state)
        debugButton.visibility = if (state.statusLabel == "RUNNING" && !snapshotMode) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showDebugToolsDialog() {
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
        }
        root.addView(TextView(activity).apply {
            text = "DEBUG TOOLS"
            setTextColor(Color.WHITE)
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(CheckBox(activity).apply {
            text = "Show hitboxes"
            setTextColor(Color.WHITE)
            buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
            isChecked = battlefieldView.debugHitboxesVisible
            setOnCheckedChangeListener { _, checked ->
                battlefieldView.debugHitboxesVisible = checked
            }
        })
        root.addView(TextView(activity).apply {
            text = "SNAPSHOT"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
            setOnClickListener {
                dialog.dismiss()
                setSnapshotMode(true)
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
        })
        root.addView(TextView(activity).apply {
            text = "ENEMY UNITS"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(4))
        })
        val unitChecks = mutableMapOf<String, CheckBox>()
        DEBUG_UNIT_IDS.sorted().forEach { unitId ->
            val checkBox = CheckBox(activity).apply {
                text = debugUnitLabel(unitId)
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.rgb(243, 210, 138))
                isChecked = unitId in viewModel.aiAllowedUnitIds()
            }
            unitChecks[unitId] = checkBox
            checkBox.setOnCheckedChangeListener { _, checked ->
                val selected = unitChecks
                    .filterValues { it.isChecked }
                    .keys
                    .toSet()
                if (!checked && selected.isEmpty()) {
                    checkBox.isChecked = true
                    return@setOnCheckedChangeListener
                }
                viewModel.setAiAllowedUnitIds(selected)
            }
            root.addView(checkBox)
        }
        root.addView(TextView(activity).apply {
            text = "CLOSE"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(com.urkaaaz.android.R.drawable.bg_hud_panel)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(14)
        })
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.45f).toInt(),
                -2,
            )
        }
        dialog.show()
    }

    private fun setSnapshotMode(enabled: Boolean) {
        snapshotMode = enabled
        battlefieldView.setSnapshotMode(enabled)
        hudView.visibility = if (enabled) View.GONE else View.VISIBLE
        ammunitionPanel.visibility = if (enabled) View.GONE else View.VISIBLE
        debugButton.visibility = if (enabled) View.GONE else View.VISIBLE
        snapshotCloseButton.visibility = if (enabled) View.VISIBLE else View.GONE
        battlefieldView.invalidate()
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun debugUnitLabel(id: String): String = when (id) {
        "RAGING_BOAR" -> "Raging Boar"
        "SLINGMASTER" -> "Slingmaster"
        else -> id.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private companion object {
        val DEBUG_UNIT_IDS = setOf(
            "DEFENDER",
            "SAPPER",
            "DEMOLISHER",
            "RAGING_BOAR",
            "SLINGMASTER",
        )
    }
}
