package com.urkaaaz.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import com.urkaaaz.application.progression.PlayerProgressResetResult
import com.urkaaaz.application.tutorial.TutorialProgress
import com.urkaaaz.application.tutorial.TutorialProgressFailure
import com.urkaaaz.application.tutorial.TutorialProgressRead
import com.urkaaaz.application.tutorial.TutorialProgressWrite
import com.urkaaaz.application.tutorial.TutorialStatus
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreTutorialProgressRepositoryTest {
    @Test
    fun terminalStatusesRoundTripAndUnknownTutorialsAreLoggedAndIgnored() = runBlocking {
        testStore().use { store ->
            val warnings = mutableListOf<String>()
            val repository = DataStoreTutorialProgressRepository(store.dataStore, warnings::add)

            assertEquals(
                TutorialProgressWrite.Saved,
                repository.markTerminal(
                    tutorialId = "match.basics",
                    status = TutorialStatus.COMPLETED,
                    schemaVersion = 1,
                    catalogRevision = "tutorial-1",
                ),
            )
            repository.markTerminal(
                tutorialId = "old.tutorial",
                status = TutorialStatus.SKIPPED,
                schemaVersion = 1,
                catalogRevision = "tutorial-1",
            )

            val read = repository.load(setOf("match.basics"))
            assertTrue(read is TutorialProgressRead.Available)
            assertEquals(
                TutorialProgress(
                    terminalStatuses = mapOf("match.basics" to TutorialStatus.COMPLETED),
                    schemaVersion = 1,
                    catalogRevision = "tutorial-1",
                ),
                (read as TutorialProgressRead.Available).progress,
            )
            assertEquals(1, warnings.size)
            assertTrue(warnings.single().contains("old.tutorial"))
        }
    }

    @Test
    fun newerSchemaIsRejectedWithoutChangingStoredValues() = runBlocking {
        testStore().use { store ->
            val repository = DataStoreTutorialProgressRepository(store.dataStore)
            store.dataStore.edit { preferences ->
                preferences[intPreferencesKey("tutorial.schema_version")] = 2
                preferences[stringPreferencesKey("tutorial.catalog_revision")] = "tutorial-2"
                preferences[stringPreferencesKey("tutorial.progress")] =
                    "match.basics=COMPLETED"
            }

            assertEquals(
                TutorialProgressRead.Failure(TutorialProgressFailure.UNSUPPORTED_SCHEMA),
                repository.load(setOf("match.basics")),
            )
            assertEquals(
                TutorialProgressWrite.Failure(TutorialProgressFailure.UNSUPPORTED_SCHEMA),
                repository.markTerminal(
                    "match.basics",
                    TutorialStatus.SKIPPED,
                    schemaVersion = 1,
                    catalogRevision = "tutorial-1",
                ),
            )
            val stored = store.dataStore.data.first()
            assertEquals(2, stored[intPreferencesKey("tutorial.schema_version")])
            assertEquals(
                "match.basics=COMPLETED",
                stored[stringPreferencesKey("tutorial.progress")],
            )
        }
    }

    @Test
    fun resetDoesNotDeleteAProgressRecordUsingANewerSchema() = runBlocking {
        testStore().use { store ->
            val repository = DataStoreTutorialProgressRepository(store.dataStore)
            store.dataStore.edit { preferences ->
                preferences[intPreferencesKey("tutorial.schema_version")] = 2
                preferences[stringPreferencesKey("tutorial.catalog_revision")] = "tutorial-2"
                preferences[stringPreferencesKey("tutorial.progress")] =
                    "match.basics=COMPLETED"
            }

            assertEquals(
                TutorialProgressWrite.Failure(TutorialProgressFailure.UNSUPPORTED_SCHEMA),
                repository.reset("match.basics"),
            )
            assertEquals(
                "match.basics=COMPLETED",
                store.dataStore.data.first()[stringPreferencesKey("tutorial.progress")],
            )
        }
    }

    @Test
    fun malformedTerminalStatusIsAnExplicitInvalidDataFailure() = runBlocking {
        testStore().use { store ->
            val repository = DataStoreTutorialProgressRepository(store.dataStore)
            store.dataStore.edit { preferences ->
                preferences[intPreferencesKey("tutorial.schema_version")] = 1
                preferences[stringPreferencesKey("tutorial.catalog_revision")] = "tutorial-1"
                preferences[stringPreferencesKey("tutorial.progress")] =
                    "match.basics=IN_PROGRESS"
            }

            assertEquals(
                TutorialProgressRead.Failure(TutorialProgressFailure.INVALID_DATA),
                repository.load(setOf("match.basics")),
            )
        }
    }

    @Test
    fun progressWriteFailureIsReturnedAsATypedResult() = runBlocking {
        val repository = DataStoreTutorialProgressRepository(
            FailingDataStore(IOException("storage unavailable")),
        )

        assertEquals(
            TutorialProgressWrite.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE),
            repository.markTerminal(
                tutorialId = "match.basics",
                status = TutorialStatus.COMPLETED,
                schemaVersion = 1,
                catalogRevision = "tutorial-1",
            ),
        )
    }

    @Test
    fun resetAdapterRemovesTutorialNamespaceWithoutChangingItsBehavior() = runBlocking {
        testStore().use { store ->
            store.dataStore.edit { preferences ->
                preferences[intPreferencesKey("tutorial.schema_version")] = 1
                preferences[stringPreferencesKey("tutorial.catalog_revision")] = "tutorial-1"
                preferences[stringPreferencesKey("tutorial.progress")] =
                    "match.basics=SKIPPED"
                preferences[DataStoreDeveloperProgressPresetAdapter.APPLIED_PROGRESS_PRESET_HASH] =
                    "preset-hash"
            }

            assertEquals(
                PlayerProgressResetResult.Reset,
                DataStorePlayerProgressResetRepository(store.dataStore).reset(),
            )

            val stored = store.dataStore.data.first()
            assertNull(stored[intPreferencesKey("tutorial.schema_version")])
            assertNull(stored[stringPreferencesKey("tutorial.catalog_revision")])
            assertNull(stored[stringPreferencesKey("tutorial.progress")])
            assertEquals(
                "preset-hash",
                stored[DataStoreDeveloperProgressPresetAdapter.APPLIED_PROGRESS_PRESET_HASH],
            )
        }
    }

    private fun testStore(): TestStore {
        val file = File.createTempFile("urkaaz-tutorial-", ".preferences_pb")
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

    private class TestStore(
        private val file: File,
        private val scope: CoroutineScope,
        val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    ) : AutoCloseable {
        override fun close() {
            scope.cancel()
            file.delete()
        }
    }

    private class FailingDataStore(
        private val failure: IOException,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw failure }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw failure
    }
}
