package com.urkaaaz.application.progression

import com.urkaaaz.campaign.CampaignLevelDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest

data class DeveloperProgressPreset(
    val schemaVersion: Int,
    val revision: String,
    val currentLevel: Int,
    val lives: Int,
    val gold: Int,
    val fieldsToApply: Set<DeveloperProgressField>,
    val applyOnNextLaunch: Boolean,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "unsupported developer progress schema version: $schemaVersion"
        }
        require(revision.isNotBlank()) { "developer progress revision must not be blank" }
        require(currentLevel in 1..CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL) {
            "current level must be within the playable campaign range"
        }
        require(lives >= 0) { "lives must not be negative" }
        require(gold >= 0) { "gold must not be negative" }
        require(!applyOnNextLaunch || fieldsToApply.isNotEmpty()) {
            "at least one progress field must be queued for application"
        }
    }

    enum class DeveloperProgressField(val jsonValue: String) {
        CURRENT_LEVEL("currentLevel"),
        LIVES("lives"),
        GOLD("gold");

        companion object {
            fun fromJson(value: String): DeveloperProgressField =
                entries.firstOrNull { it.jsonValue == value }
                    ?: throw IllegalArgumentException("unsupported progress field: $value")
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

data class LoadedDeveloperProgressPreset(
    val preset: DeveloperProgressPreset,
    val contentHash: String,
)

sealed interface DeveloperProgressPresetRead {
    data class Available(val loaded: LoadedDeveloperProgressPreset) : DeveloperProgressPresetRead
    data class Failure(val reason: DeveloperProgressPresetFailure) : DeveloperProgressPresetRead
}

enum class DeveloperProgressPresetFailure {
    MISSING_RESOURCE,
    INVALID_DATA,
    STORAGE_UNAVAILABLE,
}

data class DeveloperProgressSummary(
    val currentLevel: Int,
    val lives: Int,
    val gold: Int,
)

sealed interface DeveloperProgressPresetApply {
    data object NotRequested : DeveloperProgressPresetApply
    data object AlreadyApplied : DeveloperProgressPresetApply
    data object Applied : DeveloperProgressPresetApply
    data class Failure(val reason: DeveloperProgressPresetFailure) : DeveloperProgressPresetApply
}

interface DeveloperProgressPresetPort {
    suspend fun apply(
        preset: DeveloperProgressPreset,
        contentHash: String,
    ): DeveloperProgressPresetApply

    suspend fun summary(): DeveloperProgressSummary
}

class DeveloperProgressPresetRepository(
    private val openResource: (String) -> java.io.InputStream?,
) {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    fun load(): DeveloperProgressPresetRead = try {
        val input = openResource(RESOURCE_PATH)
            ?: return DeveloperProgressPresetRead.Failure(
                DeveloperProgressPresetFailure.MISSING_RESOURCE,
            )
        val content = input.bufferedReader().use { it.readText() }
        val preset = json.decodeFromString<DeveloperProgressPresetDto>(content).toDomain()
        DeveloperProgressPresetRead.Available(
            LoadedDeveloperProgressPreset(preset, sha256(content)),
        )
    } catch (_: SerializationException) {
        DeveloperProgressPresetRead.Failure(DeveloperProgressPresetFailure.INVALID_DATA)
    } catch (_: IllegalArgumentException) {
        DeveloperProgressPresetRead.Failure(DeveloperProgressPresetFailure.INVALID_DATA)
    } catch (_: IOException) {
        DeveloperProgressPresetRead.Failure(DeveloperProgressPresetFailure.STORAGE_UNAVAILABLE)
    }

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val RESOURCE_PATH = "progression/developer-progress.json"

        val bundled = DeveloperProgressPresetRepository { path ->
            DeveloperProgressPresetRepository::class.java.classLoader.getResourceAsStream(path)
        }
    }
}

class DeveloperProgressPresetUseCase(
    private val repository: DeveloperProgressPresetRepository,
    private val port: DeveloperProgressPresetPort,
) {
    suspend fun applyBundled(): DeveloperProgressPresetApply =
        when (val result = repository.load()) {
            is DeveloperProgressPresetRead.Failure ->
                DeveloperProgressPresetApply.Failure(result.reason)
            is DeveloperProgressPresetRead.Available -> {
                if (!result.loaded.preset.applyOnNextLaunch) {
                    DeveloperProgressPresetApply.NotRequested
                } else {
                    port.apply(result.loaded.preset, result.loaded.contentHash)
                }
            }
        }

    suspend fun current(): DeveloperProgressSummary = port.summary()
}

@Serializable
private data class DeveloperProgressPresetDto(
    val schemaVersion: Int,
    val revision: String,
    val currentLevel: Int,
    val lives: Int,
    val gold: Int,
    val fieldsToApply: List<String>,
    val applyOnNextLaunch: Boolean,
) {
    private fun decodedFieldsToApply():
        Set<DeveloperProgressPreset.DeveloperProgressField> =
        fieldsToApply.map(DeveloperProgressPreset.DeveloperProgressField::fromJson).toSet().also {
            require(it.size == fieldsToApply.size) { "progress fields must be unique" }
        }

    fun toDomain() = DeveloperProgressPreset(
        schemaVersion = schemaVersion,
        revision = revision,
        currentLevel = currentLevel,
        lives = lives,
        gold = gold,
        fieldsToApply = decodedFieldsToApply(),
        applyOnNextLaunch = applyOnNextLaunch,
    )
}
