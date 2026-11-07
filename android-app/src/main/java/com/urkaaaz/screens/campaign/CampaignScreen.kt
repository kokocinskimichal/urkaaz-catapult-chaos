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
import com.urkaaaz.ui.ScreenChromeMetrics
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
        gravity = Gravity.CENTER
    }
    private val chapterTabsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = true
        isFillViewport = true
        setPadding(dp(6), 0, dp(6), 0)
        translationY = -dp(CHAPTER_SELECTOR_CAROUSEL_UPWARD_OFFSET_DP).toFloat()
        addView(chapterTabs, LinearLayout.LayoutParams(-2, -1).apply {
            topMargin = dp(CHAPTER_SELECTOR_CAROUSEL_TOP_PADDING_DP)
            bottomMargin = dp(2)
        })
    }
    private val chapterSelectorBackplate = ImageView(context).apply {
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        scaleType = ImageView.ScaleType.FIT_XY
        setImageResource(CampaignMapPresentationCatalog.chapterSelectorBackgroundDrawable())
        pivotY = 0f
        scaleY = CHAPTER_SELECTOR_BACKPLATE_VERTICAL_SCALE
        translationY = -(
            dp(CHAPTER_SELECTOR_BACKPLATE_HEIGHT_DP) *
                CHAPTER_SELECTOR_BACKPLATE_TOP_TRANSPARENT_FRACTION *
                (CHAPTER_SELECTOR_BACKPLATE_VERTICAL_SCALE - 1f)
            )
    }
    private val chapterSelectorPanel = FrameLayout(context).apply {
        minimumWidth = dp(CHAPTER_SELECTOR_BACKPLATE_MIN_WIDTH_DP)
        clipChildren = false
        clipToPadding = false
        addView(chapterSelectorBackplate, FrameLayout.LayoutParams(-1, -1))
        addView(
            chapterTabsScroll,
            FrameLayout.LayoutParams(-1, -1).apply {
                marginEnd = dp(CHAPTER_SELECTOR_BACKPLATE_RIGHT_INSET_DP)
            },
        )
        translationX =
            (dp(CHAPTER_SELECTOR_BACKPLATE_MIN_WIDTH_DP) *
                CHAPTER_SELECTOR_BACKPLATE_RIGHT_TRANSPARENT_FRACTION).roundToInt().toFloat()
        translationY =
            -(dp(CHAPTER_SELECTOR_BACKPLATE_HEIGHT_DP) *
                CHAPTER_SELECTOR_BACKPLATE_TOP_TRANSPARENT_FRACTION)
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
            FrameLayout.LayoutParams(
                dp(CHAPTER_SELECTOR_BACKPLATE_MIN_WIDTH_DP),
                dp(CHAPTER_SELECTOR_BACKPLATE_HEIGHT_DP),
                Gravity.TOP or Gravity.END,
            ),
        )
        addView(
            startLevelButton,
            FrameLayout.LayoutParams(dp(220), dp(54), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        )
        addView(
            createTopBar(),
            FrameLayout.LayoutParams(dp(260), dp(90), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(ScreenChromeMetrics.EDGE_INSET_DP)
                marginStart = dp(ScreenChromeMetrics.EDGE_INSET_DP)
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
            setPadding(0, 0, 0, 0)
            setBackgroundResource(R.drawable.loadout_start_match_button_xs_short)
            isFocusable = true
            setOnClickListener { onBack() }
        }, FrameLayout.LayoutParams(
            dp(ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP),
            dp(ScreenChromeMetrics.BACK_BUTTON_HEIGHT_DP),
            Gravity.TOP or Gravity.START,
        ))

        addView(
            campaignLabelText,
            FrameLayout.LayoutParams(dp(146), dp(16), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(
                    ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP +
                        ScreenChromeMetrics.BACK_BUTTON_TITLE_GAP_DP,
                )
                topMargin = dp(8)
            },
        )
        addView(
            chapterNumberText,
            FrameLayout.LayoutParams(dp(146), dp(18), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(
                    ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP +
                        ScreenChromeMetrics.BACK_BUTTON_TITLE_GAP_DP,
                )
                topMargin = dp(27)
            },
        )
        addView(
            titleText,
            FrameLayout.LayoutParams(dp(146), dp(20), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(
                    ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP +
                        ScreenChromeMetrics.BACK_BUTTON_TITLE_GAP_DP,
                )
                topMargin = dp(47)
            },
        )
        addView(
            chapterProgressPanel,
            FrameLayout.LayoutParams(dp(146), dp(18), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(
                    ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP +
                        ScreenChromeMetrics.BACK_BUTTON_TITLE_GAP_DP,
                )
                topMargin = dp(68)
            },
        )
    }

    private fun createChapterStrip(): View = chapterSelectorPanel.apply {
        visibility = View.GONE
    }

    private fun render(state: CampaignUiState) {
        pageContent.removeAllViews()
        startLevelButton.visibility = View.GONE
        chapterSelectorPanel.visibility = View.GONE
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

        chapterSelectorPanel.visibility = View.VISIBLE
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
            val chapterThumbnail = CampaignMapPresentationCatalog.chapterTabThumbnailDrawable(
                page.chapter.id,
            )
            val chapterTitle = context.getString(
                CampaignChapterTitleResources.forChapter(page.chapter.id),
            )
            val chapterLabel = context.getString(R.string.campaign_chapter_number, chapterNumber)
            val tabTextColor = when {
                isSelected -> Color.rgb(255, 211, 106)
                page.status == CampaignChapterPageStatus.COMPLETED ->
                    Color.rgb(184, 220, 163)
                page.status == CampaignChapterPageStatus.PREVIEW ->
                    Color.rgb(192, 185, 172)
                else -> Color.WHITE
            }
            val tabDescription = context.getString(
                R.string.campaign_chapter_tab_accessibility,
                chapterNumber,
                chapterTitle,
                statusString,
            )
            val onTabClick = View.OnClickListener {
                if (viewModel.selectChapter(page.chapter.id)) {
                    render(viewModel.state)
                }
            }
            val statusIcon = when (page.status) {
                CampaignChapterPageStatus.COMPLETED ->
                    R.drawable.campaign_level_status_completed
                CampaignChapterPageStatus.CURRENT -> null
                CampaignChapterPageStatus.PREVIEW ->
                    R.drawable.campaign_level_status_locked
            }
            val frameDrawable =
                CampaignMapPresentationCatalog.chapterTabFrameDrawable(isSelected)
            val tab = FrameLayout(context).apply {
                scaleX = CHAPTER_SELECTOR_CARD_VISUAL_SCALE
                scaleY = CHAPTER_SELECTOR_CARD_VISUAL_SCALE
                clipChildren = false
                contentDescription = tabDescription
                isFocusable = true
                this.isSelected = isSelected
                if (chapterThumbnail != null) {
                    addView(ImageView(context).apply {
                        contentDescription = null
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply {
                            cornerRadius = dp(CHAPTER_SELECTOR_THUMBNAIL_CLIP_RADIUS_DP).toFloat()
                            setColor(Color.TRANSPARENT)
                        }
                        clipToOutline = true
                        alpha = if (isSelected) 1f else UNSELECTED_CHAPTER_THUMBNAIL_ALPHA
                        setImageResource(chapterThumbnail)
                    }, FrameLayout.LayoutParams(
                        dp(CHAPTER_SELECTOR_THUMBNAIL_WINDOW_WIDTH_DP),
                        dp(CHAPTER_SELECTOR_THUMBNAIL_WINDOW_HEIGHT_DP),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply {
                        topMargin = dp(CHAPTER_SELECTOR_THUMBNAIL_WINDOW_TOP_DP)
                    })
                } else {
                    addView(TextView(context).apply {
                        contentDescription = null
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        text = "$chapterLabel\n$chapterTitle"
                        textSize = 9f
                        maxLines = 2
                        includeFontPadding = false
                        setLineSpacing(0f, 0.9f)
                        gravity = Gravity.CENTER
                        setTextColor(tabTextColor)
                        setShadowLayer(2f, 0f, 1f, Color.BLACK)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(4).toFloat()
                            setColor(Color.argb(185, 37, 25, 16))
                        }
                    }, FrameLayout.LayoutParams(
                        dp(CHAPTER_SELECTOR_THUMBNAIL_WINDOW_WIDTH_DP),
                        dp(CHAPTER_SELECTOR_THUMBNAIL_WINDOW_HEIGHT_DP),
                        Gravity.CENTER,
                    ))
                }
                addView(ImageView(context).apply {
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageResource(frameDrawable)
                }, FrameLayout.LayoutParams(-1, -1))
                if (chapterThumbnail != null) {
                    addView(TextView(context).apply {
                        contentDescription = null
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        text = "$chapterLabel\n$chapterTitle"
                        textSize = 8f
                        maxLines = 2
                        includeFontPadding = false
                        setLineSpacing(0f, 0.9f)
                        gravity = Gravity.CENTER
                        setPadding(dp(3), 0, dp(3), 0)
                        setTextColor(Color.rgb(255, 229, 188))
                        setShadowLayer(2f, 0f, 1f, Color.BLACK)
                        background = GradientDrawable().apply {
                            cornerRadius = dp(CHAPTER_SELECTOR_THUMBNAIL_CLIP_RADIUS_DP).toFloat()
                            setColor(Color.argb(155, 37, 23, 13))
                            setStroke(dp(1), Color.argb(165, 202, 149, 67))
                        }
                    }, FrameLayout.LayoutParams(
                        dp(CHAPTER_SELECTOR_THUMBNAIL_WINDOW_WIDTH_DP),
                        dp(CHAPTER_SELECTOR_THUMBNAIL_CAPTION_HEIGHT_DP),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply {
                        topMargin = dp(CHAPTER_SELECTOR_THUMBNAIL_CAPTION_TOP_DP)
                    })
                }
                statusIcon?.let { drawable ->
                    addView(ImageView(context).apply {
                        contentDescription = null
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageResource(drawable)
                    }, FrameLayout.LayoutParams(
                        dp(CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_SIZE_DP),
                        dp(CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_SIZE_DP),
                        Gravity.TOP or Gravity.END,
                    ).apply {
                        topMargin = dp(CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_TOP_DP)
                        marginEnd = dp(CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_END_DP)
                    })
                }
                setOnClickListener(onTabClick)
            }
            chapterTabs.addView(
                tab,
                LinearLayout.LayoutParams(
                    dp(CHAPTER_SELECTOR_CARD_WIDTH_DP),
                    dp(CHAPTER_SELECTOR_CARD_HEIGHT_DP),
                ).apply {
                    setMargins(dp(1), 0, dp(1), 0)
                },
            )
            if (isSelected) {
                chapterTabsScroll.post {
                    chapterTabsScroll.smoothScrollTo(
                        tab.left.coerceAtLeast(0),
                        0,
                    )
                }
            }
        }
    }

    private fun renderChapterHeader(page: CampaignChapterPageState) {
        val chapterNumber = chapterNumber(page.chapter.id)
        val chapterTitle = context.getString(
            CampaignChapterTitleResources.forChapter(page.chapter.id),
        )
        campaignLabelText.text = context.getString(R.string.campaign_header_banner)
        chapterNumberText.text = context.getString(R.string.campaign_chapter_number, chapterNumber)
        titleText.text = chapterTitle
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
        chapterProgressPanel.contentDescription = context.resources.getQuantityString(
            R.plurals.campaign_chapter_progress_accessibility,
            completedCount,
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

    private companion object {
        const val CHAPTER_SELECTOR_BACKPLATE_MIN_WIDTH_DP = 312
        const val CHAPTER_SELECTOR_BACKPLATE_HEIGHT_DP = 104
        const val CHAPTER_SELECTOR_BACKPLATE_VERTICAL_SCALE = 1.2f
        const val CHAPTER_SELECTOR_BACKPLATE_RIGHT_INSET_DP = 8
        const val CHAPTER_SELECTOR_CAROUSEL_TOP_PADDING_DP = 16
        const val CHAPTER_SELECTOR_CAROUSEL_UPWARD_OFFSET_DP = 6
        const val CHAPTER_SELECTOR_CARD_VISUAL_SCALE = 0.8f
        const val CHAPTER_SELECTOR_CARD_WIDTH_DP = 144
        const val CHAPTER_SELECTOR_CARD_HEIGHT_DP = 74
        const val CHAPTER_SELECTOR_THUMBNAIL_WINDOW_WIDTH_DP = 126
        const val CHAPTER_SELECTOR_THUMBNAIL_WINDOW_HEIGHT_DP = 51
        const val CHAPTER_SELECTOR_THUMBNAIL_WINDOW_TOP_DP = 11
        const val CHAPTER_SELECTOR_THUMBNAIL_CAPTION_HEIGHT_DP = 16
        const val CHAPTER_SELECTOR_THUMBNAIL_CAPTION_TOP_DP = 46
        const val CHAPTER_SELECTOR_THUMBNAIL_CLIP_RADIUS_DP = 8
        const val CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_SIZE_DP = 16
        const val CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_TOP_DP = 14
        const val CHAPTER_SELECTOR_THUMBNAIL_STATUS_ICON_END_DP = 12
        const val CHAPTER_SELECTOR_BACKPLATE_RIGHT_TRANSPARENT_FRACTION = 47f / 2000f
        const val CHAPTER_SELECTOR_BACKPLATE_TOP_TRANSPARENT_FRACTION = 101f / 667f
        const val UNSELECTED_CHAPTER_THUMBNAIL_ALPHA = 0.78f
    }
}
