package com.urkaaaz.application.progression

enum class PlayerProgressResetFailure {
    STORAGE_UNAVAILABLE,
}

sealed interface PlayerProgressResetResult {
    data object Reset : PlayerProgressResetResult
    data class Failure(val reason: PlayerProgressResetFailure) : PlayerProgressResetResult
}

fun interface PlayerProgressResetRepository {
    suspend fun reset(): PlayerProgressResetResult
}
