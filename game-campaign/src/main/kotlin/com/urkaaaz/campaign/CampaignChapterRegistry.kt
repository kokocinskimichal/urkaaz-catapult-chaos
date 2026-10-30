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
            when (val catalog = CampaignChapterCatalogRepository.bundled.load()) {
                is CampaignChapterCatalogRead.Available ->
                    CampaignChapterRegistry(catalog.document.chapters.map(::chapter))
                is CampaignChapterCatalogRead.Failure ->
                    error("Unable to load campaign chapter catalog: ${catalog.reason}")
            }
        }

        fun default(): CampaignChapterRegistry = defaultRegistry

        private fun chapter(
            entry: CampaignChapterCatalogEntry,
        ): CampaignChapterDefinition {
            val biome = TerrainBiome.fromMapId(entry.biome)
                ?: error("Unknown campaign chapter biome '${entry.biome}'")
            val landmarkKinds = (entry.firstLevel..entry.lastLevel).associateWith { level ->
                if (level - entry.firstLevel + 1 in entry.bossPositions) {
                    CampaignMapLandmarkKind.BOSS
                } else {
                    CampaignMapLandmarkKind.FORTRESS
                }
            }
            return CampaignChapterDefinition(
                id = entry.id,
                title = entry.title,
                firstLevel = entry.firstLevel,
                lastLevel = entry.lastLevel,
                biome = biome,
                landmarkKindsByLevel = landmarkKinds,
            )
        }
    }
}
