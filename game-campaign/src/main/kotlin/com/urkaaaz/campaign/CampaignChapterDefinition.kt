package com.urkaaaz.campaign

import com.urkaaaz.contracts.TerrainBiome

data class CampaignChapterDefinition(
    val id: String,
    val title: String,
    val firstLevel: Int,
    val lastLevel: Int,
    val biome: TerrainBiome,
    val landmarkKindsByLevel: Map<Int, CampaignMapLandmarkKind>,
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9_]*"))) {
            "chapter ID must be a stable lowercase identifier"
        }
        require(title.isNotBlank()) { "chapter title must not be blank" }
        require(firstLevel >= 1 && lastLevel >= firstLevel) {
            "chapter level range must be valid"
        }
        require(landmarkKindsByLevel.keys == (firstLevel..lastLevel).toSet()) {
            "every chapter level must have exactly one map landmark kind"
        }
    }

    val levelCount: Int
        get() = lastLevel - firstLevel + 1

    fun containsLevel(level: Int): Boolean = level in firstLevel..lastLevel

    fun landmarkKindForLevel(level: Int): CampaignMapLandmarkKind =
        landmarkKindsByLevel[level]
            ?: error("level $level is outside campaign chapter '$id'")
}
