package com.urkaaaz.application.progression

import com.urkaaaz.application.balance.GameBalanceRepository
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.domain.LifeBalance

fun interface WallClock {
    fun nowEpochMilliseconds(): Long
}

data class LifeSnapshot(
    val lives: Int,
    val maximumLives: Int,
    val canStartCampaign: Boolean,
    val regenerationTimeRemainingMilliseconds: Long?,
    val unlimitedTimeRemainingMilliseconds: Long?,
)

enum class LifeSystemFailure {
    STORAGE_UNAVAILABLE,
    INVALID_PROFILE,
    UNSUPPORTED_PROFILE_VERSION,
}

enum class LifeOperationRejection {
    INVALID_GRANT,
    IDEMPOTENCY_CONFLICT,
}

sealed interface LifeSystemResult<out T> {
    data class Success<T>(val value: T) : LifeSystemResult<T>
    data class Rejected(val reason: LifeOperationRejection) : LifeSystemResult<Nothing>
    data class Failure(val reason: LifeSystemFailure) : LifeSystemResult<Nothing>
}

data class LifeOperationResult(
    val outcome: LifeOperationOutcome,
    val snapshot: LifeSnapshot,
    val alreadyApplied: Boolean,
)

enum class LifeGrantSource {
    AD_REWARD,
    GAME_EVENT,
}

object LifeStateFactory {
    fun create(
        lives: Int,
        nowEpochMilliseconds: Long,
    ): LifeState? = create(
        lives,
        nowEpochMilliseconds,
        GameBalanceRepository.bundled.requireConfig().runtime.campaign.lives,
    )

    fun create(
        lives: Int,
        nowEpochMilliseconds: Long,
        balance: LifeBalance,
    ): LifeState? {
        if (lives !in 0..balance.maximumLives || nowEpochMilliseconds < 0L) return null
        val nextLifeAt = if (lives < balance.maximumLives) {
            try {
                Math.addExact(nowEpochMilliseconds, balance.regenerationIntervalMilliseconds)
            } catch (_: ArithmeticException) {
                return null
            }
        } else {
            null
        }
        return LifeState(
            lives = lives,
            nextLifeAtEpochMilliseconds = nextLifeAt,
            pausedRegenerationMilliseconds = null,
            unlimitedUntilEpochMilliseconds = null,
            effectiveTimeHighWaterMarkEpochMilliseconds = nowEpochMilliseconds,
        )
    }
}

data class FiniteLifeGrant(
    val sourceId: String,
    val source: LifeGrantSource,
    val amount: Int,
)

data class UnlimitedLifeGrant(
    val sourceId: String,
    val source: LifeGrantSource,
    val durationMilliseconds: Long,
)

