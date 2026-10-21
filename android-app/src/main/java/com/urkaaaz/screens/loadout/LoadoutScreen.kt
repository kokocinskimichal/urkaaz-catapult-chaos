package com.urkaaaz.screens.loadout

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.urkaaaz.android.R
import com.urkaaaz.application.loadout.LoadoutCategory
import com.urkaaaz.application.loadout.LoadoutAmmunitionEffect
import com.urkaaaz.application.loadout.LoadoutCatalog
import com.urkaaaz.application.loadout.LoadoutCorrectionReason
import com.urkaaaz.application.loadout.LoadoutEditAction
import com.urkaaaz.application.loadout.LoadoutError
import com.urkaaaz.application.loadout.LoadoutProgressionFailure
import com.urkaaaz.application.loadout.LoadoutSpellCategory
import com.urkaaaz.application.loadout.LoadoutStorageFailure
import com.urkaaaz.application.loadout.LoadoutUseCases
import com.urkaaaz.application.loadout.MatchLoadout
import com.urkaaaz.contracts.PlayerLoadoutConfig
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.ui.UiTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoadoutScreen(
    context: Context,
    campaignLevel: Int,
    loadoutUseCases: LoadoutUseCases,
    private val onBack: () -> Unit,
    private val onStart: (PlayerLoadoutConfig) -> Unit,
) : ScreenContent {
    private val viewModel = LoadoutViewModel(campaignLevel, loadoutUseCases)
    private val unavailableArtworkFilter = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(0f) },
    )
    private val unitNameTypeface = requireNotNull(
        ResourcesCompat.getFont(context, R.font.cinzel_semibold),
    ) {
        "Cinzel SemiBold font resource could not be loaded"
    }
    private var screenScope: CoroutineScope? = null
    private var hasLoaded = false
    private var isShopPlaceholderVisible = false
    private var shopReturnsToDetails = false
    private lateinit var mainContent: LinearLayout
    private lateinit var unitScroll: ScrollView
    private lateinit var ammunitionScroll: ScrollView
    private lateinit var spellScroll: ScrollView
    private lateinit var unitCarousel: HorizontalScrollView
    private lateinit var ammunitionCarousel: HorizontalScrollView
    private lateinit var spellCarousel: HorizontalScrollView
    private lateinit var unitOptions: LinearLayout
    private lateinit var ammunitionOptions: LinearLayout
    private lateinit var spellOptions: LinearLayout
    private lateinit var goldView: TextView
    private lateinit var statusView: TextView
    private lateinit var unitCountView: TextView
    private lateinit var ammunitionCountView: TextView
    private lateinit var spellCountView: TextView
    private lateinit var unitSummaryCountView: TextView
    private lateinit var ammunitionSummaryCountView: TextView
    private lateinit var spellSummaryCountView: TextView
    private lateinit var startButton: Button
    private lateinit var detailsContent: LinearLayout

    private data class MetadataIcon(
        val drawableId: Int,
        val contentDescription: String,
    )

    override val view: View = createView(context)

    override fun onResume() {
        if (screenScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        screenScope = scope
        if (!hasLoaded) {
            scope.launch {
                viewModel.load()
                hasLoaded = true
                renderMain()
            }
        }
    }

    override fun onPause() {
        viewModel.cancelPendingCommit()
        screenScope?.cancel()
        screenScope = null
    }

    override fun onBackPressed(): Boolean {
        if (isShopPlaceholderVisible) {
            val item = viewModel.state.detailItem
            isShopPlaceholderVisible = false
            if (shopReturnsToDetails && item != null) {
                showDetails(item)
            } else {
                viewModel.closeDetails()
                showMain()
            }
            return true
        }
        if (!viewModel.handleBackPressed()) return false
        showMain()
        return true
    }

    private fun createView(context: Context): View = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.bg_main_menu)
        }, FrameLayout.LayoutParams(-1, -1))

        mainContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = dp(context, 18)
            val topPadding = dp(context, 10)
            val bottomGap = dp(context, 12)
            setPadding(horizontalPadding, topPadding, horizontalPadding, bottomGap)
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val navigationBarInset = insets
                    .getInsets(WindowInsetsCompat.Type.navigationBars())
                    .bottom
                view.setPadding(
                    horizontalPadding,
                    topPadding,
                    horizontalPadding,
                    navigationBarInset + bottomGap,
                )
                insets
            }
            addView(createHeader(context, showBack = true), matchWrap())

            val columns = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(context, 8), 0, dp(context, 8))
                addView(createColumn(
                    context,
                    R.string.loadout_units,
                    onScrollCreated = { unitScroll = it },
                    onCountCreated = { unitCountView = it },
                ) { content ->
                    unitOptions = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    unitCarousel = createCarousel(context, unitOptions)
                    content.addView(unitCarousel, matchWrap())
                }, columnParams(context))
                addView(createColumn(
                    context,
                    R.string.loadout_ammunition,
                    onScrollCreated = { ammunitionScroll = it },
                    onCountCreated = { ammunitionCountView = it },
                ) { content ->
                    ammunitionOptions = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    ammunitionCarousel = createCarousel(context, ammunitionOptions)
                    content.addView(ammunitionCarousel, matchWrap())
                }, columnParams(context))
                addView(createColumn(
                    context,
                    R.string.loadout_spells,
                    onScrollCreated = { spellScroll = it },
                    onCountCreated = { spellCountView = it },
                ) { content ->
                    spellOptions = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }
                    spellCarousel = createCarousel(context, spellOptions)
                    content.addView(spellCarousel, matchWrap())
                }, columnParams(context))
            }
            addView(columns, LinearLayout.LayoutParams(-1, 0, 1f))

            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
                setBackgroundResource(R.drawable.bg_hud_panel)
                val summary = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val categories = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(TextView(context).apply {
                            text = context.getString(R.string.loadout_summary_title)
                            textSize = 10f
                            setTypeface(typeface, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER_VERTICAL
                            maxLines = 2
                        }, LinearLayout.LayoutParams(dp(context, 72), dp(context, 40)).apply {
                            marginEnd = dp(context, 4)
                        })
                        addSummaryDivider(context, this)
                        addView(
                            createSummaryCategory(
                                context,
                                R.string.loadout_summary_units,
                                LoadoutAssetCatalog.summaryUnitsIcon(),
                            ) { unitSummaryCountView = it },
                            LinearLayout.LayoutParams(0, dp(context, 40), 1f),
                        )
                        addSummaryDivider(context, this)
                        addView(
                            createSummaryCategory(
                                context,
                                R.string.loadout_summary_ammo,
                                LoadoutAssetCatalog.summaryAmmunitionIcon(),
                            ) { ammunitionSummaryCountView = it },
                            LinearLayout.LayoutParams(0, dp(context, 40), 1f),
                        )
                        addSummaryDivider(context, this)
                        addView(
                            createSummaryCategory(
                                context,
                                R.string.loadout_summary_spells,
                                LoadoutAssetCatalog.summarySpellsIcon(),
                            ) { spellSummaryCountView = it },
                            LinearLayout.LayoutParams(0, dp(context, 40), 1f),
                        )
                    }
                    addView(categories, LinearLayout.LayoutParams(-1, dp(context, 40)))
                    statusView = TextView(context).apply {
                        textSize = 11f
                        maxLines = 1
                        setTextColor(Color.rgb(255, 205, 135))
                    }
                    addView(statusView, wrap())
                }
                addView(summary, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = dp(context, 12)
                })
                startButton = Button(context).apply {
                    text = context.getString(R.string.loadout_start_match)
                    textSize = 17f
                    UiTypography.applyButtonTypeface(this)
                    setTextColor(Color.rgb(64, 38, 20))
                    setBackgroundResource(LoadoutAssetCatalog.startMatchButtonBackground())
                    isEnabled = false
                    alpha = 0.45f
                    setOnClickListener {
                        screenScope?.launch {
                            val loadout = viewModel.commit()
                            renderMain()
                            loadout?.let(onStart)
                        }
                    }
                }
                addView(startButton, LinearLayout.LayoutParams(
                    dp(context, 220),
                    dp(context, 54),
                ).apply {
                    marginStart = dp(context, 8)
                })
            }
            addView(
                footer,
                LinearLayout.LayoutParams(-1, -2),
            )
        }
        addView(mainContent, FrameLayout.LayoutParams(-1, -1))

        detailsContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14))
            visibility = View.GONE
        }
        addView(detailsContent, FrameLayout.LayoutParams(-1, -1))
    }

    private fun createColumn(
        context: Context,
        titleResource: Int,
        onScrollCreated: (ScrollView) -> Unit,
        onCountCreated: (TextView) -> Unit,
        content: (LinearLayout) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        setBackgroundResource(R.drawable.bg_hud_panel)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = context.getString(titleResource)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(255, 230, 190))
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 1
                setPadding(dp(context, 4), dp(context, 2), dp(context, 4), dp(context, 6))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val count = TextView(context).apply {
                textSize = 10f
                setTextColor(Color.rgb(220, 207, 184))
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                maxLines = 1
            }
            onCountCreated(count)
            addView(count, wrap())
        }, wrap())
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
        }
        onScrollCreated(scroll)
        val columnContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            content(this)
        }
        scroll.addView(columnContent)
        addView(scroll, LinearLayout.LayoutParams(-1, -2))
    }

    private fun createCarousel(
        context: Context,
        content: LinearLayout,
    ): HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        addView(content, LinearLayout.LayoutParams(-2, -2))
    }

    private fun columnParams(context: Context) =
        LinearLayout.LayoutParams(0, -2, 1f).apply {
            val gap = dp(context, 5)
            marginStart = gap
            marginEnd = gap
        }

    private fun createHeader(context: Context, showBack: Boolean): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (showBack) {
                addView(TextView(context).apply {
                    text = context.getString(R.string.loadout_back)
                    textSize = 13f
                    UiTypography.applyButtonTypeface(this)
                    setTextColor(Color.rgb(64, 38, 20))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 0)
                    setBackgroundResource(LoadoutAssetCatalog.backButtonBackground())
                    setOnClickListener { onBack() }
                }, LinearLayout.LayoutParams(dp(context, 96), dp(context, 48)))
            } else {
                addView(TextView(context).apply {
                    text = "←"
                    textSize = 26f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(context, 8), dp(context, 4), dp(context, 12), dp(context, 4))
                    contentDescription = context.getString(R.string.loadout_back_to_loadout)
                    setOnClickListener { closeDetails() }
                }, wrap())
            }
            val title = FrameLayout(context).apply {
                addView(ImageView(context).apply {
                    setImageResource(LoadoutAssetCatalog.headerFrame())
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                }, FrameLayout.LayoutParams(-1, -1))
                addView(TextView(context).apply {
                    text = context.getString(R.string.loadout_title)
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(255, 211, 106))
                    gravity = Gravity.CENTER
                    setShadowLayer(3f, 0f, 1f, Color.BLACK)
                }, FrameLayout.LayoutParams(-1, dp(context, 26), Gravity.TOP).apply {
                    topMargin = dp(context, 30)
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.loadout_level, viewModel.state.level)
                    textSize = 10f
                    setTextColor(Color.rgb(239, 220, 179))
                    gravity = Gravity.CENTER
                    setShadowLayer(2f, 0f, 1f, Color.BLACK)
                }, FrameLayout.LayoutParams(-1, dp(context, 18), Gravity.TOP).apply {
                    topMargin = dp(context, 56)
                })
            }
            addView(title, LinearLayout.LayoutParams(0, dp(context, 96), 1f))
            val headerGold = TextView(context).apply {
                textSize = 12f
                setTextColor(Color.rgb(255, 211, 106))
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                text = viewModel.state.progression?.let {
                    context.getString(R.string.loadout_gold, it.goldBalance)
                }.orEmpty()
            }
            if (showBack) goldView = headerGold
            addView(headerGold, wrap())
        }

    private fun LinearLayout.addSection(
        context: Context,
        titleResource: Int,
        content: (LinearLayout) -> Unit,
    ) {
        addView(TextView(context).apply {
            text = context.getString(titleResource)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(context, 4), dp(context, 10), 0, dp(context, 5))
        }, wrap())
        val options = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        content(options)
        addView(options, wrap())
    }

    private fun renderMain() {
        val unitScrollY = unitScroll.scrollY
        val ammunitionScrollY = ammunitionScroll.scrollY
        val spellScrollY = spellScroll.scrollY
        val unitCarouselX = unitCarousel.scrollX
        val ammunitionCarouselX = ammunitionCarousel.scrollX
        val spellCarouselX = spellCarousel.scrollX
        val state = viewModel.state
        unitOptions.removeAllViews()
        availableOptionsFirst(LoadoutCatalog.units) { unit ->
            viewModel.optionStatus(LoadoutItemReference.Unit(unit.id))
        }.forEach { unit ->
            val reference = LoadoutItemReference.Unit(unit.id)
            unitOptions.addView(
                itemCard(
                    context = mainContent.context,
                    item = reference,
                    status = viewModel.optionStatus(reference),
                ) {
                    val selected = unit.id in viewModel.state.draft?.unitTypes.orEmpty()
                    edit(LoadoutEditAction.SelectUnit(unit.id, selected = !selected))
                },
                carouselItemParams(mainContent.context),
            )
        }

        ammunitionOptions.removeAllViews()
        availableOptionsFirst(LoadoutCatalog.ammunition) { ammunition ->
            viewModel.optionStatus(LoadoutItemReference.Ammunition(ammunition.id))
        }.forEach { ammunition ->
            val reference = LoadoutItemReference.Ammunition(ammunition.id)
            ammunitionOptions.addView(
                itemCard(
                    context = mainContent.context,
                    item = reference,
                    status = viewModel.optionStatus(reference),
                    isFixed = ammunition.id == MatchLoadout.ROCK,
                    trailingIcons = listOfNotNull(
                        if (ammunition.unlimited) {
                            MetadataIcon(
                                LoadoutAssetCatalog.unlimitedIcon(),
                                mainContent.context.getString(R.string.loadout_unlimited),
                            )
                        } else {
                            null
                        },
                        if (ammunition.id == MatchLoadout.ROCK) {
                            MetadataIcon(
                                LoadoutAssetCatalog.rockFixedIcon(),
                                mainContent.context.getString(R.string.loadout_state_fixed),
                            )
                        } else {
                            null
                        },
                    ),
                    trailingText = if (ammunition.unlimited) null
                    else ammunitionStockLabel(reference),
                ) {
                    val selected =
                        ammunition.id in viewModel.state.draft?.ammunitionSlots.orEmpty()
                    edit(
                        LoadoutEditAction.ToggleAmmunition(
                            ammunition.id,
                            selected = !selected,
                        ),
                    )
                },
                carouselItemParams(mainContent.context),
            )
        }

        spellOptions.removeAllViews()
        availableOptionsFirst(LoadoutCatalog.spells) { spell ->
            viewModel.optionStatus(LoadoutItemReference.Spell(spell.id))
        }.forEach { spell ->
            val reference = LoadoutItemReference.Spell(spell.id)
            spellOptions.addView(
                itemCard(
                    context = mainContent.context,
                    item = reference,
                    status = viewModel.optionStatus(reference),
                    trailingText = mainContent.context.getString(
                        R.string.loadout_prop_gold_cost,
                        spell.goldCost,
                    ),
                ) {
                    val selected = spell.id in viewModel.state.draft?.spellTypes.orEmpty()
                    edit(LoadoutEditAction.ToggleSpell(spell.id, selected = !selected))
                },
                carouselItemParams(mainContent.context),
            )
        }

        goldView.text = state.progression?.let {
            mainContent.context.getString(R.string.loadout_gold, it.goldBalance)
        }.orEmpty()
        statusView.text = statusText(mainContent.context)
        unitSummaryCountView.text = summaryCount(
            mainContent.context,
            state.draft?.unitTypes?.size ?: 0,
            MatchLoadout.MAX_UNITS,
        )
        ammunitionSummaryCountView.text = summaryCount(
            mainContent.context,
            state.draft?.ammunitionSlots?.count { it != null } ?: 0,
            MatchLoadout.AMMUNITION_SLOT_COUNT,
        )
        spellSummaryCountView.text = summaryCount(
            mainContent.context,
            state.draft?.spellTypes?.size ?: 0,
            MatchLoadout.MAX_SPELLS,
        )
        unitCountView.text = selectionCount(
            mainContent.context,
            state.draft?.unitTypes?.size ?: 0,
            MatchLoadout.MAX_UNITS,
        )
        ammunitionCountView.text = selectionCount(
            mainContent.context,
            state.draft?.ammunitionSlots?.count { it != null } ?: 0,
            MatchLoadout.AMMUNITION_SLOT_COUNT,
        )
        spellCountView.text = selectionCount(
            mainContent.context,
            state.draft?.spellTypes?.size ?: 0,
            MatchLoadout.MAX_SPELLS,
        )
        startButton.isEnabled = state.canStart
        startButton.alpha = if (state.canStart) 1f else 0.45f
        unitScroll.post { unitScroll.scrollTo(0, unitScrollY) }
        ammunitionScroll.post { ammunitionScroll.scrollTo(0, ammunitionScrollY) }
        spellScroll.post { spellScroll.scrollTo(0, spellScrollY) }
        unitCarousel.post { unitCarousel.scrollTo(unitCarouselX, 0) }
        ammunitionCarousel.post { ammunitionCarousel.scrollTo(ammunitionCarouselX, 0) }
        spellCarousel.post { spellCarousel.scrollTo(spellCarouselX, 0) }
    }

    private fun selectionCount(context: Context, selected: Int, maximum: Int): CharSequence {
        val selectedText = selected.toString()
        return SpannableString(
            context.getString(R.string.loadout_selection_count, selected, maximum),
        ).apply {
            setSpan(
                RelativeSizeSpan(1.3f),
                0,
                selectedText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                selectedText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            setSpan(
                ForegroundColorSpan(Color.rgb(255, 211, 106)),
                0,
                selectedText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun summaryCount(context: Context, selected: Int, maximum: Int): String =
        context.getString(R.string.loadout_summary_count, selected, maximum)

    private fun addSummaryDivider(context: Context, parent: LinearLayout) {
        parent.addView(
            View(context).apply {
                setBackgroundColor(Color.argb(92, 220, 207, 184))
            },
            LinearLayout.LayoutParams(dp(context, 1), dp(context, 34)).apply {
                marginStart = dp(context, 5)
                marginEnd = dp(context, 5)
            },
        )
    }

    private fun createSummaryCategory(
        context: Context,
        titleResource: Int,
        iconResource: Int,
        onCountCreated: (TextView) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply {
            setImageResource(iconResource)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)).apply {
            marginEnd = dp(context, 5)
        })
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = context.getString(titleResource)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, wrap())
            val count = TextView(context).apply {
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(255, 211, 106))
                maxLines = 1
            }
            onCountCreated(count)
            addView(count, wrap())
        }
        addView(text, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun carouselItemParams(context: Context) =
        LinearLayout.LayoutParams(
            carouselCardWidth(context),
            dp(context, 116),
        ).apply {
            marginEnd = dp(context, 4)
        }

    private fun carouselCardWidth(context: Context): Int {
        val screenWidthDp =
            context.resources.displayMetrics.widthPixels /
                context.resources.displayMetrics.density
        val panelContentWidthDp = (screenWidthDp - 36f - 30f) / 3f - 20f
        return dp(context, ((panelContentWidthDp - 12f) / 3f).toInt().coerceAtLeast(64))
    }

    private fun itemCard(
        context: Context,
        item: LoadoutItemReference,
        status: LoadoutOptionStatus,
        isFixed: Boolean = false,
        trailingIcons: List<MetadataIcon> = emptyList(),
        trailingText: String? = null,
        onSelect: () -> Unit,
    ): View {
        val isSelected = status == LoadoutOptionStatus.SELECTED
        val isLocked = status == LoadoutOptionStatus.UNAVAILABLE ||
            status == LoadoutOptionStatus.LOCKED
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 7).toFloat()
                setColor(Color.argb(210, 25, 23, 21))
            }
            clipToOutline = true
            alpha = if (status == LoadoutOptionStatus.UNAVAILABLE ||
                status == LoadoutOptionStatus.LOCKED ||
                status == LoadoutOptionStatus.OUT_OF_STOCK
            ) 0.68f else 1f
            foreground = if (isSelected) null else {
                GradientDrawable().apply {
                    cornerRadius = dp(context, 7).toFloat()
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(context, 1), Color.argb(155, 145, 130, 111))
                }
            }
        }
        val canEdit = !isFixed && viewModel.state.draft != null &&
            status in setOf(LoadoutOptionStatus.SELECTED, LoadoutOptionStatus.AVAILABLE)
        val canOpenShop = viewModel.state.draft != null &&
            item is LoadoutItemReference.Spell &&
            (status == LoadoutOptionStatus.LOCKED ||
                status == LoadoutOptionStatus.OUT_OF_STOCK)

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(context, 2), dp(context, 2), dp(context, 2), dp(context, 2))
            addView(TextView(context).apply {
                text = context.getString(itemNameResource(item))
                textSize = 10f
                typeface = unitNameTypeface
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(255, 242, 218))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(2f, 0f, 1f, Color.BLACK)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    7,
                    10,
                    1,
                    TypedValue.COMPLEX_UNIT_SP,
                )
            }, matchWrap())
            val statusText = if (status == LoadoutOptionStatus.OUT_OF_STOCK) {
                statusLabel(context, status)
            } else {
                ""
            }
            val metadataText = listOf(statusText, trailingText.orEmpty())
                .filter(String::isNotBlank)
                .joinToString(" \u2022 ")
            val metadataRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                minimumHeight = dp(context, 10)
                if (status == LoadoutOptionStatus.SELECTED &&
                    trailingIcons.isEmpty() &&
                    metadataText.isBlank()
                ) {
                    contentDescription =
                        context.getString(R.string.loadout_card_selected_accessibility)
                }
                trailingIcons.forEachIndexed { index, trailingIcon ->
                    addView(ImageView(context).apply {
                        setImageResource(trailingIcon.drawableId)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = listOfNotNull(
                            if (index == 0 && status == LoadoutOptionStatus.SELECTED) {
                                context.getString(R.string.loadout_card_selected_accessibility)
                            } else {
                                null
                            },
                            trailingIcon.contentDescription,
                        ).joinToString(", ")
                    }, LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)).apply {
                        if (index > 0) marginStart = dp(context, 2)
                    })
                }
                if (metadataText.isNotBlank()) {
                    addView(TextView(context).apply {
                        text = metadataText
                        textSize = 7f
                        setTypeface(typeface, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(statusColor(status))
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        if (status == LoadoutOptionStatus.SELECTED) {
                            contentDescription =
                                context.getString(R.string.loadout_card_selected_accessibility)
                        }
                        setShadowLayer(2f, 0f, 1f, Color.BLACK)
                    }, wrap())
                }
            }
            addView(metadataRow, matchWrap())
        }

        when (val art = LoadoutAssetCatalog.carouselArtwork(item)) {
            is LoadoutAssetLookup.Found -> card.addView(
                ImageView(context).apply {
                    setImageResource(art.drawableId)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = context.getString(itemNameResource(item))
                    if (isLocked) colorFilter = unavailableArtworkFilter
                },
                FrameLayout.LayoutParams(-1, -1),
            )
            LoadoutAssetLookup.Missing -> Unit
        }
        card.addView(
            View(context).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.argb(175, 12, 10, 9),
                        Color.argb(205, 12, 10, 9),
                    ),
                )
            },
            FrameLayout.LayoutParams(-1, dp(context, 40), Gravity.BOTTOM),
        )
        card.addView(
            textColumn,
            FrameLayout.LayoutParams(-1, dp(context, 40), Gravity.BOTTOM),
        )

        if (isLocked) {
            card.addView(ImageView(context).apply {
                setImageResource(LoadoutAssetCatalog.lockedIcon())
                scaleType = ImageView.ScaleType.FIT_CENTER
                colorFilter = unavailableArtworkFilter
                contentDescription = context.getString(
                    if (status == LoadoutOptionStatus.LOCKED) {
                        R.string.loadout_card_locked_accessibility
                    } else {
                        R.string.loadout_card_unavailable_accessibility
                    },
                )
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }, FrameLayout.LayoutParams(
                dp(context, 42),
                dp(context, 42),
                Gravity.CENTER,
            ))
        }

        if (isSelected) {
            card.addView(ImageView(context).apply {
                setImageResource(R.drawable.loadout_card_selected_frame)
                scaleType = ImageView.ScaleType.FIT_XY
                scaleX = 1.12f
                scaleY = 1.25f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
            card.addView(ImageView(context).apply {
                setImageResource(R.drawable.loadout_card_selected_badge)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(
                dp(context, 30),
                dp(context, 30),
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dp(context, 2)
                marginEnd = dp(context, 2)
            })
        }
        card.addView(createInfoButton(context, item), FrameLayout.LayoutParams(
            dp(context, 24),
            dp(context, 24),
            Gravity.TOP or Gravity.START,
        ).apply {
            topMargin = dp(context, 2)
            marginStart = dp(context, 2)
        })
        card.isClickable = canEdit || canOpenShop
        card.isFocusable = canEdit || canOpenShop
        if (canEdit) card.setOnClickListener { onSelect() }
        else if (canOpenShop) {
            card.setOnClickListener {
                showShopPlaceholder(item, returnToDetails = false)
            }
        }
        return card
    }

    private fun createInfoButton(
        context: Context,
        item: LoadoutItemReference,
    ): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.loadout_card_info)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = context.getString(
            R.string.loadout_open_details,
            context.getString(itemNameResource(item)),
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { showDetails(item) }
    }

    private fun showDetails(item: LoadoutItemReference) {
        isShopPlaceholderVisible = false
        viewModel.showDetails(item)
        val context = detailsContent.context
        detailsContent.removeAllViews()
        detailsContent.addView(createHeader(context, showBack = false), wrap())

        val body = ScrollView(context)
        val detailColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 14), 0, dp(context, 12))
            addView(TextView(context).apply {
                text = context.getString(R.string.loadout_details)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }, wrap())
            when (val illustration = LoadoutAssetCatalog.illustration(item)) {
                is LoadoutAssetLookup.Found -> addView(ImageView(context).apply {
                    setImageResource(illustration.drawableId)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = context.getString(itemNameResource(item))
                }, LinearLayout.LayoutParams(-1, dp(context, 190)).apply {
                    topMargin = dp(context, 12)
                    bottomMargin = dp(context, 12)
                })
                LoadoutAssetLookup.Missing -> Unit
            }
            addView(TextView(context).apply {
                text = context.getString(itemNameResource(item))
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(255, 211, 106))
                setPadding(0, dp(context, 8), 0, dp(context, 6))
            }, wrap())
            addView(TextView(context).apply {
                text = context.getString(descriptionResource(item))
                textSize = 14f
                setTextColor(Color.WHITE)
            }, wrap())
            val itemStatus = viewModel.optionStatus(item)
            val fixedRock = item is LoadoutItemReference.Ammunition &&
                item.identifier == MatchLoadout.ROCK
            addView(TextView(context).apply {
                text = if (fixedRock) {
                    context.getString(R.string.loadout_state_fixed)
                } else {
                    statusLabel(context, itemStatus)
                }
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(statusColor(itemStatus))
                setPadding(0, dp(context, 10), 0, dp(context, 8))
            }, wrap())
            addItemProperties(context, item, this)
        }
        body.addView(detailColumn)
        detailsContent.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        detailsContent.addView(TextView(context).apply {
            text = context.getString(R.string.loadout_spell_effect_planned)
            textSize = 12f
            setTextColor(Color.rgb(255, 205, 135))
            setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6))
            visibility = if (item is LoadoutItemReference.Spell) View.VISIBLE else View.GONE
        }, wrap())

        detailsContent.addView(detailActionButton(context, item), matchWrap())
        mainContent.visibility = View.GONE
        detailsContent.visibility = View.VISIBLE
    }

    private fun detailActionButton(context: Context, item: LoadoutItemReference): Button =
        Button(context).apply {
            val status = viewModel.optionStatus(item)
            val isSelected = status == LoadoutOptionStatus.SELECTED
            val isFixedRock = item is LoadoutItemReference.Ammunition &&
                item.identifier == MatchLoadout.ROCK
            val opensShop = item is LoadoutItemReference.Spell &&
                status in setOf(LoadoutOptionStatus.LOCKED, LoadoutOptionStatus.OUT_OF_STOCK)
            text = if (isFixedRock) {
                context.getString(R.string.loadout_state_fixed)
            } else if (opensShop) {
                context.getString(R.string.loadout_open_shop)
            } else if (isSelected) {
                context.getString(R.string.loadout_remove_item)
            } else {
                context.getString(R.string.loadout_select_item)
            }
            UiTypography.applyButtonTypeface(this)
            isEnabled = opensShop || (!isFixedRock && (
                isSelected || (
                    status == LoadoutOptionStatus.AVAILABLE &&
                        viewModel.state.draft != null
                    )
                ))
            setOnClickListener {
                if (opensShop) {
                    showShopPlaceholder(item, returnToDetails = true)
                    return@setOnClickListener
                }
                when (item) {
                    is LoadoutItemReference.Unit ->
                        edit(LoadoutEditAction.SelectUnit(item.identifier, selected = !isSelected))
                    is LoadoutItemReference.Spell ->
                        edit(LoadoutEditAction.ToggleSpell(item.identifier, selected = !isSelected))
                    is LoadoutItemReference.Ammunition ->
                        edit(
                            LoadoutEditAction.ToggleAmmunition(
                                item.identifier,
                                selected = !isSelected,
                            ),
                        )
                }
                showDetails(item)
            }
        }

    private fun showShopPlaceholder(
        item: LoadoutItemReference,
        returnToDetails: Boolean,
    ) {
        viewModel.showDetails(item)
        isShopPlaceholderVisible = true
        shopReturnsToDetails = returnToDetails
        val context = detailsContent.context
        detailsContent.removeAllViews()
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "←"
                textSize = 26f
                setTextColor(Color.WHITE)
                setPadding(dp(context, 8), dp(context, 4), dp(context, 12), dp(context, 4))
                contentDescription = context.getString(
                    if (returnToDetails) R.string.loadout_back_to_spell
                    else R.string.loadout_back_to_loadout,
                )
                setOnClickListener {
                    isShopPlaceholderVisible = false
                    if (returnToDetails) {
                        showDetails(item)
                    } else {
                        viewModel.closeDetails()
                        showMain()
                    }
                }
            }, wrap())
            addView(TextView(context).apply {
                text = context.getString(R.string.loadout_shop_title)
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(255, 211, 106))
            }, wrap())
        }
        detailsContent.addView(header, wrap())
        detailsContent.addView(TextView(context).apply {
            text = context.getString(R.string.loadout_shop_placeholder)
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(context, 8), dp(context, 18), dp(context, 8), dp(context, 18))
        }, matchWrap())
        mainContent.visibility = View.GONE
        detailsContent.visibility = View.VISIBLE
    }

    private fun addItemProperties(
        context: Context,
        item: LoadoutItemReference,
        target: LinearLayout,
    ) {
        fun property(text: String) {
            val label = TextView(context).apply {
                this.text = text
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(context, 4), 0, dp(context, 4))
            }
            target.addView(label, wrap())
        }

        when (item) {
            is LoadoutItemReference.Unit -> {
                val unit = LoadoutCatalog.findUnit(item.identifier)
                    ?: return
                property(context.getString(R.string.loadout_prop_supply_cost, unit.supplyCost))
                property(context.getString(R.string.loadout_prop_health, unit.maxHealth))
                property(context.getString(R.string.loadout_prop_speed, unit.speed))
                property(context.getString(R.string.loadout_prop_attack_damage, unit.attackDamage))
                property(context.getString(R.string.loadout_prop_fortress_damage, unit.fortressDamage))
                property(context.getString(R.string.loadout_prop_attack_range, unit.attackRange))
            }
            is LoadoutItemReference.Ammunition -> {
                val ammunition = LoadoutCatalog.findAmmunition(item.identifier)
                    ?: return
                val effectName = when (ammunition.effect) {
                    LoadoutAmmunitionEffect.DIRECT ->
                        context.getString(R.string.loadout_ammo_effect_direct)
                    LoadoutAmmunitionEffect.EXPLOSIVE ->
                        context.getString(R.string.loadout_ammo_effect_explosive)
                    LoadoutAmmunitionEffect.INCENDIARY ->
                        context.getString(R.string.loadout_ammo_effect_incendiary)
                    LoadoutAmmunitionEffect.PLAGUE ->
                        context.getString(R.string.loadout_ammo_effect_plague)
                }
                property(context.getString(R.string.loadout_prop_ammo_effect, effectName))
                property(context.getString(
                    R.string.loadout_prop_reload,
                    ammunition.reloadSeconds,
                ))
                property(context.getString(
                    R.string.loadout_prop_unit_damage,
                    ammunition.unitDamage,
                ))
                property(context.getString(
                    R.string.loadout_prop_ammo_fortress_damage,
                    ammunition.fortressDamage,
                ))
                property(context.getString(
                    R.string.loadout_prop_damage_radius,
                    ammunition.damageRadius,
                ))
                property(if (ammunition.unlimited) {
                    context.getString(R.string.loadout_unlimited)
                } else {
                    ammunitionStockLabel(item)
                })
            }
            is LoadoutItemReference.Spell -> {
                val spell = LoadoutCatalog.findSpell(item.identifier)
                    ?: return
                property(context.getString(R.string.loadout_prop_gold_cost, spell.goldCost))
                property(context.getString(
                    R.string.loadout_prop_spell_category,
                    spellCategoryName(context, spell.category),
                ))
                if (spell.special) {
                    property(context.getString(R.string.loadout_prop_special_spell))
                    property(context.getString(
                        R.string.loadout_prop_special_stock,
                        viewModel.state.progression?.specialSpellStock(item.identifier) ?: 0,
                    ))
                }
            }
        }
    }

    private fun edit(action: LoadoutEditAction) {
        viewModel.edit(action)
        renderMain()
        if (viewModel.state.detailItem != null) {
            showDetails(viewModel.state.detailItem!!)
        }
    }

    private fun closeDetails() {
        isShopPlaceholderVisible = false
        viewModel.closeDetails()
        showMain()
    }

    private fun showMain() {
        detailsContent.visibility = View.GONE
        mainContent.visibility = View.VISIBLE
    }

    private fun statusText(context: Context): String {
        val state = viewModel.state
        return when {
            state.isLoading -> context.getString(R.string.loadout_status_loading)
            state.isSaving -> context.getString(R.string.loadout_status_saving)
            state.failure != null -> failureMessage(context, state.failure!!)
            state.validationError != null -> loadoutErrorMessage(context, state.validationError!!)
            state.corrections.isNotEmpty() ->
                state.corrections.joinToString("\n") { correction ->
                    context.getString(correctionResource(correction.category, correction.reason))
                }
            state.draft == null -> context.getString(R.string.loadout_status_empty)
            else -> context.getString(R.string.loadout_status_ready)
        }
    }

    private fun ammunitionStockLabel(item: LoadoutItemReference): String {
        val count = viewModel.state.progression?.ammunitionStock(item.identifier) ?: 0
        return mainContent.context.getString(R.string.loadout_stock, count)
    }

    private fun statusLabel(context: Context, status: LoadoutOptionStatus): String =
        context.getString(when (status) {
            LoadoutOptionStatus.SELECTED -> R.string.loadout_state_selected
            LoadoutOptionStatus.AVAILABLE -> R.string.loadout_state_available
            LoadoutOptionStatus.UNAVAILABLE -> R.string.loadout_state_unavailable
            LoadoutOptionStatus.LOCKED -> R.string.loadout_state_locked
            LoadoutOptionStatus.OUT_OF_STOCK -> R.string.loadout_state_out_of_stock
        })

    private fun statusColor(status: LoadoutOptionStatus): Int = when (status) {
        LoadoutOptionStatus.SELECTED -> Color.rgb(134, 220, 143)
        LoadoutOptionStatus.AVAILABLE -> Color.rgb(255, 211, 106)
        LoadoutOptionStatus.UNAVAILABLE,
        LoadoutOptionStatus.LOCKED,
        LoadoutOptionStatus.OUT_OF_STOCK -> Color.rgb(255, 145, 125)
    }

    private fun itemName(identifier: String): String =
        mainContent.context.getString(itemNameResource(identifier))

    private fun itemNameResource(item: LoadoutItemReference): Int =
        itemNameResource(item.identifier)

    private fun itemNameResource(identifier: String): Int = when (identifier) {
        "DEFENDER" -> R.string.loadout_name_defender
        "SAPPER" -> R.string.loadout_name_sapper
        "DEMOLISHER" -> R.string.loadout_name_demolisher
        "RAGING_BOAR" -> R.string.loadout_name_raging_boar
        "SLINGMASTER" -> R.string.loadout_name_slingmaster
        "ROCK" -> R.string.loadout_name_rock
        "SIEGE_BOMB" -> R.string.loadout_name_siege_bomb
        "POWDER_BARREL" -> R.string.loadout_name_powder_barrel
        "FIRE_RAIN" -> R.string.loadout_name_fire_rain
        "PLAGUE_CAULDRON" -> R.string.loadout_name_plague_cauldron
        "ICE_TRAP" -> R.string.loadout_name_ice_trap
        "RUNIC_BASTION" -> R.string.loadout_name_runic_bastion
        "HASTE" -> R.string.loadout_name_haste
        "HEAL" -> R.string.loadout_name_heal
        "FORTRESS_SHIELD" -> R.string.loadout_name_fortress_shield
        "CATAPULT_LOCK" -> R.string.loadout_name_catapult_lock
        "SLOWING_FIELD" -> R.string.loadout_name_slowing_field
        "VAMPIRIC_BANNER" -> R.string.loadout_name_vampiric_banner
        "STONE_SKIN" -> R.string.loadout_name_stone_skin
        "SIEGE_FRENZY" -> R.string.loadout_name_siege_frenzy
        "BERSERKER" -> R.string.loadout_name_berserker
        else -> R.string.loadout_name_unknown
    }

    private fun descriptionResource(item: LoadoutItemReference): Int =
        descriptionResource(item.identifier)

    private fun descriptionResource(identifier: String): Int = when (identifier) {
        "DEFENDER" -> R.string.loadout_desc_defender
        "SAPPER" -> R.string.loadout_desc_sapper
        "DEMOLISHER" -> R.string.loadout_desc_demolisher
        "RAGING_BOAR" -> R.string.loadout_desc_raging_boar
        "SLINGMASTER" -> R.string.loadout_desc_slingmaster
        "ROCK" -> R.string.loadout_desc_rock
        "SIEGE_BOMB" -> R.string.loadout_desc_siege_bomb
        "POWDER_BARREL" -> R.string.loadout_desc_powder_barrel
        "FIRE_RAIN" -> R.string.loadout_desc_fire_rain
        "PLAGUE_CAULDRON" -> R.string.loadout_desc_plague_cauldron
        "ICE_TRAP" -> R.string.loadout_desc_ice_trap
        "RUNIC_BASTION" -> R.string.loadout_desc_runic_bastion
        "HASTE" -> R.string.loadout_desc_haste
        "HEAL" -> R.string.loadout_desc_heal
        "FORTRESS_SHIELD" -> R.string.loadout_desc_fortress_shield
        "CATAPULT_LOCK" -> R.string.loadout_desc_catapult_lock
        "SLOWING_FIELD" -> R.string.loadout_desc_slowing_field
        "VAMPIRIC_BANNER" -> R.string.loadout_desc_vampiric_banner
        "STONE_SKIN" -> R.string.loadout_desc_stone_skin
        "SIEGE_FRENZY" -> R.string.loadout_desc_siege_frenzy
        "BERSERKER" -> R.string.loadout_desc_berserker
        else -> R.string.loadout_desc_unknown
    }

    private fun spellCategoryName(context: Context, category: LoadoutSpellCategory): String =
        context.getString(when (category) {
            LoadoutSpellCategory.OFFENSIVE -> R.string.loadout_spell_category_offensive
            LoadoutSpellCategory.DEFENSIVE -> R.string.loadout_spell_category_defensive
            LoadoutSpellCategory.BATTLEFIELD_CONTROL -> R.string.loadout_spell_category_control
            LoadoutSpellCategory.SUPPORT -> R.string.loadout_spell_category_support
            LoadoutSpellCategory.SIEGE -> R.string.loadout_spell_category_siege
        })

    private fun failureMessage(
        context: Context,
        failure: LoadoutUiFailure,
    ): String = when (failure) {
        is LoadoutUiFailure.Edit -> loadoutErrorMessage(context, failure.error)
        is LoadoutUiFailure.Commit -> loadoutErrorMessage(context, failure.error)
        is LoadoutUiFailure.Progression -> context.getString(when (failure.reason) {
            LoadoutProgressionFailure.INVALID_LEVEL ->
                R.string.loadout_error_progression_invalid_level
            LoadoutProgressionFailure.CAMPAIGN_LEVEL_LOCKED ->
                R.string.loadout_error_progression_locked
            LoadoutProgressionFailure.STORAGE_UNAVAILABLE ->
                R.string.loadout_error_progression_unavailable
            LoadoutProgressionFailure.INVALID_PROFILE ->
                R.string.loadout_error_progression_invalid
            LoadoutProgressionFailure.UNSUPPORTED_PROFILE_VERSION ->
                R.string.loadout_error_progression_version
        })
        is LoadoutUiFailure.Storage -> context.getString(when (failure.reason) {
            LoadoutStorageFailure.INVALID_LEVEL -> R.string.loadout_error_storage_invalid_level
            LoadoutStorageFailure.STORAGE_UNAVAILABLE -> R.string.loadout_error_storage_unavailable
            LoadoutStorageFailure.INVALID_DATA -> R.string.loadout_error_storage_invalid
            LoadoutStorageFailure.UNSUPPORTED_VERSION -> R.string.loadout_error_storage_version
        })
    }

    private fun loadoutErrorMessage(context: Context, error: LoadoutError): String =
        context.getString(when (error) {
            LoadoutError.EMPTY_UNIT_SELECTION -> R.string.loadout_error_empty_units
            LoadoutError.EMPTY_SPELL_SELECTION -> R.string.loadout_error_empty_spells
            LoadoutError.UNKNOWN_UNIT -> R.string.loadout_error_unknown_unit
            LoadoutError.UNIT_UNAVAILABLE -> R.string.loadout_error_unit_unavailable
            LoadoutError.UNKNOWN_AMMUNITION -> R.string.loadout_error_unknown_ammunition
            LoadoutError.AMMUNITION_UNAVAILABLE -> R.string.loadout_error_ammunition_unavailable
            LoadoutError.AMMUNITION_OUT_OF_STOCK -> R.string.loadout_error_ammunition_stock
            LoadoutError.ROCK_SLOT_FIXED -> R.string.loadout_error_rock_fixed
            LoadoutError.UNKNOWN_SPELL -> R.string.loadout_error_unknown_spell
            LoadoutError.SPELL_UNAVAILABLE -> R.string.loadout_error_spell_unavailable
            LoadoutError.SPELL_LOCKED -> R.string.loadout_error_spell_locked
            LoadoutError.SPELL_ALREADY_UNLOCKED -> R.string.loadout_error_spell_already_unlocked
            LoadoutError.SPECIAL_SPELL_OUT_OF_STOCK ->
                R.string.loadout_error_special_spell_stock
            LoadoutError.INVALID_SLOT -> R.string.loadout_error_invalid_slot
            LoadoutError.DUPLICATE_SELECTION -> R.string.loadout_error_duplicate
            LoadoutError.SELECTION_LIMIT_REACHED -> R.string.loadout_error_limit
            LoadoutError.SPECIAL_SPELL_REQUIRES_SHOP -> R.string.loadout_error_shop_required
        })

    private fun correctionResource(category: LoadoutCategory, reason: LoadoutCorrectionReason): Int =
        when (reason) {
            LoadoutCorrectionReason.UNKNOWN -> R.string.loadout_correction_unknown
            LoadoutCorrectionReason.UNAVAILABLE -> R.string.loadout_correction_unavailable
            LoadoutCorrectionReason.LOCKED -> R.string.loadout_correction_locked
            LoadoutCorrectionReason.OUT_OF_STOCK -> R.string.loadout_correction_stock
            LoadoutCorrectionReason.DUPLICATE -> R.string.loadout_correction_duplicate
            LoadoutCorrectionReason.EXCESS -> R.string.loadout_correction_excess
            LoadoutCorrectionReason.INVALID_SLOT -> R.string.loadout_correction_invalid_slot
            LoadoutCorrectionReason.DEFAULT_USED -> R.string.loadout_correction_default
        }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)

    private fun wrap() = LinearLayout.LayoutParams(-2, -2)

}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
