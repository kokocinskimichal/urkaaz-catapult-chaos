package com.urkaaaz.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionRepository
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.PlayerProgressionRepositoryResult
import java.io.IOException

class DataStorePlayerProgressionRepository(
    private val dataStore: DataStore<Preferences>,
) : PlayerProgressionRepository {
    override suspend fun loadOrInitialize(
        initialProfile: LoadoutProgressionSnapshot,
    ): PlayerProgressionRepositoryResult {
        val preferences = try {
            dataStore.updateData { current ->
                if (current.contains(PROFILE_VERSION_KEY) || current.hasProfileFields()) {
                    current
                } else {
                    current.toMutablePreferences().apply {
                        this[PROFILE_VERSION_KEY] = PROFILE_VERSION
                        this[UNLOCKED_SPELLS_KEY] =
                            initialProfile.unlockedSpellTypes.sorted().joinToString("|")
                        this[AMMUNITION_STOCKS_KEY] = encodeStocks(initialProfile.ammunitionStocks)
                        this[SPECIAL_SPELL_STOCKS_KEY] =
                            encodeStocks(initialProfile.specialSpellStocks)
                        this[GOLD_BALANCE_KEY] = initialProfile.goldBalance
                    }
                }
            }
        } catch (error: IOException) {
            return PlayerProgressionRepositoryResult.Failure(
                if (error is CorruptionException) {
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE
                } else {
                    PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE
                },
            )
        }

        return decodeProfile(preferences)
    }

    private fun Preferences.hasProfileFields(): Boolean =
        asMap().keys.any { key -> key.name.startsWith(PROFILE_KEY_PREFIX) }

    private fun decodeProfile(
        preferences: Preferences,
    ): PlayerProgressionRepositoryResult {
        val version = preferences[PROFILE_VERSION_KEY]
            ?: return PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.INVALID_PROFILE,
            )
        if (version != PROFILE_VERSION) {
            return PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION,
            )
        }

        val unlockedSpells = preferences[UNLOCKED_SPELLS_KEY]
        val ammunitionStocks = preferences[AMMUNITION_STOCKS_KEY]
        val specialSpellStocks = preferences[SPECIAL_SPELL_STOCKS_KEY]
        val goldBalance = preferences[GOLD_BALANCE_KEY]
        if (
            unlockedSpells == null ||
            ammunitionStocks == null ||
            specialSpellStocks == null ||
            goldBalance == null
        ) {
            return PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.INVALID_PROFILE,
            )
        }

        val decodedAmmunition = decodeStocks(ammunitionStocks)
        val decodedSpecialSpells = decodeStocks(specialSpellStocks)
        if (decodedAmmunition == null || decodedSpecialSpells == null || goldBalance < 0) {
            return PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.INVALID_PROFILE,
            )
        }

        return try {
            PlayerProgressionRepositoryResult.Available(
                LoadoutProgressionSnapshot(
                    unlockedSpellTypes = decodeIdentifiers(unlockedSpells),
                    ammunitionStocks = decodedAmmunition,
                    specialSpellStocks = decodedSpecialSpells,
                    goldBalance = goldBalance,
                ),
            )
        } catch (error: IllegalArgumentException) {
            PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.INVALID_PROFILE,
            )
        }
    }

    private fun encodeStocks(stocks: Map<String, Int>): String =
        stocks.toSortedMap().entries.joinToString("|") { (identifier, count) ->
            "$identifier:$count"
        }

    private fun decodeStocks(encoded: String): Map<String, Int>? {
        if (encoded.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, Int>()
        for (entry in encoded.split('|')) {
            val separator = entry.lastIndexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return null
            val identifier = entry.substring(0, separator)
            val count = entry.substring(separator + 1).toIntOrNull() ?: return null
            if (identifier.isBlank() || count < 0 || result.put(identifier, count) != null) {
                return null
            }
        }
        return result
    }

    private fun decodeIdentifiers(encoded: String): Set<String> =
        if (encoded.isEmpty()) emptySet() else encoded.split('|').toSet()

    private companion object {
        const val PROFILE_VERSION = 1
        const val PROFILE_KEY_PREFIX = "profile."
        val PROFILE_VERSION_KEY = intPreferencesKey("profile.schema_version")
        val UNLOCKED_SPELLS_KEY = stringPreferencesKey("profile.unlocked_spells")
        val AMMUNITION_STOCKS_KEY = stringPreferencesKey("profile.ammunition_stocks")
        val SPECIAL_SPELL_STOCKS_KEY = stringPreferencesKey("profile.special_spell_stocks")
        val GOLD_BALANCE_KEY = intPreferencesKey("profile.gold_balance")
    }
}
