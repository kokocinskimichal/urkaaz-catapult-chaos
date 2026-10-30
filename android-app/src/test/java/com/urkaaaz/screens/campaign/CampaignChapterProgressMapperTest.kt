package com.urkaaaz.screens.campaign

import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.campaign.CampaignChapterRegistry
import com.urkaaaz.campaign.CampaignMapLandmarkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignChapterProgressMapperTest {
    private val mapper = CampaignChapterProgressMapper(CampaignChapterRegistry.default())

    @Test
    fun initialProgressShowsCurrentChapterAndOneLockedPreview() {
        val state = mapper.map(CampaignProgress.INITIAL)

        assertEquals(
            listOf("green_frontier", "broken_valley"),
            state.pages.map { it.chapter.id },
        )
        assertEquals("green_frontier", state.selectedChapterId)
        assertEquals(CampaignChapterPageStatus.CURRENT, state.pages[0].status)
        assertEquals(CampaignChapterPageStatus.PREVIEW, state.pages[1].status)
        assertEquals(CampaignLevelMarkerStatus.AVAILABLE, state.pages[0].markers.first().status)
        assertTrue(
            state.pages[0].markers.drop(1).all {
                it.status == CampaignLevelMarkerStatus.LOCKED
            },
        )
        assertTrue(
            state.pages[1].markers.all {
                it.status == CampaignLevelMarkerStatus.LOCKED
            },
        )
    }

    @Test
    fun completedAndCurrentMarkersPreserveLandmarkKinds() {
        val state = mapper.map(
            CampaignProgress(
                highestUnlockedLevel = 6,
                completedLevels = (1..5).toSet(),
            ),
        )
        val chapter = state.pages.first()

        assertEquals(CampaignLevelMarkerStatus.COMPLETED, chapter.markers[4].status)
        assertEquals(CampaignMapLandmarkKind.BOSS, chapter.markers[4].landmarkKind)
        assertEquals(CampaignLevelMarkerStatus.AVAILABLE, chapter.markers[5].status)
        assertEquals(CampaignMapLandmarkKind.BOSS, chapter.markers[9].landmarkKind)
    }

    @Test
    fun nextChapterBecomesCurrentWhileOnlyOneFollowingChapterIsPreviewed() {
        val state = mapper.map(
            CampaignProgress(
                highestUnlockedLevel = 11,
                completedLevels = (1..10).toSet(),
            ),
        )

        assertEquals(
            listOf("green_frontier", "broken_valley", "ashen_march"),
            state.pages.map { it.chapter.id },
        )
        assertEquals("broken_valley", state.selectedChapterId)
        assertEquals(CampaignChapterPageStatus.COMPLETED, state.pages[0].status)
        assertEquals(CampaignLevelMarkerStatus.AVAILABLE, state.pages[1].markers.first().status)
        assertEquals(CampaignChapterPageStatus.PREVIEW, state.pages[2].status)
        assertTrue(
            state.pages[2].markers.all {
                it.status == CampaignLevelMarkerStatus.LOCKED
            },
        )
    }

    @Test
    fun legacyProgressBeyondBossContentFrontierKeepsLaterBossLevelsLocked() {
        val state = mapper.map(
            CampaignProgress(
                highestUnlockedLevel = 100,
                completedLevels = (1..100).toSet(),
            ),
        )

        assertEquals(7, state.pages.size)
        assertEquals(state.pages[5].chapter.id, state.selectedChapterId)
        assertTrue(
            state.pages.take(5).all { page ->
                page.status == CampaignChapterPageStatus.COMPLETED
            },
        )
        assertEquals(CampaignChapterPageStatus.CURRENT, state.pages[5].status)
        assertEquals(CampaignChapterPageStatus.PREVIEW, state.pages.last().status)
        assertTrue(
            state.pages[5].markers.filter { it.level >= 55 }.all { marker ->
                marker.status == CampaignLevelMarkerStatus.LOCKED
            },
        )
        assertTrue(state.pages.last().markers.all { it.status == CampaignLevelMarkerStatus.LOCKED })
    }
}
