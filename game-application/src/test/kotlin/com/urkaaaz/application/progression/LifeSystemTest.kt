package com.urkaaaz.application.progression

import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.contracts.MatchId
import com.urkaaaz.contracts.MatchOutcome
import com.urkaaaz.domain.LifeBalance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeSystemTest {
    @Test
    fun initialLivesAndFullProfileHaveNoRegenerationTimer() = runBlocking {
        val system = lifeSystem()

        assertEquals(
            LifeSnapshot(5, 5, true, null, null),
            assertSuccess(system.snapshot()),
        )
    }

    @Test
    fun lossesChargeOnceAndPreserveAnExistingRegenerationDeadline() = runBlocking {
        val clock = MutableWallClock(1_000L)
        val repository = FakeLifeStateRepository()
        val system = lifeSystem(repository, clock)

        val first = assertSuccess(
            system.settleCampaignResult(MatchId("loss-one"), MatchOutcome.RED_WIN),
        )
        assertEquals(LifeOperationStatus.CHARGED, first.outcome.status)
        assertEquals(4, first.snapshot.lives)
        assertEquals(900_000L, first.snapshot.regenerationTimeRemainingMilliseconds)

        clock.now = 301_000L
        val second = assertSuccess(
            system.settleCampaignResult(MatchId("loss-two"), MatchOutcome.DRAW),
        )
        assertEquals(3, second.snapshot.lives)
        assertEquals(600_000L, second.snapshot.regenerationTimeRemainingMilliseconds)

        clock.now = 901_000L
        val regenerated = assertSuccess(system.snapshot())
        assertEquals(4, regenerated.lives)
        assertEquals(900_000L, regenerated.regenerationTimeRemainingMilliseconds)

        val repeated = assertSuccess(
            system.settleCampaignResult(MatchId("loss-two"), MatchOutcome.DRAW),
        )
        assertTrue(repeated.alreadyApplied)
        assertEquals(4, repeated.snapshot.lives)
    }

    @Test
    fun offlineCatchUpAppliesEveryFullIntervalAndStopsAtTheLimit() = runBlocking {
        val clock = MutableWallClock(0L)
        val repository = FakeLifeStateRepository(legacyLives = 1, hasLegacyProfile = true)
        val system = lifeSystem(repository, clock)

        assertEquals(1, assertSuccess(system.snapshot()).lives)
        clock.now = 3 * INTERVAL + INTERVAL / 2
        val caughtUp = assertSuccess(system.snapshot())

        assertEquals(4, caughtUp.lives)
        assertEquals(INTERVAL / 2, caughtUp.regenerationTimeRemainingMilliseconds)
        clock.now += INTERVAL
        val full = assertSuccess(system.snapshot())
        assertEquals(5, full.lives)
        assertNull(full.regenerationTimeRemainingMilliseconds)
    }

    @Test
    fun rollbackCannotReduceTheEffectiveTimeOrRestartRegeneration() = runBlocking {
        val clock = MutableWallClock(1_000_000L)
        val system = lifeSystem(clock = clock)
        assertSuccess(system.settleCampaignResult(MatchId("rollback-loss"), MatchOutcome.SURRENDER))

        clock.now = 500_000L
        assertEquals(
            INTERVAL,
            assertSuccess(system.snapshot()).regenerationTimeRemainingMilliseconds,
        )
        clock.now = 1_500_000L
        assertEquals(
            400_000L,
            assertSuccess(system.snapshot()).regenerationTimeRemainingMilliseconds,
        )
    }

    @Test
    fun finiteGrantIsCappedPartialIdempotentAndRejectsPayloadReuse() = runBlocking {
        val system = lifeSystem()
        assertSuccess(system.settleCampaignResult(MatchId("finite-loss"), MatchOutcome.RED_WIN))
        val grant = FiniteLifeGrant("event-42", LifeGrantSource.GAME_EVENT, 3)

        val first = assertSuccess(system.grantFinite(grant))
        assertEquals(LifeOperationStatus.PARTIALLY_GRANTED, first.outcome.status)
        assertEquals(1, first.outcome.livesChanged)
        assertEquals(2, first.outcome.overflow)
        assertEquals(5, first.snapshot.lives)
        assertNull(first.snapshot.regenerationTimeRemainingMilliseconds)

        val repeated = assertSuccess(system.grantFinite(grant))
        assertTrue(repeated.alreadyApplied)
        assertEquals(5, repeated.snapshot.lives)
        assertEquals(
            LifeSystemResult.Rejected(LifeOperationRejection.IDEMPOTENCY_CONFLICT),
            system.grantFinite(grant.copy(amount = 1)),
        )
    }

    @Test
    fun unlimitedPausesAndResumesTheRemainingTimerAndProtectsResults() = runBlocking {
        val clock = MutableWallClock(0L)
        val system = lifeSystem(clock = clock)
        repeat(4) { index ->
            assertSuccess(
                system.settleCampaignResult(
                    MatchId("unlimited-loss-$index"),
                    MatchOutcome.RED_WIN,
                ),
            )
        }
        clock.now = 300_000L
        val grant = UnlimitedLifeGrant("event-unlimited", LifeGrantSource.GAME_EVENT, 600_000L)
        val activated = assertSuccess(system.grantUnlimited(grant))
        assertEquals(1, activated.snapshot.lives)
        assertEquals(600_000L, activated.snapshot.unlimitedTimeRemainingMilliseconds)
        assertNull(activated.snapshot.regenerationTimeRemainingMilliseconds)

        clock.now = 400_000L
        val protected = assertSuccess(
            system.settleCampaignResult(MatchId("protected-loss"), MatchOutcome.SURRENDER),
        )
        assertEquals(LifeOperationStatus.UNLIMITED_PROTECTED, protected.outcome.status)
        assertEquals(1, protected.snapshot.lives)

        clock.now = 1_500_000L
        val resumed = assertSuccess(system.snapshot())
        assertEquals(2, resumed.lives)
        assertEquals(INTERVAL, resumed.regenerationTimeRemainingMilliseconds)
        clock.now = 2_400_000L
        assertEquals(3, assertSuccess(system.snapshot()).lives)
    }

    @Test
    fun unlimitedGrantsStackAndFiniteGrantsStillAffectBaseLives() = runBlocking {
        val clock = MutableWallClock(0L)
        val repository = FakeLifeStateRepository(legacyLives = 0, hasLegacyProfile = true)
        val system = lifeSystem(repository, clock)

        val first = assertSuccess(
            system.grantUnlimited(
                UnlimitedLifeGrant("event-a", LifeGrantSource.GAME_EVENT, 60_000L),
            ),
        )
        assertTrue(first.snapshot.canStartCampaign)
        val second = assertSuccess(
            system.grantUnlimited(
                UnlimitedLifeGrant("event-b", LifeGrantSource.GAME_EVENT, 90_000L),
            ),
        )
        assertEquals(150_000L, second.snapshot.unlimitedTimeRemainingMilliseconds)

        val finite = assertSuccess(
            system.grantFinite(FiniteLifeGrant("ad-1", LifeGrantSource.AD_REWARD, 1)),
        )
        assertEquals(1, finite.snapshot.lives)
        assertEquals(150_000L, finite.snapshot.unlimitedTimeRemainingMilliseconds)
    }

    @Test
    fun blueWinDoesNotConsumeLivesAndLegacyCountsAreClamped() = runBlocking {
        val repository = FakeLifeStateRepository(legacyLives = 20, hasLegacyProfile = true)
        val system = lifeSystem(repository)

        val win = assertSuccess(
            system.settleCampaignResult(MatchId("win-match"), MatchOutcome.BLUE_WIN),
        )
        assertEquals(LifeOperationStatus.NOT_CHARGED_WIN, win.outcome.status)
        assertEquals(5, win.snapshot.lives)
        assertNull(win.snapshot.regenerationTimeRemainingMilliseconds)
    }

    private fun lifeSystem(
        repository: FakeLifeStateRepository = FakeLifeStateRepository(),
        clock: MutableWallClock = MutableWallClock(0L),
    ) = LifeSystem(
        repository = repository,
        wallClock = clock,
        balance = LifeBalance(5, 5, INTERVAL),
        initialProfile = LoadoutProgressionSnapshot(
            unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
            ammunitionStocks = emptyMap(),
            specialSpellStocks = emptyMap(),
            goldBalance = 0,
        ),
    )

    private fun <T> assertSuccess(result: LifeSystemResult<T>): T {
        assertTrue("expected success but got $result", result is LifeSystemResult.Success)
        return (result as LifeSystemResult.Success).value
    }

    private class MutableWallClock(var now: Long) : WallClock {
        override fun nowEpochMilliseconds(): Long = now
    }

    private class FakeLifeStateRepository(
        private var legacyLives: Int? = null,
        private var hasLegacyProfile: Boolean = false,
    ) : LifeStateRepository {
        private var state: LifeState? = null

        override suspend fun <T> updateLifeState(
            initialProfile: LoadoutProgressionSnapshot,
            initialState: LifeState,
            migrateLegacyLives: (Int?) -> LifeState?,
            transform: (LifeState) -> LifeStateMutation<T>,
        ): LifeStateRepositoryResult<T> {
            val isMigration = state == null
            val current = state ?: if (hasLegacyProfile) {
                requireNotNull(migrateLegacyLives(legacyLives))
            } else {
                initialState
            }
            val mutation = transform(current)
            if (isMigration || mutation.shouldPersist) {
                state = mutation.state
                legacyLives = null
                hasLegacyProfile = true
            }
            return LifeStateRepositoryResult.Completed(mutation.state, mutation.value)
        }
    }

    private companion object {
        const val INTERVAL = 900_000L
    }
}
