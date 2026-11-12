package com.urkaaaz.screens.campaign

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.urkaaaz.android.R
import com.urkaaaz.application.campaign.CampaignLevelStartFailure
import com.urkaaaz.application.campaign.CampaignLevelStartRejection
import com.urkaaaz.application.campaign.CampaignLevelStartResult
import com.urkaaaz.application.campaign.CampaignLevelStartUseCase
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.progression.LifeSystemFailure
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignMapLayoutFailure
import com.urkaaaz.campaign.CampaignMapLayoutRead
import com.urkaaaz.campaign.CampaignMapLayoutRepository
import com.urkaaaz.application.tutorial.TutorialScreen
import com.urkaaaz.tutorial.TutorialTargetRegistration
import com.urkaaaz.tutorial.TutorialScreenContent
import com.urkaaaz.tutorial.visibleBoundsIn
import com.urkaaaz.ui.ScreenChromeMetrics
import com.urkaaaz.ui.UiTypography
import com.urkaaaz.screens.life.LifeCountdownFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class CampaignScreen(
    private val context: Context,
    campaignLevelStart: CampaignLevelStartUseCase,
    private val onBack: () -> Unit,
    private val onLevelSelected: (Int) -> Unit,
) : TutorialScreenContent {
    private val viewModel = CampaignViewModel(campaignLevelStart)
    private val pageContent = CampaignMapGestureContainer(context) { direction ->
        if (viewModel.navigateChapter(direction)) render(viewModel.state)
    }
    private val campaignLabelText = TextView(context).apply {
        textSize = 9f
        setTextColor(Color.rgb(235, 225, 207))
        maxLines = 1
        includeFontPadding = false
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
    }
    private val chapterNumberText = TextView(context).apply {
        textSize = 10f
        setTextColor(Color.rgb(190, 151, 92))
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        includeFontPadding = false
    }
    private val titleText = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.rgb(245, 239, 226))
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
        includeFontPadding = false
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val chapterPageIndicator = TextView(context).apply {
        textSize = 11f
        setTextColor(Color.rgb(245, 239, 226))
        gravity = Gravity.CENTER
        maxLines = 1
        includeFontPadding = false
    }
    private val chapterProgressBar = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal,
    ).apply {
        max = 10
        isIndeterminate = false
        progressTintList = ColorStateList.valueOf(Color.rgb(174, 132, 72))
        progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(35, 38, 42))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val chapterProgressLabel = TextView(context).apply {
        textSize = 9f
        setTextColor(Color.rgb(255, 239, 203))
        gravity = Gravity.CENTER
        maxLines = 1
        includeFontPadding = false
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val chapterProgressPanel = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(Color.argb(195, 24, 28, 33))
            setStroke(dp(1), Color.argb(170, 142, 113, 69))
        }
        contentDescription = ""
        isFocusable = true
        addView(
            chapterProgressBar,
            FrameLayout.LayoutParams(-1, dp(7), Gravity.CENTER).apply {
                leftMargin = dp(3)
                rightMargin = dp(3)
            },
        )
        addView(chapterProgressLabel, FrameLayout.LayoutParams(-1, -1))
    }
    private val chapterHeaderPanel = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.argb(222, 24, 28, 33))
            setStroke(dp(1), Color.argb(185, 132, 108, 73))
        }
        addView(
            campaignLabelText,
            FrameLayout.LayoutParams(dp(220), dp(12), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(10)
                topMargin = dp(2)
            },
        )
        addView(
            chapterNumberText,
            FrameLayout.LayoutParams(dp(88), dp(16), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(10)
                topMargin = dp(14)
            },
        )
        addView(
            titleText,
            FrameLayout.LayoutParams(dp(250), dp(16), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(103)
                topMargin = dp(14)
            },
        )
        addView(
            chapterPageIndicator,
            FrameLayout.LayoutParams(dp(68), dp(16), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(2)
                marginEnd = dp(10)
            },
        )
        addView(
            chapterProgressPanel,
            FrameLayout.LayoutParams(-1, dp(13), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(8)
                rightMargin = dp(8)
                topMargin = dp(34)
            },
        )
    }
    private val previousChapterButton = createChapterNavigationButton(
        direction = CampaignChapterNavigationDirection.PREVIOUS,
    )
    private val nextChapterButton = createChapterNavigationButton(
        direction = CampaignChapterNavigationDirection.NEXT,
    )
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
    private val lifeStatusText = TextView(context).apply {
        textSize = 12f
        setTextColor(Color.rgb(255, 229, 183))
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setBackgroundResource(R.drawable.bg_hud_panel)
        visibility = View.GONE
    }
    private var screenScope: CoroutineScope? = null
    private var chapterMapView: CampaignMapView? = null

    override val view: View = createView()
    override val tutorialScreen: TutorialScreen = TutorialScreen.CAMPAIGN
    override val tutorialTargetsReady: Boolean
        get() = view.isLaidOut && chapterMapView?.isLaidOut == true
    override val registeredTutorialTargetIds: Set<String> =
        TutorialTargetRegistration.idsFor(tutorialScreen)

    override fun tutorialTargetBounds(targetId: String, targetInstanceId: String?): RectF? {
        if (targetInstanceId != null) return null
        val target = when (targetId) {
            "campaign.map" -> pageContent
            "campaign.level_marker" -> {
                val selectedLevel = viewModel.state.selectedLevel
                    ?: viewModel.state.mapProgression?.pages
                        ?.firstOrNull { it.chapter.id == viewModel.state.selectedChapterId }
                        ?.markers
                        ?.firstOrNull { it.status == CampaignLevelMarkerStatus.AVAILABLE }
                        ?.level
                    ?: return null
                chapterMapView?.tutorialMarkerView(selectedLevel)
            }
            "campaign.start" -> startLevelButton
            "campaign.chapter_navigation" -> when {
                nextChapterButton.visibility == View.VISIBLE -> nextChapterButton
                previousChapterButton.visibility == View.VISIBLE -> previousChapterButton
                else -> chapterHeaderPanel
            }
            else -> null
        } ?: return null
        return target.visibleBoundsIn(view)
    }

    init {
        render(viewModel.state)
    }

    override fun onResume() {
        if (screenScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        screenScope = scope
        scope.launch {
            render(viewModel.load())
            while (isActive) {
                delay(LIFE_COUNTDOWN_TICK_MILLISECONDS)
                val life = viewModel.state.lifeSnapshot ?: continue
                val remaining = life.unlimitedTimeRemainingMilliseconds
                    ?: life.regenerationTimeRemainingMilliseconds
                    ?: continue
                val updated = if (remaining <= LIFE_COUNTDOWN_TICK_MILLISECONDS) {
                    viewModel.refreshLifeSnapshot()
                } else {
                    viewModel.elapseLifeCountdown(LIFE_COUNTDOWN_TICK_MILLISECONDS)
                }
                updateLifePresentation(updated)
            }
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
        addView(
            previousChapterButton,
            FrameLayout.LayoutParams(
                dp(CHAPTER_NAVIGATION_TARGET_SIZE_DP),
                dp(CHAPTER_NAVIGATION_TARGET_SIZE_DP),
                Gravity.CENTER_VERTICAL or Gravity.START,
            ).apply {
                marginStart = dp(CHAPTER_NAVIGATION_EDGE_INSET_DP)
            },
        )
        addView(
            nextChapterButton,
            FrameLayout.LayoutParams(
                dp(CHAPTER_NAVIGATION_TARGET_SIZE_DP),
                dp(CHAPTER_NAVIGATION_TARGET_SIZE_DP),
                Gravity.CENTER_VERTICAL or Gravity.END,
            ).apply {
                marginEnd = dp(CHAPTER_NAVIGATION_EDGE_INSET_DP)
            },
        )
        addView(
            startLevelButton,
            FrameLayout.LayoutParams(dp(220), dp(54), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        )
        addView(
            lifeStatusText,
            FrameLayout.LayoutParams(dp(420), -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(58)
            },
        )
        addView(
            createTopBar(),
            FrameLayout.LayoutParams(dp(TOP_BAR_WIDTH_DP), dp(TOP_BAR_HEIGHT_DP), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(ScreenChromeMetrics.EDGE_INSET_DP)
                marginStart = dp(ScreenChromeMetrics.EDGE_INSET_DP)
            },
        )
    }

    private fun createTopBar(): View = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(
            chapterHeaderPanel,
            FrameLayout.LayoutParams(dp(HEADER_PANEL_WIDTH_DP), dp(TOP_BAR_HEIGHT_DP), Gravity.TOP or Gravity.END),
        )

        addView(TextView(context).apply {
            text = context.getString(R.string.loadout_back)
            textSize = 13f
            UiTypography.applyButtonTypeface(this)
            setTextColor(Color.rgb(64, 38, 20))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            setBackgroundResource(R.drawable.loadout_start_match_button_xs_short)
            contentDescription = context.getString(R.string.loadout_back)
            isFocusable = true
            setOnClickListener { onBack() }
        }, FrameLayout.LayoutParams(
            dp(ScreenChromeMetrics.BACK_BUTTON_WIDTH_DP),
            dp(ScreenChromeMetrics.BACK_BUTTON_HEIGHT_DP),
            Gravity.TOP or Gravity.START,
        ).apply {
            topMargin = dp(BACK_BUTTON_TOP_MARGIN_DP)
        })
    }

    private fun render(state: CampaignUiState) {
        pageContent.removeAllViews()
        pageContent.swipeEnabled = false
        startLevelButton.visibility = View.GONE
        lifeStatusText.visibility = View.GONE
        previousChapterButton.visibility = View.INVISIBLE
        nextChapterButton.visibility = View.INVISIBLE
        chapterHeaderPanel.visibility = View.GONE
        campaignLabelText.text = ""
        chapterNumberText.text = ""
        titleText.text = ""
        chapterPageIndicator.text = ""
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

        chapterHeaderPanel.visibility = View.VISIBLE
        renderChapterHeader(page)
        renderChapterNavigation(state.mapProgression, page)
        renderChapterMap(page, state.selectedLevel)
        renderStartLevelButton(page, state.selectedLevel, state)
        renderLifeStatus(state)
        state.selectionRejection
            ?.takeUnless { it == CampaignLevelStartRejection.NO_LIVES }
            ?.let { rejection ->
                addSelectionRejection(rejection)
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

    private fun renderChapterNavigation(
        progression: CampaignMapProgressionState,
        page: CampaignChapterPageState,
    ) {
        val selectedIndex = progression.pages.indexOfFirst {
            it.chapter.id == page.chapter.id
        }
        val hasPrevious = selectedIndex > 0
        val hasNext = selectedIndex in 0 until progression.pages.lastIndex
        previousChapterButton.visibility = if (hasPrevious) View.VISIBLE else View.INVISIBLE
        nextChapterButton.visibility = if (hasNext) View.VISIBLE else View.INVISIBLE
        pageContent.swipeEnabled = hasPrevious || hasNext

        val chapterNumber = chapterNumber(page.chapter.id)
        chapterPageIndicator.text = context.getString(
            R.string.campaign_chapter_page_indicator,
            chapterNumber,
            CampaignChapterRegistry.default().chapters.size,
        )
        chapterPageIndicator.contentDescription = context.getString(
            R.string.campaign_chapter_page_accessibility,
            chapterNumber,
            CampaignChapterRegistry.default().chapters.size,
        )
    }

    private fun renderChapterMap(page: CampaignChapterPageState, selectedLevel: Int?) {
        chapterMapView = null
        when (val layout = CampaignMapLayoutRepository.bundled.load(page.chapter.id)) {
            is CampaignMapLayoutRead.Available ->
                renderMapPresentation(
                    CampaignMapPresentationCatalog.forLayout(layout.layout),
                    page,
                    selectedLevel,
                )
            is CampaignMapLayoutRead.MapArtworkMissing ->
                renderFallbackChapterMap(page, selectedLevel)
            is CampaignMapLayoutRead.Failure ->
                addStatus(context.getString(campaignMapLayoutFailureMessage(layout.reason)))
        }
    }

    private fun renderFallbackChapterMap(
        page: CampaignChapterPageState,
        selectedLevel: Int?,
    ) {
        when (val fallback = CampaignMapLayoutRepository.bundled.load(GREEN_FRONTIER_CHAPTER_ID)) {
            is CampaignMapLayoutRead.Available ->
                renderMapPresentation(
                    CampaignMapPresentationCatalog.forFallbackChapter(page.chapter, fallback.layout),
                    page,
                    selectedLevel,
                )
            is CampaignMapLayoutRead.MapArtworkMissing ->
                addStatus(context.getString(R.string.campaign_map_layout_unavailable))
            is CampaignMapLayoutRead.Failure ->
                addStatus(context.getString(campaignMapLayoutFailureMessage(fallback.reason)))
        }
    }

    private fun renderMapPresentation(
        presentationRead: CampaignMapPresentationRead,
        page: CampaignChapterPageState,
        selectedLevel: Int?,
    ) {
        when (presentationRead) {
            is CampaignMapPresentationRead.Available -> {
                val mapView = CampaignMapView(
                    context = context,
                    presentation = presentationRead.presentation,
                    markers = page.markers,
                    selectedLevel = selectedLevel,
                    onMarkerSelected = ::selectMapLevel,
                )
                chapterMapView = mapView
                pageContent.addView(
                    mapView,
                    FrameLayout.LayoutParams(-1, -1),
                )
            }
            is CampaignMapPresentationRead.Failure ->
                addStatus(context.getString(R.string.campaign_map_layout_invalid))
        }
    }

    private fun createChapterNavigationButton(
        direction: CampaignChapterNavigationDirection,
    ): ImageButton = ImageButton(context).apply {
        contentDescription = context.getString(
            when (direction) {
                CampaignChapterNavigationDirection.PREVIOUS -> R.string.campaign_previous_chapter
                CampaignChapterNavigationDirection.NEXT -> R.string.campaign_next_chapter
            },
        )
        scaleType = ImageView.ScaleType.FIT_CENTER
        background = ColorDrawable(Color.TRANSPARENT)
        setImageResource(
            when (direction) {
                CampaignChapterNavigationDirection.PREVIOUS ->
                    R.drawable.campaign_map_nav_arrow_left
                CampaignChapterNavigationDirection.NEXT ->
                    R.drawable.campaign_map_nav_arrow_right
            },
        )
        isFocusable = true
        visibility = View.INVISIBLE
        setOnClickListener {
            if (viewModel.navigateChapter(direction)) render(viewModel.state)
        }
    }

    private fun addSelectionRejection(reason: CampaignLevelStartRejection) {
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

    private fun renderStartLevelButton(
        page: CampaignChapterPageState,
        selectedLevel: Int?,
        state: CampaignUiState,
    ) {
        val availableLevel = page.markers.firstOrNull {
            it.status == CampaignLevelMarkerStatus.AVAILABLE
        }?.level ?: run {
            startLevelButton.visibility = View.GONE
            return
        }
        val canStart = selectedLevel == availableLevel &&
            state.lifeSnapshot?.canStartCampaign == true &&
            state.lifeFailure == null &&
            state.startFailure == null
        startLevelButton.visibility = View.VISIBLE
        startLevelButton.isEnabled = canStart
        startLevelButton.alpha = if (canStart) 1f else 0.45f
        startLevelButton.contentDescription = if (canStart) {
            context.getString(R.string.campaign_start_level_accessibility, availableLevel)
        } else if (state.lifeSnapshot?.canStartCampaign == false) {
            context.getString(R.string.campaign_lives_required)
        } else {
            context.getString(R.string.campaign_start_level_unavailable_accessibility)
        }
    }

    private fun renderLifeStatus(state: CampaignUiState) {
        val message = when {
            state.lifeFailure != null ->
                context.getString(lifeFailureMessage(state.lifeFailure))
            state.lifeSnapshot?.canStartCampaign == false -> {
                val remaining = state.lifeSnapshot.regenerationTimeRemainingMilliseconds
                if (remaining != null) {
                    context.getString(
                        R.string.campaign_lives_required_remaining,
                        LifeCountdownFormatter.format(remaining),
                    )
                } else {
                    context.getString(R.string.campaign_lives_required)
                }
            }
            state.startFailure != null ->
                context.getString(startFailureMessage(state.startFailure))
            else -> null
        } ?: return
        lifeStatusText.text = message
        lifeStatusText.visibility = View.VISIBLE
    }

    private fun updateLifePresentation(state: CampaignUiState) {
        val page = state.mapProgression?.pages?.firstOrNull {
            it.chapter.id == state.selectedChapterId
        } ?: return
        renderStartLevelButton(page, state.selectedLevel, state)
        renderLifeStatus(state)
    }

    private fun selectMapLevel(level: Int) {
        if (viewModel.selectMapLevel(level)) render(viewModel.state)
    }

    private fun startSelectedLevel() {
        val level = viewModel.state.selectedLevel ?: return
        screenScope?.launch {
            when (val result = viewModel.dispatch(CampaignUiAction.SelectLevel(level))) {
                is CampaignLevelStartResult.Allowed ->
                    onLevelSelected(result.definition.level)
                is CampaignLevelStartResult.Rejected,
                is CampaignLevelStartResult.Failure -> render(viewModel.state)
            }
        }
    }

    private fun chapterNumber(chapterId: String): Int =
        CampaignChapterRegistry.default().chapters.indexOfFirst {
            it.id == chapterId
        } + 1

    private fun progressFailureMessage(reason: CampaignProgressFailure): String =
        context.getString(when (reason) {
            CampaignProgressFailure.STORAGE_UNAVAILABLE ->
                R.string.campaign_progress_unavailable
            CampaignProgressFailure.INVALID_DATA ->
                R.string.campaign_progress_invalid
            CampaignProgressFailure.UNSUPPORTED_VERSION ->
                R.string.campaign_progress_version
        })

    private fun selectionRejectionMessage(reason: CampaignLevelStartRejection): String =
        context.getString(when (reason) {
            CampaignLevelStartRejection.INVALID_LEVEL ->
                R.string.campaign_selection_invalid
            CampaignLevelStartRejection.LEVEL_LOCKED ->
                R.string.campaign_selection_locked
            CampaignLevelStartRejection.LEVEL_ALREADY_COMPLETED ->
                R.string.campaign_completion_already_completed
            CampaignLevelStartRejection.NO_LIVES ->
                R.string.campaign_lives_required
        })

    private fun lifeFailureMessage(reason: LifeSystemFailure): Int =
        when (reason) {
            LifeSystemFailure.STORAGE_UNAVAILABLE ->
                R.string.campaign_lives_unavailable
            LifeSystemFailure.INVALID_PROFILE ->
                R.string.campaign_lives_invalid
            LifeSystemFailure.UNSUPPORTED_PROFILE_VERSION ->
                R.string.campaign_lives_version
        }

    private fun startFailureMessage(reason: CampaignLevelStartFailure): Int =
        when (reason) {
            CampaignLevelStartFailure.CAMPAIGN_STORAGE_UNAVAILABLE ->
                R.string.campaign_progress_unavailable
            CampaignLevelStartFailure.CAMPAIGN_INVALID_DATA ->
                R.string.campaign_progress_invalid
            CampaignLevelStartFailure.CAMPAIGN_UNSUPPORTED_VERSION ->
                R.string.campaign_progress_version
            CampaignLevelStartFailure.LIFE_STORAGE_UNAVAILABLE ->
                R.string.campaign_lives_unavailable
            CampaignLevelStartFailure.LIFE_INVALID_PROFILE ->
                R.string.campaign_lives_invalid
            CampaignLevelStartFailure.LIFE_UNSUPPORTED_PROFILE_VERSION ->
                R.string.campaign_lives_version
        }

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
        const val LIFE_COUNTDOWN_TICK_MILLISECONDS = 1_000L
        const val GREEN_FRONTIER_CHAPTER_ID = "green_frontier"
        const val TOP_BAR_WIDTH_DP = 480
        const val TOP_BAR_HEIGHT_DP = 52
        const val HEADER_PANEL_WIDTH_DP = 370
        const val BACK_BUTTON_TOP_MARGIN_DP = 2
        const val CHAPTER_NAVIGATION_TARGET_SIZE_DP = 72
        const val CHAPTER_NAVIGATION_EDGE_INSET_DP = 16
    }
}
