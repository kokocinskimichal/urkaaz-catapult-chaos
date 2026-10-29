package com.urkaaaz.campaign

import com.urkaaaz.contracts.TerrainBiome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CampaignChapterRegistryTest {
    @Test
    fun defaultRegistryCoversCampaignWithAcceptedChapterBiomes() {
        val registry = CampaignChapterRegistry.default()

        assertEquals(10, registry.chapters.size)
        assertEquals(
            listOf(
                "green_frontier",
                "broken_valley",
                "ashen_march",
                "whispering_marsh",
                "ironwood",
                "northern_march",
                "runic_wastes",
                "red_peaks",
                "final_warpath",
                "last_siege",
            ),
            registry.chapters.map(CampaignChapterDefinition::id),
        )
        assertEquals(
            listOf(
                TerrainBiome.GREEN_FRONTIER,
                TerrainBiome.FROSTBOUND,
                TerrainBiome.ASHEN_MARCH,
                TerrainBiome.SUNKEN_MARSHES,
                TerrainBiome.COPPERWOOD,
                TerrainBiome.FROSTBOUND,
                TerrainBiome.ASHEN_MARCH,
                TerrainBiome.FROSTBOUND,
                TerrainBiome.COPPERWOOD,
                TerrainBiome.ASHEN_MARCH,
            ),
            registry.chapters.map(CampaignChapterDefinition::biome),
        )
        assertEquals(1, registry.chapterForLevel(1).firstLevel)
        assertEquals(10, registry.chapterForLevel(10).lastLevel)
        assertEquals(11, registry.chapterForLevel(11).firstLevel)
        assertEquals(100, registry.chapterForLevel(100).lastLevel)
    }

    @Test
    fun everyChapterHasBossLandmarksOnlyAtPositionsFiveAndTen() {
        CampaignChapterRegistry.default().chapters.forEach { chapter ->
            val bosses = chapter.landmarkKindsByLevel
                .filterValues { it == CampaignMapLandmarkKind.BOSS }
                .keys

            assertEquals(
                setOf(chapter.firstLevel + 4, chapter.firstLevel + 9),
                bosses,
                chapter.id,
            )
            assertEquals(
                8,
                chapter.landmarkKindsByLevel.values.count {
                    it == CampaignMapLandmarkKind.FORTRESS
                },
                chapter.id,
            )
        }
    }

    @Test
    fun registryRejectsGapsAndIncorrectBossPositions() {
        assertFailsWith<IllegalArgumentException> {
            CampaignChapterRegistry(listOf(chapter(firstLevel = 1), chapter(firstLevel = 21)))
        }
        assertFailsWith<IllegalArgumentException> {
            CampaignChapterRegistry(
                listOf(
                    chapter(firstLevel = 1),
                    chapter(firstLevel = 11, bossPositions = setOf(6, 10)),
                ),
            )
        }
    }

    @Test
    fun chapterRejectsIncompleteLandmarkMetadata() {
        assertFailsWith<IllegalArgumentException> {
            CampaignChapterDefinition(
                id = "broken_chapter",
                title = "Broken Chapter",
                firstLevel = 1,
                lastLevel = 10,
                biome = TerrainBiome.GREEN_FRONTIER,
                landmarkKindsByLevel = mapOf(1 to CampaignMapLandmarkKind.FORTRESS),
            )
        }
    }

    @Test
    fun unknownLevelAndChapterAreRejected() {
        val registry = CampaignChapterRegistry.default()

        assertFailsWith<IllegalArgumentException> { registry.chapterForLevel(0) }
        assertFailsWith<IllegalArgumentException> { registry.chapterForLevel(101) }
        assertFailsWith<IllegalStateException> { registry.chapterById("missing_chapter") }
    }

    private fun chapter(
        firstLevel: Int,
        id: String = "chapter_$firstLevel",
        bossPositions: Set<Int> = setOf(5, 10),
    ): CampaignChapterDefinition {
        val lastLevel = firstLevel + CampaignChapterRegistry.LEVELS_PER_CHAPTER - 1
        return CampaignChapterDefinition(
            id = id,
            title = "Chapter $firstLevel",
            firstLevel = firstLevel,
            lastLevel = lastLevel,
            biome = TerrainBiome.GREEN_FRONTIER,
            landmarkKindsByLevel = (firstLevel..lastLevel).associateWith { level ->
                if (level - firstLevel + 1 in bossPositions) {
                    CampaignMapLandmarkKind.BOSS
                } else {
                    CampaignMapLandmarkKind.FORTRESS
                }
            },
        )
    }
}
