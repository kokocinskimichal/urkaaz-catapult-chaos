package com.urkaaaz.persistence

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.urkaaaz.application.tutorial.TutorialCatalogValidator
import com.urkaaaz.application.tutorial.TutorialProgress
import com.urkaaaz.application.tutorial.TutorialProgressFailure
import com.urkaaaz.application.tutorial.TutorialProgressRead
import com.urkaaaz.application.tutorial.TutorialProgressRepository
import com.urkaaaz.application.tutorial.TutorialProgressWrite
import com.urkaaaz.application.tutorial.TutorialStatus
import java.io.IOException
import kotlinx.coroutines.flow.first

class DataStoreTutorialProgressRepository(
    private val dataStore: DataStore<Preferences>,
    private val logWarning: (String) -> Unit = { message ->
        Log.w(TAG, message)
    },
) : TutorialProgressRepository {
    override suspend fun load(validTutorialIds: Set<String>): TutorialProgressRead =
        try {
            decode(dataStore.data.first(), validTutorialIds)
        } catch (_: IOException) {
            TutorialProgressRead.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE)
        }

    override suspend fun markTerminal(
        tutorialId: String,
        status: TutorialStatus,
        schemaVersion: Int,
        catalogRevision: String,
    ): TutorialProgressWrite {
        if (
            !TUTORIAL_ID.matches(tutorialId) ||
            schemaVersion != TutorialCatalogValidator.SUPPORTED_SCHEMA_VERSION ||
            catalogRevision.isBlank()
        ) {
            return TutorialProgressWrite.Failure(TutorialProgressFailure.INVALID_DATA)
        }
        var result: TutorialProgressWrite = TutorialProgressWrite.Saved
        return try {
            dataStore.updateData { current ->
                when (val decoded = decode(current, validTutorialIds = null)) {
                    is TutorialProgressRead.Failure -> {
                        result = TutorialProgressWrite.Failure(decoded.reason)
                        current
                    }
                    is TutorialProgressRead.Available -> {
                        val next = decoded.progress.terminalStatuses + (tutorialId to status)
                        current.toMutablePreferences().apply {
                            this[SCHEMA_VERSION] = schemaVersion
                            this[CATALOG_REVISION] = catalogRevision
                            this[TERMINAL_STATUSES] = encodeStatuses(next)
                        }
                    }
                }
            }
            result
        } catch (_: IOException) {
            TutorialProgressWrite.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE)
        }
    }

    override suspend fun reset(tutorialId: String?): TutorialProgressWrite {
        if (tutorialId != null && !TUTORIAL_ID.matches(tutorialId)) {
            return TutorialProgressWrite.Failure(TutorialProgressFailure.INVALID_DATA)
        }
        var result: TutorialProgressWrite = TutorialProgressWrite.Saved
        return try {
            dataStore.updateData { current ->
                val schemaVersion = current[SCHEMA_VERSION]
                val revision = current[CATALOG_REVISION]
                val rawStatuses = current[TERMINAL_STATUSES]
                if (
                    schemaVersion == null && revision == null && rawStatuses == null &&
                    tutorialId != null
                ) {
                    current
                } else if (
                    schemaVersion != null &&
                    schemaVersion > TutorialCatalogValidator.SUPPORTED_SCHEMA_VERSION
                ) {
                    result = TutorialProgressWrite.Failure(
                        TutorialProgressFailure.UNSUPPORTED_SCHEMA,
                    )
                    current
                } else if (
                    schemaVersion != null &&
                    schemaVersion != TutorialCatalogValidator.SUPPORTED_SCHEMA_VERSION
                ) {
                    result = TutorialProgressWrite.Failure(
                        TutorialProgressFailure.UNSUPPORTED_SCHEMA,
                    )
                    current
                } else if (tutorialId == null) {
                    current.toMutablePreferences().apply {
                        remove(SCHEMA_VERSION)
                        remove(CATALOG_REVISION)
                        remove(TERMINAL_STATUSES)
                    }
                } else {
                    val decoded = decodeStoredStatuses(rawStatuses)
                    if (
                        decoded == null ||
                        revision.isNullOrBlank() ||
                        rawStatuses == null
                    ) {
                        result = TutorialProgressWrite.Failure(
                            TutorialProgressFailure.INVALID_DATA,
                        )
                        current
                    } else {
                        val remaining = decoded - tutorialId
                        if (remaining.isEmpty()) {
                            current.toMutablePreferences().apply {
                                remove(SCHEMA_VERSION)
                                remove(CATALOG_REVISION)
                                remove(TERMINAL_STATUSES)
                            }
                        } else {
                            current.toMutablePreferences().apply {
                                this[TERMINAL_STATUSES] = encodeStatuses(remaining)
                            }
                        }
                    }
                }
            }
            result
        } catch (_: IOException) {
            TutorialProgressWrite.Failure(TutorialProgressFailure.STORAGE_UNAVAILABLE)
        }
    }

    private fun decode(
        preferences: Preferences,
        validTutorialIds: Set<String>?,
    ): TutorialProgressRead {
        val schemaVersion = preferences[SCHEMA_VERSION]
        val revision = preferences[CATALOG_REVISION]
        val rawStatuses = preferences[TERMINAL_STATUSES]
        if (schemaVersion == null && revision == null && rawStatuses == null) {
            return TutorialProgressRead.Available(TutorialProgress())
        }
        if (schemaVersion == null || revision.isNullOrBlank() || rawStatuses == null) {
            return TutorialProgressRead.Failure(TutorialProgressFailure.INVALID_DATA)
        }
        if (schemaVersion > TutorialCatalogValidator.SUPPORTED_SCHEMA_VERSION) {
            return TutorialProgressRead.Failure(TutorialProgressFailure.UNSUPPORTED_SCHEMA)
        }
        if (schemaVersion != TutorialCatalogValidator.SUPPORTED_SCHEMA_VERSION) {
            return TutorialProgressRead.Failure(TutorialProgressFailure.UNSUPPORTED_SCHEMA)
        }
        val storedStatuses = decodeStoredStatuses(rawStatuses)
            ?: return TutorialProgressRead.Failure(TutorialProgressFailure.INVALID_DATA)
        val statuses = if (validTutorialIds == null) {
            storedStatuses
        } else {
            storedStatuses.filterKeys { id ->
                if (id in validTutorialIds) {
                    true
                } else {
                    logWarning("Ignoring persisted progress for unknown tutorial '$id'")
                    false
                }
            }
        }
        return TutorialProgressRead.Available(
            TutorialProgress(
                terminalStatuses = statuses,
                schemaVersion = schemaVersion,
                catalogRevision = revision,
            ),
        )
    }

    private fun decodeStoredStatuses(value: String?): Map<String, TutorialStatus>? {
        if (value == null || value.isEmpty()) return emptyMap()
        val statuses = linkedMapOf<String, TutorialStatus>()
        value.split(ENTRY_SEPARATOR).forEach { entry ->
            val parts = entry.split(STATUS_SEPARATOR)
            if (
                parts.size != 2 ||
                !TUTORIAL_ID.matches(parts[0]) ||
                parts[0] in statuses
            ) {
                return null
            }
            val status = TutorialStatus.entries.firstOrNull { it.name == parts[1] }
                ?: return null
            statuses[parts[0]] = status
        }
        return statuses
    }

    private fun encodeStatuses(statuses: Map<String, TutorialStatus>): String =
        statuses.toSortedMap().entries.joinToString(ENTRY_SEPARATOR) { (id, status) ->
            "$id$STATUS_SEPARATOR${status.name}"
        }

    private companion object {
        const val TAG = "TutorialProgress"
        const val ENTRY_SEPARATOR = "|"
        const val STATUS_SEPARATOR = "="
        val TUTORIAL_ID = Regex("[a-z][a-z0-9._-]*")
        val SCHEMA_VERSION = intPreferencesKey("tutorial.schema_version")
        val CATALOG_REVISION = stringPreferencesKey("tutorial.catalog_revision")
        val TERMINAL_STATUSES = stringPreferencesKey("tutorial.progress")
    }
}
