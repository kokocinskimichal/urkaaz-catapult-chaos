package com.urkaaaz.screens.campaign

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.campaign.CampaignLevelSelection
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignSelectionRejection
import com.urkaaaz.application.campaign.CampaignSystem
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignMapLayoutFailure
import com.urkaaaz.campaign.CampaignMapLayoutRead
import com.urkaaaz.campaign.CampaignMapLayoutRepository
import com.urkaaaz.campaign.CampaignMapLandmarkKind
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.UiTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CampaignScreen(
    private val context: Context,
    campaignSystem: CampaignSystem,
    private val onBack: () -> Unit,
    private val onLevelSelected: (Int) -> Unit,
) : ScreenContent {
    private val viewModel = CampaignViewModel(campaignSystem)
    private val pageContent = FrameLayout(context)
    private val chapterTabs = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val chapterTabsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        setPadding(dp(6), dp(2), dp(6), dp(2))
        addView(chapterTabs, LinearLayout.LayoutParams(-2, -1))
    }
    private val campaignLabelText = TextView(context).apply {
        textSize = 10f
        setTextColor(Color.rgb(255, 242, 216))
        maxLines = 1
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }
    private val chapterNumberText = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.rgb(89, 49, 26))
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    }
    private val titleText = TextView(context).apply {
        textSize = 14f
        setTextColor(Color.rgb(89, 49, 26))
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val chapterProgressBar = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal,
    ).apply {
        max = 10
        isIndeterminate = false
        progressTintList = ColorStateList.valueOf(Color.rgb(126, 190, 39))
        progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(54, 39, 27))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val chapterProgressLabel = TextView(context).apply {
        textSize = 9f
        setTextColor(Color.rgb(255, 239, 203))
        gravity = Gravity.CENTER
        maxLines = 1
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val chapterProgressPanel = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(Color.rgb(47, 34, 24))
            setStroke(dp(1), Color.rgb(136, 88, 43))
        }
        contentDescription = ""
        isFocusable = true
        addView(
            chapterProgressBar,
            FrameLayout.LayoutParams(-1, dp(9), Gravity.CENTER).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            },
        )
        addView(chapterProgressLabel, FrameLayout.LayoutParams(-1, -1))
    }
    private val startLevelButton = Button(context).apply {
        text = context.getString(R.string.campaign_start_level)
        textSize = 17f
        UiTypography.applyButtonTypeface(this)
        setTextColor(Color.rgb(64, 38, 20))
        setBackgroundResource(R.drawable.loadout_start_match_button)
        isEnabled = false
        visibility = View.GONE
        contentDescription = context.getString(
            R.string.campaign_start_level_unavailable_accessibility,
        )
        setOnClickListener { startSelectedLevel() }
    }
    private var screenScope: CoroutineScope? = null

    override val view: View = createView()

    init {
        render(viewModel.state)
    }

    override fun onResume() {
        if (screenScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        screenScope = scope
        scope.launch {
            render(viewModel.load())
        }
    }

    override fun onPause() {
        screenScope?.cancel()
        screenScope = null
    }

    private fun createView(): View = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.bg_main_menu)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(pageContent, FrameLayout.LayoutParams(-1, -1))
        addView(ImageView(context).apply {
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.FIT_XY
            setImageResource(R.drawable.campaign_map_frame_overlay)
        }, FrameLayout.LayoutParams(-1, -1))
        addView(
            createChapterStrip(),
            FrameLayout.LayoutParams(-2, dp(52), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(12)
            },
        )
        addView(
            startLevelButton,
            FrameLayout.LayoutParams(dp(220), dp(54), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        )
        addView(
            createTopBar(),
            FrameLayout.LayoutParams(dp(260), dp(90), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(4)
                marginStart = dp(4)
            },
        )
    }

    private fun createTopBar(): View = FrameLayout(context).apply {
        addView(TextView(context).apply {
            text = context.getString(R.string.loadout_back)
            textSize = 13f
            UiTypography.applyButtonTypeface(this)
            setTextColor(Color.rgb(64, 38, 20))
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.loadout_start_match_button_xs_short)
            isFocusable = true
            setOnClickListener { onBack() }
        }, FrameLayout.LayoutParams(dp(96), dp(48), Gravity.TOP or Gravity.START))

        addView(
            campaignLabelText,
            FrameLayout.LayoutParams(dp(146), dp(16), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(102)
                topMargin = dp(8)
            },
        )
        addView(
            chapterNumberText,
            FrameLayout.LayoutParams(dp(146), dp(18), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(102)
                topMargin = dp(27)
            },
        )
        addView(
            titleText,
            FrameLayout.LayoutParams(dp(146), dp(20), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(102)
                topMargin = dp(47)
            },
        )
        addView(
            chapterProgressPanel,
            FrameLayout.LayoutParams(dp(146), dp(18), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(102)
                topMargin = dp(68)
            },
        )
    }

    private fun createChapterStrip(): View = chapterTabsScroll.apply {
        visibility = View.GONE
        background = null
    }

    private fun render(state: CampaignUiState) {
        pageContent.removeAllViews()
        startLevelButton.visibility = View.GONE
        chapterTabsScroll.visibility = View.GONE
        campaignLabelText.text = ""
        chapterNumberText.text = ""
        titleText.text = ""
        chapterProgressBar.max = 10
        chapterProgressBar.progress = 0
        chapterProgressLabel.text = ""
        chapterProgressPanel.contentDescription = ""
        if (state.isLoading) {
            addStatus(context.getString(R.string.campaign_status_loading))
            return
        }
        if (state.failure != null) {
            addStatus(progressFailureMessage(state.failure))
            addRetryButton()
            return
        }
        if (state.progress == null || state.mapProgression == null) {
            addStatus(context.getString(R.string.campaign_status_unavailable))
            return
        }

        val page = state.mapProgression.pages.firstOrNull {
            it.chapter.id == state.selectedChapterId
        }
        if (page == null) {
            addStatus(context.getString(R.string.campaign_status_unavailable))
            return
        }

        chapterTabsScroll.visibility = View.VISIBLE
        renderChapterTabs(state.mapProgression.pages, page.chapter.id)
        renderChapterHeader(page)

        when (val layout = CampaignMapLayoutRepository.bundled.load(page.chapter.id)) {
            is CampaignMapLayoutRead.Available ->
                when (val presentation = CampaignMapPresentationCatalog.forLayout(layout.layout)) {
                    is CampaignMapPresentationRead.Available ->
                        pageContent.addView(
                            CampaignMapView(
                                context = context,
                                presentation = presentation.presentation,
                                markers = page.markers,
                                selectedLevel = state.selectedLevel,
                                onMarkerSelected = ::selectMapLevel,
                            ),
                            FrameLayout.LayoutParams(-1, -1),
                        )
                    is CampaignMapPresentationRead.Failure ->
                        addStatus(context.getString(R.string.campaign_map_layout_invalid))
                }
            is CampaignMapLayoutRead.MapArtworkMissing ->
                renderChapterLevelList(page, state.selectedLevel)
            is CampaignMapLayoutRead.Failure ->
                addStatus(context.getString(campaignMapLayoutFailureMessage(layout.reason)))
        }
        renderStartLevelButton(page, state.selectedLevel)
        state.selectionRejection?.let { rejection ->
            addSelectionRejection(rejection)
        }
    }

    private fun renderChapterTabs(
        pages: List<CampaignChapterPageState>,
        selectedChapterId: String,
    ) {
        chapterTabs.removeAllViews()
        pages.forEach { page ->
            val chapterNumber = chapterNumber(page.chapter.id)
            val isSelected = page.chapter.id == selectedChapterId
            val statusString = chapterStatus(page.status)
            val label = when (page.status) {
                CampaignChapterPageStatus.COMPLETED ->
                    R.string.campaign_chapter_tab_completed
                CampaignChapterPageStatus.CURRENT ->
                    R.string.campaign_chapter_tab_current
                CampaignChapterPageStatus.PREVIEW ->
                    R.string.campaign_chapter_tab_preview
            }
            val tab = TextView(context).apply {
                text = context.getString(label, chapterNumber)
                UiTypography.applyButtonTypeface(this)
                textSize = 14f
                gravity = Gravity.CENTER
                minWidth = dp(54)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setTextColor(
                    when {
                        isSelected -> Color.rgb(255, 211, 106)
                        page.status == CampaignChapterPageStatus.COMPLETED ->
                            Color.rgb(184, 220, 163)
                        page.status == CampaignChapterPageStatus.PREVIEW ->
                            Color.rgb(192, 185, 172)
                        else -> Color.WHITE
                    },
                )
                background = chapterTabBackground(isSelected)
                contentDescription = context.getString(
                    R.string.campaign_chapter_tab_accessibility,
                    chapterNumber,
                    page.chapter.title,
                    statusString,
                )
                isFocusable = true
                setOnClickListener {
                    if (viewModel.selectChapter(page.chapter.id)) {
                        render(viewModel.state)
                    }
                }
            }
            chapterTabs.addView(tab, LinearLayout.LayoutParams(-2, -1).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
            if (isSelected) {
                chapterTabsScroll.post {
                    chapterTabsScroll.smoothScrollTo(
                        (tab.left - dp(6)).coerceAtLeast(0),
                        0,
                    )
                }
            }
        }
    }

    private fun renderChapterHeader(page: CampaignChapterPageState) {
        val chapterNumber = chapterNumber(page.chapter.id)
        campaignLabelText.text = context.getString(R.string.campaign_header_banner)
        chapterNumberText.text = context.getString(R.string.campaign_chapter_number, chapterNumber)
        titleText.text = page.chapter.title
        val completedCount = page.markers.count {
            it.status == CampaignLevelMarkerStatus.COMPLETED
        }
        val totalCount = page.markers.size
        chapterProgressBar.max = totalCount.coerceAtLeast(1)
        chapterProgressBar.progress = completedCount.coerceIn(0, chapterProgressBar.max)
        chapterProgressLabel.text = context.getString(
            R.string.campaign_chapter_progress,
            completedCount,
            totalCount,
        )
        chapterProgressPanel.contentDescription = context.getString(
            R.string.campaign_chapter_progress_accessibility,
            completedCount,
            totalCount,
        )
    }

    private fun renderChapterLevelList(page: CampaignChapterPageState, selectedLevel: Int?) {
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(14), dp(72), dp(14), dp(64))
        }
        val levels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = context.getString(R.string.campaign_map_art_pending)
                textSize = 12f
                setTextColor(Color.rgb(245, 230, 199))
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(14), dp(12), dp(14))
                setBackgroundResource(R.drawable.bg_hud_panel)
            }, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, dp(12))
            })

            page.markers.forEach { marker ->
                val isBoss = marker.landmarkKind == CampaignMapLandmarkKind.BOSS
                val textResource = when (marker.status) {
                    CampaignLevelMarkerStatus.COMPLETED ->
                        if (isBoss) {
                            R.string.campaign_boss_level_completed
                        } else {
                            R.string.campaign_level_completed
                        }
                    CampaignLevelMarkerStatus.AVAILABLE ->
                        if (isBoss) {
                            R.string.campaign_boss_level
                        } else {
                            R.string.campaign_level
                        }
                    CampaignLevelMarkerStatus.LOCKED ->
                        if (isBoss) {
                            R.string.campaign_boss_level_locked
                        } else {
                            R.string.campaign_level_locked
                        }
                }
                val available = marker.status == CampaignLevelMarkerStatus.AVAILABLE
                addView(TextView(context).apply {
                    text = context.getString(textResource, marker.level)
                    UiTypography.applyButtonTypeface(this)
                    textSize = 18f
                    setTextColor(
                        if (marker.level == selectedLevel) {
                            Color.rgb(255, 211, 106)
                        } else {
                            Color.WHITE
                        },
                    )
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    alpha = when (marker.status) {
                        CampaignLevelMarkerStatus.AVAILABLE -> 1f
                        CampaignLevelMarkerStatus.COMPLETED -> 0.62f
                        CampaignLevelMarkerStatus.LOCKED -> 0.42f
                    }
                    isEnabled = available
                    isClickable = available
                    setBackgroundResource(R.drawable.bg_hud_panel)
                    if (available) setOnClickListener { selectMapLevel(marker.level) }
                }, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(0, dp(5), 0, dp(5))
                })
            }
        }
        scrollView.addView(levels)
        pageContent.addView(scrollView, FrameLayout.LayoutParams(-1, -1))
    }

    private fun addSelectionRejection(reason: CampaignSelectionRejection) {
        val message = selectionRejectionMessage(reason)
        pageContent.addView(TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundResource(R.drawable.bg_hud_panel)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(64)
        })
    }

    private fun addStatus(message: String) {
        pageContent.addView(TextView(context).apply {
            text = message
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundResource(R.drawable.bg_hud_panel)
        }, FrameLayout.LayoutParams(dp(420), -2, Gravity.CENTER))
    }

    private fun addRetryButton() {
        pageContent.addView(TextView(context).apply {
            text = context.getString(R.string.campaign_retry)
            UiTypography.applyButtonTypeface(this)
            textSize = 14f
            setTextColor(Color.rgb(255, 211, 106))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundResource(R.drawable.bg_hud_panel)
            setOnClickListener {
                screenScope?.launch {
                    render(viewModel.load())
                }
            }
        }, FrameLayout.LayoutParams(dp(420), -2, Gravity.CENTER).apply {
            topMargin = dp(90)
        })
    }

    private fun renderStartLevelButton(page: CampaignChapterPageState, selectedLevel: Int?) {
        val availableLevel = page.markers.firstOrNull {
            it.status == CampaignLevelMarkerStatus.AVAILABLE
        }?.level ?: run {
            startLevelButton.visibility = View.GONE
            return
        }
        val canStart = selectedLevel == availableLevel
        startLevelButton.visibility = View.VISIBLE
        startLevelButton.isEnabled = canStart
        startLevelButton.alpha = if (canStart) 1f else 0.45f
        startLevelButton.contentDescription = if (canStart) {
            context.getString(R.string.campaign_start_level_accessibility, availableLevel)
        } else {
            context.getString(R.string.campaign_start_level_unavailable_accessibility)
        }
    }

    private fun selectMapLevel(level: Int) {
        if (viewModel.selectMapLevel(level)) render(viewModel.state)
    }

    private fun startSelectedLevel() {
        val level = viewModel.state.selectedLevel ?: return
        screenScope?.launch {
            when (val result = viewModel.dispatch(CampaignUiAction.SelectLevel(level))) {
                is CampaignLevelSelection.Selected ->
                    onLevelSelected(result.definition.level)
                is CampaignLevelSelection.Rejected,
                is CampaignLevelSelection.Failure -> render(viewModel.state)
            }
        }
    }

    private fun chapterNumber(chapterId: String): Int =
        CampaignChapterRegistry.default().chapters.indexOfFirst {
            it.id == chapterId
        } + 1

    private fun chapterStatus(status: CampaignChapterPageStatus): String =
        context.getString(
            when (status) {
                CampaignChapterPageStatus.COMPLETED ->
                    R.string.campaign_chapter_status_completed
                CampaignChapterPageStatus.CURRENT ->
                    R.string.campaign_chapter_status_current
                CampaignChapterPageStatus.PREVIEW ->
                    R.string.campaign_chapter_status_preview
            },
        )

    private fun chapterTabBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.argb(if (selected) 245 else 190, 48, 36, 25))
            setStroke(
                dp(if (selected) 2 else 1),
                if (selected) Color.rgb(255, 211, 106) else Color.argb(150, 200, 185, 160),
            )
        }

    private fun progressFailureMessage(reason: CampaignProgressFailure): String =
        context.getString(when (reason) {
            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                R.string.campaign_progress_unavailable
            CampaignProgressFailure.INVALID_DATA ->
                R.string.campaign_progress_invalid
            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                R.string.campaign_progress_version
        })

    private fun selectionRejectionMessage(reason: CampaignSelectionRejection): String =
        context.getString(when (reason) {
            CampaignSelectionRejection.INVALID_LEVEL ->
                R.string.campaign_selection_invalid
            CampaignSelectionRejection.LEVEL_LOCKED ->
                R.string.campaign_selection_locked
            CampaignSelectionRejection.LEVEL_ALREADY_COMPLETED ->
                R.string.campaign_completion_already_completed
        })

    private fun campaignMapLayoutFailureMessage(reason: CampaignMapLayoutFailure): Int =
        when (reason) {
            CampaignMapLayoutFailure.INVALID_CHAPTER,
            CampaignMapLayoutFailure.INVALID_DATA ->
                R.string.campaign_map_layout_invalid
            CampaignMapLayoutFailure.STORAGE_UNAVAILABLE ->
                R.string.campaign_map_layout_unavailable
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
