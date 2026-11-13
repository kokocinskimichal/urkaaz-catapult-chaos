package com.urkaaaz.screens.shop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.urkaaaz.android.R
import com.urkaaaz.application.shop.ShopCategory
import com.urkaaaz.application.shop.ShopItemReference
import com.urkaaaz.application.shop.ShopOffer
import com.urkaaaz.application.shop.ShopUseCases
import com.urkaaaz.navigation.ScreenContent
import com.urkaaaz.screens.loadout.LoadoutAssetCatalog
import com.urkaaaz.screens.loadout.LoadoutAssetLookup
import com.urkaaaz.screens.loadout.LoadoutItemReference
import com.urkaaaz.screens.loadout.LoadoutItemResources
import com.urkaaaz.ui.ButtonTypographyControl
import com.urkaaaz.ui.UiTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShopScreen(
    private val context: Context,
    useCases: ShopUseCases,
    initialItem: ShopItemReference? = null,
    private val onBack: () -> Unit,
) : ScreenContent {
    private val viewModel = ShopViewModel(useCases, initialItem)
    private val headingTypeface = requireNotNull(
        ResourcesCompat.getFont(context, R.font.cinzel_semibold),
    ) {
        "Cinzel SemiBold font resource could not be loaded"
    }
    private var screenScope: CoroutineScope? = null
    private lateinit var balanceLabel: TextView
    private lateinit var spellsTab: TextView
    private lateinit var ammunitionTab: TextView
    private lateinit var offerGrid: LinearLayout
    private lateinit var detailPanel: LinearLayout

    override val view: View = createView(context)

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

    override fun onBackPressed(): Boolean {
        onBack()
        return true
    }

    private fun createView(context: Context): View = FrameLayout(context).apply {
        addView(ImageView(context).apply {
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.shop_background_reference)
        }, FrameLayout.LayoutParams(-1, -1))

        addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, Color.argb(95, 10, 8, 7)),
            )
        }, FrameLayout.LayoutParams(-1, -1))

        addView(createHeader(context), FrameLayout.LayoutParams(
            -1,
            dp(context, 72),
            Gravity.TOP,
        ).apply {
            topMargin = dp(context, 12)
            leftMargin = dp(context, 18)
            rightMargin = dp(context, 18)
        })

        val metrics = resources.displayMetrics
        val panelWidth = (metrics.widthPixels * 0.62f)
            .toInt()
            .coerceAtMost(metrics.widthPixels - dp(context, 28))
        val panelHeight = (metrics.heightPixels * 0.75f)
            .toInt()
            .coerceAtMost(metrics.heightPixels - dp(context, 112))
        val panel = createCatalogPanel(context)
        addView(
            panel,
            FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = dp(context, 24)
                bottomMargin = dp(context, 20)
            },
        )
    }

    private fun createHeader(context: Context): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(TextView(context).apply {
                text = context.getString(R.string.shop_back)
                gravity = Gravity.CENTER
                UiTypography.applyTextSize(this, ButtonTypographyControl.SHOP_BACK)
                setTextColor(Color.rgb(72, 43, 20))
                typeface = headingTypeface
                contentDescription = context.getString(R.string.shop_back)
                setBackgroundResource(R.drawable.loadout_start_match_button_xs_short)
                setOnClickListener { onBack() }
            }, LinearLayout.LayoutParams(dp(context, 126), dp(context, 48)))

            addView(FrameLayout(context).apply {
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.shared_header_frame_low_profile)
                    scaleType = ImageView.ScaleType.FIT_XY
                    contentDescription = null
                }, FrameLayout.LayoutParams(-1, -1))
                addView(TextView(context).apply {
                    text = context.getString(R.string.shop_title)
                    gravity = Gravity.CENTER
                    textSize = 17f
                    typeface = headingTypeface
                    setTextColor(Color.rgb(255, 238, 199))
                    setShadowLayer(dp(context, 2).toFloat(), 0f, dp(context, 1).toFloat(), Color.BLACK)
                }, FrameLayout.LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(dp(context, 286), dp(context, 72)).apply {
                marginStart = dp(context, 18)
            })

            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

            val coinPanel = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 12), dp(context, 4), dp(context, 14), dp(context, 4))
                background = frameBackground(context, radiusDp = 12, strokeDp = 2)
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.icon_orcoins)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = null
                }, LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)))
                balanceLabel = TextView(context).apply {
                    textSize = 17f
                    typeface = headingTypeface
                    setTextColor(Color.rgb(255, 239, 205))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(context, 9), 0, 0, 0)
                    contentDescription = context.getString(R.string.shop_header_accessibility)
                }
                addView(balanceLabel, LinearLayout.LayoutParams(-2, -1))
            }
            addView(coinPanel, LinearLayout.LayoutParams(-2, dp(context, 50)))
        }

    private fun createCatalogPanel(context: Context): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            background = frameBackground(context, radiusDp = 20, strokeDp = 3)

            val tabs = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(createTab(context, ShopCategory.SPELLS).also { spellsTab = it },
                    LinearLayout.LayoutParams(0, dp(context, 52), 1f).apply {
                        marginEnd = dp(context, 5)
                    })
                addView(createTab(context, ShopCategory.AMMUNITION).also { ammunitionTab = it },
                    LinearLayout.LayoutParams(0, dp(context, 52), 1f).apply {
                        marginStart = dp(context, 5)
                    })
            }
            addView(tabs, LinearLayout.LayoutParams(-1, dp(context, 56)))

            addView(View(context).apply {
                setBackgroundColor(Color.argb(190, 185, 140, 70))
            }, LinearLayout.LayoutParams(-1, dp(context, 1)).apply {
                topMargin = dp(context, 5)
                bottomMargin = dp(context, 8)
            })

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                offerGrid = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.TOP
                    setPadding(dp(context, 2), dp(context, 2), dp(context, 6), dp(context, 2))
                }
                val offerScroll = ScrollView(context).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = false
                    addView(offerGrid)
                }
                addView(offerScroll, LinearLayout.LayoutParams(0, -1, 1.55f))
                addView(View(context).apply {
                    setBackgroundColor(Color.argb(200, 190, 143, 78))
                }, LinearLayout.LayoutParams(dp(context, 1), -1).apply {
                    marginStart = dp(context, 2)
                    marginEnd = dp(context, 8)
                })
                val detailScroll = ScrollView(context).apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = false
                    detailPanel = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        setPadding(dp(context, 8), dp(context, 2), dp(context, 2), dp(context, 2))
                    }
                    addView(detailPanel)
                }
                addView(detailScroll, LinearLayout.LayoutParams(0, -1, 1.08f))
            }
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        }

    private fun createTab(context: Context, category: ShopCategory): TextView =
        TextView(context).apply {
            gravity = Gravity.CENTER
            text = context.getString(
                if (category == ShopCategory.SPELLS) {
                    R.string.shop_tab_spells
                } else {
                    R.string.shop_tab_ammunition
                },
            )
            UiTypography.applyTextSize(
                this,
                if (category == ShopCategory.SPELLS) {
                    ButtonTypographyControl.SHOP_TAB_SPELLS
                } else {
                    ButtonTypographyControl.SHOP_TAB_AMMUNITION
                },
            )
            typeface = headingTypeface
            setTextColor(Color.rgb(255, 239, 205))
            setOnClickListener {
                viewModel.selectCategory(category)
                render()
            }
        }

    private fun render() {
        val state = viewModel.state
        if (!::balanceLabel.isInitialized) return
        balanceLabel.text = state.catalog?.let {
            contextString(R.string.shop_balance, it.goldBalance)
        } ?: context.getString(R.string.shop_balance_unavailable)
        spellsTab.setBackgroundResource(tabBackground(ShopCategory.SPELLS, state.category))
        ammunitionTab.setBackgroundResource(tabBackground(ShopCategory.AMMUNITION, state.category))
        spellsTab.setTextColor(tabTextColor(ShopCategory.SPELLS, state.category))
        ammunitionTab.setTextColor(tabTextColor(ShopCategory.AMMUNITION, state.category))
        renderOffers(state)
        renderDetails(state)
    }

    private fun renderOffers(state: ShopUiState) {
        offerGrid.removeAllViews()
        if (state.isLoading) {
            offerGrid.addView(messageText(context, R.string.shop_loading), wrap())
            return
        }
        if (state.catalog == null) {
            offerGrid.addView(messageText(context, R.string.shop_catalog_load_failed), wrap())
            return
        }
        val offers = state.offers
        if (offers.isEmpty()) {
            offerGrid.addView(messageText(context, R.string.shop_empty_category), wrap())
            return
        }
        offers.chunked(3).forEach { rowOffers ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            rowOffers.forEachIndexed { index, offer ->
                row.addView(
                    createOfferCard(offer, offer.item == state.selectedItem),
                    LinearLayout.LayoutParams(0, dp(172), 1f).apply {
                        if (index > 0) marginStart = dp(6)
                        bottomMargin = dp(8)
                    },
                )
            }
            repeat(3 - rowOffers.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, dp(172), 1f))
            }
            offerGrid.addView(row, LinearLayout.LayoutParams(-1, dp(180)))
        }
    }

    private fun createOfferCard(offer: ShopOffer, selected: Boolean): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.argb(236, 19, 17, 15))
                setStroke(
                    dp(if (selected) 2 else 1),
                    if (selected) Color.rgb(255, 191, 55) else Color.rgb(137, 108, 72),
                )
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                viewModel.selectItem(offer.item)
                render()
            }

            addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(
                    LoadoutItemResources.name(offer.item.identifier),
                )
                when (val image = itemArtwork(offer.item)) {
                    is LoadoutAssetLookup.Found -> setImageResource(image.drawableId)
                    LoadoutAssetLookup.Missing -> Unit
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            if (itemArtwork(offer.item) == LoadoutAssetLookup.Missing) {
                addView(messageText(
                    context,
                    R.string.shop_art_unavailable,
                ).apply {
                    UiTypography.applyTextSize(
                        this,
                        ButtonTypographyControl.SHOP_OFFER_ART_UNAVAILABLE,
                    )
                }, wrap())
            }
            addView(TextView(context).apply {
                text = context.getString(LoadoutItemResources.name(offer.item.identifier))
                UiTypography.applyTextSize(
                    this,
                    ButtonTypographyControl.commonItemName(offer.item.identifier),
                )
                typeface = headingTypeface
                gravity = Gravity.CENTER
                maxLines = 2
                setTextColor(Color.rgb(255, 239, 205))
                setPadding(0, dp(2), 0, dp(2))
            }, LinearLayout.LayoutParams(-1, dp(31)))
            addView(TextView(context).apply {
                text = context.getString(R.string.shop_price, offer.priceGold)
                UiTypography.applyTextSize(this, ButtonTypographyControl.SHOP_OFFER_PRICE)
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(255, 207, 100))
                maxLines = 1
            }, LinearLayout.LayoutParams(-1, dp(17)))
            addView(TextView(context).apply {
                text = context.getString(R.string.shop_stock, offer.stock)
                UiTypography.applyTextSize(this, ButtonTypographyControl.SHOP_OFFER_STOCK)
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(218, 210, 192))
                maxLines = 1
            }, LinearLayout.LayoutParams(-1, dp(16)))
        }

    private fun renderDetails(state: ShopUiState) {
        detailPanel.removeAllViews()
        val offer = state.selectedOffer
        if (offer == null) {
            detailPanel.addView(messageText(
                context,
                if (state.isLoading) R.string.shop_loading else R.string.shop_empty_category,
            ), wrap())
            state.error?.let { addStatus(detailPanel, errorMessage(it)) }
            return
        }

        detailPanel.addView(TextView(context).apply {
            text = context.getString(LoadoutItemResources.name(offer.item.identifier))
            textSize = 18f
            typeface = headingTypeface
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 231, 183))
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, dp(50)))
        detailPanel.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(
                LoadoutItemResources.name(offer.item.identifier),
            )
            when (val image = itemArtwork(offer.item)) {
                is LoadoutAssetLookup.Found -> setImageResource(image.drawableId)
                LoadoutAssetLookup.Missing -> Unit
            }
        }, LinearLayout.LayoutParams(-1, dp(148)).apply {
            topMargin = dp(5)
            bottomMargin = dp(7)
        })
        if (itemArtwork(offer.item) == LoadoutAssetLookup.Missing) {
            addStatus(
                detailPanel,
                context.getString(R.string.shop_art_unavailable),
                warning = true,
            )
        }
        detailPanel.addView(TextView(context).apply {
            text = context.getString(LoadoutItemResources.description(offer.item.identifier))
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(245, 231, 202))
            setPadding(dp(3), dp(4), dp(3), dp(8))
        }, wrap())
        if (!offer.runtimeImplemented) {
            addStatus(
                detailPanel,
                context.getString(R.string.shop_unavailable_in_match),
                warning = true,
            )
        }
        detailPanel.addView(TextView(context).apply {
            text = context.getString(R.string.shop_price, offer.priceGold)
            textSize = 13f
            typeface = headingTypeface
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 211, 106))
            setPadding(0, dp(6), 0, dp(4))
        }, wrap())
        detailPanel.addView(TextView(context).apply {
            text = context.getString(R.string.shop_stock, offer.stock)
            textSize = 12f
            typeface = headingTypeface
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(239, 228, 207))
            setPadding(0, dp(2), 0, dp(8))
        }, wrap())
        state.message?.let { message ->
            addStatus(
                detailPanel,
                context.getString(
                    if (message == ShopUiMessage.PURCHASED) {
                        R.string.shop_purchase_success
                    } else {
                        R.string.shop_insufficient_orcoins
                    },
                ),
                warning = message == ShopUiMessage.INSUFFICIENT_ORCOINS,
            )
        }
        state.error?.let { addStatus(detailPanel, errorMessage(it), warning = true) }

        val canAfford = state.catalog?.goldBalance?.let { it >= offer.priceGold } == true
        detailPanel.addView(TextView(context).apply {
            text = context.getString(R.string.shop_buy)
            gravity = Gravity.CENTER
            UiTypography.applyTextSize(this, ButtonTypographyControl.SHOP_BUY)
            typeface = headingTypeface
            setTextColor(Color.rgb(73, 42, 18))
            setBackgroundResource(R.drawable.loadout_start_match_button)
            isEnabled = !state.isLoading && !state.isPurchasing && canAfford
            alpha = if (isEnabled) 1f else 0.55f
            contentDescription = context.getString(
                R.string.shop_offer_accessibility,
                context.getString(LoadoutItemResources.name(offer.item.identifier)),
                offer.priceGold,
                offer.stock,
            )
            setOnClickListener {
                screenScope?.launch {
                    viewModel.purchaseSelected()
                    render()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        })
        if (state.catalog?.goldBalance?.let { it < offer.priceGold } == true) {
            addStatus(detailPanel, context.getString(R.string.shop_insufficient_orcoins), warning = true)
        }
    }

    private fun addStatus(
        target: LinearLayout,
        text: String,
        warning: Boolean = false,
    ) {
        target.addView(TextView(context).apply {
            this.text = text
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(
                if (warning) Color.rgb(255, 165, 126) else Color.rgb(174, 231, 174),
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun errorMessage(error: ShopUiError): String = context.getString(
        when (error) {
            is ShopUiError.Catalog -> when (error.reason) {
                com.urkaaaz.application.shop.ShopReadFailure.STORAGE_UNAVAILABLE ->
                    R.string.shop_storage_unavailable
                com.urkaaaz.application.shop.ShopReadFailure.INVALID_PROFILE ->
                    R.string.shop_invalid_profile
                com.urkaaaz.application.shop.ShopReadFailure.UNSUPPORTED_PROFILE_VERSION ->
                    R.string.shop_unsupported_profile
            }
            ShopUiError.UNKNOWN_ITEM,
            ShopUiError.NOT_FOR_SALE,
            -> R.string.shop_item_unavailable
            ShopUiError.INVALID_PROFILE -> R.string.shop_invalid_profile
            ShopUiError.UNSUPPORTED_PROFILE_VERSION -> R.string.shop_unsupported_profile
            ShopUiError.STORAGE_UNAVAILABLE -> R.string.shop_storage_unavailable
            ShopUiError.STOCK_OVERFLOW -> R.string.shop_stock_limit_reached
            ShopUiError.PURCHASE_SAVED_REFRESH_FAILED ->
                R.string.shop_purchase_saved_refresh_failed
        },
    )

    private fun messageText(context: Context, resource: Int): TextView =
        TextView(context).apply {
            text = context.getString(resource)
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.rgb(239, 228, 207))
            setPadding(dp(context, 12), dp(context, 14), dp(context, 12), dp(context, 14))
        }

    private fun itemArtwork(item: ShopItemReference): LoadoutAssetLookup =
        when (item.category) {
            ShopCategory.AMMUNITION ->
                LoadoutAssetCatalog.carouselArtwork(LoadoutItemReference.Ammunition(item.identifier))
            ShopCategory.SPELLS ->
                LoadoutAssetCatalog.carouselArtwork(LoadoutItemReference.Spell(item.identifier))
        }

    private fun tabBackground(category: ShopCategory, selected: ShopCategory): Int =
        if (category == selected) {
            R.drawable.loadout_start_match_button
        } else {
            R.drawable.ui_button_dark_m
        }

    private fun tabTextColor(category: ShopCategory, selected: ShopCategory): Int =
        if (category == selected) {
            Color.rgb(68, 40, 18)
        } else {
            Color.rgb(242, 229, 207)
        }

    private fun frameBackground(
        context: Context,
        radiusDp: Int,
        strokeDp: Int,
    ) = GradientDrawable().apply {
        cornerRadius = dp(context, radiusDp).toFloat()
        setColor(Color.argb(233, 18, 15, 13))
        setStroke(dp(context, strokeDp), Color.rgb(164, 120, 61))
    }

    private fun contextString(resource: Int, value: Int): String =
        context.getString(resource, value)

    private fun wrap() = LinearLayout.LayoutParams(-1, -2)

    private fun dp(value: Int): Int = dp(context, value)

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
