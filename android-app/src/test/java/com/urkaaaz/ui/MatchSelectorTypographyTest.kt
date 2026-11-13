package com.urkaaaz.ui

import android.view.View
import android.widget.TextView
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.localization.LocalizedContext
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MatchSelectorTypographyTest {
    @Test
    fun localizedUnitAmmunitionAndSpellLabelsFitTheirSelectorWidth() {
        val controls = listOf(
            ButtonTypographyControl.MATCH_UNIT_SAPPER,
            ButtonTypographyControl.MATCH_UNIT_DEFENDER,
            ButtonTypographyControl.MATCH_UNIT_DEMOLISHER,
            ButtonTypographyControl.MATCH_UNIT_BOAR,
            ButtonTypographyControl.MATCH_UNIT_SLINGMASTER,
            ButtonTypographyControl.MATCH_AMMO_ROCK,
            ButtonTypographyControl.MATCH_AMMO_SIEGE,
            ButtonTypographyControl.MATCH_AMMO_BARREL,
            ButtonTypographyControl.MATCH_AMMO_FIRE_RAIN,
            ButtonTypographyControl.MATCH_AMMO_PLAGUE,
            ButtonTypographyControl.MATCH_SPELL_ICE_TRAP,
            ButtonTypographyControl.MATCH_SPELL_RUNIC_BASTION,
            ButtonTypographyControl.MATCH_SPELL_HASTE,
            ButtonTypographyControl.MATCH_SPELL_HEAL,
            ButtonTypographyControl.MATCH_SPELL_FORTRESS_SHIELD,
            ButtonTypographyControl.MATCH_SPELL_CATAPULT_LOCK,
            ButtonTypographyControl.MATCH_SPELL_SLOWING_FIELD,
            ButtonTypographyControl.MATCH_SPELL_VAMPIRIC_BANNER,
            ButtonTypographyControl.MATCH_SPELL_STONE_SKIN,
            ButtonTypographyControl.MATCH_SPELL_SIEGE_FRENZY,
            ButtonTypographyControl.MATCH_SPELL_BERSERKER,
        )
        val base = RuntimeEnvironment.getApplication()

        listOf(AppLanguage.EN, AppLanguage.PL, AppLanguage.ES).forEach { language ->
            val context = LocalizedContext(base, language)
            val density = context.resources.displayMetrics.density
            val width = (SELECTOR_LABEL_WIDTH_DP * density).roundToInt()

            controls.forEach { control ->
                val label = TextView(context).apply {
                    text = context.getString(control.stringResource)
                    maxLines = 1
                    includeFontPadding = false
                    UiTypography.applyButtonTypeface(this)
                    UiTypography.applyTextSize(this, control)
                }
                label.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(
                        (40f * density).roundToInt(),
                        View.MeasureSpec.AT_MOST,
                    ),
                )

                val lineWidth = requireNotNull(label.layout).getLineWidth(0)
                assertTrue(
                    "$language ${control.controlId} width $lineWidth exceeds $width px",
                    lineWidth <= width + 0.5f,
                )
            }
        }
    }

    private companion object {
        const val SELECTOR_LABEL_WIDTH_DP = 60f
    }
}
