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

enum class LoadoutStorageScope {
    CAMPAIGN_LEVEL,
    DEMO,
}

class DataStoreLoadoutRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: LoadoutStorageScope = LoadoutStorageScope.CAMPAIGN_LEVEL,
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

        val keys = LevelKeys(level, scope)
        val hasSelection = preferences.contains(keys.units) ||
            preferences.contains(keys.ammunition) ||
            preferences.contains(keys.spells)
        val hasLoadoutFields = preferences.hasLoadoutFields(scope)
        val version = preferences.loadoutVersion(scope)
        if (!hasLoadoutFields && version == null) {
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

        val keys = LevelKeys(level, scope)
        val preferences = try {
            dataStore.updateData { current ->
                val version = current.loadoutVersion(scope)
                if (
                    version != null && version != LOADOUT_VERSION ||
                    version == null && current.hasLoadoutFields(scope)
                ) {
                    current
                } else {
                    current.toMutablePreferences().apply {
                        this[scope.versionKey()] = LOADOUT_VERSION
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

        return when (preferences.loadoutVersion(scope)) {
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

    private fun Preferences.loadoutVersion(scope: LoadoutStorageScope): Int? =
        when (scope) {
            LoadoutStorageScope.CAMPAIGN_LEVEL -> this[LOADOUT_VERSION_KEY]
            LoadoutStorageScope.DEMO -> this[DEMO_LOADOUT_VERSION_KEY]
                ?: if (hasLoadoutFields(scope)) this[LOADOUT_VERSION_KEY] else null
        }

    private fun LoadoutStorageScope.versionKey(): Preferences.Key<Int> =
        when (this) {
            LoadoutStorageScope.CAMPAIGN_LEVEL -> LOADOUT_VERSION_KEY
            LoadoutStorageScope.DEMO -> DEMO_LOADOUT_VERSION_KEY
        }

    private fun Preferences.hasLoadoutFields(scope: LoadoutStorageScope): Boolean =
        asMap().keys.any { key ->
            when (scope) {
                LoadoutStorageScope.CAMPAIGN_LEVEL ->
                    key.name.startsWith(LOADOUT_KEY_PREFIX) &&
                        !key.name.startsWith(DEMO_LOADOUT_KEY_PREFIX) &&
                        key.name != LOADOUT_VERSION_KEY.name
                LoadoutStorageScope.DEMO ->
                    key.name.startsWith(DEMO_LOADOUT_KEY_PREFIX) &&
                        key.name != DEMO_LOADOUT_VERSION_KEY.name
            }
        }

    private class LevelKeys(level: Int, scope: LoadoutStorageScope) {
        private val prefix = when (scope) {
            LoadoutStorageScope.CAMPAIGN_LEVEL -> "loadout.$level"
            LoadoutStorageScope.DEMO -> "loadout.demo"
        }
        val units = stringPreferencesKey("$prefix.units")
        val ammunition = stringPreferencesKey("$prefix.ammunition")
        val spells = stringPreferencesKey("$prefix.spells")
    }

    private companion object {
        const val LOADOUT_VERSION = 1
        const val EMPTY_SLOT = "-"
        const val LOADOUT_KEY_PREFIX = "loadout."
        const val DEMO_LOADOUT_KEY_PREFIX = "loadout.demo."
        val LOADOUT_VERSION_KEY = intPreferencesKey("loadout.schema_version")
        val DEMO_LOADOUT_VERSION_KEY =
            intPreferencesKey("${DEMO_LOADOUT_KEY_PREFIX}schema_version")
    }
}
