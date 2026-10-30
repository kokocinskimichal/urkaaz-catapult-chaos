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
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.campaign.CampaignLevelDefinition
import com.urkaaaz.campaign.CampaignMilestoneRegistry
import java.io.IOException
import kotlinx.coroutines.flow.first

class DataStoreDeveloperProgressPresetAdapter(
    private val dataStore: DataStore<Preferences>,
    private val milestones: CampaignMilestoneRegistry = CampaignMilestoneRegistry.default(),
) : DeveloperProgressPresetPort {
    override suspend fun apply(
        preset: DeveloperProgressPreset,
        contentHash: String,
    ): DeveloperProgressPresetApply {
        if (!preset.applyOnNextLaunch) return DeveloperProgressPresetApply.NotRequested
        var result: DeveloperProgressPresetApply = DeveloperProgressPresetApply.AlreadyApplied
        return try {
            dataStore.updateData { current ->
                if (current[APPLIED_PROGRESS_PRESET_HASH] == contentHash) {
                    result = DeveloperProgressPresetApply.AlreadyApplied
                    current
                } else {
                    result = DeveloperProgressPresetApply.Applied
                    current.toMutablePreferences().apply {
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
                        if (
                            DeveloperProgressField.LIVES in preset.fieldsToApply ||
                            DeveloperProgressField.GOLD in preset.fieldsToApply
                        ) {
                            val initialProfile = PlayerProgressionUseCases.INITIAL_PROFILE
                            this[PROFILE_SCHEMA_VERSION] = DATA_SCHEMA_VERSION
                            if (!contains(PROFILE_UNLOCKED_SPELLS)) {
                                this[PROFILE_UNLOCKED_SPELLS] =
                                    initialProfile.unlockedSpellTypes.sorted().joinToString("|")
                            }
                            if (!contains(PROFILE_AMMUNITION_STOCKS)) {
                                this[PROFILE_AMMUNITION_STOCKS] =
                                    encodeStocks(initialProfile.ammunitionStocks)
                            }
                            if (!contains(PROFILE_SPECIAL_SPELL_STOCKS)) {
                                this[PROFILE_SPECIAL_SPELL_STOCKS] =
                                    encodeStocks(initialProfile.specialSpellStocks)
                            }
                            if (DeveloperProgressField.GOLD in preset.fieldsToApply) {
                                this[PROFILE_GOLD] = preset.gold
                            }
                            if (DeveloperProgressField.LIVES in preset.fieldsToApply) {
                                this[PROFILE_LIVES] = preset.lives
                            }
                        }
                        this[APPLIED_PROGRESS_PRESET_HASH] = contentHash
                    }
                }
            }
            result
        } catch (_: IOException) {
            DeveloperProgressPresetApply.Failure(
                DeveloperProgressPresetFailure.STORAGE_UNAVAILABLE,
            )
        }
    }

    override suspend fun summary(): DeveloperProgressSummary {
        val preferences = dataStore.data.first()
        val storedLevel = preferences[CAMPAIGN_HIGHEST_LEVEL] ?: 1
        val lives = preferences[PROFILE_LIVES] ?: DEFAULT_LIVES
        val gold = preferences[PROFILE_GOLD] ?: 0
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

    private fun encodeStocks(stocks: Map<String, Int>): String =
        stocks.toSortedMap().entries.joinToString("|") { (identifier, count) ->
            "$identifier:$count"
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
        private val PROFILE_SCHEMA_VERSION = intPreferencesKey("profile.schema_version")
        private val PROFILE_UNLOCKED_SPELLS = stringPreferencesKey("profile.unlocked_spells")
        private val PROFILE_AMMUNITION_STOCKS =
            stringPreferencesKey("profile.ammunition_stocks")
        private val PROFILE_SPECIAL_SPELL_STOCKS =
            stringPreferencesKey("profile.special_spell_stocks")
        private val PROFILE_GOLD = intPreferencesKey("profile.gold_balance")
        private val PROFILE_LIVES = intPreferencesKey("profile.lives")
    }
}
