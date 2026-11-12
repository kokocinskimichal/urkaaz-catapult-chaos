package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignMapLayoutRead
import com.urkaaaz.campaign.CampaignMapLayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignMapPresentationCatalogTest {
    @Test
    fun brokenValleyLayoutUsesItsImportedCampaignMapDrawable() {
        val layoutRead = CampaignMapLayoutRepository.bundled.load("broken_valley")
        assertTrue(layoutRead is CampaignMapLayoutRead.Available)

        val presentationRead = CampaignMapPresentationCatalog.forLayout(
            (layoutRead as CampaignMapLayoutRead.Available).layout,
        )
        assertTrue(presentationRead is CampaignMapPresentationRead.Available)
        assertEquals(
            R.drawable.campaign_chapter_02_broken_valley_map,
            (presentationRead as CampaignMapPresentationRead.Available)
                .presentation.backgroundDrawable,
        )
    }

    @Test
    fun missingChapterArtworkUsesGreenFrontierPresentationWithGlobalLevelAnchors() {
        val greenFrontier = CampaignMapLayoutRepository.bundled.load("green_frontier")
        assertTrue(greenFrontier is CampaignMapLayoutRead.Available)
        val sourceLayout = (greenFrontier as CampaignMapLayoutRead.Available).layout

        CampaignChapterRegistry.default().chapters.drop(4).forEach { chapter ->
            val presentationRead = CampaignMapPresentationCatalog.forFallbackChapter(
                chapter,
                sourceLayout,
            )

            assertTrue(presentationRead is CampaignMapPresentationRead.Available)
            val presentation =
                (presentationRead as CampaignMapPresentationRead.Available).presentation
            assertEquals(chapter.id, presentation.chapterId)
            assertEquals(
                R.drawable.campaign_chapter_01_green_frontier_map,
                presentation.backgroundDrawable,
            )
            assertEquals(sourceLayout.imageWidthPx, presentation.imageWidthPx)
            assertEquals(sourceLayout.imageHeightPx, presentation.imageHeightPx)
            assertEquals(
                (chapter.firstLevel..chapter.lastLevel).toList(),
                presentation.anchors.map { it.level },
            )
            assertEquals(sourceLayout.anchors.map { it.x }, presentation.anchors.map { it.x })
            assertEquals(sourceLayout.anchors.map { it.y }, presentation.anchors.map { it.y })
        }
    }

    @Test
    fun greenFrontierFallbackIsRejectedForChaptersWithTheirOwnArtwork() {
        val greenFrontier = CampaignMapLayoutRepository.bundled.load("green_frontier")
        assertTrue(greenFrontier is CampaignMapLayoutRead.Available)

        val presentationRead = CampaignMapPresentationCatalog.forFallbackChapter(
            CampaignChapterRegistry.default().chapterById("broken_valley"),
            (greenFrontier as CampaignMapLayoutRead.Available).layout,
        )

        assertTrue(presentationRead is CampaignMapPresentationRead.Failure)
    }

    @Test
    fun ironwoodLayoutUsesItsImportedCampaignMapDrawable() {
        val layoutRead = CampaignMapLayoutRepository.bundled.load("ironwood")
        assertTrue(layoutRead is CampaignMapLayoutRead.Available)

        val presentationRead = CampaignMapPresentationCatalog.forLayout(
            (layoutRead as CampaignMapLayoutRead.Available).layout,
        )
        assertTrue(presentationRead is CampaignMapPresentationRead.Available)
        assertEquals(
            R.drawable.campaign_chapter_03_ironwood_map,
            (presentationRead as CampaignMapPresentationRead.Available)
                .presentation.backgroundDrawable,
        )
    }

    @Test
    fun ashenMarchLayoutUsesItsImportedChapterFourCampaignMapDrawable() {
        val layoutRead = CampaignMapLayoutRepository.bundled.load("ashen_march")
        assertTrue(layoutRead is CampaignMapLayoutRead.Available)

        val presentationRead = CampaignMapPresentationCatalog.forLayout(
            (layoutRead as CampaignMapLayoutRead.Available).layout,
        )
        assertTrue(presentationRead is CampaignMapPresentationRead.Available)
        assertEquals(
            R.drawable.campaign_chapter_04_ashen_march_map,
            (presentationRead as CampaignMapPresentationRead.Available)
                .presentation.backgroundDrawable,
        )
    }
}
