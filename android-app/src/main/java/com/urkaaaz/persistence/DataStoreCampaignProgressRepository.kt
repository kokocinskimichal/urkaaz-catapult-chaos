package com.urkaaaz.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.campaign.CampaignProgressFailure
import com.urkaaaz.application.campaign.CampaignProgressRepository
import com.urkaaaz.application.campaign.CampaignProgressRepositoryResult
import java.io.IOException

class DataStoreCampaignProgressRepository(
    private val dataStore: DataStore<Preferences>,
) : CampaignProgressRepository {
    override suspend fun loadOrInitialize(): CampaignProgressRepositoryResult {
        val preferences = try {
            dataStore.updateData { current ->
                when (val decoded = decode(current)) {
                    DecodeResult.Missing -> encode(current, CampaignProgress.INITIAL)
                    is DecodeResult.Available -> current
                    is DecodeResult.Failure -> throw CampaignProgressDataException(decoded.reason)
                }
            }
        } catch (error: CampaignProgressDataException) {
            return CampaignProgressRepositoryResult.Failure(error.reason)
        } catch (error: IOException) {
            return CampaignProgressRepositoryResult.Failure(
                if (error is CorruptionException) {
                    CampaignProgressFailure.INVALID_DATA
                } else {
                    CampaignProgressFailure.STORAGE_UNAVAILABLE
                },
            )
        }
        return decode(preferences).toRepositoryResult()
    }

    override suspend fun update(
        transform: (CampaignProgress) -> CampaignProgress,
    ): CampaignProgressRepositoryResult {
        val preferences = try {
            dataStore.updateData { current ->
                val progress = when (val decoded = decode(current)) {
                    DecodeResult.Missing -> CampaignProgress.INITIAL
                    is DecodeResult.Available -> decoded.progress
                    is DecodeResult.Failure -> throw CampaignProgressDataException(decoded.reason)
                }
                encode(current, transform(progress))
            }
        } catch (error: CampaignProgressDataException) {
            return CampaignProgressRepositoryResult.Failure(error.reason)
        } catch (error: IOException) {
            return CampaignProgressRepositoryResult.Failure(
                if (error is CorruptionException) {
                    CampaignProgressFailure.INVALID_DATA
                } else {
                    CampaignProgressFailure.STORAGE_UNAVAILABLE
                },
            )
        }
        return decode(preferences).toRepositoryResult()
    }

    private fun encode(
        preferences: Preferences,
        progress: CampaignProgress,
    ): Preferences = preferences.toMutablePreferences().apply {
        this[SCHEMA_VERSION_KEY] = SCHEMA_VERSION
        this[HIGHEST_UNLOCKED_LEVEL_KEY] = progress.highestUnlockedLevel
        this[COMPLETED_LEVELS_KEY] = progress.completedLevels.sorted().joinToString("|")
        this[APPLIED_MILESTONES_KEY] = progress.appliedMilestoneIds.sorted().joinToString("|")
    }

    private fun decode(preferences: Preferences): DecodeResult {
        if (preferences.asMap().keys.none { it.name.startsWith(CAMPAIGN_KEY_PREFIX) }) {
            return DecodeResult.Missing
        }
        val version = preferences[SCHEMA_VERSION_KEY]
            ?: return DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)
        if (version != SCHEMA_VERSION) {
            return DecodeResult.Failure(CampaignProgressFailure.UNSUPPORTED_VERSION)
        }
        val highestUnlockedLevel = preferences[HIGHEST_UNLOCKED_LEVEL_KEY]
            ?: return DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)
        val completedLevelIdentifiers = decodeIdentifiers(preferences[COMPLETED_LEVELS_KEY])
            ?: return DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)
        val completedLevels = mutableSetOf<Int>()
        for (identifier in completedLevelIdentifiers) {
            val level = identifier.toIntOrNull()
                ?: return DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)
            if (!completedLevels.add(level)) {
                return DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)
            }
        }
        val appliedMilestoneIds = decodeIdentifiers(preferences[APPLIED_MILESTONES_KEY])
            ?: return DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)

        return try {
            DecodeResult.Available(
                CampaignProgress(
                    highestUnlockedLevel = highestUnlockedLevel,
                    completedLevels = completedLevels,
                    appliedMilestoneIds = appliedMilestoneIds.toSet(),
                ),
            )
        } catch (error: IllegalArgumentException) {
            DecodeResult.Failure(CampaignProgressFailure.INVALID_DATA)
        }
    }

    private fun decodeIdentifiers(encoded: String?): List<String>? {
        if (encoded == null) return null
        if (encoded.isEmpty()) return emptyList()
        val identifiers = encoded.split('|')
        if (identifiers.any(String::isBlank) || identifiers.distinct().size != identifiers.size) {
            return null
        }
        return identifiers
    }

    private fun DecodeResult.toRepositoryResult(): CampaignProgressRepositoryResult =
        when (this) {
            DecodeResult.Missing ->
                CampaignProgressRepositoryResult.Failure(CampaignProgressFailure.INVALID_DATA)
            is DecodeResult.Available ->
                CampaignProgressRepositoryResult.Available(progress)
            is DecodeResult.Failure ->
                CampaignProgressRepositoryResult.Failure(reason)
        }

    private sealed interface DecodeResult {
        data object Missing : DecodeResult
        data class Available(val progress: CampaignProgress) : DecodeResult
        data class Failure(val reason: CampaignProgressFailure) : DecodeResult
    }

    private class CampaignProgressDataException(
        val reason: CampaignProgressFailure,
    ) : Exception()

    private companion object {
        const val SCHEMA_VERSION = 1
        const val CAMPAIGN_KEY_PREFIX = "campaign."
        val SCHEMA_VERSION_KEY = intPreferencesKey("campaign.schema_version")
        val HIGHEST_UNLOCKED_LEVEL_KEY = intPreferencesKey("campaign.highest_unlocked_level")
        val COMPLETED_LEVELS_KEY = stringPreferencesKey("campaign.completed_levels")
        val APPLIED_MILESTONES_KEY = stringPreferencesKey("campaign.applied_milestones")
    }
}
