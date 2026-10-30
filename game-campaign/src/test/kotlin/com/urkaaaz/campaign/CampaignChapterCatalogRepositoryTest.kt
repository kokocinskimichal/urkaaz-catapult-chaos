package com.urkaaaz.campaign

import java.io.ByteArrayInputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CampaignChapterCatalogRepositoryTest {
    @Test
    fun rejectsUnknownBiomeBeforeRegistryConstruction() {
        val valid = (CampaignChapterCatalogRepository.bundled.load() as CampaignChapterCatalogRead.Available)
            .document
        val invalid = valid.copy(
            chapters = valid.chapters.mapIndexed { index, chapter ->
                if (index == 0) chapter.copy(biome = "unknown_biome") else chapter
            },
        )
        val content = Json.encodeToString(invalid)
        val repository = CampaignChapterCatalogRepository {
            ByteArrayInputStream(content.toByteArray())
        }

        assertEquals(
            CampaignChapterCatalogRead.Failure(CampaignChapterCatalogFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun missingCatalogResourceIsExplicit() {
        val repository = CampaignChapterCatalogRepository { null }

        assertEquals(
            CampaignChapterCatalogRead.Failure(CampaignChapterCatalogFailure.MISSING_RESOURCE),
            repository.load(),
        )
    }
}
