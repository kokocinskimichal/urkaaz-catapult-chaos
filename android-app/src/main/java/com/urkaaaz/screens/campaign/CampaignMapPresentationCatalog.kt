package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R

data class NormalizedCampaignMapAnchor(
    val level: Int,
    val x: Float,
    val y: Float,
) {
    init {
        require(level in 1..10) { "campaign map anchor level must be between 1 and 10" }
        require(x.isFinite() && x in 0f..1f && y.isFinite() && y in 0f..1f) {
            "campaign map anchor coordinates must be finite and normalized"
        }
    }
}

data class CampaignMapPresentation(
    val chapterId: String,
    val backgroundDrawable: Int,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val anchors: List<NormalizedCampaignMapAnchor>,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(backgroundDrawable != 0) { "campaign map background resource is required" }
        require(imageWidthPx > 0 && imageHeightPx > 0) {
            "campaign map source dimensions must be positive"
        }
        require(anchors.map { it.level } == (1..10).toList()) {
            "campaign map must define one ordered anchor for each level"
        }
        require(anchors.map { it.x to it.y }.distinct().size == anchors.size) {
            "campaign map anchor coordinates must be unique"
        }
    }
}

object CampaignMapPresentationCatalog {
    private val greenFrontier = CampaignMapPresentation(
        chapterId = "green_frontier",
        backgroundDrawable = R.drawable.campaign_chapter_01_green_frontier_map,
        imageWidthPx = 1608,
        imageHeightPx = 905,
        anchors = listOf(
            NormalizedCampaignMapAnchor(1, 0.145f, 0.750f),
            NormalizedCampaignMapAnchor(2, 0.255f, 0.545f),
            NormalizedCampaignMapAnchor(3, 0.365f, 0.365f),
            NormalizedCampaignMapAnchor(4, 0.485f, 0.260f),
            NormalizedCampaignMapAnchor(5, 0.645f, 0.330f),
            NormalizedCampaignMapAnchor(6, 0.845f, 0.245f),
            NormalizedCampaignMapAnchor(7, 0.855f, 0.455f),
            NormalizedCampaignMapAnchor(8, 0.755f, 0.675f),
            NormalizedCampaignMapAnchor(9, 0.565f, 0.750f),
            NormalizedCampaignMapAnchor(10, 0.485f, 0.475f),
        ),
    )

    fun forChapter(chapterId: String): CampaignMapPresentation? =
        when (chapterId) {
            greenFrontier.chapterId -> greenFrontier
            else -> null
        }
}
