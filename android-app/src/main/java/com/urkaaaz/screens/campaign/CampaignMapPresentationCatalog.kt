package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R
import com.urkaaaz.campaign.CampaignChapterDefinition
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignMapAnchor
import com.urkaaaz.campaign.CampaignMapLayoutDefinition

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
            BROKEN_VALLEY_MAP_ASSET_KEY -> R.drawable.campaign_chapter_02_broken_valley_map
            ASHEN_MARCH_MAP_ASSET_KEY -> R.drawable.campaign_chapter_03_ashen_march_map
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

    fun forFallbackChapter(
        chapter: CampaignChapterDefinition,
        greenFrontierLayout: CampaignMapLayoutDefinition,
    ): CampaignMapPresentationRead {
        val chapters = CampaignChapterRegistry.default().chapters
        val chapterIndex = chapters.indexOfFirst {
            it.id == chapter.id
        }
        if (chapterIndex < FIRST_FALLBACK_CHAPTER_INDEX ||
            chapters[chapterIndex] != chapter
        ) {
            return CampaignMapPresentationRead.Failure(
                "Green Frontier fallback is not supported for chapter '${chapter.id}'",
            )
        }
        if (greenFrontierLayout.chapterId != GREEN_FRONTIER_CHAPTER_ID ||
            greenFrontierLayout.backgroundAssetKey != GREEN_FRONTIER_MAP_ASSET_KEY ||
            greenFrontierLayout.imageWidthPx <= 0 ||
            greenFrontierLayout.imageHeightPx <= 0 ||
            greenFrontierLayout.anchors.size != chapter.levelCount ||
            greenFrontierLayout.anchors.map { it.level } != (1..chapter.levelCount).toList()
        ) {
            return CampaignMapPresentationRead.Failure(
                "Green Frontier fallback layout is invalid for chapter '${chapter.id}'",
            )
        }

        val anchors = greenFrontierLayout.anchors.mapIndexed { index, anchor ->
            CampaignMapAnchor(
                level = chapter.firstLevel + index,
                x = anchor.x,
                y = anchor.y,
            )
        }
        return CampaignMapPresentationRead.Available(
            CampaignMapPresentation(
                chapterId = chapter.id,
                backgroundDrawable = R.drawable.campaign_chapter_01_green_frontier_map,
                imageWidthPx = greenFrontierLayout.imageWidthPx,
                imageHeightPx = greenFrontierLayout.imageHeightPx,
                anchors = anchors,
            ),
        )
    }

    private const val FIRST_FALLBACK_CHAPTER_INDEX = 3
    private const val GREEN_FRONTIER_CHAPTER_ID = "green_frontier"
    private const val GREEN_FRONTIER_MAP_ASSET_KEY =
        "campaign_chapter_01_green_frontier_map"
    private const val BROKEN_VALLEY_MAP_ASSET_KEY =
        "campaign_chapter_02_broken_valley_map"
    private const val ASHEN_MARCH_MAP_ASSET_KEY =
        "campaign_chapter_03_ashen_march_map"
}
