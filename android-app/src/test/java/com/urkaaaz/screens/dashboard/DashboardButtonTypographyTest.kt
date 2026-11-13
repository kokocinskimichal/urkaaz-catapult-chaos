package com.urkaaaz.screens.dashboard

import android.view.View
import android.widget.TextView
import com.urkaaaz.application.language.AppLanguage
import com.urkaaaz.localization.LocalizedContext
import com.urkaaaz.ui.ButtonTypographyControl
import com.urkaaaz.ui.UiTypography
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardButtonTypographyTest {
    @Test
    fun testModeButtonLabelFitsOnOneLineInEveryLanguage() {
        val base = RuntimeEnvironment.getApplication()

        listOf(AppLanguage.EN, AppLanguage.PL, AppLanguage.ES).forEach { language ->
            val context = LocalizedContext(base, language)
            val density = context.resources.displayMetrics.density
            val contentWidth = (TEST_MODE_BUTTON_WIDTH_DP - BUTTON_HORIZONTAL_PADDING_DP * 2) *
                density
            val typographyControl = ButtonTypographyControl.DASHBOARD_TEST_MODE
            val label = TextView(context).apply {
                text = context.getString(typographyControl.stringResource)
                UiTypography.applyButtonTypeface(this)
                UiTypography.applyTextSize(this, typographyControl)
                maxLines = 1
                includeFontPadding = false
            }

            label.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth.roundToInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                    (100f * density).roundToInt(),
                    View.MeasureSpec.AT_MOST,
                ),
            )

            val layout = requireNotNull(label.layout)
            assertEquals(1, layout.lineCount)
            assertTrue(
                "$language label width ${layout.getLineWidth(0)} exceeds $contentWidth px",
                layout.getLineWidth(0) <= contentWidth + 0.5f,
            )
        }
    }

    private companion object {
        const val TEST_MODE_BUTTON_WIDTH_DP = 126f
        const val BUTTON_HORIZONTAL_PADDING_DP = 8f
    }
}
