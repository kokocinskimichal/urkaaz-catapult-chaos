package com.urkaaaz.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.shop.MatchStockSettlementFailure
import com.urkaaaz.application.shop.MatchStockSettlementId
import com.urkaaaz.application.shop.MatchStockSettlementPort
import com.urkaaaz.application.shop.MatchStockSettlementRejection
import com.urkaaaz.application.shop.MatchStockSettlementResult
import com.urkaaaz.application.shop.MatchStockUsage
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.PlayerProgressionMutation
import com.urkaaaz.application.progression.PlayerProgressionMutationResult
import com.urkaaaz.application.progression.PlayerProgressionRepository
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.PlayerProgressionRepositoryResult
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import java.io.IOException

class DataStorePlayerProgressionRepository(
    private val dataStore: DataStore<Preferences>,
) : PlayerProgressionRepository, MatchStockSettlementPort {
    override suspend fun loadOrInitialize(
        initialProfile: LoadoutProgressionSnapshot,
    ): PlayerProgressionRepositoryResult {
        val preferences = try {
            dataStore.updateData { current ->
                when (val decoded = PlayerProgressionDataStoreCodec.decode(current)) {
                    PlayerProgressionProfileDecode.Missing ->
                        PlayerProgressionDataStoreCodec.encode(current, initialProfile)
                    is PlayerProgressionProfileDecode.Available -> current
                    is PlayerProgressionProfileDecode.Failure ->
                        throw ProfileDataException(decoded.reason)
                }
            }
        } catch (error: ProfileDataException) {
            return PlayerProgressionRepositoryResult.Failure(error.reason)
        } catch (error: IOException) {
            return PlayerProgressionRepositoryResult.Failure(
                if (error is CorruptionException) {
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE
                } else {
                    PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE
                },
            )
        }

        return when (val decoded = PlayerProgressionDataStoreCodec.decode(preferences)) {
            is PlayerProgressionProfileDecode.Available ->
                PlayerProgressionRepositoryResult.Available(decoded.snapshot)
            PlayerProgressionProfileDecode.Missing ->
                PlayerProgressionRepositoryResult.Failure(
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                )
            is PlayerProgressionProfileDecode.Failure ->
                PlayerProgressionRepositoryResult.Failure(decoded.reason)
        }
    }

    override suspend fun <T> updateProfile(
        initialProfile: LoadoutProgressionSnapshot,
        transform: (LoadoutProgressionSnapshot) -> PlayerProgressionMutation<T>,
    ): PlayerProgressionMutationResult<T> {
        var mutation: PlayerProgressionMutation<T>? = null
        try {
            dataStore.updateData { current ->
                val (profile, shouldInitialize) = when (
                    val decoded = PlayerProgressionDataStoreCodec.decode(current)
                ) {
                    PlayerProgressionProfileDecode.Missing -> initialProfile to true
                    is PlayerProgressionProfileDecode.Available -> decoded.snapshot to false
                    is PlayerProgressionProfileDecode.Failure ->
                        throw ProfileDataException(decoded.reason)
                }
                val decision = transform(profile)
                mutation = decision
                if (shouldInitialize || decision.shouldPersist) {
                    PlayerProgressionDataStoreCodec.encode(current, decision.snapshot)
                } else {
                    current
                }
            }
        } catch (error: ProfileDataException) {
            return PlayerProgressionMutationResult.Failure(error.reason)
        } catch (error: IOException) {
            return PlayerProgressionMutationResult.Failure(
                if (error is CorruptionException) {
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE
                } else {
                    PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE
                },
            )
        }

        val completed = mutation ?: return PlayerProgressionMutationResult.Failure(
            PlayerProgressionRepositoryFailure.INVALID_PROFILE,
        )
        return PlayerProgressionMutationResult.Completed(completed.snapshot, completed.value)
    }

    override suspend fun settle(
        id: MatchStockSettlementId,
        usage: MatchStockUsage,
    ): MatchStockSettlementResult {
        var outcome: MatchStockSettlementResult? = null
        try {
            dataStore.updateData { current ->
                val profile = when (val decoded = PlayerProgressionDataStoreCodec.decode(current)) {
                    PlayerProgressionProfileDecode.Missing ->
                        throw StockSettlementDataException(
                            MatchStockSettlementFailure.INVALID_PROFILE,
                        )
                    is PlayerProgressionProfileDecode.Available -> decoded.snapshot
                    is PlayerProgressionProfileDecode.Failure ->
                        throw StockSettlementDataException(
                            decoded.reason.toSettlementFailure(),
                        )
                }
                if (!PlayerProgressionUseCases.isValidProfile(profile)) {
                    throw StockSettlementDataException(
                        MatchStockSettlementFailure.INVALID_PROFILE,
                    )
                }
                val receiptVersion = current[STOCK_SETTLEMENT_VERSION]
                val lastSettlementId = current[STOCK_SETTLEMENT_LAST_ID]
                if ((receiptVersion == null) != (lastSettlementId == null)) {
                    throw StockSettlementDataException(
                        MatchStockSettlementFailure.INVALID_PROFILE,
                    )
                }
                if (receiptVersion != null && receiptVersion != STOCK_SETTLEMENT_SCHEMA_VERSION) {
                    throw StockSettlementDataException(
                        MatchStockSettlementFailure.UNSUPPORTED_PROFILE_VERSION,
                    )
                }
                if (lastSettlementId == id.storageValue()) {
                    outcome = MatchStockSettlementResult.AlreadySettled(profile)
                    current
                } else if (usage.hasUnsupportedCount()) {
                    outcome = MatchStockSettlementResult.Rejected(
                        MatchStockSettlementRejection.INVALID_USAGE,
                    )
                    current
                } else {
                    val nextProfile = profile.debit(usage)
                    if (nextProfile == null) {
                        outcome = MatchStockSettlementResult.Rejected(
                            MatchStockSettlementRejection.STOCK_CONFLICT,
                        )
                        current
                    } else {
                        outcome = MatchStockSettlementResult.Settled(nextProfile)
                        PlayerProgressionDataStoreCodec
                            .encode(current, nextProfile)
                            .toMutablePreferences()
                            .apply {
                                this[STOCK_SETTLEMENT_VERSION] =
                                    STOCK_SETTLEMENT_SCHEMA_VERSION
                                this[STOCK_SETTLEMENT_LAST_ID] = id.storageValue()
                            }
                    }
                }
            }
        } catch (error: StockSettlementDataException) {
            return MatchStockSettlementResult.Failure(error.reason)
        } catch (error: IOException) {
            return MatchStockSettlementResult.Failure(
                if (error is CorruptionException) {
                    MatchStockSettlementFailure.INVALID_PROFILE
                } else {
                    MatchStockSettlementFailure.STORAGE_UNAVAILABLE
                },
            )
        }
        return outcome ?: MatchStockSettlementResult.Failure(
            MatchStockSettlementFailure.INVALID_PROFILE,
        )
    }

    private fun LoadoutProgressionSnapshot.debit(
        usage: MatchStockUsage,
    ): LoadoutProgressionSnapshot? {
        val ammunition = debit(ammunitionStocks, usage.ammunitionUsed) ?: return null
        val specialSpells = debit(specialSpellStocks, usage.specialSpellsUsed) ?: return null
        return copy(
            ammunitionStocks = ammunition,
            specialSpellStocks = specialSpells,
        )
    }

    private fun MatchStockUsage.hasUnsupportedCount(): Boolean =
        ammunitionUsed.values.any { it <= 0 || it > Int.MAX_VALUE.toLong() } ||
            specialSpellsUsed.values.any { it <= 0 || it > Int.MAX_VALUE.toLong() }

    private fun debit(
        stocks: Map<String, Int>,
        usage: Map<String, Long>,
    ): Map<String, Int>? {
        var next = stocks
        usage.forEach { (identifier, amount) ->
            val current = stocks[identifier] ?: 0
            if (current.toLong() < amount) return null
            val remaining = current - amount.toInt()
            next = if (remaining == 0) next - identifier else next + (identifier to remaining)
        }
        return next
    }

    private fun com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
        .toSettlementFailure(): MatchStockSettlementFailure =
        when (this) {
            PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE ->
                MatchStockSettlementFailure.STORAGE_UNAVAILABLE
            PlayerProgressionRepositoryFailure.INVALID_PROFILE ->
                MatchStockSettlementFailure.INVALID_PROFILE
            PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION ->
                MatchStockSettlementFailure.UNSUPPORTED_PROFILE_VERSION
        }

    private class StockSettlementDataException(
        val reason: MatchStockSettlementFailure,
    ) : Exception()

    private class ProfileDataException(
        val reason: PlayerProgressionRepositoryFailure,
    ) : Exception()

    private companion object {
        const val STOCK_SETTLEMENT_SCHEMA_VERSION = 1
        val STOCK_SETTLEMENT_VERSION =
            intPreferencesKey("match_stock_settlement.schema_version")
        val STOCK_SETTLEMENT_LAST_ID =
            stringPreferencesKey("match_stock_settlement.last_id")
    }
}
