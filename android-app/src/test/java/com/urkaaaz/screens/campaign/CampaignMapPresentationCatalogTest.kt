package com.urkaaaz.screens.campaign

import com.urkaaaz.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignMapPresentationCatalogTest {
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
}
