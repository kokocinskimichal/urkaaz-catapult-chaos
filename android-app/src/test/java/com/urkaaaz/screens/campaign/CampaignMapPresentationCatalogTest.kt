package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R
import com.urkaaaz.campaign.CampaignMapLayoutRead
import com.urkaaaz.campaign.CampaignMapLayoutRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignMapPresentationCatalogTest {
    @Test
    fun chapterSelectorUsesOneSharedParchmentBackplate() {
        assertEquals(
            R.drawable.campaign_chapter_selector_parchment,
            CampaignMapPresentationCatalog.chapterSelectorBackgroundDrawable(),
        )
    }

    @Test
    fun chapterTabThumbnailsAreRegisteredOnlyForChaptersWithImportedArt() {
        assertEquals(
            R.drawable.campaign_chapter_01_thumbnail,
            CampaignMapPresentationCatalog.chapterTabThumbnailDrawable("green_frontier"),
        )
        assertEquals(
            R.drawable.campaign_chapter_02_thumbnail,
            CampaignMapPresentationCatalog.chapterTabThumbnailDrawable("broken_valley"),
        )
        assertEquals(
            R.drawable.campaign_chapter_03_thumbnail,
            CampaignMapPresentationCatalog.chapterTabThumbnailDrawable("ashen_march"),
        )
        assertNull(CampaignMapPresentationCatalog.chapterTabThumbnailDrawable("whispering_marsh"))
    }

    @Test
    fun chapterTabFramesDistinguishTheSelectedCardFromOtherCards() {
        assertEquals(
            R.drawable.campaign_chapter_thumbnail_frame_current,
            CampaignMapPresentationCatalog.chapterTabFrameDrawable(isSelected = true),
        )
        assertEquals(
            R.drawable.campaign_chapter_thumbnail_frame_other,
            CampaignMapPresentationCatalog.chapterTabFrameDrawable(isSelected = false),
        )
    }

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
    fun ashenMarchLayoutUsesItsImportedCampaignMapDrawable() {
        val layoutRead = CampaignMapLayoutRepository.bundled.load("ashen_march")
        assertTrue(layoutRead is CampaignMapLayoutRead.Available)

        val presentationRead = CampaignMapPresentationCatalog.forLayout(
            (layoutRead as CampaignMapLayoutRead.Available).layout,
        )
        assertTrue(presentationRead is CampaignMapPresentationRead.Available)
        assertEquals(
            R.drawable.campaign_chapter_03_ashen_march_map,
            (presentationRead as CampaignMapPresentationRead.Available)
                .presentation.backgroundDrawable,
        )
    }
}
