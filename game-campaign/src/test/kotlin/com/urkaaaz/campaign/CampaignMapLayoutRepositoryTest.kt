package com.urkaaaz.campaign

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CampaignMapLayoutRepositoryTest {
    @Test
    fun bundledChapterOneLayoutContainsEveryGlobalLevelAnchor() {
        val result = CampaignMapLayoutRepository.bundled.load("green_frontier")

        assertTrue(result is CampaignMapLayoutRead.Available)
        val layout = (result as CampaignMapLayoutRead.Available).layout
        assertEquals("campaign_chapter_01_green_frontier_map", layout.backgroundAssetKey)
        assertEquals((1..10).toList(), layout.anchors.map { it.level })
    }

    @Test
    fun missingChapterArtworkIsReportedSeparatelyFromInvalidChapter() {
        assertEquals(
            CampaignMapLayoutRead.MapArtworkMissing("broken_valley"),
            CampaignMapLayoutRepository.bundled.load("broken_valley"),
        )
        assertEquals(
            CampaignMapLayoutRead.Failure(
                "unknown_chapter",
                CampaignMapLayoutFailure.INVALID_CHAPTER,
            ),
            CampaignMapLayoutRepository.bundled.load("unknown_chapter"),
        )
    }

    @Test
    fun layoutRejectsMarkersOutsideNormalizedCoordinates() {
        val chapter = CampaignChapterRegistry.default().chapterById("green_frontier")
        val layout = CampaignMapLayoutDefinition(
            schemaVersion = 1,
            chapterId = "green_frontier",
            backgroundAssetKey = "campaign_map",
            imageWidthPx = 1608,
            imageHeightPx = 905,
            anchors = (1..10).map { level ->
                CampaignMapAnchor(
                    level = level,
                    x = if (level == 3) 1.1f else level / 10f,
                    y = level / 10f,
                )
            },
        )

        val errors = CampaignMapLayoutRepository.validate(layout, chapter)

        assertTrue(errors.any { it == "anchors[2].x: must be finite and normalized" })
    }

    @Test
    fun malformedLayoutResourceIsAnExplicitFailure() {
        val repository = CampaignMapLayoutRepository(
            openResource = { ByteArrayInputStream("{}".toByteArray()) },
        )

        assertEquals(
            CampaignMapLayoutRead.Failure(
                "green_frontier",
                CampaignMapLayoutFailure.INVALID_DATA,
            ),
            repository.load("green_frontier"),
        )
    }
}
