package com.urkaaaz.screens.campaign

import com.urkaaaz.campaign.CampaignChapterRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignChapterTitleResourcesTest {
    @Test
    fun everyRegisteredChapterHasAStableAndroidTitleResource() {
        CampaignChapterRegistry.default().chapters.forEach { chapter ->
            assertTrue(
                "missing title resource for ${chapter.id}",
                CampaignChapterTitleResources.forChapter(chapter.id) > 0,
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun unknownChapterIdsAreRejected() {
        CampaignChapterTitleResources.forChapter("unknown_chapter")
    }
}
