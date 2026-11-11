package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot

data class LifeOperationId(
    val kind: LifeOperationKind,
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "life operation ID must not be blank" }
    }
}

enum class LifeOperationKind {
    MATCH,
    GRANT,
}

enum class LifeOperationStatus {
    CHARGED,
    UNLIMITED_PROTECTED,
    NO_LIFE_AVAILABLE,
    NOT_CHARGED_WIN,
    GRANTED,
    PARTIALLY_GRANTED,
    LIMIT_REACHED,
}

data class LifeOperationOutcome(
    val status: LifeOperationStatus,
    val livesChanged: Int,
    val overflow: Int,
    val livesAfter: Int,
) {
    init {
        require(livesChanged >= 0 || status == LifeOperationStatus.CHARGED) {
            "only a life charge may have a negative life change"
        }
        require(overflow >= 0) { "life grant overflow must not be negative" }
        require(livesAfter >= 0) { "lives after an operation must not be negative" }
    }
}

data class LifeOperationReceipt(
    val id: LifeOperationId,
    val payloadFingerprint: String,
    val outcome: LifeOperationOutcome,
) {
    init {
        require(payloadFingerprint.isNotBlank()) { "life receipt fingerprint must not be blank" }
    }
}

data class LifeState(
    val lives: Int,
    val nextLifeAtEpochMilliseconds: Long?,
    val pausedRegenerationMilliseconds: Long?,
    val unlimitedUntilEpochMilliseconds: Long?,
    val effectiveTimeHighWaterMarkEpochMilliseconds: Long,
    val receipts: Map<LifeOperationId, LifeOperationReceipt> = emptyMap(),
)

data class LifeStateMutation<T>(
    val state: LifeState,
    val value: T,
    val shouldPersist: Boolean,
)

sealed interface LifeStateRepositoryResult<out T> {
    data class Completed<T>(
        val state: LifeState,
        val value: T,
    ) : LifeStateRepositoryResult<T>

    data class Failure(
        val reason: PlayerProgressionRepositoryFailure,
    ) : LifeStateRepositoryResult<Nothing>
}

interface LifeStateRepository {
    /**
     * Reads and transforms the life state inside the same transaction used by the profile.
     * The transform and migration callback must be pure because DataStore may rerun either.
     */
    suspend fun <T> updateLifeState(
        initialProfile: LoadoutProgressionSnapshot,
        initialState: LifeState,
        migrateLegacyLives: (Int?) -> LifeState?,
        transform: (LifeState) -> LifeStateMutation<T>,
    ): LifeStateRepositoryResult<T>
}
