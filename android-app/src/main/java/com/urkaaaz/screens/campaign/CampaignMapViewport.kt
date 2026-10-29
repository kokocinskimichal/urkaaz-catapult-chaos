package com.urkaaaz.screens.campaign

import kotlin.math.max

data class CampaignMapImageBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

data class CampaignMapPixelPoint(
    val x: Float,
    val y: Float,
)

object CampaignMapViewport {
    fun coverBounds(
        viewportWidth: Int,
        viewportHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): CampaignMapImageBounds {
        require(viewportWidth > 0 && viewportHeight > 0) {
            "campaign map viewport dimensions must be positive"
        }
        require(imageWidth > 0 && imageHeight > 0) {
            "campaign map image dimensions must be positive"
        }
        val scale = max(
            viewportWidth.toFloat() / imageWidth,
            viewportHeight.toFloat() / imageHeight,
        )
        val width = imageWidth * scale
        val height = imageHeight * scale
        return CampaignMapImageBounds(
            left = (viewportWidth - width) / 2f,
            top = (viewportHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun anchorCenter(
        bounds: CampaignMapImageBounds,
        anchor: NormalizedCampaignMapAnchor,
    ): CampaignMapPixelPoint =
        CampaignMapPixelPoint(
            x = bounds.left + bounds.width * anchor.x,
            y = bounds.top + bounds.height * anchor.y,
        )
}
