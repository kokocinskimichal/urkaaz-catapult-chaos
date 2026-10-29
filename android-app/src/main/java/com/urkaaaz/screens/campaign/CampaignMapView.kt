package com.urkaaaz.screens.campaign

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.urkaaaz.android.R
import kotlin.math.roundToInt

class CampaignMapView(
    context: Context,
    private val presentation: CampaignMapPresentation,
    markers: List<CampaignLevelMarkerState>,
    private val selectedLevel: Int?,
    private val onMarkerSelected: (Int) -> Unit,
) : FrameLayout(context) {
    private val markerStates = markers.associateBy { it.level }
    private val markerViews = mutableMapOf<Int, View>()
    private val markerSizes = mutableMapOf<Int, Int>()
    private val glowViews = mutableMapOf<Int, View>()
    private val glowSizes = mutableMapOf<Int, Int>()

    init {
        clipChildren = false
        clipToPadding = false
        require(markers.map { it.level } == presentation.anchors.map { it.level }) {
            "campaign map markers must match the presentation anchors"
        }

        addView(ImageView(context).apply {
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(presentation.backgroundDrawable)
        }, LayoutParams(-1, -1))

        markers
            .filter {
                it.status == CampaignLevelMarkerStatus.AVAILABLE &&
                    it.level == selectedLevel
            }
            .forEach { marker ->
                val glowSize = markerIconSize(marker) + dp(64)
                glowViews[marker.level] = ImageView(context).apply {
                    contentDescription = null
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    alpha = 0.72f
                    setImageResource(R.drawable.campaign_map_marker_available_glow)
                }
                glowSizes[marker.level] = glowSize
                addView(glowViews.getValue(marker.level), LayoutParams(glowSize, glowSize))
            }

        markers.forEach { marker ->
            val markerView = createMarker(marker)
            val markerSize = markerSize(marker)
            markerViews[marker.level] = markerView
            markerSizes[marker.level] = markerSize
            addView(markerView, LayoutParams(markerSize, markerSize))
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        val imageBounds = CampaignMapViewport.coverBounds(
            viewportWidth = width,
            viewportHeight = height,
            imageWidth = presentation.imageWidthPx,
            imageHeight = presentation.imageHeightPx,
        )
        presentation.anchors.forEach { anchor ->
            val center = CampaignMapViewport.anchorCenter(imageBounds, anchor)
            val markerSize = markerSizes.getValue(anchor.level)
            glowViews[anchor.level]?.let { glowView ->
                val glowSize = glowSizes.getValue(anchor.level)
                val iconSize = markerIconSize(markerStates.getValue(anchor.level))
                glowView.layoutParams = LayoutParams(glowSize, glowSize).apply {
                    leftMargin = (center.x - glowSize / 2f).roundToInt()
                    topMargin = (
                        center.y - markerSize / 2f + (iconSize - glowSize) / 2f
                    ).roundToInt()
                }
            }
            markerViews.getValue(anchor.level).layoutParams =
                LayoutParams(markerSize, markerSize).apply {
                    leftMargin = (center.x - markerSize / 2f).roundToInt()
                    topMargin = (center.y - markerSize / 2f).roundToInt()
                }
        }
    }

    private fun createMarker(marker: CampaignLevelMarkerState): View =
        FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            val isAvailable = marker.status == CampaignLevelMarkerStatus.AVAILABLE
            val isSelected = marker.level == selectedLevel
            val iconSize = markerIconSize(marker)
            isFocusable = true
            isEnabled = isAvailable
            isClickable = isAvailable
            contentDescription = context.getString(
                if (isSelected) {
                    R.string.campaign_map_marker_accessibility_selected
                } else {
                    R.string.campaign_map_marker_accessibility
                },
                marker.level,
                context.getString(
                    if (marker.landmarkKind == com.urkaaaz.campaign.CampaignMapLandmarkKind.BOSS) {
                        R.string.campaign_marker_type_boss
                    } else {
                        R.string.campaign_marker_type_fortress
                    },
                ),
                context.getString(
                    when (marker.status) {
                        CampaignLevelMarkerStatus.COMPLETED ->
                            R.string.campaign_marker_status_completed
                        CampaignLevelMarkerStatus.AVAILABLE ->
                            R.string.campaign_marker_status_available
                        CampaignLevelMarkerStatus.LOCKED ->
                            R.string.campaign_marker_status_locked
                    },
                ),
            )

            addView(ImageView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = when (marker.status) {
                    CampaignLevelMarkerStatus.COMPLETED -> 0.78f
                    CampaignLevelMarkerStatus.AVAILABLE -> 1f
                    CampaignLevelMarkerStatus.LOCKED -> 0.72f
                }
                setImageResource(
                    if (marker.landmarkKind == com.urkaaaz.campaign.CampaignMapLandmarkKind.BOSS) {
                        R.drawable.campaign_map_marker_boss
                    } else {
                        R.drawable.campaign_map_marker_fortress
                    },
                )
            }, LayoutParams(iconSize, iconSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

            val plaqueWidth = dp(56)
            val plaqueHeight = dp(22)
            val plaqueTop = iconSize - dp(12)
            val statusIconSize = dp(20)
            val statusIconTop = plaqueTop + (plaqueHeight - statusIconSize) / 2
            val markerWidth = iconSize + dp(28)
            addView(ImageView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(plaqueDrawable(marker.status))
            }, LayoutParams(plaqueWidth, plaqueHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = plaqueTop
            })

            val hasStatusGlyph = marker.status != CampaignLevelMarkerStatus.AVAILABLE
            addView(TextView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                text = marker.level.toString()
                textSize = 13f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setTextColor(
                    if (marker.status == CampaignLevelMarkerStatus.LOCKED) {
                        Color.rgb(67, 45, 28)
                    } else {
                        Color.rgb(255, 239, 211)
                    },
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                if (hasStatusGlyph) setPadding(0, 0, statusIconSize, 0)
                if (marker.status != CampaignLevelMarkerStatus.LOCKED) {
                    setShadowLayer(2f, 0f, 1f, Color.BLACK)
                }
            }, LayoutParams(plaqueWidth, plaqueHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = plaqueTop
            })

            when (marker.status) {
                CampaignLevelMarkerStatus.COMPLETED -> addView(ImageView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(R.drawable.campaign_level_status_completed)
                }, LayoutParams(statusIconSize, statusIconSize, Gravity.TOP or Gravity.END).apply {
                    topMargin = statusIconTop
                    marginEnd = (markerWidth - plaqueWidth) / 2 + dp(2)
                })
                CampaignLevelMarkerStatus.AVAILABLE -> Unit
                CampaignLevelMarkerStatus.LOCKED -> addView(ImageView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(R.drawable.campaign_level_status_locked)
                }, LayoutParams(statusIconSize, statusIconSize, Gravity.TOP or Gravity.END).apply {
                    topMargin = statusIconTop
                    marginEnd = (markerWidth - plaqueWidth) / 2 + dp(2)
                })
            }
            if (isAvailable) {
                setOnClickListener { onMarkerSelected(marker.level) }
            }
        }

    private fun plaqueDrawable(status: CampaignLevelMarkerStatus): Int =
        when (status) {
            CampaignLevelMarkerStatus.COMPLETED -> R.drawable.campaign_level_plate_completed
            CampaignLevelMarkerStatus.AVAILABLE -> R.drawable.campaign_level_plate_available
            CampaignLevelMarkerStatus.LOCKED -> R.drawable.campaign_level_plate_locked
        }

    private fun markerIconSize(marker: CampaignLevelMarkerState): Int =
        dp(
            if (marker.landmarkKind == com.urkaaaz.campaign.CampaignMapLandmarkKind.BOSS) {
                48
            } else {
                72
            },
        )

    private fun markerSize(marker: CampaignLevelMarkerState): Int =
        markerIconSize(marker) + dp(28)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
