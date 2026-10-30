package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignChapterDefinition
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignMapLandmarkKind

enum class CampaignChapterPageStatus {
    COMPLETED,
    CURRENT,
    PREVIEW,
}

enum class CampaignLevelMarkerStatus {
    COMPLETED,
    AVAILABLE,
    LOCKED,
}

data class CampaignLevelMarkerState(
    val level: Int,
    val landmarkKind: CampaignMapLandmarkKind,
    val status: CampaignLevelMarkerStatus,
)

data class CampaignChapterPageState(
    val chapter: CampaignChapterDefinition,
    val status: CampaignChapterPageStatus,
    val markers: List<CampaignLevelMarkerState>,
)

data class CampaignMapProgressionState(
    val pages: List<CampaignChapterPageState>,
    val selectedChapterId: String,
)

class CampaignChapterProgressMapper(
    private val registry: CampaignChapterRegistry = CampaignChapterRegistry.default(),
) {
    fun map(progress: CampaignProgress): CampaignMapProgressionState {
        val playableFrontier = progress.highestUnlockedLevel.coerceAtMost(
            CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL,
        )
        val currentChapter = registry.chapterForLevel(playableFrontier)
        val currentIndex = registry.chapters.indexOf(currentChapter)
        val currentChapterId = currentChapter.id

        val visibleChapters = registry.chapters.mapIndexedNotNull { index, chapter ->
            val isCompleted = chapter.lastLevel <=
                CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL &&
                (chapter.firstLevel..chapter.lastLevel)
                .all { it in progress.completedLevels }
            val status = when {
                isCompleted -> CampaignChapterPageStatus.COMPLETED
                index == currentIndex -> CampaignChapterPageStatus.CURRENT
                index == currentIndex + 1 -> CampaignChapterPageStatus.PREVIEW
                else -> return@mapIndexedNotNull null
            }
            val markers = (chapter.firstLevel..chapter.lastLevel).map { level ->
                val markerStatus = when {
                    level > CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL ->
                        CampaignLevelMarkerStatus.LOCKED
                    level in progress.completedLevels -> CampaignLevelMarkerStatus.COMPLETED
                    status == CampaignChapterPageStatus.CURRENT &&
                        level == playableFrontier ->
                        CampaignLevelMarkerStatus.AVAILABLE
                    else -> CampaignLevelMarkerStatus.LOCKED
                }
                CampaignLevelMarkerState(
                    level = level,
                    landmarkKind = chapter.landmarkKindForLevel(level),
                    status = markerStatus,
                )
            }
            CampaignChapterPageState(chapter, status, markers)
        }

        return CampaignMapProgressionState(
            pages = visibleChapters,
            selectedChapterId = currentChapterId,
        )
    }
}
