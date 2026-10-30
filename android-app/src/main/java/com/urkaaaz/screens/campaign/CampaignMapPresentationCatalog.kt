package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R
import com.urkaaaz.campaign.CampaignMapAnchor
import com.urkaaaz.campaign.CampaignMapLayoutDefinition
import com.urkaaaz.campaign.CampaignChapterRegistry

data class CampaignMapPresentation(
    val chapterId: String,
    val backgroundDrawable: Int,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val anchors: List<CampaignMapAnchor>,
) {
    init {
        require(chapterId.isNotBlank()) { "chapterId must not be blank" }
        require(backgroundDrawable != 0) { "campaign map background resource is required" }
        require(imageWidthPx > 0 && imageHeightPx > 0) {
            "campaign map source dimensions must be positive"
        }
        require(anchors.size == CampaignChapterRegistry.LEVELS_PER_CHAPTER) {
            "campaign map must define one anchor for every chapter level"
        }
    }
}

sealed interface CampaignMapPresentationRead {
    data class Available(val presentation: CampaignMapPresentation) : CampaignMapPresentationRead
    data class Failure(val reason: String) : CampaignMapPresentationRead
}

object CampaignMapPresentationCatalog {
    fun forLayout(layout: CampaignMapLayoutDefinition): CampaignMapPresentationRead {
        val backgroundDrawable = when (layout.backgroundAssetKey) {
            GREEN_FRONTIER_MAP_ASSET_KEY -> R.drawable.campaign_chapter_01_green_frontier_map
            else -> return CampaignMapPresentationRead.Failure(
                "Unsupported campaign map asset: ${layout.backgroundAssetKey}",
            )
        }

        return CampaignMapPresentationRead.Available(
            CampaignMapPresentation(
                chapterId = layout.chapterId,
                backgroundDrawable = backgroundDrawable,
                imageWidthPx = layout.imageWidthPx,
                imageHeightPx = layout.imageHeightPx,
                anchors = layout.anchors,
            ),
        )
    }

    private const val GREEN_FRONTIER_MAP_ASSET_KEY =
        "campaign_chapter_01_green_frontier_map"
}
