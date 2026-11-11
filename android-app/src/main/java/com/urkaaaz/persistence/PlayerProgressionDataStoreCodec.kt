package com.urkaaaz.persistence

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.LifeOperationId
import com.urkaaaz.application.progression.LifeOperationKind
import com.urkaaaz.application.progression.LifeOperationOutcome
import com.urkaaaz.application.progression.LifeOperationReceipt
import com.urkaaaz.application.progression.LifeOperationStatus
import com.urkaaaz.application.progression.LifeState
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal sealed interface PlayerProgressionProfileDecode {
    data object Missing : PlayerProgressionProfileDecode

    data class Available(
        val snapshot: LoadoutProgressionSnapshot,
        val lifeState: LifeState? = null,
        val legacyLives: Int? = null,
    ) : PlayerProgressionProfileDecode

    data class Failure(
        val reason: PlayerProgressionRepositoryFailure,
    ) : PlayerProgressionProfileDecode
}

internal object PlayerProgressionDataStoreCodec {
    private const val LEGACY_PROFILE_VERSION = 1
    private const val PROFILE_VERSION = 2
    private const val PROFILE_KEY_PREFIX = "profile."
    private val profileVersionKey = intPreferencesKey("profile.schema_version")
    private val unlockedSpellsKey = stringPreferencesKey("profile.unlocked_spells")
    private val ammunitionStocksKey = stringPreferencesKey("profile.ammunition_stocks")
    private val specialSpellStocksKey = stringPreferencesKey("profile.special_spell_stocks")
    private val goldBalanceKey = intPreferencesKey("profile.gold_balance")
    private val legacyLivesKey = intPreferencesKey("profile.lives")
    private val lifeLivesKey = intPreferencesKey("profile.life_lives")
    private val lifeHighWaterKey = longPreferencesKey("profile.life_high_water_ms")
    private val lifeNextRegenerationKey = longPreferencesKey("profile.life_next_regeneration_ms")
    private val lifePausedRegenerationKey =
        longPreferencesKey("profile.life_paused_regeneration_ms")
    private val lifeUnlimitedUntilKey = longPreferencesKey("profile.life_unlimited_until_ms")
    private val lifeReceiptsKey = stringPreferencesKey("profile.life_receipts")

    fun decode(preferences: Preferences): PlayerProgressionProfileDecode {
        val hasProfileData = preferences.asMap().keys.any { key ->
            key.name.startsWith(PROFILE_KEY_PREFIX)
        }
        if (!hasProfileData) return PlayerProgressionProfileDecode.Missing

        val version = preferences[profileVersionKey]
            ?: return invalidProfile()
        if (version != LEGACY_PROFILE_VERSION && version != PROFILE_VERSION) {
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
            val snapshot = LoadoutProgressionSnapshot(
                unlockedSpellTypes = decodedUnlockedSpells,
                ammunitionStocks = decodedAmmunitionStocks,
                specialSpellStocks = decodedSpecialSpellStocks,
                goldBalance = goldBalance,
            )
            when (version) {
                LEGACY_PROFILE_VERSION -> {
                    if (hasLifeStateData(preferences)) return invalidProfile()
                    val legacyLives = preferences[legacyLivesKey]
                    if (legacyLives != null && legacyLives < 0) return invalidProfile()
                    PlayerProgressionProfileDecode.Available(
                        snapshot = snapshot,
                        legacyLives = legacyLives,
                    )
                }
                PROFILE_VERSION -> {
                    if (preferences[legacyLivesKey] != null) return invalidProfile()
                    val lifeState = decodeLifeState(preferences) ?: return invalidProfile()
                    PlayerProgressionProfileDecode.Available(snapshot, lifeState)
                }
                else -> invalidProfile()
            }
        } catch (_: IllegalArgumentException) {
            invalidProfile()
        }
    }

    fun encode(
        preferences: Preferences,
        snapshot: LoadoutProgressionSnapshot,
    ): Preferences = preferences.toMutablePreferences().apply {
        this[profileVersionKey] = if (preferences[profileVersionKey] == PROFILE_VERSION) {
            PROFILE_VERSION
        } else {
            LEGACY_PROFILE_VERSION
        }
        this[unlockedSpellsKey] = snapshot.unlockedSpellTypes.sorted().joinToString("|")
        this[ammunitionStocksKey] = encodeStocks(snapshot.ammunitionStocks)
        this[specialSpellStocksKey] = encodeStocks(snapshot.specialSpellStocks)
        this[goldBalanceKey] = snapshot.goldBalance
    }

