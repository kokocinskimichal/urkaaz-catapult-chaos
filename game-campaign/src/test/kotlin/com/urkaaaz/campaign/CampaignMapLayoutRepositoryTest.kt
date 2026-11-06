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
        assertEquals(1_670, layout.imageWidthPx)
        assertEquals(942, layout.imageHeightPx)
        assertEquals((1..10).toList(), layout.anchors.map { it.level })
    }

    @Test
    fun bundledChapterTwoLayoutContainsEveryGlobalLevelAnchor() {
        val result = CampaignMapLayoutRepository.bundled.load("broken_valley")

        assertTrue(result is CampaignMapLayoutRead.Available)
        val layout = (result as CampaignMapLayoutRead.Available).layout
        assertEquals("campaign_chapter_02_broken_valley_map", layout.backgroundAssetKey)
        assertEquals(1_670, layout.imageWidthPx)
        assertEquals(942, layout.imageHeightPx)
        assertEquals((11..20).toList(), layout.anchors.map { it.level })
    }

    @Test
    fun bundledChapterThreeLayoutContainsEveryGlobalLevelAnchor() {
        val result = CampaignMapLayoutRepository.bundled.load("ashen_march")

        assertTrue(result is CampaignMapLayoutRead.Available)
        val layout = (result as CampaignMapLayoutRead.Available).layout
        assertEquals("campaign_chapter_03_ashen_march_map", layout.backgroundAssetKey)
        assertEquals(1_672, layout.imageWidthPx)
        assertEquals(941, layout.imageHeightPx)
        assertEquals((21..30).toList(), layout.anchors.map { it.level })
    }

    @Test
    fun missingChapterArtworkIsReportedSeparatelyFromInvalidChapter() {
        assertEquals(
            CampaignMapLayoutRead.MapArtworkMissing("whispering_marsh"),
            CampaignMapLayoutRepository.bundled.load("whispering_marsh"),
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
