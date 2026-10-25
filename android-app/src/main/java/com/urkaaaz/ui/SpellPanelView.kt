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
import com.urkaaaz.screens.loadout.LoadoutAssetCatalog
import com.urkaaaz.screens.loadout.LoadoutAssetLookup
import com.urkaaaz.screens.loadout.LoadoutItemReference

class SpellPanelView(
    context: Context,
    spellTypes: List<String>,
) : LinearLayout(context) {
    private val slots = spellTypes.take(MAX_VISIBLE_SPELLS).mapNotNull(::createSlot)
    private val manaOrb = ManaOrbView(context)

    var onSpellCast: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(3), dp(8), dp(3), dp(3))
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                addView(
                    groupHeader(context.getString(R.string.match_hud_spells)),
                    LayoutParams(0, -1, 1f),
                )
                addView(
                    groupHeader(context.getString(R.string.match_hud_mana)),
                    LayoutParams(dp(80), -1).apply {
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
                slots.forEach { slot ->
                    addView(
                        slot.root,
                        LayoutParams(0, -1, 1f),
                    )
                }
                addView(
                    manaOrb,
                    LayoutParams(dp(80), dp(80)).apply {
                        marginStart = dp(1)
                    },
                )
            },
            LayoutParams(-1, 0, 1f),
        )
    }

    fun render(state: RenderState) {
        manaOrb.render(state.playerMana, LoadoutCatalog.maximumMana)
        slots.forEach { slot ->
            val active = slot.type in state.activeSpellTypes
            val enoughMana = state.playerMana >= slot.manaCost
            val ready = slot.implemented &&
                (!active || slot.recastableWhileActive) &&
                enoughMana &&
                state.statusLabel == "RUNNING"
            val remainingMilliseconds = state.activeSpellRemainingMilliseconds[slot.type]
            val remainingSeconds = remainingMilliseconds
                ?.let { ((it + 999L) / 1_000L).toInt() }
            val status = when {
                active && slot.recastableWhileActive && remainingSeconds != null ->
                    context.getString(
                        R.string.match_hud_spell_active_timer,
                        remainingSeconds,
                    )
                active -> context.getString(R.string.match_hud_active)
                !slot.implemented -> context.getString(R.string.match_hud_planned)
                state.statusLabel != "RUNNING" -> context.getString(R.string.match_hud_unavailable)
                !enoughMana -> context.getString(R.string.match_hud_low_mana)
                else -> context.getString(R.string.match_hud_ready)
            }
            slot.button.isEnabled = ready
            slot.button.alpha = when {
                active -> 1f
                ready -> 1f
                else -> 0.4f
            }
            if (active != slot.active || ready != slot.ready) {
                slot.button.background = HudPanelStyle.background(
                    context,
                    if (active) {
                        Color.argb(190, 44, 87, 119)
                    } else {
                        Color.argb(82, 28, 33, 36)
                    },
                    selected = active || ready,
                )
                slot.active = active
                slot.ready = ready
            }
            slot.button.contentDescription = context.getString(
                R.string.match_hud_spell_accessibility,
                context.getString(slot.nameResource),
                slot.manaCost,
                status,
            )
            val shieldCoveredFraction = if (slot.type == "FORTRESS_SHIELD" && active) {
                FortressShieldPresentation.shieldIconCoveredFraction(
                    health = state.blueFortressShieldHealth,
                    maxHealth = state.blueFortressShieldMaxHealth,
                )
            } else {
                0f
            }
            val durationCoveredFraction = if (active) {
                SpellIconProgressPresentation.coveredFraction(
                    remainingMilliseconds = remainingMilliseconds,
                    durationMilliseconds = slot.activeDurationMilliseconds,
                )
            } else {
                0f
            }
            val coveredFraction = maxOf(shieldCoveredFraction, durationCoveredFraction)
            slot.progressShade.coveredFraction = coveredFraction
            slot.progressShade.visibility =
                if (coveredFraction > 0f) View.VISIBLE else View.GONE
        }
    }

    private fun createSlot(type: String): SpellSlot? {
        val spell = LoadoutCatalog.findSpell(type) ?: return null
        val (nameResource, shortNameResource) = spellNameResources(type) ?: return null
        val artwork = LoadoutAssetCatalog.illustration(LoadoutItemReference.Spell(type))
        val iconResource = (artwork as? LoadoutAssetLookup.Found)?.drawableId
            ?: return null
        val button = ImageButton(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(0, 0, 0, 0)
            setImageResource(iconResource)
            background = HudPanelStyle.background(context, Color.argb(82, 28, 33, 36))
            clipToOutline = true
            setOnClickListener { onSpellCast?.invoke(type) }
        }
        val progressShade = VerticalProgressShade(context, dp(9).toFloat())
        val name = TextView(context).apply {
            text = context.getString(shortNameResource)
            contentDescription = context.getString(nameResource)
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = 8f
            maxLines = 1
            setTextColor(Color.rgb(239, 220, 179))
            UiTypography.applyButtonTypeface(this)
        }
        val costBadge = TextView(context).apply {
            text = spell.manaCost.toString()
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
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            clipChildren = true
            clipToPadding = true
            addView(
                FrameLayout(context).apply {
                    clipChildren = true
                    clipToPadding = true
                    addView(button, FrameLayout.LayoutParams(-1, -1))
                    addView(progressShade, FrameLayout.LayoutParams(-1, -1))
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
            addView(name, LayoutParams(-1, dp(12)))
        }
        return SpellSlot(
            type = type,
            manaCost = spell.manaCost,
            implemented = spell.implemented,
            recastableWhileActive = spell.recastableWhileActive,
            activeDurationMilliseconds = spell.activeDurationMilliseconds,
            nameResource = nameResource,
            button = button,
            progressShade = progressShade,
            root = root,
            active = false,
            ready = false,
        )
    }

    private fun spellNameResources(type: String): Pair<Int, Int>? = when (type) {
        "ICE_TRAP" ->
            R.string.loadout_name_ice_trap to R.string.match_hud_spell_short_ice_trap
        "RUNIC_BASTION" ->
            R.string.loadout_name_runic_bastion to R.string.match_hud_spell_short_runic_bastion
        "HASTE" -> R.string.loadout_name_haste to R.string.loadout_name_haste
        "HEAL" -> R.string.loadout_name_heal to R.string.loadout_name_heal
        "FORTRESS_SHIELD" ->
            R.string.loadout_name_fortress_shield to R.string.match_hud_spell_short_fortress_shield
        "CATAPULT_LOCK" ->
            R.string.loadout_name_catapult_lock to R.string.match_hud_spell_short_catapult_lock
        "SLOWING_FIELD" ->
            R.string.loadout_name_slowing_field to R.string.match_hud_spell_short_slowing_field
        "VAMPIRIC_BANNER" ->
            R.string.loadout_name_vampiric_banner to R.string.match_hud_spell_short_vampiric_banner
        "STONE_SKIN" ->
            R.string.loadout_name_stone_skin to R.string.loadout_name_stone_skin
        "SIEGE_FRENZY" ->
            R.string.loadout_name_siege_frenzy to R.string.match_hud_spell_short_siege_frenzy
        "BERSERKER" -> R.string.loadout_name_berserker to R.string.loadout_name_berserker
        else -> null
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

    private data class SpellSlot(
        val type: String,
        val manaCost: Int,
        val implemented: Boolean,
        val recastableWhileActive: Boolean,
        val activeDurationMilliseconds: Long?,
        val nameResource: Int,
        val button: ImageButton,
        val progressShade: VerticalProgressShade,
        val root: LinearLayout,
        var active: Boolean,
        var ready: Boolean,
    )

    private companion object {
        const val MAX_VISIBLE_SPELLS = 3
    }
}
