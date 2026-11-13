package com.urkaaaz.screens.testmode

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.ui.ButtonTypographyControl
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.ScreenChromeMetrics
import com.urkaaaz.ui.UiTypography
import kotlin.math.roundToInt

class TestLevelPickerScreen(
    private val context: Context,
    private val onBack: () -> Unit,
    private val onLevelSelected: (Int) -> Unit,
) : ScreenContent {
    override val view: View = createView()

    private fun createView(): View = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.bg_main_menu)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(ImageView(context).apply {
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.FIT_XY
            setImageResource(R.drawable.campaign_map_frame_overlay)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(createHeader(), FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP))
        addView(
            createLevelList(),
            FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = dp(64)
                bottomMargin = dp(8)
                leftMargin = dp(18)
                rightMargin = dp(18)
            },
        )
    }

    private fun createHeader(): View = FrameLayout(context).apply {
        addView(TextView(context).apply {
            text = context.getString(R.string.loadout_back)
            UiTypography.applyTextSize(this, ButtonTypographyControl.COMMON_LOADOUT_BACK)
            UiTypography.applyButtonTypeface(this)
            setTextColor(Color.rgb(64, 38, 20))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.loadout_start_match_button_xs_short)
            contentDescription = context.getString(R.string.loadout_back)
            isFocusable = true
            setOnClickListener { onBack() }
        }, FrameLayout.LayoutParams(
            dp(ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP),
            dp(ScreenChromeMetrics.BACK_BUTTON_HEIGHT_DP),
            Gravity.TOP or Gravity.START,
        ).apply {
            topMargin = dp(ScreenChromeMetrics.EDGE_INSET_DP)
            marginStart = dp(ScreenChromeMetrics.EDGE_INSET_DP)
        })

        addView(TextView(context).apply {
            text = context.getString(R.string.test_level_picker_title)
            textSize = 20f
            UiTypography.applyButtonTypeface(this)
            setTextColor(Color.rgb(255, 222, 154))
            gravity = Gravity.CENTER
            setShadowLayer(3f, 0f, 2f, Color.BLACK)
        }, FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP).apply {
            topMargin = dp(6)
            leftMargin = dp(150)
            rightMargin = dp(24)
        })
    }

    private fun createLevelList(): View = ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        clipToPadding = false
        setPadding(dp(6), dp(6), dp(6), dp(18))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            TestLevelPickerCatalog.chapters().forEach { chapter ->
                addView(createChapterSection(chapter), LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                })
            }
        })
    }

    private fun createChapterSection(chapter: TestLevelPickerChapter): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.argb(190, 33, 27, 21))
                setStroke(dp(1), Color.argb(190, 180, 125, 62))
            }
            addView(TextView(context).apply {
                text = context.getString(
                    R.string.test_level_picker_chapter,
                    chapter.number,
                    context.getString(chapter.titleResource),
                )
                textSize = 15f
                typeface = Typeface.create("serif", Typeface.BOLD)
                setTextColor(Color.rgb(255, 221, 155))
                setShadowLayer(2f, 0f, 1f, Color.BLACK)
            }, LinearLayout.LayoutParams(-1, dp(26)))

            chapter.levels.chunked(LEVELS_PER_ROW).forEach { rowLevels ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                rowLevels.forEach { option ->
                    row.addView(
                        levelButton(option),
                        LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                            setMargins(dp(3), dp(2), dp(3), dp(2))
                        },
                    )
                }
                repeat(LEVELS_PER_ROW - rowLevels.size) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                        setMargins(dp(3), dp(2), dp(3), dp(2))
                    })
                }
                addView(row, LinearLayout.LayoutParams(-1, -2))
            }
        }

    private fun levelButton(option: TestLevelOption): View =
        TextView(context).apply {
            val typographyControl = if (option.isBoss) {
                ButtonTypographyControl.TEST_BOSS_LEVEL
            } else {
                ButtonTypographyControl.TEST_LEVEL
            }
            text = context.getString(typographyControl.stringResource, option.level)
            UiTypography.applyTextSize(this, typographyControl)
            UiTypography.applyButtonTypeface(this)
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(
                if (option.isBoss) {
                    Color.rgb(255, 225, 178)
                } else {
                    Color.rgb(250, 239, 216)
                },
            )
            background = GradientDrawable().apply {
                cornerRadius = dp(5).toFloat()
                setColor(
                    if (option.isBoss) {
                        Color.argb(220, 111, 48, 28)
                    } else {
                        Color.argb(210, 54, 43, 32)
                    },
                )
                setStroke(
                    dp(if (option.isBoss) 2 else 1),
                    if (option.isBoss) {
                        Color.rgb(220, 157, 76)
                    } else {
                        Color.argb(150, 178, 139, 94)
                    },
                )
            }
            contentDescription = context.getString(
                R.string.test_level_picker_level_accessibility,
                option.level,
                if (option.isBoss) {
                    context.getString(R.string.campaign_marker_type_boss)
                } else {
                    context.getString(R.string.campaign_marker_type_fortress)
                },
            )
            isFocusable = true
            setOnClickListener { onLevelSelected(option.level) }
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val LEVELS_PER_ROW = 5
    }
}
