package com.urkaaaz.screens.loadout

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.navigation.ScreenContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoadoutScreen(
    context: Context,
    campaignLevel: Int,
    loadoutUseCases: LoadoutUseCases,
    onBack: () -> Unit,
    onStart: (PlayerLoadoutConfig) -> Unit,
) : ScreenContent {
    private val viewModel = LoadoutViewModel(campaignLevel, loadoutUseCases)
    private var screenScope: CoroutineScope? = null
    private lateinit var statusView: TextView
    private lateinit var selectedView: TextView
    private lateinit var progressionView: TextView
    private lateinit var startButton: Button

    override val view = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.bg_main_menu)
        }, FrameLayout.LayoutParams(-1, -1))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 22), dp(context, 18), dp(context, 22), dp(context, 18))
            addView(TextView(context).apply {
                text = "LOADOUT · LEVEL ${viewModel.state.level}"
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(255, 211, 106))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2))

            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
                setBackgroundResource(R.drawable.bg_hud_panel)
                addCategory(context, "UNITS AVAILABLE", viewModel.state.availableUnits)
                addCategory(
                    context,
                    "AMMUNITION AVAILABLE",
                    viewModel.state.availableAmmunition,
                )
                addCategory(context, "SPELLS AVAILABLE ON THIS LEVEL", viewModel.state.availableSpells)
                addView(TextView(context).apply {
                    text = "SELECTED LOADOUT"
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(context, 14), 0, dp(context, 4))
                }, LinearLayout.LayoutParams(-1, -2))
                selectedView = TextView(context).apply {
                    textSize = 14f
                    setTextColor(Color.LTGRAY)
                }
                addView(selectedView, LinearLayout.LayoutParams(-1, -2))
                progressionView = TextView(context).apply {
                    textSize = 14f
                    setTextColor(Color.LTGRAY)
                    setPadding(0, dp(context, 8), 0, 0)
                }
                addView(progressionView, LinearLayout.LayoutParams(-1, -2))
                statusView = TextView(context).apply {
                    textSize = 15f
                    setTextColor(Color.rgb(255, 205, 135))
                    setPadding(0, dp(context, 14), 0, 0)
                }
                addView(statusView, LinearLayout.LayoutParams(-1, -2))
            }

            addView(ScrollView(context).apply {
                addView(details)
            }, LinearLayout.LayoutParams(-1, 0, 1f).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 18)
            })

            startButton = Button(context).apply {
                text = "START MATCH"
                isEnabled = false
                alpha = 0.45f
                setOnClickListener {
                    screenScope?.launch {
                        val loadout = viewModel.commit()
                        render()
                        loadout?.let(onStart)
                    }
                }
            }
            addView(startButton, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, 16)
            })
            addView(Button(context).apply {
                text = "BACK"
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, 8)
            })
        }
        addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onResume() {
        if (screenScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        screenScope = scope
        scope.launch {
            viewModel.load()
            render()
        }
    }

    override fun onPause() {
        screenScope?.cancel()
        screenScope = null
    }

    private fun render() {
        val state = viewModel.state
        selectedView.text = state.draft?.let { draft ->
            "Units: ${draft.unitTypes.joinToString().ifEmpty { "None" }}\n" +
                "Ammunition: ${draft.ammunitionSlots.filterNotNull().joinToString()}\n" +
                "Spells: ${draft.spellTypes.joinToString().ifEmpty { "None" }}"
        } ?: "No loadout available."
        progressionView.text = state.progression?.let { progression ->
            "Gold: ${progression.goldBalance}  ·  " +
                "Ammunition stock: ${progression.ammunitionStocks.values.sumOf { it.toLong() }}  ·  " +
                "Special spell stock: ${progression.specialSpellStocks.values.sumOf { it.toLong() }}"
        }.orEmpty()
        statusView.text = when {
            state.isLoading -> "Loading progression and saved loadout…"
            state.isSaving -> "Saving loadout…"
            state.errorMessage != null -> state.errorMessage
            state.corrections.isNotEmpty() ->
                "Saved loadout was normalized: ${state.corrections.joinToString { it.reason.name }}."
            else -> "Loadout validated and ready."
        }
        startButton.isEnabled = state.canStart
        startButton.alpha = if (state.canStart) 1f else 0.45f
    }
}

private fun LinearLayout.addCategory(
    context: Context,
    title: String,
    identifiers: List<String>,
) {
    addView(TextView(context).apply {
        text = title
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(0, dp(context, 12), 0, dp(context, 4))
    }, LinearLayout.LayoutParams(-1, -2))
    addView(TextView(context).apply {
        text = identifiers.ifEmpty { listOf("None available") }.joinToString("  ·  ")
        textSize = 14f
        setTextColor(Color.LTGRAY)
    }, LinearLayout.LayoutParams(-1, -2))
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
