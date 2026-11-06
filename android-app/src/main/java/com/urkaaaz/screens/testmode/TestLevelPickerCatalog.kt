package com.urkaaaz.screens.testmode

import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignMapLandmarkKind
import com.urkaaaz.screens.campaign.CampaignChapterTitleResources

data class TestLevelOption(
    val level: Int,
    val isBoss: Boolean,
)

data class TestLevelPickerChapter(
    val number: Int,
    val id: String,
    val titleResource: Int,
    val levels: List<TestLevelOption>,
)

object TestLevelPickerCatalog {
    fun chapters(
        registry: CampaignChapterRegistry = CampaignChapterRegistry.default(),
    ): List<TestLevelPickerChapter> =
        registry.chapters.mapIndexedNotNull { index, chapter ->
            val levels = (chapter.firstLevel..minOf(
                chapter.lastLevel,
                CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL,
            )).map { level ->
                TestLevelOption(
                    level = level,
                    isBoss = chapter.landmarkKindForLevel(level) ==
                        CampaignMapLandmarkKind.BOSS,
                )
            }
            if (levels.isEmpty()) {
                null
            } else {
                TestLevelPickerChapter(
                    number = index + 1,
                    id = chapter.id,
                    titleResource = CampaignChapterTitleResources.forChapter(chapter.id),
                    levels = levels,
                )
            }
        }
}
