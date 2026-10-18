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

interface PlayerProgressionRepository {
    /**
     * Reads the profile or atomically initializes it once when no profile exists.
     * Existing partial, invalid, or unsupported data must be reported as a failure.
     */
    suspend fun loadOrInitialize(
        initialProfile: LoadoutProgressionSnapshot,
    ): PlayerProgressionRepositoryResult
}
