package com.urkaaaz.campaign

import kotlinx.serialization.Serializable

@Serializable
data class CampaignMapLayoutDefinition(
    val schemaVersion: Int,
    val chapterId: String,
    val backgroundAssetKey: String,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val anchors: List<CampaignMapAnchor>,
)

@Serializable
data class CampaignMapAnchor(
    val level: Int,
    val x: Float,
    val y: Float,
)
