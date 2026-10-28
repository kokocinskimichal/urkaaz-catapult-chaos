package com.urkaaaz.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.campaign.CampaignProgress
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import com.urkaaaz.application.reward.EarnedReward
import com.urkaaaz.application.reward.LevelRewardResult
import com.urkaaaz.application.reward.RewardSettlementFailure
import com.urkaaaz.application.reward.RewardSettlementOutcome
import com.urkaaaz.application.reward.RewardSettlementPort
import com.urkaaaz.application.reward.RewardSettlementRepositoryResult
import com.urkaaaz.application.reward.RewardSettlementState
import com.urkaaaz.application.reward.RewardSettlementTransaction
import com.urkaaaz.application.reward.RewardTrackingIssue
import java.io.IOException

class DataStoreRewardSettlementAdapter(
    private val dataStore: DataStore<Preferences>,
) : RewardSettlementPort {
    override suspend fun update(
        transform: (RewardSettlementState) -> RewardSettlementTransaction,
    ): RewardSettlementRepositoryResult {
        var outcome: RewardSettlementOutcome? = null
        try {
            dataStore.updateData { current ->
                val state = decodeState(current)
                    ?: throw RewardSettlementDataException(RewardSettlementFailure.INVALID_DATA)
                val transaction = transform(state)
                outcome = transaction.outcome
                if (transaction.shouldPersist) encodeState(current, transaction.state) else current
            }
        } catch (error: RewardSettlementDataException) {
            return RewardSettlementRepositoryResult.Failure(error.reason)
        } catch (error: IOException) {
            return RewardSettlementRepositoryResult.Failure(
                if (error is CorruptionException) {
                    RewardSettlementFailure.INVALID_DATA
                } else {
                    RewardSettlementFailure.STORAGE_UNAVAILABLE
                },
            )
        }
        return outcome?.let(RewardSettlementRepositoryResult::Completed)
            ?: RewardSettlementRepositoryResult.Failure(RewardSettlementFailure.INVALID_DATA)
    }

    private fun decodeState(preferences: Preferences): RewardSettlementState? {
        val campaign = decodeCampaign(preferences) ?: return null
        val profile = decodeProfile(preferences) ?: return null
        val receipts = decodeReceipts(preferences) ?: return null
        return RewardSettlementState(campaign, profile, receipts)
    }

    private fun decodeCampaign(preferences: Preferences): CampaignProgress? {
        val hasKeys = preferences.asMap().keys.any { it.name.startsWith("campaign.") }
        if (!hasKeys) return CampaignProgress.INITIAL
        val version = preferences[CAMPAIGN_VERSION] ?: return null
        if (version != 1) {
            throw RewardSettlementDataException(RewardSettlementFailure.UNSUPPORTED_VERSION)
        }
        val highest = preferences[CAMPAIGN_HIGHEST] ?: return null
        val completed = decodeIntegerSet(preferences[CAMPAIGN_COMPLETED]) ?: return null
        val milestoneIds = decodeStringSet(preferences[CAMPAIGN_MILESTONES]) ?: return null
        return runCatching {
            CampaignProgress(
                highestUnlockedLevel = highest,
                completedLevels = completed,
                appliedMilestoneIds = milestoneIds,
            )
        }.getOrNull()
    }

    private fun decodeProfile(preferences: Preferences): LoadoutProgressionSnapshot? {
        val hasKeys = preferences.asMap().keys.any { it.name.startsWith("profile.") }
        if (!hasKeys) return PlayerProgressionUseCases.INITIAL_PROFILE
        val version = preferences[PROFILE_VERSION] ?: return null
        if (version != 1) {
            throw RewardSettlementDataException(RewardSettlementFailure.UNSUPPORTED_VERSION)
        }
        val unlocked = preferences[PROFILE_SPELLS] ?: return null
        val ammunition = preferences[PROFILE_AMMUNITION] ?: return null
        val special = preferences[PROFILE_SPECIAL] ?: return null
        val gold = preferences[PROFILE_GOLD] ?: return null
        val ammoStocks = decodeStocks(ammunition) ?: return null
        val specialStocks = decodeStocks(special) ?: return null
        val unlockedTypes = decodeStringSet(unlocked) ?: return null
        val profile = runCatching {
            LoadoutProgressionSnapshot(
                unlockedSpellTypes = unlockedTypes,
                ammunitionStocks = ammoStocks,
                specialSpellStocks = specialStocks,
                goldBalance = gold,
            )
        }.getOrNull()
        return profile?.takeIf { PlayerProgressionUseCases.isValidProfile(it) }
    }

    private fun decodeReceipts(preferences: Preferences): Map<String, LevelRewardResult>? {
        val encoded = preferences[REWARD_RECEIPTS]
        if (encoded == null) {
            return if (preferences[REWARD_VERSION] == null) emptyMap() else null
        }
        val version = preferences[REWARD_VERSION] ?: return null
        if (version != 1) {
            throw RewardSettlementDataException(RewardSettlementFailure.UNSUPPORTED_VERSION)
        }
        if (encoded.isEmpty()) return emptyMap()
        val decoded = linkedMapOf<String, LevelRewardResult>()
        for (row in encoded.split('\n')) {
            val parts = row.split(',', limit = 2)
            if (parts.size != 2) return null
            val key = decodeBase64(parts[0]) ?: return null
            val payload = decodeBase64(parts[1]) ?: return null
            val fields = payload.split('|', limit = 5)
            if (fields.size != 5) return null
            val level = fields[0].toIntOrNull() ?: return null
            val matchId = decodeBase64(fields[1]) ?: return null
            val total = fields[2].toLongOrNull() ?: return null
            val rewards = if (fields[3].isEmpty()) emptyList() else fields[3].split(';').map { item ->
                val rewardFields = item.split(':', limit = 2)
                if (rewardFields.size != 2) return null
                val gold = rewardFields[1].toLongOrNull() ?: return null
                runCatching { EarnedReward(rewardFields[0], gold) }.getOrNull() ?: return null
            }
            val issues = if (fields[4].isEmpty()) emptySet() else fields[4]
                .split(';')
                .map { value -> runCatching { RewardTrackingIssue.valueOf(value) }.getOrNull() ?: return null }
                .toSet()
            val result = runCatching {
                LevelRewardResult(level, matchId, rewards, total, issues)
            }.getOrNull() ?: return null
            if (key != "$level|$matchId" || decoded.put(key, result) != null) return null
        }
        return decoded
    }

    private fun encodeState(
        preferences: Preferences,
        state: RewardSettlementState,
    ): Preferences = preferences.toMutablePreferences().apply {
        this[CAMPAIGN_VERSION] = 1
        this[CAMPAIGN_HIGHEST] = state.campaign.highestUnlockedLevel
        this[CAMPAIGN_COMPLETED] = state.campaign.completedLevels.sorted().joinToString("|")
        this[CAMPAIGN_MILESTONES] = state.campaign.appliedMilestoneIds.sorted().joinToString("|")
        this[PROFILE_VERSION] = 1
        this[PROFILE_SPELLS] = state.profile.unlockedSpellTypes.sorted().joinToString("|")
        this[PROFILE_AMMUNITION] = encodeStocks(state.profile.ammunitionStocks)
        this[PROFILE_SPECIAL] = encodeStocks(state.profile.specialSpellStocks)
        this[PROFILE_GOLD] = state.profile.goldBalance
        this[REWARD_VERSION] = 1
        this[REWARD_RECEIPTS] = state.receipts.toSortedMap().entries.joinToString("\n") { (key, result) ->
            val payload = listOf(
                result.level,
                encodeBase64(result.matchId),
                result.totalGold,
                result.rewards.joinToString(";") { "${it.id}:${it.gold}" },
                result.issues.sortedBy { it.name }.joinToString(";") { it.name },
            ).joinToString("|")
            "${encodeBase64(key)},${encodeBase64(payload)}"
        }
    }

    private fun decodeIntegerSet(encoded: String?): Set<Int>? =
        decodeStringSet(encoded)?.map { it.toIntOrNull() ?: return null }?.toSet()

    private fun decodeStringSet(encoded: String?): Set<String>? {
        if (encoded == null) return null
        if (encoded.isEmpty()) return emptySet()
        val values = encoded.split('|')
        if (values.any(String::isBlank) || values.distinct().size != values.size) return null
        return values.toSet()
    }

    private fun decodeStocks(encoded: String): Map<String, Int>? {
        if (encoded.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, Int>()
        encoded.split('|').forEach { entry ->
            val separator = entry.lastIndexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return null
            val name = entry.substring(0, separator)
            val count = entry.substring(separator + 1).toIntOrNull() ?: return null
            if (name.isBlank() || count < 0 || result.put(name, count) != null) return null
        }
        return result
    }

    private fun encodeStocks(stocks: Map<String, Int>) =
        stocks.toSortedMap().entries.joinToString("|") { (id, count) -> "$id:$count" }

    private fun encodeBase64(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val encoded = StringBuilder((bytes.size * 4 + 2) / 3)
        var index = 0
        while (index + 2 < bytes.size) {
            val first = bytes[index].toInt() and 0xff
            val second = bytes[index + 1].toInt() and 0xff
            val third = bytes[index + 2].toInt() and 0xff
            encoded.append(URL_SAFE_BASE64_ALPHABET[first shr 2])
            encoded.append(URL_SAFE_BASE64_ALPHABET[((first and 0x03) shl 4) or (second shr 4)])
            encoded.append(URL_SAFE_BASE64_ALPHABET[((second and 0x0f) shl 2) or (third shr 6)])
            encoded.append(URL_SAFE_BASE64_ALPHABET[third and 0x3f])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val first = bytes[index].toInt() and 0xff
                encoded.append(URL_SAFE_BASE64_ALPHABET[first shr 2])
                encoded.append(URL_SAFE_BASE64_ALPHABET[(first and 0x03) shl 4])
            }
            2 -> {
                val first = bytes[index].toInt() and 0xff
                val second = bytes[index + 1].toInt() and 0xff
                encoded.append(URL_SAFE_BASE64_ALPHABET[first shr 2])
                encoded.append(URL_SAFE_BASE64_ALPHABET[((first and 0x03) shl 4) or (second shr 4)])
                encoded.append(URL_SAFE_BASE64_ALPHABET[(second and 0x0f) shl 2])
            }
        }
        return encoded.toString()
    }

    private fun decodeBase64(value: String): String? {
        val decoded = ByteArray(value.length * 3 / 4 + 2)
        var decodedSize = 0
        var accumulator = 0
        var bitCount = 0
        value.forEach { character ->
            val digit = URL_SAFE_BASE64_ALPHABET.indexOf(character)
            if (digit < 0) return null
            accumulator = (accumulator shl 6) or digit
            bitCount += 6
            if (bitCount >= 8) {
                bitCount -= 8
                decoded[decodedSize++] = (accumulator shr bitCount).toByte()
                accumulator = accumulator and ((1 shl bitCount) - 1)
            }
        }
        if (bitCount == 6 || accumulator != 0) return null
        return String(decoded, 0, decodedSize, Charsets.UTF_8)
    }

    private class RewardSettlementDataException(
        val reason: RewardSettlementFailure,
    ) : Exception()

    private companion object {
        const val URL_SAFE_BASE64_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val CAMPAIGN_VERSION = intPreferencesKey("campaign.schema_version")
        val CAMPAIGN_HIGHEST = intPreferencesKey("campaign.highest_unlocked_level")
        val CAMPAIGN_COMPLETED = stringPreferencesKey("campaign.completed_levels")
        val CAMPAIGN_MILESTONES = stringPreferencesKey("campaign.applied_milestones")
        val PROFILE_VERSION = intPreferencesKey("profile.schema_version")
        val PROFILE_SPELLS = stringPreferencesKey("profile.unlocked_spells")
        val PROFILE_AMMUNITION = stringPreferencesKey("profile.ammunition_stocks")
        val PROFILE_SPECIAL = stringPreferencesKey("profile.special_spell_stocks")
        val PROFILE_GOLD = intPreferencesKey("profile.gold_balance")
        val REWARD_VERSION = intPreferencesKey("reward.schema_version")
        val REWARD_RECEIPTS = stringPreferencesKey("reward.receipts")
    }
}
