package com.urkaaaz.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.progression.DeveloperProgressPreset
import com.urkaaaz.application.progression.DeveloperProgressPreset.DeveloperProgressField
import com.urkaaaz.application.progression.DeveloperProgressPresetApply
import com.urkaaaz.application.progression.DeveloperProgressPresetFailure
import com.urkaaaz.application.progression.DeveloperProgressPresetPort
import com.urkaaaz.application.progression.DeveloperProgressSummary
import com.urkaaaz.application.progression.LifeStateFactory
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.application.campaign.BundledCampaignBalance
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignMilestoneRegistry
import java.io.IOException
import kotlinx.coroutines.flow.first

class DataStoreDeveloperProgressPresetAdapter(
    private val dataStore: DataStore<Preferences>,
    private val milestones: CampaignMilestoneRegistry = BundledCampaignBalance.milestoneRegistry(),
    private val nowEpochMilliseconds: () -> Long = System::currentTimeMillis,
) : DeveloperProgressPresetPort {
    override suspend fun apply(
        preset: DeveloperProgressPreset,
        contentHash: String,
    ): DeveloperProgressPresetApply {
        if (!preset.applyOnNextLaunch) return DeveloperProgressPresetApply.NotRequested
        val presetTime = nowEpochMilliseconds()
        var result: DeveloperProgressPresetApply = DeveloperProgressPresetApply.AlreadyApplied
        return try {
            dataStore.updateData { current ->
                if (current[APPLIED_PROGRESS_PRESET_HASH] == contentHash) {
                    result = DeveloperProgressPresetApply.AlreadyApplied
                    current
                } else {
                    var updated = current
                    if (
                        DeveloperProgressField.LIVES in preset.fieldsToApply ||
                        DeveloperProgressField.GOLD in preset.fieldsToApply
                    ) {
                        val profile = when (
                            val decoded = PlayerProgressionDataStoreCodec.decode(current)
                        ) {
                            PlayerProgressionProfileDecode.Missing ->
                                PlayerProgressionUseCases.INITIAL_PROFILE
                            is PlayerProgressionProfileDecode.Available -> decoded.snapshot
                            is PlayerProgressionProfileDecode.Failure ->
                                throw ProfileDataException(decoded.reason)
                        }
                        val nextProfile = profile.copy(
                            goldBalance = if (DeveloperProgressField.GOLD in preset.fieldsToApply) {
                                preset.gold
                            } else {
                                profile.goldBalance
                            },
                        )
                        updated = if (DeveloperProgressField.LIVES in preset.fieldsToApply) {
                            val lifeState = LifeStateFactory.create(
                                preset.lives,
                                presetTime,
                            ) ?: throw ProfileDataException(
                                com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                            )
                            PlayerProgressionDataStoreCodec.encodeWithLifeState(
                                current,
                                nextProfile,
                                lifeState,
                            )
                        } else {
                            PlayerProgressionDataStoreCodec.encode(current, nextProfile)
                        }
                    }
                    result = DeveloperProgressPresetApply.Applied
                    updated.toMutablePreferences().apply {
                        if (DeveloperProgressField.CURRENT_LEVEL in preset.fieldsToApply) {
                            this[CAMPAIGN_SCHEMA_VERSION] = DATA_SCHEMA_VERSION
                            this[CAMPAIGN_HIGHEST_LEVEL] = preset.currentLevel
                            this[CAMPAIGN_COMPLETED_LEVELS] =
                                (1 until preset.currentLevel).joinToString("|")
                            this[CAMPAIGN_APPLIED_MILESTONES] =
                                (1 until preset.currentLevel)
                                    .flatMap(milestones::milestonesForCompletedLevel)
                                    .map { it.id }
                                    .distinct()
                                    .sorted()
                                    .joinToString("|")
                        }
                        this[APPLIED_PROGRESS_PRESET_HASH] = contentHash
                    }
                }
            }
            result
        } catch (error: ProfileDataException) {
            DeveloperProgressPresetApply.Failure(
                if (error.reason == com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE) {
                    DeveloperProgressPresetFailure.STORAGE_UNAVAILABLE
                } else {
                    DeveloperProgressPresetFailure.INVALID_DATA
                },
            )
        } catch (_: IOException) {
            DeveloperProgressPresetApply.Failure(
                DeveloperProgressPresetFailure.STORAGE_UNAVAILABLE,
            )
        }
    }

    override suspend fun summary(): DeveloperProgressSummary {
        val preferences = dataStore.data.first()
        val profile = when (val decoded = PlayerProgressionDataStoreCodec.decode(preferences)) {
            PlayerProgressionProfileDecode.Missing -> null
            is PlayerProgressionProfileDecode.Available -> decoded
            is PlayerProgressionProfileDecode.Failure ->
                throw IllegalStateException("stored player profile is invalid: ${decoded.reason}")
        }
        val storedLevel = preferences[CAMPAIGN_HIGHEST_LEVEL] ?: 1
        val lives = profile?.lifeState?.lives ?: profile?.legacyLives ?: DEFAULT_LIVES
        val gold = profile?.snapshot?.goldBalance ?: 0
        require(storedLevel in 1..CampaignLevelDefinition.MAX_LEVELS) {
            "stored campaign level is invalid"
        }
        require(lives >= 0) { "stored lives count is invalid" }
        require(gold >= 0) { "stored gold balance is invalid" }
        return DeveloperProgressSummary(
            currentLevel = storedLevel
                .coerceIn(1..CampaignLevelDefinition.LAST_COMPLETE_CAMPAIGN_LEVEL),
            lives = lives,
            gold = gold,
        )
    }

    companion object {
        const val DEFAULT_LIVES = 5
        internal val APPLIED_PROGRESS_PRESET_HASH =
            stringPreferencesKey("developer_progress.applied_hash")
        private const val DATA_SCHEMA_VERSION = 1
        private val CAMPAIGN_SCHEMA_VERSION = intPreferencesKey("campaign.schema_version")
        private val CAMPAIGN_HIGHEST_LEVEL = intPreferencesKey("campaign.highest_unlocked_level")
        private val CAMPAIGN_COMPLETED_LEVELS = stringPreferencesKey("campaign.completed_levels")
        private val CAMPAIGN_APPLIED_MILESTONES =
            stringPreferencesKey("campaign.applied_milestones")
    }

    private class ProfileDataException(
        val reason: com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure,
    ) : Exception()
}
