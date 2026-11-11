package com.urkaaaz.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.progression.FiniteLifeGrant
import com.urkaaaz.application.progression.LifeGrantSource
import com.urkaaaz.application.progression.LifeOperationRejection
import com.urkaaaz.application.progression.LifeSystem
import com.urkaaaz.application.progression.LifeSystemResult
import com.urkaaaz.application.progression.PlayerProgressionMutation
import com.urkaaaz.application.progression.PlayerProgressionMutationResult
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.WallClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreLifeStateRepositoryTest {
    @Test
    fun legacyLivesMigrateOnceAndProfileUpdatesPreserveCanonicalLifeState() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[intPreferencesKey("profile.schema_version")] = 1
                    this[stringPreferencesKey("profile.unlocked_spells")] = "FORTRESS_SHIELD"
                    this[stringPreferencesKey("profile.ammunition_stocks")] = ""
                    this[stringPreferencesKey("profile.special_spell_stocks")] = ""
                    this[intPreferencesKey("profile.gold_balance")] = 125
                    this[intPreferencesKey("profile.lives")] = 3
                }
            }
            val clock = MutableWallClock(1_000L)
            val lifeSystem = lifeSystem(store.dataStore, clock)

            val migrated = assertSuccess(lifeSystem.snapshot())
            assertEquals(3, migrated.lives)
            assertEquals(900_000L, migrated.regenerationTimeRemainingMilliseconds)

            var stored = store.dataStore.data.first()
            assertEquals(2, stored[intPreferencesKey("profile.schema_version")])
            assertEquals(3, stored[intPreferencesKey("profile.life_lives")])
            assertEquals(1_000L, stored[longPreferencesKey("profile.life_high_water_ms")])
            assertEquals(
                901_000L,
                stored[longPreferencesKey("profile.life_next_regeneration_ms")],
            )
            assertNull(stored[intPreferencesKey("profile.lives")])

            val profileRepository = DataStorePlayerProgressionRepository(store.dataStore)
            val updated = profileRepository.updateProfile(initialProfile()) { current ->
                PlayerProgressionMutation(
                    snapshot = current.copy(goldBalance = current.goldBalance + 50),
                    value = Unit,
                    shouldPersist = true,
                )
            }
            assertTrue(updated is PlayerProgressionMutationResult.Completed)
            stored = store.dataStore.data.first()
            assertEquals(3, stored[intPreferencesKey("profile.life_lives")])
            assertEquals(
                901_000L,
                stored[longPreferencesKey("profile.life_next_regeneration_ms")],
            )
            assertEquals(175, stored[intPreferencesKey("profile.gold_balance")])
        }
    }

    @Test
    fun grantReceiptsAreAtomicAndRemainIdempotentAfterRecreation() = runBlocking {
        testStore().use { store ->
            val clock = MutableWallClock(100L)
            val system = lifeSystem(store.dataStore, clock)
            assertSuccess(system.snapshot())
            assertSuccess(
                system.settleCampaignResult(
                    com.urkaaaz.contracts.MatchId("life-data-store-loss"),
                    com.urkaaaz.contracts.MatchOutcome.RED_WIN,
                ),
            )

            val grant = FiniteLifeGrant("reward-event-1", LifeGrantSource.GAME_EVENT, 1)
            assertEquals(5, assertSuccess(system.grantFinite(grant)).snapshot.lives)
            val recreated = lifeSystem(store.dataStore, clock)
            val repeated = assertSuccess(recreated.grantFinite(grant))

            assertTrue(repeated.alreadyApplied)
            assertEquals(5, repeated.snapshot.lives)
            assertEquals(
                LifeSystemResult.Rejected(LifeOperationRejection.IDEMPOTENCY_CONFLICT),
                recreated.grantFinite(grant.copy(amount = 2)),
            )
            val stored = PlayerProgressionDataStoreCodec.decode(store.dataStore.data.first())
            assertTrue(
                (stored as PlayerProgressionProfileDecode.Available)
                    .lifeState!!.receipts.keys.any { it.value == "reward-event-1" },
            )
        }
    }

    @Test
    fun resetClearsLifeReceiptsTimersAndProfileButPreservesPresetMarker() = runBlocking {
        testStore().use { store ->
            val clock = MutableWallClock(200L)
            val system = lifeSystem(store.dataStore, clock)
            assertSuccess(system.snapshot())
            assertSuccess(
                system.settleCampaignResult(
                    com.urkaaaz.contracts.MatchId("reset-life-match"),
                    com.urkaaaz.contracts.MatchOutcome.SURRENDER,
                ),
            )
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[stringPreferencesKey("developer_progress.applied_hash")] = "preset-hash"
                }
            }

            assertEquals(
                com.urkaaaz.application.progression.PlayerProgressResetResult.Reset,
                DataStorePlayerProgressResetRepository(store.dataStore).reset(),
            )
            val afterReset = store.dataStore.data.first()
            assertEquals(
                "preset-hash",
                afterReset[stringPreferencesKey("developer_progress.applied_hash")],
            )
            assertNull(afterReset[intPreferencesKey("profile.life_lives")])
            assertNull(afterReset[stringPreferencesKey("profile.life_receipts")])
            assertEquals(5, assertSuccess(system.snapshot()).lives)
        }
    }

    @Test
    fun malformedLifeReceiptsFailInsteadOfBeingReplaced() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[intPreferencesKey("profile.schema_version")] = 2
                    this[stringPreferencesKey("profile.unlocked_spells")] = "FORTRESS_SHIELD"
                    this[stringPreferencesKey("profile.ammunition_stocks")] = ""
                    this[stringPreferencesKey("profile.special_spell_stocks")] = ""
                    this[intPreferencesKey("profile.gold_balance")] = 0
                    this[intPreferencesKey("profile.life_lives")] = 5
                    this[longPreferencesKey("profile.life_high_water_ms")] = 10L
                    this[stringPreferencesKey("profile.life_receipts")] = "invalid"
                }
            }

            assertEquals(
                LifeSystemResult.Failure(
                    com.urkaaaz.application.progression.LifeSystemFailure.INVALID_PROFILE,
                ),
                lifeSystem(store.dataStore, MutableWallClock(10L)).snapshot(),
            )
            assertEquals(
                PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                PlayerProgressionDataStoreCodec.decode(store.dataStore.data.first())
                    .let { result ->
                        (result as PlayerProgressionProfileDecode.Failure).reason
                    },
            )
        }
    }

    private fun lifeSystem(
        dataStore: DataStore<Preferences>,
        clock: MutableWallClock,
    ) = LifeSystem(
        repository = DataStoreLifeStateRepository(dataStore),
        wallClock = clock,
        initialProfile = initialProfile(),
    )

    private fun initialProfile() = LoadoutProgressionSnapshot(
        unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
        ammunitionStocks = emptyMap(),
        specialSpellStocks = emptyMap(),
        goldBalance = 0,
    )

    private fun <T> assertSuccess(result: LifeSystemResult<T>): T {
        assertTrue("expected success but got $result", result is LifeSystemResult.Success)
        return (result as LifeSystemResult.Success).value
    }

    private fun testStore(): TestStore {
        val file = File.createTempFile("urkaaz-life-", ".preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return TestStore(
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            ),
            scope = scope,
            file = file,
        )
    }

    private class MutableWallClock(var now: Long) : WallClock {
        override fun nowEpochMilliseconds(): Long = now
    }

    private class TestStore(
        val dataStore: DataStore<Preferences>,
        private val scope: CoroutineScope,
        private val file: File,
    ) : AutoCloseable {
        override fun close() {
            scope.cancel()
            file.delete()
        }
    }
}
