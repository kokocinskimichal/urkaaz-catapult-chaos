package com.urkaaaz.application.progression

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeveloperProgressPresetTest {
    @Test
    fun bundledPresetQueuesOnlyTheSelectedLevel() {
        val loaded = assertIs<DeveloperProgressPresetRead.Available>(
            DeveloperProgressPresetRepository.bundled.load(),
        ).loaded

        assertEquals(2, loaded.preset.schemaVersion)
        assertEquals(5, loaded.preset.currentLevel)
        assertEquals(5, loaded.preset.lives)
        assertEquals(0, loaded.preset.gold)
        assertEquals(setOf(DeveloperProgressPreset.DeveloperProgressField.CURRENT_LEVEL), loaded.preset.fieldsToApply)
        assertEquals(true, loaded.preset.applyOnNextLaunch)
        assertEquals(64, loaded.contentHash.length)
    }

    @Test
    fun presetLoaderRejectsUnsupportedCurrentLevelAndUnknownFields() {
        val invalidContent = """
            {
              "schemaVersion": 2,
              "revision": "bad",
              "currentLevel": 55,
              "lives": 5,
              "gold": 0,
              "fieldsToApply": ["currentLevel"],
              "applyOnNextLaunch": true,
              "unexpected": true
            }
        """.trimIndent()
        val repository = DeveloperProgressPresetRepository {
            ByteArrayInputStream(invalidContent.toByteArray())
        }

        assertEquals(
            DeveloperProgressPresetRead.Failure(DeveloperProgressPresetFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun presetLoaderRejectsUnknownApplicationTargets() {
        val content = """
            {
              "schemaVersion": 2,
              "revision": "bad-field",
              "currentLevel": 5,
              "lives": 5,
              "gold": 0,
              "fieldsToApply": ["health"],
              "applyOnNextLaunch": true
            }
        """.trimIndent()
        val repository = DeveloperProgressPresetRepository {
            ByteArrayInputStream(content.toByteArray())
        }

        assertEquals(
            DeveloperProgressPresetRead.Failure(DeveloperProgressPresetFailure.INVALID_DATA),
            repository.load(),
        )
    }

    @Test
    fun initializerDoesNotTouchDataStoreUnlessPresetIsQueued() = runBlocking {
        var applyCount = 0
        val useCase = DeveloperProgressPresetUseCase(
            repository = DeveloperProgressPresetRepository {
                ByteArrayInputStream(
                    """
                        {
                          "schemaVersion": 2,
                          "revision": "not-queued",
                          "currentLevel": 1,
                          "lives": 5,
                          "gold": 0,
                          "fieldsToApply": [],
                          "applyOnNextLaunch": false
                        }
                    """.trimIndent().toByteArray(),
                )
            },
            port = object : DeveloperProgressPresetPort {
                override suspend fun apply(
                    preset: DeveloperProgressPreset,
                    contentHash: String,
                ): DeveloperProgressPresetApply {
                    applyCount += 1
                    return DeveloperProgressPresetApply.Applied
                }

                override suspend fun summary() = DeveloperProgressSummary(1, 5, 0)
            },
        )

        assertEquals(DeveloperProgressPresetApply.NotRequested, useCase.applyBundled())
        assertEquals(0, applyCount)
    }
}
