package com.urkaaaz.persistence

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.loadout.LoadoutRepository
import com.urkaaaz.application.loadout.LoadoutStorageFailure
import com.urkaaaz.application.loadout.LoadoutStorageResult
import com.urkaaaz.application.loadout.LoadoutStorageWriteResult
import com.urkaaaz.application.loadout.MatchLoadout
import com.urkaaaz.application.loadout.StoredLoadoutSelection
import java.io.IOException
import kotlinx.coroutines.flow.first

class DataStoreLoadoutRepository(
    private val dataStore: DataStore<Preferences>,
) : LoadoutRepository {
    override suspend fun read(level: Int): LoadoutStorageResult {
        if (level <= 0) {
            return LoadoutStorageResult.Failure(LoadoutStorageFailure.INVALID_LEVEL)
        }
        val preferences = try {
            dataStore.data.first()
        } catch (error: IOException) {
            return LoadoutStorageResult.Failure(
                if (error is CorruptionException) {
                    LoadoutStorageFailure.INVALID_DATA
                } else {
                    LoadoutStorageFailure.STORAGE_UNAVAILABLE
                },
            )
        }

        val keys = LevelKeys(level)
        val hasSelection = preferences.contains(keys.units) ||
            preferences.contains(keys.ammunition) ||
            preferences.contains(keys.spells)
        val version = preferences[LOADOUT_VERSION_KEY]
        if (!preferences.hasLoadoutFields() && version == null) {
            return LoadoutStorageResult.Success(null)
        }

        if (version == null) {
            return LoadoutStorageResult.Failure(LoadoutStorageFailure.INVALID_DATA)
        }
        if (version != LOADOUT_VERSION) {
            return LoadoutStorageResult.Failure(LoadoutStorageFailure.UNSUPPORTED_VERSION)
        }
        if (!hasSelection) return LoadoutStorageResult.Success(null)

        val units = preferences[keys.units]
        val ammunition = preferences[keys.ammunition]
        val spells = preferences[keys.spells]
        if (units == null || ammunition == null || spells == null) {
            return LoadoutStorageResult.Failure(LoadoutStorageFailure.INVALID_DATA)
        }

        return LoadoutStorageResult.Success(
            StoredLoadoutSelection(
                unitTypes = decodeIdentifiers(units),
                ammunitionSlots = decodeSlots(ammunition),
                spellTypes = decodeIdentifiers(spells),
            ),
        )
    }

    override suspend fun save(
        level: Int,
        loadout: MatchLoadout,
    ): LoadoutStorageWriteResult {
        if (level <= 0) {
            return LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.INVALID_LEVEL)
        }

        val keys = LevelKeys(level)
        val preferences = try {
            dataStore.updateData { current ->
                val version = current[LOADOUT_VERSION_KEY]
                if (
                    version != null && version != LOADOUT_VERSION ||
                    version == null && current.hasLoadoutFields()
                ) {
                    current
                } else {
                    current.toMutablePreferences().apply {
                        this[LOADOUT_VERSION_KEY] = LOADOUT_VERSION
                        this[keys.units] = encodeIdentifiers(loadout.unitTypes)
                        this[keys.ammunition] = encodeSlots(loadout.ammunitionSlots)
                        this[keys.spells] = encodeIdentifiers(loadout.spellTypes)
                    }
                }
            }
        } catch (error: IOException) {
            return LoadoutStorageWriteResult.Failure(
                if (error is CorruptionException) {
                    LoadoutStorageFailure.INVALID_DATA
                } else {
                    LoadoutStorageFailure.STORAGE_UNAVAILABLE
                },
            )
        }

        return when (preferences[LOADOUT_VERSION_KEY]) {
            LOADOUT_VERSION -> LoadoutStorageWriteResult.Saved
            null -> LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.INVALID_DATA)
            else -> LoadoutStorageWriteResult.Failure(LoadoutStorageFailure.UNSUPPORTED_VERSION)
        }
    }

    private fun encodeIdentifiers(identifiers: List<String>): String =
        identifiers.joinToString("|")

    private fun decodeIdentifiers(encoded: String): List<String> =
        if (encoded.isEmpty()) emptyList() else encoded.split('|')

    private fun encodeSlots(slots: List<String?>): String =
        slots.joinToString("|") { it ?: EMPTY_SLOT }

    private fun decodeSlots(encoded: String): List<String?> =
        encoded.split('|').map { value -> value.takeUnless { it == EMPTY_SLOT } }

    private fun Preferences.hasLoadoutFields(): Boolean =
        asMap().keys.any { key ->
            key.name.startsWith(LOADOUT_KEY_PREFIX) && key.name != LOADOUT_VERSION_KEY.name
        }

    private class LevelKeys(level: Int) {
        val units = stringPreferencesKey("loadout.$level.units")
        val ammunition = stringPreferencesKey("loadout.$level.ammunition")
        val spells = stringPreferencesKey("loadout.$level.spells")
    }

    private companion object {
        const val LOADOUT_VERSION = 1
        const val EMPTY_SLOT = "-"
        const val LOADOUT_KEY_PREFIX = "loadout."
        val LOADOUT_VERSION_KEY = intPreferencesKey("loadout.schema_version")
    }
}
