package com.urkaaaz.persistence

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure

internal sealed interface PlayerProgressionProfileDecode {
    data object Missing : PlayerProgressionProfileDecode

    data class Available(
        val snapshot: LoadoutProgressionSnapshot,
    ) : PlayerProgressionProfileDecode

    data class Failure(
        val reason: PlayerProgressionRepositoryFailure,
    ) : PlayerProgressionProfileDecode
}

internal object PlayerProgressionDataStoreCodec {
    private const val PROFILE_VERSION = 1
    private const val PROFILE_KEY_PREFIX = "profile."
    private val profileVersionKey = intPreferencesKey("profile.schema_version")
    private val unlockedSpellsKey = stringPreferencesKey("profile.unlocked_spells")
    private val ammunitionStocksKey = stringPreferencesKey("profile.ammunition_stocks")
    private val specialSpellStocksKey = stringPreferencesKey("profile.special_spell_stocks")
    private val goldBalanceKey = intPreferencesKey("profile.gold_balance")

    fun decode(preferences: Preferences): PlayerProgressionProfileDecode {
        val hasProfileData = preferences.asMap().keys.any { key ->
            key.name.startsWith(PROFILE_KEY_PREFIX)
        }
        if (!hasProfileData) return PlayerProgressionProfileDecode.Missing

        val version = preferences[profileVersionKey]
            ?: return invalidProfile()
        if (version != PROFILE_VERSION) {
            return PlayerProgressionProfileDecode.Failure(
                PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION,
            )
        }

        val unlockedSpells = preferences[unlockedSpellsKey] ?: return invalidProfile()
        val ammunitionStocks = preferences[ammunitionStocksKey] ?: return invalidProfile()
        val specialSpellStocks = preferences[specialSpellStocksKey] ?: return invalidProfile()
        val goldBalance = preferences[goldBalanceKey] ?: return invalidProfile()
        val decodedUnlockedSpells = decodeIdentifiers(unlockedSpells) ?: return invalidProfile()
        val decodedAmmunitionStocks = decodeStocks(ammunitionStocks) ?: return invalidProfile()
        val decodedSpecialSpellStocks = decodeStocks(specialSpellStocks) ?: return invalidProfile()
        if (goldBalance < 0) return invalidProfile()

        return try {
            PlayerProgressionProfileDecode.Available(
                LoadoutProgressionSnapshot(
                    unlockedSpellTypes = decodedUnlockedSpells,
                    ammunitionStocks = decodedAmmunitionStocks,
                    specialSpellStocks = decodedSpecialSpellStocks,
                    goldBalance = goldBalance,
                ),
            )
        } catch (_: IllegalArgumentException) {
            invalidProfile()
        }
    }

    fun encode(
        preferences: Preferences,
        snapshot: LoadoutProgressionSnapshot,
    ): Preferences = preferences.toMutablePreferences().apply {
        this[profileVersionKey] = PROFILE_VERSION
        this[unlockedSpellsKey] = snapshot.unlockedSpellTypes.sorted().joinToString("|")
        this[ammunitionStocksKey] = encodeStocks(snapshot.ammunitionStocks)
        this[specialSpellStocksKey] = encodeStocks(snapshot.specialSpellStocks)
        this[goldBalanceKey] = snapshot.goldBalance
    }

    private fun decodeIdentifiers(encoded: String): Set<String>? {
        if (encoded.isEmpty()) return emptySet()
        val identifiers = encoded.split('|')
        if (identifiers.any(String::isBlank) || identifiers.distinct().size != identifiers.size) {
            return null
        }
        return identifiers.toSet()
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

    private fun encodeStocks(stocks: Map<String, Int>): String =
        stocks.toSortedMap().entries.joinToString("|") { (identifier, count) ->
            "$identifier:$count"
        }

    private fun invalidProfile() =
        PlayerProgressionProfileDecode.Failure(PlayerProgressionRepositoryFailure.INVALID_PROFILE)
}
