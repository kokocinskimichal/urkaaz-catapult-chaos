package com.urkaaaz.campaign

import com.urkaaaz.contracts.TerrainBiome

class CampaignChapterRegistry(
    chapters: List<CampaignChapterDefinition>,
) {
    val chapters: List<CampaignChapterDefinition> = chapters.toList()
    private val chaptersById = this.chapters.associateBy(CampaignChapterDefinition::id)

    init {
        require(this.chapters.isNotEmpty()) { "at least one campaign chapter is required" }
        require(this.chapters.map(CampaignChapterDefinition::id).distinct().size == this.chapters.size) {
            "campaign chapter IDs must be unique"
        }
        require(this.chapters.map(CampaignChapterDefinition::title).distinct().size == this.chapters.size) {
            "campaign chapter titles must be unique"
        }

        var nextExpectedLevel = 1
        this.chapters.forEach { chapter ->
            require(chapter.firstLevel == nextExpectedLevel) {
                "campaign chapters must be ordered and cover levels without gaps or overlaps"
            }
            require(chapter.levelCount == LEVELS_PER_CHAPTER) {
                "each campaign chapter must contain $LEVELS_PER_CHAPTER levels"
            }
            require(chapter.landmarkKindsByLevel.filterValues {
                it == CampaignMapLandmarkKind.BOSS
            }.keys == setOf(chapter.firstLevel + 4, chapter.firstLevel + 9)) {
                "boss landmarks must be at chapter positions 5 and 10"
            }
            nextExpectedLevel = chapter.lastLevel + 1
        }
        require(nextExpectedLevel == CampaignLevelDefinition.MAX_LEVELS + 1) {
            "campaign chapters must cover levels 1 through ${CampaignLevelDefinition.MAX_LEVELS}"
        }
    }

    fun chapterForLevel(level: Int): CampaignChapterDefinition {
        require(level in 1..CampaignLevelDefinition.MAX_LEVELS) {
            "campaign level $level is outside the registered range"
        }
        return chapters.first { it.containsLevel(level) }
    }

    fun chapterById(id: String): CampaignChapterDefinition =
        chaptersById[id] ?: error("unknown campaign chapter: $id")

    companion object {
        const val LEVELS_PER_CHAPTER = 10

        private val defaultRegistry by lazy {
            CampaignChapterRegistry(
                listOf(
                    chapter("green_frontier", "The Green Frontier", 1, TerrainBiome.GREEN_FRONTIER),
                    chapter("broken_valley", "The Broken Valley", 11, TerrainBiome.FROSTBOUND),
                    chapter("ashen_march", "The Ashen March", 21, TerrainBiome.ASHEN_MARCH),
                    chapter("whispering_marsh", "The Whispering Marsh", 31, TerrainBiome.SUNKEN_MARSHES),
                    chapter("ironwood", "The Ironwood", 41, TerrainBiome.COPPERWOOD),
                    chapter("northern_march", "The Northern March", 51, TerrainBiome.FROSTBOUND),
                    chapter("runic_wastes", "The Runic Wastes", 61, TerrainBiome.ASHEN_MARCH),
                    chapter("red_peaks", "The Red Peaks", 71, TerrainBiome.FROSTBOUND),
                    chapter("final_warpath", "The Final Warpath", 81, TerrainBiome.COPPERWOOD),
                    chapter("last_siege", "The Last Siege", 91, TerrainBiome.ASHEN_MARCH),
                ),
            )
        }

        fun default(): CampaignChapterRegistry = defaultRegistry

        private fun chapter(
            id: String,
            title: String,
            firstLevel: Int,
            biome: TerrainBiome,
        ): CampaignChapterDefinition {
            val lastLevel = firstLevel + LEVELS_PER_CHAPTER - 1
            val landmarkKinds = (firstLevel..lastLevel).associateWith { level ->
                when (level - firstLevel + 1) {
                    5, 10 -> CampaignMapLandmarkKind.BOSS
                    else -> CampaignMapLandmarkKind.FORTRESS
                }
            }
            return CampaignChapterDefinition(
                id = id,
                title = title,
                firstLevel = firstLevel,
                lastLevel = lastLevel,
                biome = biome,
                landmarkKindsByLevel = landmarkKinds,
            )
        }
    }
}
