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
    fun chapterTabIconsAreRegisteredOnlyForTheTwoImportedChapters() {
        assertEquals(
            R.drawable.campaign_chapter_01_icon,
            CampaignMapPresentationCatalog.chapterTabIconDrawable("green_frontier"),
        )
        assertEquals(
            R.drawable.campaign_chapter_02_icon,
            CampaignMapPresentationCatalog.chapterTabIconDrawable("broken_valley"),
        )
        assertNull(CampaignMapPresentationCatalog.chapterTabIconDrawable("ashen_march"))
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