    fun encodeWithLifeState(
        preferences: Preferences,
        snapshot: LoadoutProgressionSnapshot,
        lifeState: LifeState,
    ): Preferences = encode(preferences, snapshot).toMutablePreferences().apply {
        this[profileVersionKey] = PROFILE_VERSION
        remove(legacyLivesKey)
        this[lifeLivesKey] = lifeState.lives
        this[lifeHighWaterKey] = lifeState.effectiveTimeHighWaterMarkEpochMilliseconds
        lifeState.nextLifeAtEpochMilliseconds?.let { this[lifeNextRegenerationKey] = it }
            ?: remove(lifeNextRegenerationKey)
        lifeState.pausedRegenerationMilliseconds?.let {
            this[lifePausedRegenerationKey] = it
        } ?: remove(lifePausedRegenerationKey)
        lifeState.unlimitedUntilEpochMilliseconds?.let { this[lifeUnlimitedUntilKey] = it }
            ?: remove(lifeUnlimitedUntilKey)
        this[lifeReceiptsKey] = encodeReceipts(lifeState.receipts)
    }

    private fun decodeLifeState(preferences: Preferences): LifeState? {
        val lives = preferences[lifeLivesKey] ?: return null
        val highWater = preferences[lifeHighWaterKey] ?: return null
        val receipts = decodeReceipts(preferences[lifeReceiptsKey] ?: return null) ?: return null
        val nextLifeAt = preferences[lifeNextRegenerationKey]
        val pausedRegeneration = preferences[lifePausedRegenerationKey]
        val unlimitedUntil = preferences[lifeUnlimitedUntilKey]
        if (
            listOfNotNull(nextLifeAt, pausedRegeneration, unlimitedUntil)
                .any { it < 0L } ||
            highWater < 0L ||
            lives < 0
        ) {
            return null
        }
        return LifeState(
            lives = lives,
            nextLifeAtEpochMilliseconds = nextLifeAt,
            pausedRegenerationMilliseconds = pausedRegeneration,
            unlimitedUntilEpochMilliseconds = unlimitedUntil,
            effectiveTimeHighWaterMarkEpochMilliseconds = highWater,
            receipts = receipts,
        )
    }

    private fun hasLifeStateData(preferences: Preferences): Boolean =
        preferences.asMap().keys.any { key ->
            key.name.startsWith("profile.life_")
        }

    private fun decodeReceipts(encoded: String): Map<LifeOperationId, LifeOperationReceipt>? {
        if (encoded.isEmpty()) return emptyMap()
        val result = linkedMapOf<LifeOperationId, LifeOperationReceipt>()
        for (row in encoded.split('\n')) {
            val fields = row.split('|')
            if (fields.size != 7) return null
            val kind = decodeText(fields[0])?.let {
                runCatching { LifeOperationKind.valueOf(it) }.getOrNull()
            } ?: return null
            val idValue = decodeText(fields[1]) ?: return null
            val fingerprint = decodeText(fields[2]) ?: return null
            val status = runCatching { LifeOperationStatus.valueOf(fields[3]) }.getOrNull()
                ?: return null
            val livesChanged = fields[4].toIntOrNull() ?: return null
            val overflow = fields[5].toIntOrNull() ?: return null
            val livesAfter = fields[6].toIntOrNull() ?: return null
            val id = runCatching { LifeOperationId(kind, idValue) }.getOrNull() ?: return null
            val receipt = runCatching {
                LifeOperationReceipt(
                    id = id,
                    payloadFingerprint = fingerprint,
                    outcome = LifeOperationOutcome(status, livesChanged, overflow, livesAfter),
                )
            }.getOrNull() ?: return null
            if (result.put(id, receipt) != null) return null
        }
        return result
    }

    private fun encodeReceipts(
        receipts: Map<LifeOperationId, LifeOperationReceipt>,
    ): String = receipts.toSortedMap(compareBy({ it.kind.name }, { it.value }))
        .values.joinToString("\n") { receipt ->
            listOf(
                encodeText(receipt.id.kind.name),
                encodeText(receipt.id.value),
                encodeText(receipt.payloadFingerprint),
                receipt.outcome.status.name,
                receipt.outcome.livesChanged.toString(),
                receipt.outcome.overflow.toString(),
                receipt.outcome.livesAfter.toString(),
            ).joinToString("|")
        }

    private fun encodeText(value: String): String =
        buildString {
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX_DIGITS[unsigned ushr 4])
                append(HEX_DIGITS[unsigned and 0x0f])
            }
        }

    private fun decodeText(value: String): String? {
        if (value.length % 2 != 0) return null
        val bytes = ByteArray(value.length / 2)
        for (index in bytes.indices) {
            val high = HEX_DIGITS.indexOf(value[index * 2])
            val low = HEX_DIGITS.indexOf(value[index * 2 + 1])
            if (high < 0 || low < 0) return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
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

    private const val HEX_DIGITS = "0123456789abcdef"
}
