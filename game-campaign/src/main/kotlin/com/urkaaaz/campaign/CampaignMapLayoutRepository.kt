package com.urkaaaz.campaign

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

sealed interface CampaignMapLayoutRead {
    data class Available(val layout: CampaignMapLayoutDefinition) : CampaignMapLayoutRead
    data class MapArtworkMissing(val chapterId: String) : CampaignMapLayoutRead
    data class Failure(
        val chapterId: String,
        val reason: CampaignMapLayoutFailure,
    ) : CampaignMapLayoutRead
}

enum class CampaignMapLayoutFailure {
    INVALID_CHAPTER,
    INVALID_DATA,
    STORAGE_UNAVAILABLE,
}

class CampaignMapLayoutRepository(
    private val openResource: (String) -> java.io.InputStream?,
    private val chapterRegistry: CampaignChapterRegistry = CampaignChapterRegistry.default(),
) {
    private val cache = ConcurrentHashMap<String, CampaignMapLayoutRead>()
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = true
    }

    fun load(chapterId: String): CampaignMapLayoutRead =
        cache.computeIfAbsent(chapterId, ::loadUncached)

    private fun loadUncached(chapterId: String): CampaignMapLayoutRead {
        val chapter = chapterRegistry.chapters.firstOrNull { it.id == chapterId }
            ?: return CampaignMapLayoutRead.Failure(
                chapterId,
                CampaignMapLayoutFailure.INVALID_CHAPTER,
            )
        return try {
            val input = openResource("$RESOURCE_DIRECTORY/$chapterId.json")
                ?: return CampaignMapLayoutRead.MapArtworkMissing(chapterId)
            val layout = input.bufferedReader().use {
                json.decodeFromString<CampaignMapLayoutDefinition>(it.readText())
            }
            val errors = validate(layout, chapter)
            if (errors.isNotEmpty()) {
                CampaignMapLayoutRead.Failure(
                    chapterId,
                    CampaignMapLayoutFailure.INVALID_DATA,
                )
            } else {
                CampaignMapLayoutRead.Available(layout)
            }
        } catch (error: SerializationException) {
            CampaignMapLayoutRead.Failure(chapterId, CampaignMapLayoutFailure.INVALID_DATA)
        } catch (error: IllegalArgumentException) {
            CampaignMapLayoutRead.Failure(chapterId, CampaignMapLayoutFailure.INVALID_DATA)
        } catch (error: IOException) {
            CampaignMapLayoutRead.Failure(chapterId, CampaignMapLayoutFailure.STORAGE_UNAVAILABLE)
        }
    }

    companion object {
        const val RESOURCE_DIRECTORY = "campaign-map-layouts"
        const val SCHEMA_VERSION = 1

        private val BACKGROUND_ASSET_KEY =
            Regex("campaign_chapter_[0-9]{2}_[a-z0-9_]+_map")

        val bundled = CampaignMapLayoutRepository(
            openResource = { path ->
                CampaignMapLayoutRepository::class.java.classLoader.getResourceAsStream(path)
            },
        )

        fun validate(
            layout: CampaignMapLayoutDefinition,
            chapter: CampaignChapterDefinition,
        ): List<String> = buildList {
            if (layout.schemaVersion != SCHEMA_VERSION) add("schemaVersion: unsupported")
            if (layout.chapterId != chapter.id) add("chapterId: does not match chapter")
            if (!BACKGROUND_ASSET_KEY.matches(layout.backgroundAssetKey)) {
                add("backgroundAssetKey: invalid asset key")
            }
            if (layout.imageWidthPx <= 0) add("imageWidthPx: must be positive")
            if (layout.imageHeightPx <= 0) add("imageHeightPx: must be positive")
            if (layout.anchors.map { it.level } !=
                (chapter.firstLevel..chapter.lastLevel).toList()
            ) {
                add("anchors: must contain every chapter level in order")
            }
            layout.anchors.forEachIndexed { index, anchor ->
                if (!anchor.x.isFinite() || anchor.x !in 0f..1f) {
                    add("anchors[$index].x: must be finite and normalized")
                }
                if (!anchor.y.isFinite() || anchor.y !in 0f..1f) {
                    add("anchors[$index].y: must be finite and normalized")
                }
            }
        }
    }
}
