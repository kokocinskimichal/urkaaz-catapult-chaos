package com.urkaaaz.campaign

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.urkaaaz.contracts.TerrainBiome
import java.io.IOException

@Serializable
data class CampaignChapterCatalogDocument(
    val schemaVersion: Int,
    val chapters: List<CampaignChapterCatalogEntry>,
)

@Serializable
data class CampaignChapterCatalogEntry(
    val id: String,
    val title: String,
    val firstLevel: Int,
    val lastLevel: Int,
    val biome: String,
    val bossPositions: List<Int>,
)

sealed interface CampaignChapterCatalogRead {
    data class Available(val document: CampaignChapterCatalogDocument) : CampaignChapterCatalogRead
    data class Failure(val reason: CampaignChapterCatalogFailure) : CampaignChapterCatalogRead
}

enum class CampaignChapterCatalogFailure {
    MISSING_RESOURCE,
    INVALID_DATA,
    STORAGE_UNAVAILABLE,
}

class CampaignChapterCatalogRepository(
    private val openResource: (String) -> java.io.InputStream?,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    }

    fun load(): CampaignChapterCatalogRead {
        return try {
            val input = openResource(CAMPAIGN_CHAPTERS_RESOURCE)
                ?: return CampaignChapterCatalogRead.Failure(
                    CampaignChapterCatalogFailure.MISSING_RESOURCE,
                )
            val document = input.bufferedReader().use {
                json.decodeFromString<CampaignChapterCatalogDocument>(it.readText())
            }
            if (!isValid(document)) {
                CampaignChapterCatalogRead.Failure(CampaignChapterCatalogFailure.INVALID_DATA)
            } else {
                CampaignChapterCatalogRead.Available(document)
            }
        } catch (_: SerializationException) {
            CampaignChapterCatalogRead.Failure(CampaignChapterCatalogFailure.INVALID_DATA)
        } catch (_: IllegalArgumentException) {
            CampaignChapterCatalogRead.Failure(CampaignChapterCatalogFailure.INVALID_DATA)
        } catch (_: IOException) {
            CampaignChapterCatalogRead.Failure(CampaignChapterCatalogFailure.STORAGE_UNAVAILABLE)
        }
    }

    private fun isValid(document: CampaignChapterCatalogDocument): Boolean {
        if (document.schemaVersion != SCHEMA_VERSION || document.chapters.isEmpty()) return false
        if (document.chapters.map { it.id }.distinct().size != document.chapters.size) return false
        if (document.chapters.map { it.title }.distinct().size != document.chapters.size) return false

        var nextLevel = 1
        for (chapter in document.chapters) {
            if (!chapter.id.matches(CHAPTER_ID) ||
                chapter.title.isBlank() ||
                chapter.firstLevel != nextLevel ||
                chapter.lastLevel != chapter.firstLevel + CampaignChapterRegistry.LEVELS_PER_CHAPTER - 1 ||
                TerrainBiome.fromMapId(chapter.biome) == null ||
                chapter.bossPositions != EXPECTED_BOSS_POSITIONS
            ) return false
            nextLevel = chapter.lastLevel + 1
        }
        return nextLevel == CampaignLevelDefinition.MAX_LEVELS + 1
    }

    companion object {
        const val CAMPAIGN_CHAPTERS_RESOURCE = "campaign-chapters.json"
        const val SCHEMA_VERSION = 1
        private val CHAPTER_ID = Regex("[a-z][a-z0-9_]*")
        private val EXPECTED_BOSS_POSITIONS = listOf(5, 10)

        val bundled = CampaignChapterCatalogRepository { path ->
            CampaignChapterCatalogRepository::class.java.classLoader.getResourceAsStream(path)
        }
    }
}
