package com.urkaaaz.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.loadout.LoadoutProgressionRead
import com.urkaaaz.application.loadout.LoadoutProgressionSnapshot
import com.urkaaaz.application.loadout.LoadoutStorageFailure
import com.urkaaaz.application.loadout.LoadoutStorageResult
import com.urkaaaz.application.loadout.LoadoutStorageWriteResult
import com.urkaaaz.application.loadout.MatchLoadout
import com.urkaaaz.application.loadout.StoredLoadoutSelection
import com.urkaaaz.application.progression.PlayerProgressionRepositoryFailure
import com.urkaaaz.application.progression.PlayerProgressionRepositoryResult
import com.urkaaaz.application.progression.PlayerProgressionUseCases
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreRepositoriesTest {
    @Test
    fun profileInitializesOnceAndLoadoutWritesPreserveProgression() = runBlocking {
        testStore().use { store ->
            val progression = PlayerProgressionUseCases(
                DataStorePlayerProgressionRepository(store.dataStore),
            )
            val initialSnapshot =
                (progression.snapshot(1) as LoadoutProgressionRead.Available).snapshot
            assertEquals(setOf("FORTRESS_SHIELD"), initialSnapshot.unlockedSpellTypes)
            assertEquals(emptyMap<String, Int>(), initialSnapshot.ammunitionStocks)
            assertEquals(emptyMap<String, Int>(), initialSnapshot.specialSpellStocks)
            assertEquals(0, initialSnapshot.goldBalance)

            val loadoutRepository = DataStoreLoadoutRepository(store.dataStore)
            val loadout = MatchLoadout(
                unitTypes = listOf("SAPPER", "DEFENDER"),
                ammunitionSlots = listOf("ROCK", "POWDER_BARREL", "SIEGE_BOMB"),
                spellTypes = listOf("HEAL", "HASTE"),
            )
            assertEquals(
                LoadoutStorageWriteResult.Saved,
                loadoutRepository.save(level = 3, loadout = loadout),
            )
            assertEquals(
                LoadoutStorageResult.Success(
                    StoredLoadoutSelection(
                        unitTypes = loadout.unitTypes,
                        ammunitionSlots = loadout.ammunitionSlots,
                        spellTypes = loadout.spellTypes,
                    ),
                ),
                loadoutRepository.read(level = 3),
            )
            assertEquals(LoadoutStorageResult.Success(null), loadoutRepository.read(level = 4))

            assertEquals(
                PlayerProgressionRepositoryResult.Available(initialSnapshot),
                DataStorePlayerProgressionRepository(store.dataStore)
                    .loadOrInitialize(initialSnapshot.copy(goldBalance = 999)),
            )
        }
    }

    @Test
    fun partialProfileIsNotReplacedWithDefaults() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[intPreferencesKey("profile.gold_balance")] = 17
                }
            }

            val result = DataStorePlayerProgressionRepository(store.dataStore)
                .loadOrInitialize(initialProfile())

            assertEquals(
                PlayerProgressionRepositoryResult.Failure(
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                ),
                result,
            )
        }
    }

    @Test
    fun unsupportedLoadoutVersionPreventsOverwrite() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[intPreferencesKey("loadout.schema_version")] = 99
                }
            }
            val repository = DataStoreLoadoutRepository(store.dataStore)
            val loadout = validLoadout()

            assertEquals(
                LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.UNSUPPORTED_VERSION),
                repository.save(1, loadout),
            )
            assertTrue(repository.read(1) is LoadoutStorageResult.Failure)
        }
    }

    @Test
    fun partialLoadoutWithoutSchemaIsReportedInsteadOfReplaced() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[stringPreferencesKey("loadout.2.units")] = "DEFENDER"
                }
            }
            val repository = DataStoreLoadoutRepository(store.dataStore)

            assertEquals(
                LoadoutStorageResult.Failure(LoadoutStorageFailure.INVALID_DATA),
                repository.read(1),
            )
            assertEquals(
                LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.INVALID_DATA),
                repository.save(1, validLoadout()),
            )
        }
    }

    @Test
    fun malformedProfilePayloadIsNotReplacedWithInitialValues() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[intPreferencesKey("profile.schema_version")] = 1
                    this[stringPreferencesKey("profile.unlocked_spells")] = "FORTRESS_SHIELD"
                    this[stringPreferencesKey("profile.ammunition_stocks")] = "POWDER_BARREL:nope"
                    this[stringPreferencesKey("profile.special_spell_stocks")] = ""
                    this[intPreferencesKey("profile.gold_balance")] = 0
                }
            }

            assertEquals(
                PlayerProgressionRepositoryResult.Failure(
                    PlayerProgressionRepositoryFailure.INVALID_PROFILE,
                ),
                DataStorePlayerProgressionRepository(store.dataStore)
                    .loadOrInitialize(initialProfile()),
            )
        }
    }

    @Test
    fun profileStocksAndUnknownLoadoutIdentifiersRoundTripWithoutNormalization() = runBlocking {
        testStore().use { store ->
            val profile = LoadoutProgressionSnapshot(
                unlockedSpellTypes = setOf("FORTRESS_SHIELD", "HASTE"),
                ammunitionStocks = mapOf("POWDER_BARREL" to 2),
                specialSpellStocks = mapOf("CATAPULT_LOCK" to 1),
                goldBalance = 42,
            )
            assertEquals(
                PlayerProgressionRepositoryResult.Available(profile),
                DataStorePlayerProgressionRepository(store.dataStore).loadOrInitialize(profile),
            )
            assertEquals(
                PlayerProgressionRepositoryResult.Available(profile),
                DataStorePlayerProgressionRepository(store.dataStore)
                    .loadOrInitialize(initialProfile()),
            )

            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[stringPreferencesKey("loadout.5.units")] = "UNKNOWN|DEFENDER"
                    this[stringPreferencesKey("loadout.5.ammunition")] = "ROCK|-|UNKNOWN_AMMO"
                    this[stringPreferencesKey("loadout.5.spells")] = "UNKNOWN_SPELL|HASTE"
                    this[intPreferencesKey("loadout.schema_version")] = 1
                }
            }
            assertEquals(
                LoadoutStorageResult.Success(
                    StoredLoadoutSelection(
                        unitTypes = listOf("UNKNOWN", "DEFENDER"),
                        ammunitionSlots = listOf("ROCK", null, "UNKNOWN_AMMO"),
                        spellTypes = listOf("UNKNOWN_SPELL", "HASTE"),
                    ),
                ),
                DataStoreLoadoutRepository(store.dataStore).read(5),
            )
        }
    }

    @Test
    fun unknownProfileVersionAndStorageIoAreTypedFailures() = runBlocking {
        testStore().use { store ->
            store.dataStore.updateData { current ->
                current.toMutablePreferences().apply {
                    this[intPreferencesKey("profile.schema_version")] = 99
                }
            }
            assertEquals(
                PlayerProgressionRepositoryResult.Failure(
                    PlayerProgressionRepositoryFailure.UNSUPPORTED_VERSION,
                ),
                DataStorePlayerProgressionRepository(store.dataStore)
                    .loadOrInitialize(initialProfile()),
            )
        }

        val failingStore = FailingDataStore(IOException("disk unavailable"))
        assertEquals(
            PlayerProgressionRepositoryResult.Failure(
                PlayerProgressionRepositoryFailure.STORAGE_UNAVAILABLE,
            ),
            DataStorePlayerProgressionRepository(failingStore).loadOrInitialize(initialProfile()),
        )
        assertEquals(
            LoadoutStorageResult.Failure(LoadoutStorageFailure.STORAGE_UNAVAILABLE),
            DataStoreLoadoutRepository(failingStore).read(1),
        )
        assertEquals(
            LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.STORAGE_UNAVAILABLE),
            DataStoreLoadoutRepository(failingStore).save(1, validLoadout()),
        )
    }

    @Test
    fun invalidLevelIsRejectedByLoadoutRepository() = runBlocking {
        testStore().use { store ->
            val repository = DataStoreLoadoutRepository(store.dataStore)

            assertEquals(
                LoadoutStorageResult.Failure(LoadoutStorageFailure.INVALID_LEVEL),
                repository.read(0),
            )
            assertEquals(
                LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.INVALID_LEVEL),
                repository.save(0, validLoadout()),
            )
        }
    }

    private fun testStore(): TestStore {
        val file = File.createTempFile("urkaaz-profile-", ".preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return TestStore(
            file = file,
            scope = scope,
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            ),
        )
    }

    private fun initialProfile() = LoadoutProgressionSnapshot(
        unlockedSpellTypes = setOf("FORTRESS_SHIELD"),
        ammunitionStocks = emptyMap(),
        specialSpellStocks = emptyMap(),
        goldBalance = 0,
    )

    private fun validLoadout() = MatchLoadout(
        unitTypes = listOf("DEFENDER"),
        ammunitionSlots = listOf("ROCK", null, null),
        spellTypes = listOf("FORTRESS_SHIELD"),
    )

    private class TestStore(
        private val file: File,
        private val scope: CoroutineScope,
        val dataStore: DataStore<Preferences>,
    ) : AutoCloseable {
        override fun close() {
            scope.cancel()
            file.delete()
        }
    }

    private class FailingDataStore(
        private val failure: IOException,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            throw failure
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw failure
    }
}
