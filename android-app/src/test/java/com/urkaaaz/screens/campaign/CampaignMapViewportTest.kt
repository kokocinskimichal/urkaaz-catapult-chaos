package com.urkaaaz.screens.campaign

import com.urkaaaz.campaign.CampaignMapAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignMapViewportTest {
    @Test
    fun coverBoundsCropsImageEdgesAndKeepsTheImageCenterAnchored() {
        val bounds = CampaignMapViewport.coverBounds(
            viewportWidth = 1000,
            viewportHeight = 1000,
            imageWidth = 1600,
            imageHeight = 900,
        )

        assertEquals(-388.8889f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(1777.7778f, bounds.width, 0.01f)
        assertEquals(1000f, bounds.height, 0.001f)

        val center = CampaignMapViewport.anchorCenter(
            bounds,
            CampaignMapAnchor(level = 1, x = 0.5f, y = 0.5f),
        )
        assertEquals(500f, center.x, 0.001f)
        assertEquals(500f, center.y, 0.001f)
    }

    @Test
    fun coverBoundsFillsWideLandscapeViewportsWithoutSideLetterboxing() {
        val bounds = CampaignMapViewport.coverBounds(
            viewportWidth = 2400,
            viewportHeight = 1080,
            imageWidth = 1608,
            imageHeight = 905,
        )

        val expectedHeight = 2400f * 905f / 1608f
        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(0f - (expectedHeight - 1080f) / 2f, bounds.top, 0.001f)
        assertEquals(2400f, bounds.width, 0.001f)
        assertEquals(expectedHeight, bounds.height, 0.001f)

        val levelOne = CampaignMapViewport.anchorCenter(
            bounds,
            CampaignMapAnchor(level = 1, x = 0.145f, y = 0.75f),
        )
        assertEquals(348f, levelOne.x, 0.001f)
        assertEquals(bounds.top + bounds.height * 0.75f, levelOne.y, 0.001f)
        assertTrue(levelOne.y in 0f..1080f)
    }
}
