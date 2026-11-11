package com.urkaaaz.screens.campaign

import com.urkaaaz.campaign.CampaignMapAnchor
import com.urkaaaz.campaign.CampaignMapLandmarkKind
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
            imageWidth = 1717,
            imageHeight = 916,
        )

        val expectedHeight = 2400f * 916f / 1717f
        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(0f - (expectedHeight - 1080f) / 2f, bounds.top, 0.001f)
        assertEquals(2400f, bounds.width, 0.001f)
        assertEquals(expectedHeight, bounds.height, 0.001f)

        val levelOneAnchor = CampaignMapAnchor(
            level = 1,
            x = 0.145f,
            y = 0.75f,
        )
        val levelOne = CampaignMapViewport.anchorCenter(
            bounds,
            levelOneAnchor,
        )
        assertEquals(bounds.width * levelOneAnchor.x, levelOne.x, 0.001f)
        assertEquals(bounds.top + bounds.height * levelOneAnchor.y, levelOne.y, 0.001f)
        assertTrue(levelOne.y in 0f..1080f)
    }

    @Test
    fun bossMapArtIs150PercentOfItsFormerSizeWithoutChangingFortressArt() {
        assertEquals(
            CampaignMapMarkerDimensions.BOSS_BASE_ICON_SIZE_DP *
                CampaignMapMarkerDimensions.BOSS_ICON_SCALE_PERCENT / 100,
            CampaignMapMarkerDimensions.iconSizeDp(CampaignMapLandmarkKind.BOSS),
        )
        assertEquals(
            72,
            CampaignMapMarkerDimensions.iconSizeDp(CampaignMapLandmarkKind.BOSS),
        )
        assertEquals(
            72,
            CampaignMapMarkerDimensions.iconSizeDp(CampaignMapLandmarkKind.FORTRESS),
        )
    }
}
