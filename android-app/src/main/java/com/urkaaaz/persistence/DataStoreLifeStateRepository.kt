package com.urkaaaz.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.LifeState
import com.urkaaaz.application.progression.LifeStateMutation
import com.urkaaaz.application.progression.LifeStateRepository
import com.urkaaaz.application.progression.LifeStateRepositoryResult
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import java.io.IOException

class DataStoreLifeStateRepository(
    private val dataStore: DataStore<Preferences>,
) : LifeStateRepository {
    override suspend fun <T> updateLifeState(
        initialProfile: LoadoutProgressionSnapshot,
        initialState: LifeState,
        migrateLegacyLives: (Int?) -> LifeState?,
        transform: (LifeState) -> LifeStateMutation<T>,
    ): LifeStateRepositoryResult<T> {
        var mutation: LifeStateMutation<T>? = null
        try {
            dataStore.updateData { current ->
                val (profile, lifeState, requiresMigration) =
                    when (val decoded = PlayerProgressionDataStoreCodec.decode(current)) {
                        PlayerProgressionProfileDecode.Missing ->
                            Triple(initialProfile, initialState, true)
                        is PlayerProgressionProfileDecode.Available ->
                            if (!PlayerProgressionUseCases.isValidProfile(decoded.snapshot)) {
                                throw LifeProfileDataException(
                                    PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                                )
                            } else if (decoded.lifeState != null) {
                                Triple(decoded.snapshot, decoded.lifeState, false)
                            } else {
                                val migrated = migrateLegacyLives(decoded.legacyLives)
                                    ?: throw LifeProfileDataException(
                                        PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                                    )
                                Triple(decoded.snapshot, migrated, true)
                            }
                        is PlayerProgressionProfileDecode.Failure ->
                            throw LifeProfileDataException(decoded.reason)
                    }
                val decision = transform(lifeState)
                mutation = decision
                if (requiresMigration || decision.shouldPersist) {
                    PlayerProgressionDataStoreCodec.encodeWithLifeState(
                        current,
                        profile,
                        decision.state,
                    )
                } else {
                    current
                }
            }
        } catch (error: LifeProfileDataException) {
            return LifeStateRepositoryResult.Failure(error.reason)
        } catch (error: IOException) {
            return LifeStateRepositoryResult.Failure(
                if (error is CorruptionException) {
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE
                } else {
                    PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE
                },
            )
        }
        val completed = mutation ?: return LifeStateRepositoryResult.Failure(
            PlayerProgressionRepositoryFailure.INVALID_PROFILE,
        )
        return LifeStateRepositoryResult.Completed(completed.state, completed.value)
    }

    private class LifeProfileDataException(
        val reason: PlayerProgressionRepositoryFailure,
    ) : Exception()
}
