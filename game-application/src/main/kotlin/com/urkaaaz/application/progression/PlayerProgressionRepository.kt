package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot

enum class PlayerProgressionRepositoryFailure {
    STORAGE_UNAVAILABLE,
    INVALID_PROFILE,
    UNSUPPORTED_VERSION,
}

sealed interface PlayerProgressionRepositoryResult {
    data class Available(val snapshot: LoadoutProgressionSnapshot) :
        PlayerProgressionRepositoryResult

    data class Failure(val reason: PlayerProgressionRepositoryFailure) :
        PlayerProgressionRepositoryResult
}

data class PlayerProgressionMutation<T>(
    val snapshot: LoadoutProgressionSnapshot,
    val value: T,
    val shouldPersist: Boolean,
)

sealed interface PlayerProgressionMutationResult<out T> {
    data class Completed<T>(
        val snapshot: LoadoutProgressionSnapshot,
        val value: T,
    ) : PlayerProgressionMutationResult<T>

    data class Failure(
        val reason: PlayerProgressionRepositoryFailure,
    ) : PlayerProgressionMutationResult<Nothing>
}

interface PlayerProgressionRepository {
    /**
     * Reads the profile or atomically initializes it once when no profile exists.
     * Existing partial, invalid, or unsupported data must be reported as a failure.
     */
    suspend fun loadOrInitialize(
        initialProfile: LoadoutProgressionSnapshot,
    ): PlayerProgressionRepositoryResult

    /**
     * Applies a transform to the latest profile in the persistence transaction.
     * The transform must be pure because a persistence implementation may retry it.
     */
    suspend fun <T> updateProfile(
        initialProfile: LoadoutProgressionSnapshot,
        transform: (LoadoutProgressionSnapshot) -> PlayerProgressionMutation<T>,
    ): PlayerProgressionMutationResult<T>
}
