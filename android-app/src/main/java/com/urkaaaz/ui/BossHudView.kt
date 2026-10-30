package com.urkaaaz.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.contracts.BossArchetype
import com.urkaaaz.contracts.BossPhase

class BossHudView(context: Context) : LinearLayout(context) {
    private val nameText = textView(12f)
    private val healthText = textView(11f)
    private val phaseText = textView(9f)
    private val healthBar = progressBar(Color.rgb(211, 76, 62))
    private val blueDamageText = textView(9f).apply {
        setTextColor(Color.rgb(105, 183, 238))
    }
    private val redDamageText = textView(9f).apply {
        setTextColor(Color.rgb(229, 111, 92))
    }
    private val blueDamageBar = progressBar(Color.rgb(74, 153, 211))
    private val redDamageBar = progressBar(Color.rgb(188, 76, 61))
    private val damageRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = HudPanelStyle.universalFrameBackground(context)
        HudPanelStyle.applyShadow(this, elevationDp = 2f)
        setPadding(dp(9), dp(3), dp(9), dp(4))
        clipChildren = false
        clipToPadding = false

        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(nameText, LayoutParams(0, dp(18), 1f))
                addView(healthText, LayoutParams(LayoutParams.WRAP_CONTENT, dp(18)))
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(18)),
        )
        addView(healthBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(5)))
        addView(phaseText, LayoutParams(LayoutParams.MATCH_PARENT, dp(14)))
        damageRow.addView(blueDamageText, LayoutParams(dp(54), dp(15)))
        damageRow.addView(blueDamageBar, LayoutParams(0, dp(5), 1f))
        damageRow.addView(Space(context), LayoutParams(dp(6), dp(1)))
        damageRow.addView(redDamageBar, LayoutParams(0, dp(5), 1f))
        damageRow.addView(redDamageText, LayoutParams(dp(54), dp(15)))
        addView(damageRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(15)))
        visibility = View.GONE
    }

    fun render(boss: BossRenderState?) {
        if (boss == null || boss.health <= 0) {
            visibility = View.GONE
            return
        }
        val presentation = BossPresentationCatalog.definition(boss.presentationKey)
        nameText.setText(presentation.nameResource)
        healthText.text = context.getString(
            R.string.boss_hud_health,
            boss.health,
            boss.maxHealth,
        )
        phaseText.text = context.getString(phaseLabel(boss.phase))
        healthBar.progress = progress(boss.health, boss.maxHealth)
        if (boss.archetype == BossArchetype.CENTRAL_OBJECTIVE) {
            val totalDamage = boss.blueDamage + boss.redDamage
            blueDamageText.text = context.getString(R.string.boss_hud_blue_damage, boss.blueDamage)
            redDamageText.text = context.getString(R.string.boss_hud_red_damage, boss.redDamage)
            blueDamageBar.progress = progress(boss.blueDamage, totalDamage)
            redDamageBar.progress = progress(boss.redDamage, totalDamage)
            damageRow.visibility = View.VISIBLE
        } else {
            damageRow.visibility = View.GONE
        }
        contentDescription = context.getString(
            R.string.boss_hud_accessibility,
            context.getString(presentation.nameResource),
            boss.health,
            boss.maxHealth,
        )
        visibility = View.VISIBLE
    }

    private fun phaseLabel(phase: BossPhase): Int = when (phase) {
        BossPhase.IDLE -> R.string.boss_phase_idle
        BossPhase.MARCHING -> R.string.boss_phase_marching
        BossPhase.ENGAGED -> R.string.boss_phase_engaged
        BossPhase.ENRAGED -> R.string.boss_phase_enraged
        BossPhase.AT_GATE -> R.string.boss_phase_at_gate
        BossPhase.DEFEATED -> R.string.boss_phase_defeated
    }

    private fun progress(value: Int, maximum: Int): Int =
        if (maximum <= 0) 0 else (value.toLong() * BAR_MAX / maximum).toInt().coerceIn(0, BAR_MAX)

    private fun progressBar(color: Int) = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal,
    ).apply {
        max = BAR_MAX
        progressTintList = ColorStateList.valueOf(color)
        progressBackgroundTintList = ColorStateList.valueOf(Color.argb(90, 8, 10, 12))
    }

    private fun textView(sizeSp: Float) = TextView(context).apply {
        textSize = sizeSp
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setTextColor(Color.rgb(239, 220, 179))
        UiTypography.applyButtonTypeface(this)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BAR_MAX = 1_000
    }
}