class LifeSystem internal constructor(
    private val repository: LifeStateRepository,
    private val wallClock: WallClock,
    private val balance: LifeBalance,
    private val initialProfile: LoadoutProgressionSnapshot,
) {
    constructor(
        repository: LifeStateRepository,
        wallClock: WallClock,
        initialProfile: LoadoutProgressionSnapshot =
            PlayerProgressionUseCases.INITIAL_PROFILE,
    ) : this(
        repository,
        wallClock,
        GameBalanceRepository.bundled.requireConfig().runtime.campaign.lives,
        initialProfile,
    )

    suspend fun snapshot(): LifeSystemResult<LifeSnapshot> =
        transact { state, now ->
            val current = normalize(state, now)
                ?: return@transact mutation(state, LifeSystemResult.Failure(LifeSystemFailure.INVALID_PROFILE))
            mutation(
                current,
                LifeSystemResult.Success(current.toSnapshot(now)),
                shouldPersist = current != state,
            )
        }

    suspend fun settleCampaignResult(
        matchId: MatchId,
        outcome: MatchOutcome,
    ): LifeSystemResult<LifeOperationResult> {
        if (outcome == MatchOutcome.BLUE_WIN) {
            return when (val current = snapshot()) {
                is LifeSystemResult.Success ->
                    LifeSystemResult.Success(
                        LifeOperationResult(
                            outcome = LifeOperationOutcome(
                                LifeOperationStatus.NOT_CHARGED_WIN,
                                0,
                                0,
                                current.value.lives,
                            ),
                            snapshot = current.value,
                            alreadyApplied = false,
                        ),
                    )
                is LifeSystemResult.Rejected -> current
                is LifeSystemResult.Failure -> current
            }
        }

        val operationId = LifeOperationId(LifeOperationKind.MATCH, matchId.value)
        val fingerprint = "MATCH:${outcome.name}"
        return transact { state, now ->
            val current = normalize(state, now)
                ?: return@transact mutation(
                    state,
                    LifeSystemResult.Failure(LifeSystemFailure.INVALID_PROFILE),
                )
            current.receipts[operationId]?.let { receipt ->
                if (receipt.payloadFingerprint != fingerprint) {
                    return@transact mutation(
                        current,
                        LifeSystemResult.Rejected(LifeOperationRejection.IDEMPOTENCY_CONFLICT),
                        shouldPersist = current != state,
                    )
                }
                return@transact mutation(
                    current,
                    LifeSystemResult.Success(
                        LifeOperationResult(receipt.outcome, current.toSnapshot(now), true),
                    ),
                    shouldPersist = current != state,
                )
            }

            val effectiveNow = current.effectiveTimeHighWaterMarkEpochMilliseconds
            val operationOutcome = when {
                current.unlimitedUntilEpochMilliseconds?.let { it > effectiveNow } == true ->
                    LifeOperationOutcome(
                        LifeOperationStatus.UNLIMITED_PROTECTED,
                        0,
                        0,
                        current.lives,
                    )
                current.lives > 0 ->
                    LifeOperationOutcome(
                        LifeOperationStatus.CHARGED,
                        -1,
                        0,
                        current.lives - 1,
                    )
                else ->
                    LifeOperationOutcome(
                        LifeOperationStatus.NO_LIFE_AVAILABLE,
                        0,
                        0,
                        0,
                    )
            }

            val debitedState = if (operationOutcome.status == LifeOperationStatus.CHARGED) {
                if (current.lives == balance.maximumLives) {
                    val nextLifeAt = safeAdd(
                        effectiveNow,
                        balance.regenerationIntervalMilliseconds,
                    )
                        ?: return@transact mutation(
                            current,
                            LifeSystemResult.Failure(LifeSystemFailure.INVALID_PROFILE),
                        )
                    current.copy(
                        lives = operationOutcome.livesAfter,
                        nextLifeAtEpochMilliseconds = nextLifeAt,
                    )
                } else {
                    current.copy(lives = operationOutcome.livesAfter)
                }
            } else {
                current
            }
            val nextState = debitedState.withReceipt(operationId, fingerprint, operationOutcome)
            mutation(
                nextState,
                LifeSystemResult.Success(
                    LifeOperationResult(operationOutcome, nextState.toSnapshot(now), false),
                ),
                shouldPersist = true,
            )
        }
    }

    suspend fun grantFinite(grant: FiniteLifeGrant): LifeSystemResult<LifeOperationResult> {
        if (grant.sourceId.isBlank() || grant.amount <= 0) {
            return LifeSystemResult.Rejected(LifeOperationRejection.INVALID_GRANT)
        }
        val operationId = LifeOperationId(LifeOperationKind.GRANT, grant.sourceId)
        val fingerprint = "FINITE:${grant.source.name}:${grant.amount}"
        return applyGrant(operationId, fingerprint) { current, _ ->
            val applied = minOf(grant.amount, balance.maximumLives - current.lives)
            val overflow = grant.amount - applied
            val livesAfter = current.lives + applied
            val status = when {
                applied == 0 -> LifeOperationStatus.LIMIT_REACHED
                overflow > 0 -> LifeOperationStatus.PARTIALLY_GRANTED
                else -> LifeOperationStatus.GRANTED
            }
            val nextState = if (livesAfter == balance.maximumLives) {
                current.copy(
                    lives = livesAfter,
                    nextLifeAtEpochMilliseconds = null,
                    pausedRegenerationMilliseconds = null,
                )
            } else {
                current.copy(lives = livesAfter)
            }
            LifeStateMutation(
                state = nextState,
                value = LifeOperationOutcome(status, applied, overflow, livesAfter),
                shouldPersist = true,
            )
        }
    }

    suspend fun grantUnlimited(grant: UnlimitedLifeGrant): LifeSystemResult<LifeOperationResult> {
        if (grant.sourceId.isBlank() || grant.durationMilliseconds <= 0L) {
            return LifeSystemResult.Rejected(LifeOperationRejection.INVALID_GRANT)
        }
        val operationId = LifeOperationId(LifeOperationKind.GRANT, grant.sourceId)
        val fingerprint = "UNLIMITED:${grant.source.name}:${grant.durationMilliseconds}"
        return applyGrant(operationId, fingerprint) { current, _ ->
            val effectiveNow = current.effectiveTimeHighWaterMarkEpochMilliseconds
            val currentUntil = current.unlimitedUntilEpochMilliseconds
                ?.takeIf { it > effectiveNow }
                ?: effectiveNow
            val unlimitedUntil = safeAdd(currentUntil, grant.durationMilliseconds)
                ?: return@applyGrant null
            val pausedRemaining = when {
                current.unlimitedUntilEpochMilliseconds?.let { it > effectiveNow } == true ->
                    current.pausedRegenerationMilliseconds
                current.nextLifeAtEpochMilliseconds != null ->
                    (current.nextLifeAtEpochMilliseconds - effectiveNow).coerceAtLeast(0L)
                else -> null
            }
            val nextState = current.copy(
                nextLifeAtEpochMilliseconds = null,
                pausedRegenerationMilliseconds = pausedRemaining,
                unlimitedUntilEpochMilliseconds = unlimitedUntil,
            )
            LifeStateMutation(
                state = nextState,
                value = LifeOperationOutcome(
                    LifeOperationStatus.GRANTED,
                    0,
                    0,
                    current.lives,
                ),
                shouldPersist = true,
            )
        }
    }

    private suspend fun applyGrant(
        operationId: LifeOperationId,
        fingerprint: String,
        grant: (LifeState, Long) -> LifeStateMutation<LifeOperationOutcome>?,
    ): LifeSystemResult<LifeOperationResult> {
        val now = wallClock.nowEpochMilliseconds()
        val initialLifeState = initialState(now)
            ?: return LifeSystemResult.Failure(LifeSystemFailure.INVALID_PROFILE)
        return when (
            val stored = repository.updateLifeState(
                initialProfile = initialProfile,
                initialState = initialLifeState,
                migrateLegacyLives = { legacyLives -> initialState(now, legacyLives) },
                transform = transform@{ state ->
                    val current = normalize(state, now)
                        ?: return@transform mutation(
                            state,
                            LifeSystemResult.Failure(LifeSystemFailure.INVALID_PROFILE),
                        )
                    current.receipts[operationId]?.let { receipt ->
                        if (receipt.payloadFingerprint != fingerprint) {
                            return@transform mutation(
                                current,
                                LifeSystemResult.Rejected(
                                    LifeOperationRejection.IDEMPOTENCY_CONFLICT,
                                ),
                                shouldPersist = current != state,
                            )
                        }
                        return@transform mutation(
                            current,
                            LifeSystemResult.Success(
                                LifeOperationResult(receipt.outcome, current.toSnapshot(now), true),
                            ),
                            shouldPersist = current != state,
                        )
                    }
                    val decision = grant(current, now)
                        ?: return@transform mutation(
                            current,
                            LifeSystemResult.Rejected(LifeOperationRejection.INVALID_GRANT),
                            shouldPersist = current != state,
                        )
                    val nextState = decision.state.withReceipt(
                        operationId,
                        fingerprint,
                        decision.value,
                    )
                    mutation(
                        nextState,
                        LifeSystemResult.Success(
                            LifeOperationResult(decision.value, nextState.toSnapshot(now), false),
                        ),
                        shouldPersist = decision.shouldPersist || current != state,
                    )
                },
            )
        ) {
            is LifeStateRepositoryResult.Completed -> stored.value
            is LifeStateRepositoryResult.Failure -> stored.reason.toLifeFailure()
        }
    }

    private suspend fun <T> transact(
        transform: (LifeState, Long) -> LifeStateMutation<LifeSystemResult<T>>,
    ): LifeSystemResult<T> {
        val now = wallClock.nowEpochMilliseconds()
        val initialLifeState = initialState(now)
            ?: return LifeSystemResult.Failure(LifeSystemFailure.INVALID_PROFILE)
        return when (
            val stored = repository.updateLifeState(
                initialProfile = initialProfile,
                initialState = initialLifeState,
                migrateLegacyLives = { legacyLives -> initialState(now, legacyLives) },
                transform = { state -> transform(state, now) },
            )
        ) {
            is LifeStateRepositoryResult.Completed -> stored.value
            is LifeStateRepositoryResult.Failure -> stored.reason.toLifeFailure()
        }
    }

    private fun initialState(
        nowEpochMilliseconds: Long,
        legacyLives: Int? = null,
    ): LifeState? {
        if (legacyLives != null && legacyLives < 0) return null
        val lives = (legacyLives ?: balance.initialLives).coerceAtMost(balance.maximumLives)
        return LifeStateFactory.create(lives, nowEpochMilliseconds, balance)
    }

    private fun normalize(state: LifeState, now: Long): LifeState? {
        if (!isValid(state) || now < 0L) return null
        val effectiveNow = maxOf(now, state.effectiveTimeHighWaterMarkEpochMilliseconds)
        var current = state.copy(effectiveTimeHighWaterMarkEpochMilliseconds = effectiveNow)
        val unlimitedUntil = current.unlimitedUntilEpochMilliseconds
        if (unlimitedUntil != null && effectiveNow >= unlimitedUntil) {
            val resumedAt = current.pausedRegenerationMilliseconds?.let { remaining ->
                safeAdd(unlimitedUntil, remaining) ?: return null
            }
            current = current.copy(
                nextLifeAtEpochMilliseconds = resumedAt,
                pausedRegenerationMilliseconds = null,
                unlimitedUntilEpochMilliseconds = null,
            )
        }
        if (current.lives < balance.maximumLives &&
            current.unlimitedUntilEpochMilliseconds == null
        ) {
            val nextLifeAt = current.nextLifeAtEpochMilliseconds ?: return null
            if (effectiveNow >= nextLifeAt) {
                val capacity = balance.maximumLives - current.lives
                val intervalsAfterFirst =
                    (effectiveNow - nextLifeAt) / balance.regenerationIntervalMilliseconds
                val gained = if (intervalsAfterFirst >= (capacity - 1).toLong()) {
                    capacity
                } else {
                    intervalsAfterFirst.toInt() + 1
                }
                val lives = current.lives + gained
                val nextAt = if (lives == balance.maximumLives) {
                    null
                } else {
                    val elapsed = safeMultiply(gained.toLong(), balance.regenerationIntervalMilliseconds)
                        ?: return null
                    safeAdd(nextLifeAt, elapsed) ?: return null
                }
                current = current.copy(lives = lives, nextLifeAtEpochMilliseconds = nextAt)
            }
        }
        if (current.lives == balance.maximumLives) {
            current = current.copy(
                nextLifeAtEpochMilliseconds = null,
                pausedRegenerationMilliseconds = null,
            )
        }
        return current
    }

    private fun isValid(state: LifeState): Boolean {
        if (state.lives !in 0..balance.maximumLives ||
            state.effectiveTimeHighWaterMarkEpochMilliseconds < 0L
        ) {
            return false
        }
        if (state.receipts.any { (id, receipt) ->
                id != receipt.id || receipt.outcome.livesAfter !in 0..balance.maximumLives
            }
        ) {
            return false
        }
        val hasUnlimited = state.unlimitedUntilEpochMilliseconds != null
        if (hasUnlimited) {
            if (state.nextLifeAtEpochMilliseconds != null) return false
            if (state.lives < balance.maximumLives) {
                val remaining = state.pausedRegenerationMilliseconds ?: return false
                if (remaining !in 1..balance.regenerationIntervalMilliseconds) return false
            } else if (state.pausedRegenerationMilliseconds != null) {
                return false
            }
        } else {
            if (state.pausedRegenerationMilliseconds != null) return false
            if (state.lives == balance.maximumLives) {
                if (state.nextLifeAtEpochMilliseconds != null) return false
            } else if (state.nextLifeAtEpochMilliseconds == null) {
                return false
            }
        }
        return listOfNotNull(
            state.nextLifeAtEpochMilliseconds,
            state.pausedRegenerationMilliseconds,
            state.unlimitedUntilEpochMilliseconds,
        ).all { it >= 0L }
    }

    private fun LifeState.toSnapshot(now: Long): LifeSnapshot {
        val effectiveNow = maxOf(now, effectiveTimeHighWaterMarkEpochMilliseconds)
        val unlimitedRemaining = unlimitedUntilEpochMilliseconds
            ?.minus(effectiveNow)
            ?.coerceAtLeast(0L)
        val regenerationRemaining = if (unlimitedUntilEpochMilliseconds != null) {
            null
        } else {
            nextLifeAtEpochMilliseconds?.minus(effectiveNow)?.coerceAtLeast(0L)
        }
        return LifeSnapshot(
            lives = lives,
            maximumLives = balance.maximumLives,
            canStartCampaign = lives > 0 || (unlimitedRemaining ?: 0L) > 0L,
            regenerationTimeRemainingMilliseconds = regenerationRemaining,
            unlimitedTimeRemainingMilliseconds = unlimitedRemaining,
        )
    }

    private fun LifeState.withReceipt(
        id: LifeOperationId,
        fingerprint: String,
        outcome: LifeOperationOutcome,
    ): LifeState = copy(
        receipts = receipts + (id to LifeOperationReceipt(id, fingerprint, outcome)),
    )

    private fun <T> mutation(
        state: LifeState,
        value: LifeSystemResult<T>,
        shouldPersist: Boolean = false,
    ) = LifeStateMutation(state, value, shouldPersist)

    private fun PlayerProgressionRepositoryFailure.toLifeFailure(): LifeSystemResult<Nothing> =
        LifeSystemResult.Failure(
            when (this) {
                PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE ->
                    LifeSystemFailure.STORAGE_UNAVAILABLE
                PlayerProgressionRepositoryFailure.INVALID_PROFILE ->
                    LifeSystemFailure.INVALID_PROFILE
                PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION ->
                    LifeSystemFailure.UNSUPPORTED_PROFILE_VERSION
            },
        )

    private fun safeAdd(first: Long, second: Long): Long? =
        try {
            Math.addExact(first, second)
        } catch (_: ArithmeticException) {
            null
        }

    private fun safeMultiply(first: Long, second: Long): Long? =
        try {
            Math.multiplyExact(first, second)
        } catch (_: ArithmeticException) {
            null
        }
}
